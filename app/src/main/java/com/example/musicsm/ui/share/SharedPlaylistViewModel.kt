package com.example.musicsm.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.share.SharedPlaylist
import com.example.musicsm.navigation.SharedPlaylistBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the import has got to; the screen shows a spinner while [saving] is true. */
data class SharedPlaylistUiState(
    val playlist: SharedPlaylist? = null,
    val saving: Boolean = false,
    /** Set once the playlist has been written; carries the new local playlist id. */
    val savedPlaylistId: Long? = null,
)

/**
 * Backs the "someone shared a playlist with you" screen.
 *
 * Nothing is written until the user taps save — a link from outside the app should never be able
 * to modify the library on its own.
 */
@HiltViewModel
class SharedPlaylistViewModel @Inject constructor(
    private val bus: SharedPlaylistBus,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SharedPlaylistUiState(playlist = bus.pending.value))
    val state: StateFlow<SharedPlaylistUiState> = _state.asStateFlow()

    fun save() {
        val playlist = _state.value.playlist ?: return
        if (_state.value.saving || _state.value.savedPlaylistId != null) return

        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val id = libraryRepository.createPlaylist(playlist.name.ifBlank { DEFAULT_NAME })
            // Sequential on purpose: playlist order is the shared order, and the DAO appends.
            playlist.songs.forEach { libraryRepository.addToPlaylist(id, it) }
            playlist.songs.firstOrNull()?.artworkUrl?.let { libraryRepository.setPlaylistArtwork(id, it) }
            bus.clear()
            _state.value = _state.value.copy(saving = false, savedPlaylistId = id)
        }
    }

    /** Called when the user backs out without importing, so the link doesn't reappear later. */
    fun discard() {
        bus.clear()
    }

    private companion object {
        const val DEFAULT_NAME = "Shared playlist"
    }
}
