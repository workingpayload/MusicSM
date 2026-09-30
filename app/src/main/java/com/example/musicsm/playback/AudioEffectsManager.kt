package com.example.musicsm.playback

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.musicsm.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the equalizer UI needs to lay itself out, read from the device once a session exists. */
data class EqualizerCapabilities(
    val available: Boolean = false,
    /** Centre frequency of each band, in Hz. */
    val bandFrequencies: List<Int> = emptyList(),
    /** Lowest gain the device accepts, in millibels (typically -1500). */
    val minLevelMb: Int = -1500,
    /** Highest gain the device accepts, in millibels (typically +1500). */
    val maxLevelMb: Int = 1500,
    val presetNames: List<String> = emptyList(),
    val bassBoostSupported: Boolean = false,
    val virtualizerSupported: Boolean = false,
    val loudnessSupported: Boolean = false,
)

/**
 * Owns the `android.media.audiofx` effects attached to the player's audio session.
 *
 * Effects are created **only while the user has them switched on**. An `audiofx` effect stays
 * bound to the output chain it was built for, and on many devices that chain does not survive the
 * move to Bluetooth A2DP: the route switches, the player keeps reporting progress, and nothing
 * comes out of the headphones while the phone speaker still works. A disabled effect is still an
 * *attached* effect, so "effects off" has to mean no effect objects exist at all rather than
 * objects sitting there with `enabled = false`.
 *
 * The consequence is that [capabilities] is only populated while the effects are live, so the
 * equalizer UI fills in its band layout once the feature is enabled rather than before.
 *
 * Every device implements a different subset of these effects, and several OEM ROMs throw from
 * the constructors outright, so each effect is created defensively and simply stays absent when
 * unsupported.
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) {

    private var sessionId: Int = AudioEffect.ERROR_BAD_VALUE
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private val _capabilities = MutableStateFlow(EqualizerCapabilities())
    val capabilities: StateFlow<EqualizerCapabilities> = _capabilities.asStateFlow()

    /**
     * Binds to [audioSessionId]. Any effects held against a previous session are torn down first,
     * so a route change that hands us a new session does not leave the old chain behind.
     */
    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId != sessionId) {
            releaseEffects()
            sessionId = audioSessionId
        }
        sync()
    }

    /**
     * Pushes the current preferences onto the effects, creating or tearing them down as the
     * enabled flag changes. Safe to call from a settings flow collector on every change.
     */
    @Synchronized
    fun apply() {
        sync()
    }

    private fun sync() {
        if (!preferences.effectsEnabledNow) {
            releaseEffects()
            return
        }
        if (sessionId <= 0) return
        if (equalizer == null && bassBoost == null && virtualizer == null && loudness == null) {
            equalizer = create("Equalizer") { Equalizer(PRIORITY, sessionId) }
            bassBoost = create("BassBoost") { BassBoost(PRIORITY, sessionId) }
            virtualizer = create("Virtualizer") { Virtualizer(PRIORITY, sessionId) }
            loudness = create("LoudnessEnhancer") { LoudnessEnhancer(sessionId) }
            _capabilities.value = readCapabilities()
        }
        applySettings()
    }

    private fun applySettings() {
        val enabled = preferences.effectsEnabledNow

        equalizer?.let { eq ->
            runCatching {
                eq.enabled = enabled
                if (!enabled) return@runCatching
                val preset = preferences.equalizerPresetNow
                if (preset != AppPreferences.CUSTOM_PRESET && preset < eq.numberOfPresets) {
                    eq.usePreset(preset.toShort())
                } else {
                    val levels = preferences.equalizerBandsNow
                    val range = eq.bandLevelRange
                    for (band in 0 until eq.numberOfBands) {
                        val level = levels.getOrNull(band) ?: 0
                        eq.setBandLevel(
                            band.toShort(),
                            level.coerceIn(range[0].toInt(), range[1].toInt()).toShort(),
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Equalizer rejected settings", it) }
        }

        bassBoost?.let { fx ->
            runCatching {
                val strength = preferences.bassBoostNow
                fx.enabled = enabled && strength > 0
                if (fx.strengthSupported) fx.setStrength(strength.toShort())
            }.onFailure { Log.w(TAG, "BassBoost rejected settings", it) }
        }

        virtualizer?.let { fx ->
            runCatching {
                val strength = preferences.virtualizerNow
                fx.enabled = enabled && strength > 0
                if (fx.strengthSupported) fx.setStrength(strength.toShort())
            }.onFailure { Log.w(TAG, "Virtualizer rejected settings", it) }
        }

        loudness?.let { fx ->
            runCatching {
                val gain = preferences.loudnessGainMbNow
                fx.enabled = enabled && gain > 0
                fx.setTargetGain(gain)
            }.onFailure { Log.w(TAG, "LoudnessEnhancer rejected settings", it) }
        }
    }

    @Synchronized
    fun release() {
        releaseEffects()
        sessionId = AudioEffect.ERROR_BAD_VALUE
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        _capabilities.value = EqualizerCapabilities()
    }

    /**
     * Current per-band gains. While a device preset is selected the live equalizer is the source
     * of truth (so the sliders show that preset's curve); in custom mode the stored values win.
     */
    @Synchronized
    fun currentBandLevels(): List<Int> {
        val stored = preferences.equalizerBandsNow
        val eq = equalizer ?: return stored
        val useStored = preferences.equalizerPresetNow == AppPreferences.CUSTOM_PRESET
        return runCatching {
            (0 until eq.numberOfBands).map { band ->
                (if (useStored) stored.getOrNull(band) else null)
                    ?: eq.getBandLevel(band.toShort()).toInt()
            }
        }.getOrDefault(stored)
    }

    /**
     * Tells the system (and any installed equalizer app) that this session is open, so a
     * third-party effect panel can attach to it. Paired with [notifySessionClosed].
     */
    fun notifySessionOpen(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
    }

    fun notifySessionClosed(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
    }

    private fun broadcast(action: String, audioSessionId: Int) {
        runCatching {
            context.sendBroadcast(
                android.content.Intent(action).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        }
    }

    private fun readCapabilities(): EqualizerCapabilities {
        val eq = equalizer ?: return EqualizerCapabilities(
            available = false,
            bassBoostSupported = bassBoost != null,
            virtualizerSupported = virtualizer != null,
            loudnessSupported = loudness != null,
        )
        return runCatching {
            val range = eq.bandLevelRange
            EqualizerCapabilities(
                available = true,
                // getCenterFreq() is in millihertz.
                bandFrequencies = (0 until eq.numberOfBands).map {
                    eq.getCenterFreq(it.toShort()) / 1000
                },
                minLevelMb = range[0].toInt(),
                maxLevelMb = range[1].toInt(),
                presetNames = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                bassBoostSupported = bassBoost?.strengthSupported == true,
                virtualizerSupported = virtualizer?.strengthSupported == true,
                loudnessSupported = loudness != null,
            )
        }.getOrElse { EqualizerCapabilities() }
    }

    private fun <T> create(name: String, factory: () -> T): T? =
        runCatching(factory).onFailure { Log.w(TAG, "$name unavailable on this device", it) }.getOrNull()

    private companion object {
        const val TAG = "AudioEffects"

        /** Audio-effect priority; positive values outrank background apps. */
        const val PRIORITY = 100
    }
}
