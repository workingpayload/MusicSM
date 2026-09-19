package com.example.musicsm.data.jam

import com.example.musicsm.domain.jam.JamDiscoveryProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Runs a real [JamBeacon] against real [JamDiscovery] probes over loopback UDP.
 *
 * Discovery exists precisely because the QR path failed on real hardware, so it would be careless
 * to ship it on the strength of reading the code. Loopback can't prove a phone's Wi-Fi driver will
 * deliver a broadcast, but it does prove the probe/answer logic, the code gate and the
 * discoverability gate behave.
 */
class JamDiscoveryLoopbackTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var beacon: JamBeacon? = null

    private val loopback = listOf(InetAddress.getByName("127.0.0.1"))

    @After
    fun tearDown() {
        beacon?.stop()
        scope.cancel()
    }

    private fun startBeacon(
        sessionName: String = "Kitchen",
        hostName: String = "Pixel 7",
        port: Int = 41234,
        token: String = "s3cr3ttok",
        code: String = "K7M2P9",
        discoverable: Boolean = true,
    ): JamBeacon {
        val announcement = JamBeacon.Announcement(
            info = JamDiscoveryProtocol.Announcement(
                sessionName = sessionName,
                hostName = hostName,
                port = port,
                token = token,
            ),
            code = code,
            discoverable = discoverable,
        )
        val b = JamBeacon(scope = scope, wifiLock = null) { announcement }
        assertTrue("beacon could not bind ${JamDiscoveryProtocol.PORT}", b.start())
        beacon = b
        return b
    }

    @Test
    fun `a discoverable host answers a browse probe`() = runBlocking {
        startBeacon()

        val found = JamDiscovery.browse(timeoutMs = 1_500, targets = loopback)

        assertEquals(1, found.size)
        val jam = found.single()
        assertEquals("Kitchen", jam.invite.sessionName)
        assertEquals("Pixel 7", jam.hostName)
        assertEquals(41234, jam.invite.port)
        // The token has to come back, or the guest could never complete the TCP handshake.
        assertEquals("s3cr3ttok", jam.invite.token)
    }

    @Test
    fun `a hidden host stays out of the browse list`() = runBlocking {
        startBeacon(discoverable = false)

        val found = JamDiscovery.browse(timeoutMs = 1_000, targets = loopback)

        assertTrue("a hidden host must not be listed, got $found", found.isEmpty())
    }

    @Test
    fun `a hidden host is still reachable by its code`() = runBlocking {
        startBeacon(discoverable = false)

        val invite = JamDiscovery.resolve("K7M2P9", timeoutMs = 1_500, targets = loopback)

        assertNotNull("hiding must not disable the typed code", invite)
        assertEquals(41234, invite!!.port)
        assertEquals("s3cr3ttok", invite.token)
    }

    @Test
    fun `resolving a code returns a directly connectable invite`() = runBlocking {
        startBeacon()

        val invite = JamDiscovery.resolve("K7M2P9", timeoutMs = 1_500, targets = loopback)

        assertNotNull(invite)
        assertEquals("127.0.0.1", invite!!.address)
        assertEquals("Kitchen", invite.sessionName)
    }

    @Test
    fun `the code check ignores case`() = runBlocking {
        startBeacon(code = "K7M2P9")

        val invite = JamDiscovery.resolve("k7m2p9", timeoutMs = 1_500, targets = loopback)

        assertNotNull(invite)
    }

    @Test
    fun `a wrong code gets no answer`() = runBlocking {
        startBeacon(code = "K7M2P9")

        val invite = JamDiscovery.resolve("ZZZZZZ", timeoutMs = 800, targets = loopback)

        assertNull(invite)
    }

    @Test
    fun `nothing is found once the beacon stops`() = runBlocking {
        val b = startBeacon()
        assertEquals(1, JamDiscovery.browse(timeoutMs = 1_500, targets = loopback).size)

        b.stop()
        beacon = null

        val found = JamDiscovery.browse(timeoutMs = 800, targets = loopback)
        assertTrue("a stopped beacon must go quiet, got $found", found.isEmpty())
    }

    @Test
    fun `the port is released so a later Jam can rebind it`() = runBlocking {
        startBeacon(sessionName = "First").stop()
        beacon = null

        // Without reuseAddress this is exactly where a second Jam would silently fail.
        startBeacon(sessionName = "Second")
        val found = JamDiscovery.browse(timeoutMs = 1_500, targets = loopback)

        assertEquals(1, found.size)
        assertEquals("Second", found.single().invite.sessionName)
    }

    @Test
    fun `a host that stops hosting mid-scan stops answering`() = runBlocking {
        // A null announcement is what the provider returns once state leaves Hosting.
        val b = JamBeacon(scope = scope, wifiLock = null) { null }
        assertTrue(b.start())
        beacon = b

        val found = JamDiscovery.browse(timeoutMs = 800, targets = loopback)

        assertTrue("a beacon with no session must stay silent, got $found", found.isEmpty())
    }

    @Test
    fun `browsing an empty network is a normal empty result`() = runBlocking {
        val found = JamDiscovery.browse(timeoutMs = 600, targets = loopback)

        assertTrue(found.isEmpty())
    }

    @Test
    fun `discovery survives junk arriving on the port`() = runBlocking {
        startBeacon()

        java.net.DatagramSocket().use { noise ->
            val junk = "SSDP/1.1 M-SEARCH * HTTP/1.1".toByteArray()
            repeat(3) {
                noise.send(
                    java.net.DatagramPacket(
                        junk, junk.size, loopback.single(), JamDiscoveryProtocol.PORT,
                    ),
                )
            }
        }

        val found = JamDiscovery.browse(timeoutMs = 1_500, targets = loopback)

        assertEquals("the beacon must survive unrelated traffic", 1, found.size)
    }
}
