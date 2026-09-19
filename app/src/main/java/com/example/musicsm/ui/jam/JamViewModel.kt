package com.example.musicsm.ui.jam

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.jam.JamManager
import com.example.musicsm.data.jam.JamState
import com.example.musicsm.domain.jam.DiscoveredJam
import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.jam.JamJoinCode
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Jam screen renders, flattened so host and guest share one set of widgets. */
data class JamUiState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
)

/**
 * The "how do I get in?" half of the idle screen.
 *
 * Scanning is surfaced explicitly because an empty list is ambiguous — "still looking" and "found
 * nothing, check your Wi-Fi" need to read differently or the user just waits forever.
 */
data class JamNearbyState(
    val scanning: Boolean = false,
    val scanned: Boolean = false,
    val jams: List<DiscoveredJam> = emptyList(),
    val codeBusy: Boolean = false,
    val codeFailed: Boolean = false,
    /** Set when a hand-typed host address could not be understood. */
    val addressFailed: Boolean = false,
)

@HiltViewModel
class JamViewModel @Inject constructor(
    private val jamManager: JamManager,
    private val controller: MediaControllerManager,
) : ViewModel() {

    val state: StateFlow<JamState> = jamManager.state

    /**
     * The queue to show.
     *
     * A host reads its own player directly, so the screen stays live even with nobody connected.
     * A guest has no player of its own and renders the host's last snapshot instead.
     */
    val ui: StateFlow<JamUiState> = combine(jamManager.state, controller.state) { jam, player ->
        when (jam) {
            is JamState.Guest -> JamUiState(
                queue = jam.snapshot.queue,
                currentIndex = jam.snapshot.currentIndex,
                isPlaying = jam.snapshot.isPlaying,
            )

            else -> JamUiState(
                queue = player.queue,
                currentIndex = player.currentIndex,
                isPlaying = player.isPlaying,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JamUiState())

    /** Default session name: recognisable on a stranger's screen without being personal. */
    fun defaultSessionName(): String = Build.MODEL.ifBlank { DEFAULT_NAME }

    private val _nearby = MutableStateFlow(JamNearbyState())
    val nearby: StateFlow<JamNearbyState> = _nearby.asStateFlow()

    private var scanJob: Job? = null

    /**
     * Looks for Jams on this Wi-Fi.
     *
     * Restarting cancels any scan in flight, so hammering the refresh button can't leave two
     * overlapping scans racing to publish different lists.
     */
    fun refreshNearby() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _nearby.value = _nearby.value.copy(scanning = true, codeFailed = false)
            val found = jamManager.browseNearby()
            _nearby.value = _nearby.value.copy(scanning = false, scanned = true, jams = found)
        }
    }

    fun joinDiscovered(jam: DiscoveredJam) {
        jamManager.join(jam.invite, displayName = defaultSessionName())
    }

    /** True if [code] could even be a code — lets the UI enable the button without a round trip. */
    fun isCodeComplete(code: String): Boolean = JamJoinCode.normalize(code) != null

    /**
     * Resolves a typed code over the network and joins it.
     *
     * Failure is kept local to this state rather than going through [JamState.Failed]: a typo
     * should leave the user on the idle screen with the field still filled in, not bounce them
     * through a full-screen error.
     */
    fun joinByCode(code: String) {
        if (_nearby.value.codeBusy) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _nearby.value = _nearby.value.copy(codeBusy = true, codeFailed = false)
            val joined = jamManager.joinByCode(code, displayName = defaultSessionName())
            _nearby.value = _nearby.value.copy(codeBusy = false, codeFailed = !joined)
        }
    }

    fun setDiscoverable(discoverable: Boolean) = jamManager.setDiscoverable(discoverable)

    /**
     * Joins a host by an address the user read off its screen, skipping discovery entirely.
     *
     * Unlike [joinByCode] this cannot report "not found" inline, because a direct connection
     * either succeeds or fails asynchronously; only a malformed address is caught here.
     */
    fun joinByAddress(address: String, code: String) {
        val accepted = jamManager.joinByAddress(address, code, displayName = defaultSessionName())
        _nearby.value = _nearby.value.copy(addressFailed = !accepted)
    }

    fun startHosting(sessionName: String) {
        viewModelScope.launch {
            jamManager.startHosting(
                sessionName = sessionName.trim().ifBlank { defaultSessionName() },
                hostDisplayName = defaultSessionName(),
            )
        }
    }

    fun leave() {
        viewModelScope.launch { jamManager.leave() }
    }

    fun togglePlayPause() = jamManager.request(JamCommand.PlayPause)

    fun next() = jamManager.request(JamCommand.Next)

    fun previous() = jamManager.request(JamCommand.Previous)

    fun removeAt(index: Int) = jamManager.request(JamCommand.RemoveAt(index))

    private companion object {
        const val DEFAULT_NAME = "MusicSM"
    }
}
