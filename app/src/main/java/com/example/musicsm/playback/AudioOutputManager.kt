package com.example.musicsm.playback

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.musicsm.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioOutputKind {
    PHONE,
    BLUETOOTH,
    WIRED,
    USB,
    HDMI,
    CAST,
    OTHER,
}

data class AudioOutputDevice(
    val id: String,
    val name: String,
    val kind: AudioOutputKind,
    val isCurrent: Boolean,
    val canSelectInApp: Boolean,
    val isNameGeneric: Boolean = false,
)

data class AudioOutputState(
    val outputs: List<AudioOutputDevice> = emptyList(),
    val current: AudioOutputDevice? = null,
    val hasBluetoothOutput: Boolean = false,
    val bluetoothNamePermissionMissing: Boolean = false,
    val canOpenSystemSwitcher: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    val canOpenOutputSettings: Boolean = true,
)

@Singleton
class AudioOutputManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mediaRouter = appContext.getSystemService(MediaRouter::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    private val _preferredDevice = MutableStateFlow<AudioDeviceInfo?>(null)

    /**
     * Output the user picked in-app, applied to the players via `setPreferredAudioDevice`. `null`
     * means "follow Android's routing". This is the only switch that still works for apps: the
     * old MediaRouter route selection stopped moving media audio in Android 10.
     */
    val preferredDevice: StateFlow<AudioDeviceInfo?> = _preferredDevice.asStateFlow()

    private var deviceMap: Map<String, AudioDeviceInfo> = emptyMap()
    private var systemDeviceKey: String? = null

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshAfterRouteSettles()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshAfterRouteSettles()
    }

    private val settledRefresh = Runnable { refreshNow() }

    /** Device lists change before the platform re-routes media, so check again shortly after. */
    private fun refreshAfterRouteSettles() {
        refresh()
        mainHandler.removeCallbacks(settledRefresh)
        mainHandler.postDelayed(settledRefresh, ROUTE_SETTLE_MS)
    }

    private val routeCallback = object : MediaRouter.SimpleCallback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, type: Int, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteUnselected(router: MediaRouter, type: Int, route: MediaRouter.RouteInfo) = refresh()
    }

    init {
        runCatching { audioManager?.registerAudioDeviceCallback(audioCallback, mainHandler) }
        runCatching {
            mediaRouter?.addCallback(
                MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
                routeCallback,
                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS,
            )
        }
        refresh()
    }

    fun refresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshNow()
        } else {
            mainHandler.post { refreshNow() }
        }
    }

    fun selectOutput(outputId: String): Boolean {
        val device = deviceMap[outputId] ?: return false
        // Picking what Android would route to anyway drops the override, so later connects and
        // disconnects route normally again.
        _preferredDevice.value = device.takeIf { outputKey(it) != systemDeviceKey }
        refreshNow()
        return true
    }

    fun openSystemOutputSwitcher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opened = runCatching {
                MediaRouter2.getInstance(appContext).showSystemOutputSwitcher()
            }.getOrDefault(false)
            if (opened) return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (startPanel(Settings.Panel.ACTION_VOLUME)) return true
        }
        return startActivity(Settings.ACTION_BLUETOOTH_SETTINGS) || startActivity(Settings.ACTION_SOUND_SETTINGS)
    }

    private fun refreshNow() {
        val bluetoothPermissionMissing = bluetoothNamePermissionMissing()
        val devices = runCatching { audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull()
            .orEmpty()
            .filter { isMediaOutput(it.type) }
        val systemKey = systemMediaDevice(devices)?.let(::outputKey)
        systemDeviceKey = systemKey

        // Drop the in-app override once it no longer means anything: the device went away, or
        // Android now routes there by itself (e.g. the headset the user moved away from disconnected).
        val preferred = _preferredDevice.value
            ?.let { p -> devices.firstOrNull { it.id == p.id } }
            ?.takeIf { outputKey(it) != systemKey }
        if (preferred == null && _preferredDevice.value != null) _preferredDevice.value = null
        val currentKey = preferred?.let(::outputKey) ?: systemKey

        val newDeviceMap = linkedMapOf<String, AudioDeviceInfo>()
        // A single physical output shows up as several AudioDeviceInfos (e.g. A2DP + LE audio for
        // one headset, or one entry per USB endpoint), so collapse them by kind + name.
        val outputs = devices
            .groupBy(::outputKey)
            .map { (key, group) ->
                val device = group.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: group.first()
                val kind = deviceKind(device.type)
                val genericName = genericName(kind)
                val name = deviceName(device, kind, genericName, bluetoothPermissionMissing)
                val id = "device:$key"
                newDeviceMap[id] = device
                AudioOutputDevice(
                    id = id,
                    name = name,
                    kind = kind,
                    isCurrent = key == currentKey,
                    canSelectInApp = true,
                    isNameGeneric = name == genericName,
                )
            }
            .sortedWith(compareByDescending<AudioOutputDevice> { it.isCurrent }.thenBy { it.kind.ordinal })
        deviceMap = newDeviceMap
        _state.value = AudioOutputState(
            outputs = outputs,
            current = outputs.firstOrNull { it.isCurrent } ?: outputs.firstOrNull { it.kind == AudioOutputKind.PHONE },
            hasBluetoothOutput = outputs.any { it.kind == AudioOutputKind.BLUETOOTH },
            bluetoothNamePermissionMissing = bluetoothPermissionMissing,
            canOpenSystemSwitcher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            canOpenOutputSettings = true,
        )
    }

    /** Where Android routes music when the app expresses no preference. */
    private fun systemMediaDevice(devices: List<AudioDeviceInfo>): AudioDeviceInfo? {
        if (devices.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val routed = runCatching {
                audioManager?.getAudioDevicesForAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
            }.getOrNull().orEmpty()
            routed.firstNotNullOfOrNull { r -> devices.firstOrNull { it.id == r.id } }?.let { return it }
            routed.firstNotNullOfOrNull { r -> devices.firstOrNull { it.type == r.type } }?.let { return it }
        }
        // Older platforms have no public query; media goes to the most personal connected output.
        // (MediaRouter's selected route is not reliable here — it can report the speaker while
        // A2DP is playing.)
        return devices.minByOrNull { routingPriority(deviceKind(it.type)) }
    }

    private fun routingPriority(kind: AudioOutputKind): Int = when (kind) {
        AudioOutputKind.BLUETOOTH -> 0
        AudioOutputKind.WIRED -> 1
        AudioOutputKind.USB -> 2
        AudioOutputKind.HDMI -> 3
        AudioOutputKind.CAST -> 4
        AudioOutputKind.PHONE -> 5
        AudioOutputKind.OTHER -> 6
    }

    private fun outputKey(device: AudioDeviceInfo): String {
        val kind = deviceKind(device.type)
        // The built-in speaker is one output no matter how many ports the HAL exposes.
        if (kind == AudioOutputKind.PHONE) return kind.name
        val product = runCatching { device.productName?.toString() }.getOrNull().orEmpty()
        return "${kind.name}:${product.lowercase()}"
    }

    private fun deviceName(
        device: AudioDeviceInfo,
        kind: AudioOutputKind,
        genericName: String,
        bluetoothPermissionMissing: Boolean,
    ): String {
        // The built-in speaker reports the phone's model number as its product name; say what it is.
        if (kind == AudioOutputKind.PHONE) return genericName
        if (kind == AudioOutputKind.BLUETOOTH && bluetoothPermissionMissing) return genericName
        return runCatching { device.productName?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: genericName
    }

    private fun deviceKind(type: Int): AudioOutputKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutputKind.PHONE
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioOutputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioOutputKind.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputKind.USB
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> AudioOutputKind.HDMI
        else -> AudioOutputKind.OTHER
    }

    /**
     * Outputs a person would pick for music. Excludes the earpiece and call-only SCO links, which
     * otherwise appear as duplicate "phone" and "headset" entries.
     */
    private fun isMediaOutput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_TELEPHONY -> false
        else -> deviceKind(type) != AudioOutputKind.OTHER
    }

    private fun genericName(kind: AudioOutputKind): String = when (kind) {
        AudioOutputKind.PHONE -> appContext.getString(R.string.audio_output_phone_speaker)
        AudioOutputKind.BLUETOOTH -> appContext.getString(R.string.audio_output_bluetooth_device)
        AudioOutputKind.WIRED -> appContext.getString(R.string.audio_output_wired_device)
        AudioOutputKind.USB -> appContext.getString(R.string.audio_output_usb_device)
        AudioOutputKind.HDMI -> appContext.getString(R.string.audio_output_hdmi_device)
        AudioOutputKind.CAST -> appContext.getString(R.string.audio_output_cast_device)
        AudioOutputKind.OTHER -> appContext.getString(R.string.audio_output_device)
    }

    private fun bluetoothNamePermissionMissing(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

    private fun startPanel(action: String): Boolean = startActivity(action)

    private fun startActivity(action: String): Boolean = runCatching {
        appContext.startActivity(
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.recoverCatching { error ->
        if (error is ActivityNotFoundException) false else throw error
    }.getOrDefault(false)

    private companion object {
        const val ROUTE_SETTLE_MS = 700L
    }
}
