package com.example.musicsm.domain.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Every discovery datagram arrives from an unknown device, so decoding is the risky half. */
class JamDiscoveryProtocolTest {

    private val announcement = JamDiscoveryProtocol.Announcement(
        sessionName = "Kitchen",
        hostName = "Pixel 7",
        port = 41234,
        token = "s3cr3ttok",
    )

    @Test
    fun `browse probe round trips`() {
        val decoded = JamDiscoveryProtocol.decodeProbe(JamDiscoveryProtocol.encodeProbe())
        assertNotNull(decoded)
        assertNull("a browse probe must not carry a code", decoded!!.code)
    }

    @Test
    fun `code probe round trips`() {
        val decoded = JamDiscoveryProtocol.decodeProbe(JamDiscoveryProtocol.encodeProbe("K7M2P9"))
        assertEquals("K7M2P9", decoded?.code)
    }

    @Test
    fun `blank code is treated as a browse`() {
        val decoded = JamDiscoveryProtocol.decodeProbe(JamDiscoveryProtocol.encodeProbe("   "))
        assertNull(decoded?.code)
    }

    @Test
    fun `reply round trips and takes its address from the caller`() {
        val encoded = JamDiscoveryProtocol.encodeReply(announcement)
        val jam = JamDiscoveryProtocol.decodeReply(encoded, "192.168.1.42")

        assertNotNull(jam)
        assertEquals("192.168.1.42", jam!!.invite.address)
        assertEquals(41234, jam.invite.port)
        assertEquals("s3cr3ttok", jam.invite.token)
        assertEquals("Kitchen", jam.invite.sessionName)
        assertEquals("Pixel 7", jam.hostName)
    }

    @Test
    fun `a decoded reply is directly joinable`() {
        val encoded = JamDiscoveryProtocol.encodeReply(announcement)
        val jam = JamDiscoveryProtocol.decodeReply(encoded, "192.168.1.42")!!
        // The link path and the discovery path must produce identical invites.
        assertEquals(jam.invite, JamInvite.parse(jam.invite.toUri()))
    }

    @Test
    fun `rejects foreign traffic on the port`() {
        assertNull(JamDiscoveryProtocol.decodeProbe("SSDP/1.1 M-SEARCH"))
        assertNull(JamDiscoveryProtocol.decodeReply("SSDP/1.1 200 OK", "192.168.1.42"))
        assertNull(JamDiscoveryProtocol.decodeProbe(""))
        assertNull(JamDiscoveryProtocol.decodeReply("", "192.168.1.42"))
    }

    @Test
    fun `a probe is not mistaken for a reply`() {
        val probe = JamDiscoveryProtocol.encodeProbe("K7M2P9")
        assertNull(JamDiscoveryProtocol.decodeReply(probe, "192.168.1.42"))
    }

    @Test
    fun `a reply is not mistaken for a probe`() {
        assertNull(JamDiscoveryProtocol.decodeProbe(JamDiscoveryProtocol.encodeReply(announcement)))
    }

    @Test
    fun `rejects a reply with an unusable port`() {
        val bad = JamDiscoveryProtocol.encodeReply(announcement.copy(port = 0))
        assertNull(JamDiscoveryProtocol.decodeReply(bad, "192.168.1.42"))

        val huge = JamDiscoveryProtocol.encodeReply(announcement.copy(port = 70000))
        assertNull(JamDiscoveryProtocol.decodeReply(huge, "192.168.1.42"))
    }

    @Test
    fun `rejects a reply with no token`() {
        val bad = JamDiscoveryProtocol.encodeReply(announcement.copy(token = ""))
        assertNull(JamDiscoveryProtocol.decodeReply(bad, "192.168.1.42"))
    }

    @Test
    fun `rejects a reply with no sender address`() {
        val encoded = JamDiscoveryProtocol.encodeReply(announcement)
        assertNull(JamDiscoveryProtocol.decodeReply(encoded, ""))
    }

    @Test
    fun `a session name cannot forge a field boundary`() {
        val hostile = announcement.copy(sessionName = "Evil\u001F9999\u001Fstolen")
        val jam = JamDiscoveryProtocol.decodeReply(
            JamDiscoveryProtocol.encodeReply(hostile),
            "192.168.1.42",
        )
        assertNotNull(jam)
        assertEquals("the real port must survive", 41234, jam!!.invite.port)
        assertEquals("s3cr3ttok", jam.invite.token)
    }

    @Test
    fun `an over-long session name cannot inflate a datagram`() {
        val huge = announcement.copy(sessionName = "x".repeat(4000))
        val encoded = JamDiscoveryProtocol.encodeReply(huge)
        assert(encoded.toByteArray().size < JamDiscoveryProtocol.MAX_PACKET_BYTES) {
            "reply must always fit the receive buffer, was ${encoded.length}"
        }
    }

    @Test
    fun `hosts are keyed by address and port`() {
        val a = JamDiscoveryProtocol.decodeReply(
            JamDiscoveryProtocol.encodeReply(announcement),
            "192.168.1.42",
        )!!
        val b = JamDiscoveryProtocol.decodeReply(
            JamDiscoveryProtocol.encodeReply(announcement),
            "192.168.1.43",
        )!!
        assertEquals("192.168.1.42:41234", a.key)
        assert(a.key != b.key)
    }
}
