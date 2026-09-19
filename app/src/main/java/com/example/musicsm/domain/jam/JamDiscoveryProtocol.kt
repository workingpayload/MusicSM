package com.example.musicsm.domain.jam

/**
 * The UDP side of Jam: how a guest finds a host without scanning anything.
 *
 * A guest broadcasts a probe; any host that wants to answer replies **unicast** to the sender.
 * The host's address is therefore never written into a datagram — it is read from the packet's
 * source address, which is both shorter and more reliable than a host trying to guess which of its
 * own interfaces a guest can actually reach.
 *
 * Two kinds of probe share one format:
 *
 * - a **browse** probe (no code) asks "is anyone hosting?", and is answered only by hosts that
 *   have chosen to be discoverable;
 * - a **code** probe asks "who owns `K7M-2P9`?", and is answered whether or not the host is
 *   listed, so a host can stay hidden and still be joinable by someone who was told the code.
 *
 * Datagrams are single messages rather than a stream, so there is no framing to get wrong; the
 * only separator needed is [FIELD], and it is stripped from any text written to the wire.
 */
object JamDiscoveryProtocol {

    /**
     * A fixed port, unlike the TCP session's ephemeral one: a guest has to know where to send a
     * probe before it knows anything else about the host.
     */
    const val PORT = 47_654

    /** Replies are tiny; anything larger than this is not ours and is not worth parsing. */
    const val MAX_PACKET_BYTES = 512

    private const val MAGIC = "MSMJAM1"
    private const val FIELD = '\u001F'
    private const val KIND_PROBE = "Q"
    private const val KIND_REPLY = "R"
    private const val MAX_NAME_LENGTH = 64

    /** A guest asking who is out there, optionally naming the one host it wants. */
    data class Probe(val code: String?)

    /** What a host says about itself, once the address is filled in from the packet. */
    data class Announcement(
        val sessionName: String,
        val hostName: String,
        val port: Int,
        val token: String,
    )

    fun encodeProbe(code: String? = null): String =
        listOf(MAGIC, KIND_PROBE, clean(code.orEmpty())).joinToString(FIELD.toString())

    fun decodeProbe(raw: String): Probe? {
        val parts = raw.split(FIELD)
        if (parts.getOrNull(0) != MAGIC || parts.getOrNull(1) != KIND_PROBE) return null
        return Probe(code = parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }

    fun encodeReply(announcement: Announcement): String = listOf(
        MAGIC,
        KIND_REPLY,
        clean(announcement.sessionName).take(MAX_NAME_LENGTH),
        clean(announcement.hostName).take(MAX_NAME_LENGTH),
        announcement.port.toString(),
        clean(announcement.token),
    ).joinToString(FIELD.toString())

    /**
     * Turns a reply into something connectable. [address] comes from the datagram's sender, so a
     * malicious payload cannot redirect a guest at a third party.
     */
    fun decodeReply(raw: String, address: String): DiscoveredJam? {
        val parts = raw.split(FIELD)
        if (parts.getOrNull(0) != MAGIC || parts.getOrNull(1) != KIND_REPLY) return null
        val port = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val token = parts.getOrNull(5)?.takeIf { it.isNotBlank() } ?: return null
        if (address.isBlank()) return null
        return DiscoveredJam(
            invite = JamInvite(
                address = address,
                port = port,
                token = token,
                sessionName = parts.getOrElse(2) { "" },
            ),
            hostName = parts.getOrElse(3) { "" },
        )
    }

    private fun clean(value: String) = value
        .replace("\n", " ")
        .replace("\r", " ")
        .replace(FIELD.toString(), "")
}

/** A Jam found on the local network, ready to be joined without scanning or typing. */
data class DiscoveredJam(
    val invite: JamInvite,
    val hostName: String,
) {
    /** Keyed by where it lives, so the same host answering twice is listed once. */
    val key: String get() = "${invite.address}:${invite.port}"
}
