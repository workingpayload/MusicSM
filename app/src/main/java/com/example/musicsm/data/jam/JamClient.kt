package com.example.musicsm.data.jam

import android.util.Log
import com.example.musicsm.domain.jam.JAM_PROTOCOL_VERSION
import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.jam.JamMessage
import com.example.musicsm.domain.jam.JamProtocol
import com.example.musicsm.domain.jam.JamSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Guest side of a Jam session.
 *
 * A guest is a remote control: it plays no audio, holds no queue of its own and never mutates
 * anything locally. It sends requests and renders whatever the host last broadcast, so the host's
 * snapshot is always the single source of truth and the two can never disagree.
 */
internal class JamClient(
    private val hostAddress: String,
    private val hostPort: Int,
    private val token: String,
    private val displayName: String,
    private val scope: CoroutineScope,
    private val onConnected: (sessionName: String) -> Unit,
    private val onSnapshot: (JamSnapshot) -> Unit,
    private val onDisconnected: (reason: String) -> Unit,
) {

    private var socket: Socket? = null
    private var job: Job? = null
    private val outbound = Channel<String>(
        capacity = OUTBOUND_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Connects and then reads until the host goes away or [disconnect] is called. */
    fun connect() {
        job = scope.launch(Dispatchers.IO) { run() }
    }

    /** Asks the host to do something. Silently dropped when not connected. */
    fun send(command: JamCommand) {
        outbound.trySend(JamProtocol.encode(JamMessage.Command(command)))
    }

    fun disconnect() {
        job?.cancel()
        job = null
        // Closing the socket is what unblocks the blocking readLine below.
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun run() {
        var reason = DISCONNECT_FAILED
        try {
            val s = Socket()
            socket = s
            s.connect(InetSocketAddress(hostAddress, hostPort), CONNECT_TIMEOUT_MS)
            s.tcpNoDelay = true

            val reader = s.getInputStream().bufferedReader()
            val writer = s.getOutputStream().bufferedWriter()

            writer.writeLine(JamProtocol.encode(JamMessage.Hello(JAM_PROTOCOL_VERSION, token, displayName)))

            // The host answers a valid hello with WELCOME and anything else with BYE. Note that
            // every exit from here on sets `reason` and returns: the single onDisconnected call
            // lives in the finally block, so a refusal reports why rather than being overwritten
            // by the generic connect failure.
            when (val first = JamProtocol.decode(reader.readLine().orEmpty())) {
                is JamMessage.Welcome -> onConnected(first.sessionName)
                is JamMessage.Bye -> {
                    reason = first.reason
                    return
                }
                else -> return
            }

            val writerJob = scope.launch(Dispatchers.IO) {
                for (line in outbound) {
                    runCatching { writer.writeLine(line) }.onFailure { return@launch }
                }
            }

            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    when (val message = JamProtocol.decode(line)) {
                        is JamMessage.Snapshot -> onSnapshot(message.snapshot)
                        is JamMessage.Bye -> {
                            reason = message.reason
                            return
                        }
                        else -> Unit
                    }
                }
                reason = DISCONNECT_HOST_GONE
            } finally {
                writerJob.cancel()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Jam client disconnected", e)
        } finally {
            withContext(Dispatchers.IO) { runCatching { socket?.close() } }
            socket = null
            onDisconnected(reason)
        }
    }

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
        flush()
    }

    companion object {
        private const val TAG = "JamClient"
        private const val OUTBOUND_BUFFER = 32
        private const val CONNECT_TIMEOUT_MS = 5_000

        const val DISCONNECT_FAILED = "connect_failed"
        const val DISCONNECT_HOST_GONE = "host_gone"
    }
}
