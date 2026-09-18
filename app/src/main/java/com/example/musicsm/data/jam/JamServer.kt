package com.example.musicsm.data.jam

import android.util.Log
import com.example.musicsm.domain.jam.JAM_PROTOCOL_VERSION
import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.jam.JamMember
import com.example.musicsm.domain.jam.JamMessage
import com.example.musicsm.domain.jam.JamProtocol
import com.example.musicsm.domain.jam.JamRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host side of a Jam session: a plain TCP server on the local network.
 *
 * Raw sockets rather than HTTP/WebSocket because the protocol is one line per message and the app
 * would otherwise need a whole server framework as a dependency. The host stays authoritative —
 * guests send requests, and the only thing that ever changes the queue is the host applying them.
 *
 * Every public method is safe to call from any thread; all socket I/O happens on [Dispatchers.IO].
 */
internal class JamServer(
    private val sessionName: String,
    private val token: String,
    private val hostMember: JamMember,
    private val scope: CoroutineScope,
    private val onCommand: (JamCommand) -> Unit,
    private val onMembersChanged: (List<JamMember>) -> Unit,
) {

    private class Client(
        val member: JamMember,
        val socket: Socket,
        val outbound: Channel<String>,
    )

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val clientsLock = Mutex()
    private val clients = LinkedHashMap<String, Client>()
    private val memberIds = AtomicInteger(0)

    /** The port the OS handed us; valid only once [start] has returned true. */
    var port: Int = 0
        private set

    /**
     * Binds a port and begins accepting guests.
     *
     * @return false if the port could not be bound, in which case the caller should not advertise
     * a session.
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // Port 0 asks the OS for any free port, which avoids clashing with anything else and
            // means the port has to travel in the join link.
            val socket = ServerSocket(0)
            serverSocket = socket
            port = socket.localPort
            acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
            true
        }.getOrElse {
            Log.w(TAG, "Could not start Jam server", it)
            false
        }
    }

    /** Sends [message] to every connected guest. */
    fun broadcast(message: JamMessage) {
        val line = JamProtocol.encode(message)
        scope.launch {
            clientsLock.withLock { clients.values.toList() }
                // trySend, never send: a guest whose buffer is full must not stall the host.
                .forEach { it.outbound.trySend(line) }
        }
    }

    /** Says goodbye to every guest and releases the port. */
    suspend fun stop() {
        val farewell = JamProtocol.encode(JamMessage.Bye(BYE_SESSION_ENDED))
        val open = clientsLock.withLock {
            val snapshot = clients.values.toList()
            clients.clear()
            snapshot
        }
        withContext(Dispatchers.IO) {
            open.forEach { client ->
                runCatching {
                    // Best effort: a departing guest being told why is nice, not essential.
                    client.socket.getOutputStream().write((farewell + "\n").toByteArray())
                    client.socket.getOutputStream().flush()
                }
                client.outbound.close()
                runCatching { client.socket.close() }
            }
            runCatching { serverSocket?.close() }
        }
        acceptJob?.cancel()
        acceptJob = null
        serverSocket = null
    }

    // --- internals ---------------------------------------------------------

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive && !server.isClosed) {
            // accept() only unblocks by returning a socket or by the socket being closed in stop().
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch(Dispatchers.IO) { handleClient(socket) }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        var registered: String? = null
        try {
            socket.tcpNoDelay = true
            val reader = socket.getInputStream().bufferedReader()
            val writer = socket.getOutputStream().bufferedWriter()

            val member = handshake(reader, writer, socket) ?: return
            registered = member.id

            val outbound = Channel<String>(
                capacity = OUTBOUND_BUFFER,
                // Snapshots are absolute, not deltas, so dropping a stale one costs nothing and is
                // far better than blocking the host on a slow guest.
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            clientsLock.withLock { clients[member.id] = Client(member, socket, outbound) }
            publishMembers()

            val writerJob = scope.launch(Dispatchers.IO) { pump(outbound, writer) }
            try {
                readCommands(reader)
            } finally {
                writerJob.cancel()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Jam guest connection ended", e)
        } finally {
            registered?.let { id -> clientsLock.withLock { clients.remove(id) }?.outbound?.close() }
            runCatching { socket.close() }
            if (registered != null) publishMembers()
        }
    }

    /** Validates the guest's [JamMessage.Hello] and replies, or refuses and closes. */
    private suspend fun handshake(
        reader: BufferedReader,
        writer: BufferedWriter,
        socket: Socket,
    ): JamMember? {
        // A peer that connects and says nothing must not hold a thread forever.
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        val line = runCatching { reader.readLine() }.getOrNull() ?: return null
        socket.soTimeout = 0

        val hello = JamProtocol.decode(line) as? JamMessage.Hello
        val refusal = when {
            hello == null -> BYE_BAD_HANDSHAKE
            hello.version != JAM_PROTOCOL_VERSION -> BYE_VERSION_MISMATCH
            hello.token != token -> BYE_BAD_TOKEN
            else -> null
        }
        if (refusal != null) {
            runCatching {
                writer.write(JamProtocol.encode(JamMessage.Bye(refusal)))
                writer.newLine()
                writer.flush()
            }
            return null
        }

        val member = JamMember(
            id = "g${memberIds.incrementAndGet()}",
            name = hello!!.displayName.ifBlank { DEFAULT_GUEST_NAME },
            role = JamRole.GUEST,
        )
        writer.write(JamProtocol.encode(JamMessage.Welcome(JAM_PROTOCOL_VERSION, sessionName, member.id)))
        writer.newLine()
        writer.flush()
        return member
    }

    private fun readCommands(reader: BufferedReader) {
        while (true) {
            val line = reader.readLine() ?: return
            when (val message = JamProtocol.decode(line)) {
                is JamMessage.Command -> onCommand(message.command)
                is JamMessage.Bye -> return
                // Anything else is a guest talking out of turn; ignore rather than disconnect.
                else -> Unit
            }
        }
    }

    private suspend fun pump(outbound: Channel<String>, writer: BufferedWriter) {
        for (line in outbound) {
            runCatching {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }.onFailure { return }
        }
    }

    private fun publishMembers() {
        scope.launch {
            val guests = clientsLock.withLock { clients.values.map { it.member } }
            onMembersChanged(listOf(hostMember) + guests)
        }
    }

    companion object {
        private const val TAG = "JamServer"
        private const val OUTBOUND_BUFFER = 32
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val DEFAULT_GUEST_NAME = "Guest"

        const val BYE_SESSION_ENDED = "session_ended"
        const val BYE_BAD_HANDSHAKE = "bad_handshake"
        const val BYE_VERSION_MISMATCH = "version_mismatch"
        const val BYE_BAD_TOKEN = "bad_token"
    }
}
