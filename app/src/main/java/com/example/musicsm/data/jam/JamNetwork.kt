package com.example.musicsm.data.jam

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The host's address on the local network, so guests can be told where to connect.
 *
 * Enumerating interfaces rather than asking `WifiManager` is deliberate: it needs no permission,
 * and it also works over a hotspot or Ethernet dock, where the Wi-Fi APIs report nothing useful.
 */
internal fun localAddressOrNull(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        // Prefer a real Wi-Fi interface; tethering and VPN interfaces come last.
        .sortedBy { iface ->
            when {
                iface.name.startsWith("wlan") -> 0
                iface.name.startsWith("eth") -> 1
                iface.name.startsWith("ap") -> 2
                else -> 3
            }
        }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

/**
 * Parses an address a user typed on the join screen, such as `192.168.43.1` or `192.168.1.5:47655`.
 *
 * This is the escape hatch for networks that drop the UDP broadcast discovery depends on: a guest
 * who can read the host's address off its screen needs nothing else, because the host prefers a
 * known port and the join code doubles as the token.
 *
 * Returns null if [input] could not be an address, so the caller can show an inline error instead
 * of attempting a doomed connection.
 */
internal fun parseHostAddress(input: String, defaultPort: Int): Pair<String, Int>? {
    val trimmed = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    if (trimmed.isEmpty()) return null

    val host = trimmed.substringBefore(':')
    val portPart = trimmed.substringAfter(':', "")
    val port = when {
        portPart.isEmpty() -> defaultPort
        else -> portPart.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    }

    // Only dotted-quad IPv4 is accepted: a hostname would need a DNS lookup that local networks
    // usually cannot answer, and letting one through would just stall the connection attempt.
    val octets = host.split('.')
    if (octets.size != 4) return null
    if (octets.any { part -> part.toIntOrNull()?.takeIf { it in 0..255 } == null }) return null

    return host to port
}
