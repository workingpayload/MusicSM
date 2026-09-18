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
