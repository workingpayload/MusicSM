package com.example.musicsm.data.jam

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Holds the Wi-Fi radio in a state where it will actually hand us broadcast datagrams.
 *
 * Kept behind an interface purely so the beacon stays a plain JVM object in tests — the socket
 * behaviour is what is worth testing, and it does not depend on any of this.
 */
internal interface JamWifiLock {
    fun acquire()
    fun release()
}

/**
 * Real implementation, backed by [WifiManager.MulticastLock].
 *
 * Android normally filters out packets that aren't addressed to the device, which includes the
 * subnet broadcasts discovery relies on. The lock costs battery while held, so it is taken only
 * while actually hosting a Jam and released the moment hosting stops.
 *
 * It needs the `CHANGE_WIFI_MULTICAST_STATE` permission, which is install-time and never prompts.
 */
internal class WifiMulticastLock(context: Context) : JamWifiLock {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var lock: WifiManager.MulticastLock? = null

    override fun acquire() {
        if (lock != null) return
        runCatching {
            wifiManager?.createMulticastLock(TAG)?.apply {
                setReferenceCounted(false)
                acquire()
                lock = this
            }
        }.onFailure { Log.d(TAG, "Could not acquire multicast lock", it) }
    }

    override fun release() {
        runCatching { lock?.takeIf { it.isHeld }?.release() }
            .onFailure { Log.d(TAG, "Could not release multicast lock", it) }
        lock = null
    }

    private companion object {
        const val TAG = "JamWifiLock"
    }
}
