package com.example.musicsm.data.jam

import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.jam.JamInvite
import com.example.musicsm.domain.jam.JamMember
import com.example.musicsm.domain.jam.JamMessage
import com.example.musicsm.domain.jam.JamRole
import com.example.musicsm.domain.jam.JamSnapshot
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MediaControllerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** What this device is doing in a Jam right now. */
sealed interface JamState {
    /** Not in a session. */
    data object Off : JamState

    /** This device owns the queue and is the one making noise. */
    data class Hosting(
        val invite: JamInvite,
        val members: List<JamMember>,
    ) : JamState

    data class Connecting(val invite: JamInvite) : JamState

    /** This device is a remote control for someone else's player. */
    data class Guest(
        val sessionName: String,
        val snapshot: JamSnapshot,
    ) : JamState

    /** [reason] is one of the `BYE_*`/`DISCONNECT_*` constants, mapped to a string by the UI. */
    data class Failed(val reason: String) : JamState
}

/**
 * Owns the Jam session for the whole app.
 *
 * The design is host-authoritative: the host's Media3 timeline *is* the queue, and a guest only
 * ever renders the last snapshot it was sent. Nothing is mirrored or merged, so the two devices
 * cannot drift out of agreement — the worst case is a guest showing a slightly stale queue.
 *
 * Only the host plays audio. That is what makes the MVP robust: there is no clock synchronisation
 * and no drift, because there is only ever one player. The snapshot already carries `positionMs`
 * and `hostClockMs` so that synchronised playback can be added later without a protocol change.
 */
@Singleton
class JamManager @Inject constructor(
    private val controller: MediaControllerManager,
) {

    // Application-scoped on purpose: a Jam must outlive the screen that started it, exactly like
    // MediaControllerManager and SleepTimerManager.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow<JamState>(JamState.Off)
    val state: StateFlow<JamState> = _state.asStateFlow()

    private var server: JamServer? = null
    private var client: JamClient? = null
    private var broadcastJobs = mutableListOf<Job>()
    private var members: List<JamMember> = emptyList()

    val isHosting: Boolean get() = server != null
    val isGuest: Boolean get() = client != null

    /**
     * Starts hosting.
     *
     * @return false when no local network address is available (no Wi-Fi) or the port could not be
     * bound; [state] is set to [JamState.Failed] in that case.
     */
    suspend fun startHosting(sessionName: String, hostDisplayName: String): Boolean {
        if (isHosting) return true
        leave()

        val address = withContext(Dispatchers.IO) { localAddressOrNull() }
        if (address == null) {
            _state.value = JamState.Failed(FAILED_NO_NETWORK)
            return false
        }

        val token = newToken()
        val hostMember = JamMember(id = HOST_MEMBER_ID, name = hostDisplayName, role = JamRole.HOST)
        members = listOf(hostMember)

        val jamServer = JamServer(
            sessionName = sessionName,
            token = token,
            hostMember = hostMember,
            scope = scope,
            onCommand = ::applyGuestCommand,
            onMembersChanged = { updated ->
                members = updated
                val current = _state.value
                if (current is JamState.Hosting) _state.value = current.copy(members = updated)
                // A new guest needs the queue immediately, not at the next heartbeat.
                broadcastSnapshot()
            },
        )

        if (!jamServer.start()) {
            _state.value = JamState.Failed(FAILED_PORT)
            return false
        }

        server = jamServer
        _state.value = JamState.Hosting(
            invite = JamInvite(address, jamServer.port, token, sessionName),
            members = members,
        )
        startBroadcasting()
        return true
    }

    /** Joins the session described by [invite] as a remote control. */
    fun join(invite: JamInvite, displayName: String) {
        if (isGuest) return
        scope.launch { leave() }

        _state.value = JamState.Connecting(invite)
        val jamClient = JamClient(
            hostAddress = invite.address,
            hostPort = invite.port,
            token = invite.token,
            displayName = displayName,
            scope = scope,
            onConnected = { sessionName ->
                _state.value = JamState.Guest(
                    sessionName = sessionName.ifBlank { invite.sessionName },
                    snapshot = JamSnapshot(),
                )
            },
            onSnapshot = { snapshot ->
                val current = _state.value
                if (current is JamState.Guest) _state.value = current.copy(snapshot = snapshot)
            },
            onDisconnected = { reason ->
                client = null
                // A clean shutdown from leave() has already moved the state to Off; don't clobber
                // it with a failure the user didn't cause.
                if (_state.value != JamState.Off) _state.value = JamState.Failed(reason)
            },
        )
        client = jamClient
        jamClient.connect()
    }

    /** Sends a control request. On the host this is applied directly; on a guest it is forwarded. */
    fun request(command: JamCommand) {
        val remote = client
        if (remote != null) remote.send(command) else applyGuestCommand(command)
    }

    /** Ends hosting or leaves a session, whichever applies. */
    suspend fun leave() {
        broadcastJobs.forEach(Job::cancel)
        broadcastJobs.clear()

        client?.disconnect()
        client = null

        server?.stop()
        server = null

        members = emptyList()
        _state.value = JamState.Off
    }

    // --- host internals ----------------------------------------------------

    private fun startBroadcasting() {
        // Structural changes go out at once: a guest pressing skip should see it immediately.
        broadcastJobs += scope.launch {
            controller.state
                .map { Triple(it.queue.map(Song::id), it.currentIndex, it.isPlaying) }
                .distinctUntilChanged()
                .collect { broadcastSnapshot() }
        }
        // The position ticks constantly, so it rides a slow heartbeat instead of the state flow —
        // otherwise every guest would get ~20 messages a second for a number nothing reads yet.
        broadcastJobs += scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                broadcastSnapshot()
            }
        }
    }

    private fun broadcastSnapshot() {
        val jamServer = server ?: return
        val player = controller.state.value
        jamServer.broadcast(
            JamMessage.Snapshot(
                JamSnapshot(
                    queue = player.queue,
                    currentIndex = player.currentIndex,
                    isPlaying = player.isPlaying,
                    positionMs = player.positionMs,
                    hostClockMs = System.currentTimeMillis(),
                    members = members,
                ),
            ),
        )
    }

    /**
     * Applies a guest request to the real player.
     *
     * Media3's controller is main-thread-only and this arrives on a socket thread, so every call
     * is hopped onto the main dispatcher. Indices are re-checked against the live queue because the
     * guest's view of it may be a few hundred milliseconds out of date.
     */
    private fun applyGuestCommand(command: JamCommand) {
        scope.launch(Dispatchers.Main) {
            when (command) {
                JamCommand.PlayPause -> controller.togglePlayPause()
                JamCommand.Next -> controller.next()
                JamCommand.Previous -> controller.previous()
                is JamCommand.AddSong -> controller.addToQueue(command.song)
                is JamCommand.RemoveAt -> {
                    val size = controller.state.value.queue.size
                    if (command.index in 0 until size) controller.removeItem(command.index)
                }
            }
        }
    }

    private fun newToken(): String {
        val random = SecureRandom()
        return (1..TOKEN_LENGTH)
            .map { TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)] }
            .joinToString("")
    }

    companion object {
        private const val HEARTBEAT_MS = 3_000L
        private const val TOKEN_LENGTH = 10

        // No look-alike characters: the token can end up being read aloud or typed in.
        private const val TOKEN_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

        const val HOST_MEMBER_ID = "host"
        const val FAILED_NO_NETWORK = "no_network"
        const val FAILED_PORT = "port_unavailable"
    }
}
