package com.example.musicsm.data.jam

import android.content.Context
import com.example.musicsm.domain.jam.DiscoveredJam
import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.jam.JamDiscoveryProtocol
import com.example.musicsm.domain.jam.JamInvite
import com.example.musicsm.domain.jam.JamJoinCode
import com.example.musicsm.domain.jam.JamMember
import com.example.musicsm.domain.jam.JamMessage
import com.example.musicsm.domain.jam.JamRole
import com.example.musicsm.domain.jam.JamSnapshot
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MediaControllerManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
        /** Short code a guest can type when scanning isn't an option, which is most of the time. */
        val joinCode: String = "",
        /** Whether this Jam answers "who's out there?" probes and appears in nearby lists. */
        val discoverable: Boolean = true,
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
    @param:ApplicationContext private val context: Context,
) {

    // Application-scoped on purpose: a Jam must outlive the screen that started it, exactly like
    // MediaControllerManager and SleepTimerManager.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow<JamState>(JamState.Off)
    val state: StateFlow<JamState> = _state.asStateFlow()

    private var server: JamServer? = null
    private var client: JamClient? = null
    private var beacon: JamBeacon? = null
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

        val joinCode = JamJoinCode.random()
        // The code *is* the token. One secret instead of two means a guest who can see the host's
        // screen has everything needed to connect directly, without discovery resolving anything.
        val token = joinCode
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
            joinCode = joinCode,
            discoverable = true,
        )
        startBeacon(hostDisplayName)
        startBroadcasting()
        return true
    }

    /**
     * Starts answering discovery probes.
     *
     * The beacon reads [state] on every probe rather than capturing the session, so it reports the
     * live code and discoverability, and stops answering on its own once hosting ends.
     */
    private fun startBeacon(hostDisplayName: String) {
        val jamBeacon = JamBeacon(
            scope = scope,
            wifiLock = WifiMulticastLock(context),
        ) {
            val hosting = _state.value as? JamState.Hosting ?: return@JamBeacon null
            JamBeacon.Announcement(
                info = JamDiscoveryProtocol.Announcement(
                    sessionName = hosting.invite.sessionName,
                    hostName = hostDisplayName,
                    port = hosting.invite.port,
                    token = hosting.invite.token,
                ),
                code = hosting.joinCode,
                discoverable = hosting.discoverable,
            )
        }
        beacon = jamBeacon.takeIf { it.start() }
    }

    /** Hides this Jam from nearby lists. The join code keeps working either way. */
    fun setDiscoverable(discoverable: Boolean) {
        val hosting = _state.value as? JamState.Hosting ?: return
        _state.value = hosting.copy(discoverable = discoverable)
    }

    /** Jams currently answering on this network. Empty is a normal result, not an error. */
    suspend fun browseNearby(): List<DiscoveredJam> = JamDiscovery.browse()

    /**
     * Joins by typed code, resolving it to a host over the network first.
     *
     * @return false if the code is malformed or nobody claimed it, leaving [state] untouched so
     * the caller can show an inline error rather than tearing the screen down.
     */
    suspend fun joinByCode(code: String, displayName: String): Boolean {
        val normalized = JamJoinCode.normalize(code) ?: return false
        val invite = JamDiscovery.resolve(normalized) ?: return false
        join(invite, displayName)
        return true
    }

    /**
     * Joins a host whose address was read off its screen and typed in by hand.
     *
     * This is the fallback for networks that never deliver the UDP broadcast discovery relies on —
     * guest isolation, some hotspots, corporate Wi-Fi. TCP to a known address is far harder for a
     * network to break than a broadcast, so this works in places where nothing is ever "found".
     *
     * @return false if the address or code is malformed, leaving [state] untouched. A well-formed
     * address that nothing answers surfaces through [JamState.Failed] like any other bad connect.
     */
    fun joinByAddress(address: String, code: String, displayName: String): Boolean {
        val normalized = JamJoinCode.normalize(code) ?: return false
        val (host, port) = parseHostAddress(address, JamServer.PREFERRED_PORT) ?: return false
        join(
            invite = JamInvite(address = host, port = port, token = normalized, sessionName = ""),
            displayName = displayName,
        )
        return true
    }

    /** Joins the session described by [invite] as a remote control. */
    fun join(invite: JamInvite, displayName: String) {
        if (isGuest) return
        // Tearing down and connecting must happen in that order on one coroutine. Launching the
        // teardown separately let it land *after* the new client was installed, which nulled the
        // fresh connection out and dropped the UI back to idle mid-connect.
        scope.launch { startJoin(invite, displayName) }
    }

    private suspend fun startJoin(invite: JamInvite, displayName: String) {
        leave()

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

        beacon?.stop()
        beacon = null

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

    companion object {
        private const val HEARTBEAT_MS = 3_000L

        const val HOST_MEMBER_ID = "host"
        const val FAILED_NO_NETWORK = "no_network"
        const val FAILED_PORT = "port_unavailable"
    }
}
