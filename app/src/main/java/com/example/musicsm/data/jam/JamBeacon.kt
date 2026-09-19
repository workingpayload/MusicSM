package com.example.musicsm.data.jam

import android.util.Log
import com.example.musicsm.domain.jam.JamDiscoveryProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * The host half of discovery: a UDP socket that answers probes so guests can find this device.
 *
 * It exists because the QR code turned out not to be a usable discovery mechanism on real phones —
 * OEM camera apps refuse to open a custom scheme — so the app has to do its own finding.
 *
 * [announcementProvider] is called per probe rather than captured once, so the beacon always
 * reports the live session and stops answering by itself the moment hosting ends.
 */
internal class JamBeacon(
    private val scope: CoroutineScope,
    private val wifiLock: JamWifiLock? = null,
    private val announcementProvider: () -> Announcement?,
) {

    /**
     * What to say when asked. [discoverable] only gates *browse* probes — a guest who was told
     * [code] is always answered, so a host can stay out of the list and still be joinable.
     */
    data class Announcement(
        val info: JamDiscoveryProtocol.Announcement,
        val code: String,
        val discoverable: Boolean,
    )

    private var socket: DatagramSocket? = null
    private var job: Job? = null

    fun start(): Boolean = runCatching {
        // Samsung and OnePlus Wi-Fi drivers drop broadcast frames that aren't addressed to the
        // device unless this lock is held, which would make the host silently undiscoverable.
        wifiLock?.acquire()
        // Reuse matters: a beacon torn down a moment ago can leave the port in TIME_WAIT, and
        // failing to rebind would silently make the next Jam undiscoverable.
        val bound = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(JamDiscoveryProtocol.PORT))
        }
        socket = bound
        job = scope.launch(Dispatchers.IO) { listen(bound) }
        true
    }.getOrElse {
        Log.w(TAG, "Could not start Jam beacon", it)
        wifiLock?.release()
        false
    }

    fun stop() {
        job?.cancel()
        job = null
        // close() is what unblocks the receive() the listen loop is parked in.
        runCatching { socket?.close() }
        socket = null
        wifiLock?.release()
    }

    private fun listen(bound: DatagramSocket) {
        val buffer = ByteArray(JamDiscoveryProtocol.MAX_PACKET_BYTES)
        while (!bound.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = runCatching { bound.receive(packet); true }.getOrElse { return }
            if (!received) return

            val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            val probe = JamDiscoveryProtocol.decodeProbe(raw) ?: continue
            val announcement = announcementProvider() ?: continue

            val shouldAnswer = when (val code = probe.code) {
                null -> announcement.discoverable
                else -> code.equals(announcement.code, ignoreCase = true)
            }
            if (!shouldAnswer) continue

            val reply = JamDiscoveryProtocol.encodeReply(announcement.info).toByteArray(Charsets.UTF_8)
            runCatching {
                // Unicast straight back at whoever asked, so the reply never needs a broadcast.
                bound.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
            }.onFailure { Log.d(TAG, "Jam beacon reply failed", it) }
        }
    }

    private companion object {
        const val TAG = "JamBeacon"
    }
}
