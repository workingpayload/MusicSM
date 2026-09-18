package com.example.musicsm.ui.jam

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.jam.JamManager
import com.example.musicsm.data.jam.JamState
import com.example.musicsm.domain.jam.JamCommand
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
