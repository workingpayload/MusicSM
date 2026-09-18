package com.example.musicsm.domain.jam

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Everything a guest needs to reach a host, as carried by the join QR code or link.
 *
 * The [token] is not a security boundary in any serious sense — anyone who can see the QR can
 * join, which is the point. It exists so that another MusicSM user who happens to be on the same
 * Wi-Fi cannot stumble into the session by port-scanning, and so a stale link stops working once
 * the host restarts.
 */
data class JamInvite(
    val address: String,
    val port: Int,
    val token: String,
    val sessionName: String,
) {
    fun toUri(): String = buildString {
        append(LINK_PREFIX)
        append("?h=").append(encode(address))
        append("&p=").append(port)
        append("&t=").append(encode(token))
        append("&n=").append(encode(sessionName))
    }

    companion object {
        const val LINK_PREFIX = "musicsm://jam"

        /** Parses a `musicsm://jam?…` link, or returns null if anything is missing or malformed. */
        fun parse(uri: String): JamInvite? {
            if (!uri.startsWith(LINK_PREFIX)) return null
            val query = uri.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
            val params = query.split('&').mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                val value = pair.substringAfter('=', "")
                if (key.isEmpty()) null else key to decode(value)
            }.toMap()

            val address = params["h"]?.takeIf { it.isNotBlank() } ?: return null
            // Reject anything outside the ephemeral range a ServerSocket(0) can hand out.
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val token = params["t"]?.takeIf { it.isNotBlank() } ?: return null
            return JamInvite(
                address = address,
                port = port,
                token = token,
                sessionName = params["n"].orEmpty(),
            )
        }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun decode(value: String) =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }
}
