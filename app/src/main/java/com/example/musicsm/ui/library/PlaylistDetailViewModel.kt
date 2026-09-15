package com.example.musicsm.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val title: String = "",
    val songs: List<Song> = emptyList(),
    val isLiked: Boolean = false,
    val artworkUrl: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        savedStateHandle.get<Long>(Routes.ARG_PLAYLIST_ID) ?: Routes.LIKED_PLAYLIST_ID
    private val isLiked = playlistId == Routes.LIKED_PLAYLIST_ID

    val state: StateFlow<PlaylistDetailUiState> =
        (if (isLiked) {
            libraryRepository.likedSongs()
                .map { PlaylistDetailUiState("Liked Songs", it, isLiked = true) }
        } else {
            libraryRepository.playlist(playlistId)
                .map {
                    PlaylistDetailUiState(
                        title = it?.name ?: "Playlist",
                        songs = it?.songs ?: emptyList(),
                        artworkUrl = it?.artworkUrl,
                    )
                }
        }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    fun remove(song: Song) {
        if (isLiked) return
        viewModelScope.launch { libraryRepository.removeFromPlaylist(playlistId, song.id) }
    }

    /** Delete this playlist (no-op for the built-in Liked Songs collection). */
    fun delete() {
        if (isLiked) return
        viewModelScope.launch { libraryRepository.deletePlaylist(playlistId) }
    }

    /** Set a custom cover image (content URI string) for this playlist. */
    fun setArtwork(url: String) {
        if (isLiked) return
        viewModelScope.launch { libraryRepository.setPlaylistArtwork(playlistId, url) }
    }
}
