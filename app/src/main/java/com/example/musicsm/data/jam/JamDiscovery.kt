package com.example.musicsm.data.jam

import android.util.Log
import com.example.musicsm.domain.jam.DiscoveredJam
import com.example.musicsm.domain.jam.JamDiscoveryProtocol
import com.example.musicsm.domain.jam.JamInvite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

/**
 * The guest half of discovery: broadcast a probe, collect whatever answers.
 *
 * Probes are sent more than once and to every broadcast address the device has, because UDP is
 * allowed to drop them and because phones routinely hold several interfaces at once (Wi-Fi, a VPN,
 * a tether). Sending wide and deduplicating is far cheaper than guessing the right interface.
 */
internal object JamDiscovery {

    /** Every Jam that answers within [timeoutMs], newest answer per host winning. */
    suspend fun browse(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        targets: List<InetAddress> = broadcastTargets(),
    ): List<DiscoveredJam> = probe(code = null, timeoutMs = timeoutMs, targets = targets) { false }

    /**
     * Finds the single host holding [code].
     *
     * Returns as soon as one answers rather than waiting out the timeout — there is only ever one
     * owner of a code, so there is nothing to be gained by listening longer.
     */
    suspend fun resolve(
        code: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        targets: List<InetAddress> = broadcastTargets(),
    ): JamInvite? = probe(code = code, timeoutMs = timeoutMs, targets = targets) { true }
        .firstOrNull()
        ?.invite

    private suspend fun probe(
        code: String?,
        timeoutMs: Long,
        targets: List<InetAddress>,
        stopEarly: (DiscoveredJam) -> Boolean,
    ): List<DiscoveredJam> = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext emptyList()

        val found = LinkedHashMap<String, DiscoveredJam>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = POLL_MS

                val payload = JamDiscoveryProtocol.encodeProbe(code).toByteArray(Charsets.UTF_8)
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(JamDiscoveryProtocol.MAX_PACKET_BYTES)
                var nextSendAt = 0L

                while (System.currentTimeMillis() < deadline) {
                    coroutineContext.ensureActive()

                    // Re-send periodically: a single lost datagram must not cost the whole scan.
                    if (System.currentTimeMillis() >= nextSendAt) {
                        targets.forEach { target ->
                            runCatching {
                                socket.send(
                                    DatagramPacket(payload, payload.size, target, JamDiscoveryProtocol.PORT),
                                )
                            }
                        }
                        nextSendAt = System.currentTimeMillis() + RESEND_MS
                    }

                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val address = packet.address?.hostAddress ?: continue
                    val jam = JamDiscoveryProtocol.decodeReply(raw, address) ?: continue
                    found[jam.key] = jam
                    if (stopEarly(jam)) return@use
                }
            }
        }.onFailure { Log.d(TAG, "Jam discovery failed", it) }

        found.values.toList()
    }

    /**
     * Broadcast addresses for every usable interface, plus the global one.
     *
     * The per-interface addresses are what actually work: many Android builds drop packets sent to
     * 255.255.255.255, while a directed subnet broadcast gets through. Both are tried.
     */
    private fun broadcastTargets(): List<InetAddress> = runCatching {
        val targets = LinkedHashSet<InetAddress>()
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { it.broadcast }
            .filterIsInstance<Inet4Address>()
            .forEach(targets::add)
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        targets.toList()
    }.getOrElse { emptyList() }

    private const val TAG = "JamDiscovery"
    private const val DEFAULT_TIMEOUT_MS = 2_500L
    private const val RESEND_MS = 600L
    private const val POLL_MS = 250
}
