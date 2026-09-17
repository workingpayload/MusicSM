package com.example.musicsm.ui.library

import android.content.Context
import com.example.musicsm.R
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongSort
import com.example.musicsm.domain.model.sortedFor
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    @param:ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val preferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        savedStateHandle.get<Long>(Routes.ARG_PLAYLIST_ID) ?: Routes.LIKED_PLAYLIST_ID
    private val isLiked = playlistId == Routes.LIKED_PLAYLIST_ID

    val sort: StateFlow<SongSort> = preferences.playlistSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongSort.DEFAULT)

    val state: StateFlow<PlaylistDetailUiState> =
        combine(
            if (isLiked) {
                libraryRepository.likedSongs()
                    .map { PlaylistDetailUiState(context.getString(R.string.library_liked_songs), it, isLiked = true) }
            } else {
                libraryRepository.playlist(playlistId)
                    .map {
                        PlaylistDetailUiState(
                            title = it?.name ?: context.getString(R.string.playlist_fallback_title),
                            songs = it?.songs ?: emptyList(),
                            artworkUrl = it?.artworkUrl,
                        )
                    }
            },
            preferences.playlistSort,
        ) { ui, order -> ui.copy(songs = ui.songs.sortedFor(order)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    fun setSort(order: SongSort) = preferences.setPlaylistSort(order)

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
