package com.example.musicsm.data.jam

import com.example.musicsm.domain.jam.JAM_PROTOCOL_VERSION
import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.jam.JamMember
import com.example.musicsm.domain.jam.JamMessage
import com.example.musicsm.domain.jam.JamProtocol
import com.example.musicsm.domain.jam.JamRole
import com.example.musicsm.domain.jam.JamSnapshot
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Drives a real [JamServer] and [JamClient] against each other over loopback.
 *
 * Jam is the one feature that genuinely cannot be checked by reading the code — the handshake,
 * the token check and the disconnect paths only exist as behaviour between two processes. Running
 * both ends in-process is the closest thing to a two-device test that fits in CI.
 */
class JamLoopbackTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: JamServer? = null
    private var client: JamClient? = null

    private val hostMember = JamMember("host", "Host phone", JamRole.HOST)
    private val token = "s3cr3t"

    @After
    fun tearDown() = runBlocking {
        client?.disconnect()
        server?.stop()
        scope.cancel()
    }

    private fun startServer(
        onCommand: (JamCommand) -> Unit = {},
        onMembersChanged: (List<JamMember>) -> Unit = {},
    ): JamServer = runBlocking {
        val s = JamServer(
            sessionName = "Kitchen",
            token = token,
            hostMember = hostMember,
            scope = scope,
            onCommand = onCommand,
            onMembersChanged = onMembersChanged,
        )
        assertTrue("server should bind a port", s.start())
        server = s
        s
    }

    /** Polls until [condition] holds or the deadline passes; socket work is genuinely async. */
    private fun await(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for: $message")
    }

    private fun song(id: String) = Song(id = id, title = "Song $id", artist = "Artist", durationMs = 1000L)

    @Test
    fun `a guest with the right token connects and receives snapshots`() {
        val s = startServer()
        val sessionName = AtomicReference<String?>(null)
        val snapshots = ConcurrentLinkedQueue<JamSnapshot>()

        client = JamClient(
            hostAddress = LOOPBACK,
            hostPort = s.port,
            token = token,
            displayName = "Guest phone",
            scope = scope,
            onConnected = { sessionName.set(it) },
            onSnapshot = { snapshots += it },
            onDisconnected = {},
        ).also { it.connect() }

        await("guest to be welcomed") { sessionName.get() != null }
        assertEquals("Kitchen", sessionName.get())

        s.broadcast(
            JamMessage.Snapshot(
                JamSnapshot(queue = listOf(song("a"), song("b")), currentIndex = 1, isPlaying = true),
            ),
        )

        await("snapshot to arrive") { snapshots.isNotEmpty() }
        val received = snapshots.last()
        assertEquals(2, received.queue.size)
        assertEquals("b", received.currentSong?.id)
        assertTrue(received.isPlaying)
    }

    @Test
    fun `guest commands reach the host`() {
        val commands = ConcurrentLinkedQueue<JamCommand>()
        val s = startServer(onCommand = { commands += it })
        val connected = AtomicReference<String?>(null)

        client = JamClient(LOOPBACK, s.port, token, "Guest", scope, { connected.set(it) }, {}, {})
            .also { it.connect() }
        await("guest to connect") { connected.get() != null }

        client!!.send(JamCommand.PlayPause)
        client!!.send(JamCommand.Next)
        client!!.send(JamCommand.AddSong(song("z")))
        client!!.send(JamCommand.RemoveAt(2))

        await("all four commands to arrive") { commands.size >= 4 }
        val received = commands.toList()
        assertTrue(received.contains(JamCommand.PlayPause))
        assertTrue(received.contains(JamCommand.Next))
        assertTrue(received.contains(JamCommand.RemoveAt(2)))
        assertEquals("z", (received.filterIsInstance<JamCommand.AddSong>().first()).song.id)
    }

    @Test
    fun `the member list gains and loses the guest`() {
        val latest = AtomicReference<List<JamMember>>(emptyList())
        val s = startServer(onMembersChanged = { latest.set(it) })
        val connected = AtomicReference<String?>(null)

        client = JamClient(LOOPBACK, s.port, token, "Guest phone", scope, { connected.set(it) }, {}, {})
            .also { it.connect() }

        await("guest to appear in the member list") { latest.get().size == 2 }
        assertEquals(JamRole.HOST, latest.get()[0].role)
        assertEquals("Guest phone", latest.get()[1].name)

        client!!.disconnect()
        await("guest to drop out of the member list") { latest.get().size == 1 }
    }

    @Test
    fun `a wrong token is refused`() {
        val s = startServer()
        val reason = AtomicReference<String?>(null)

        client = JamClient(LOOPBACK, s.port, "wrong-token", "Intruder", scope, {}, {}, { reason.set(it) })
            .also { it.connect() }

        await("the join to be refused") { reason.get() != null }
        assertEquals(JamServer.BYE_BAD_TOKEN, reason.get())
    }

    /** [JamClient] always sends the current version, so an old peer has to be simulated by hand. */
    @Test
    fun `a protocol version mismatch is refused`() {
        val s = startServer()
        Socket().use { raw ->
            raw.connect(InetSocketAddress(LOOPBACK, s.port), 5_000)
            val writer = raw.getOutputStream().bufferedWriter()
            writer.write(JamProtocol.encode(JamMessage.Hello(JAM_PROTOCOL_VERSION + 99, token, "Old app")))
            writer.newLine()
            writer.flush()

            val reply = JamProtocol.decode(raw.getInputStream().bufferedReader().readLine().orEmpty())
            assertEquals(JamServer.BYE_VERSION_MISMATCH, (reply as? JamMessage.Bye)?.reason)
        }
    }

    @Test
    fun `garbage instead of a handshake is refused`() {
        val s = startServer()
        Socket().use { raw ->
            raw.connect(InetSocketAddress(LOOPBACK, s.port), 5_000)
            val writer = raw.getOutputStream().bufferedWriter()
            writer.write("not a handshake at all")
            writer.newLine()
            writer.flush()

            val reply = JamProtocol.decode(raw.getInputStream().bufferedReader().readLine().orEmpty())
            assertEquals(JamServer.BYE_BAD_HANDSHAKE, (reply as? JamMessage.Bye)?.reason)
        }
    }

    @Test
    fun `stopping the host disconnects the guest`() {
        val s = startServer()
        val connected = AtomicReference<String?>(null)
        val reason = AtomicReference<String?>(null)

        client = JamClient(LOOPBACK, s.port, token, "Guest", scope, { connected.set(it) }, {}, { reason.set(it) })
            .also { it.connect() }
        await("guest to connect") { connected.get() != null }

        runBlocking { s.stop() }
        server = null

        await("guest to notice the host left") { reason.get() != null }
        assertNotNull(reason.get())
    }

    @Test
    fun `two guests both receive the broadcast`() {
        val s = startServer()
        val first = ConcurrentLinkedQueue<JamSnapshot>()
        val second = ConcurrentLinkedQueue<JamSnapshot>()
        val members = AtomicReference<List<JamMember>>(emptyList())
        runBlocking { s.stop() }

        val s2 = startServer(onMembersChanged = { members.set(it) })
        val a = JamClient(LOOPBACK, s2.port, token, "A", scope, {}, { first += it }, {}).also { it.connect() }
        val b = JamClient(LOOPBACK, s2.port, token, "B", scope, {}, { second += it }, {}).also { it.connect() }
        client = a

        await("both guests to join") { members.get().size == 3 }
        s2.broadcast(JamMessage.Snapshot(JamSnapshot(queue = listOf(song("a")))))

        await("first guest to receive") { first.isNotEmpty() }
        await("second guest to receive") { second.isNotEmpty() }
        b.disconnect()
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
