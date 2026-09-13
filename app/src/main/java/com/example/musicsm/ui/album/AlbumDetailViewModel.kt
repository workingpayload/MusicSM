package com.example.musicsm.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val year: String? = null,
    val songs: List<Song> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>(Routes.ARG_ALBUM_ID).orEmpty()

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state

    /** True when every album track is in Liked Songs. */
    val favorited: StateFlow<Boolean> =
        combine(_state, libraryRepository.likedSongs()) { st, liked ->
            val likedIds = liked.mapTo(HashSet()) { it.id }
            st.songs.isNotEmpty() && st.songs.all { it.id in likedIds }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when a playlist named after this album already exists. */
    val added: StateFlow<Boolean> =
        combine(_state, libraryRepository.playlists()) { st, playlists ->
            st.title.isNotBlank() && playlists.any { it.name == st.title }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = AlbumDetailUiState(loading = true)
            runCatching { musicRepository.album(albumId) }
                .onSuccess { album ->
                    _state.value = AlbumDetailUiState(
                        title = album.title,
                        artist = album.artist,
                        artworkUrl = album.artworkUrl,
                        year = album.year,
                        songs = album.songs,
                        loading = false,
                    )
                }
                .onFailure {
                    _state.value = AlbumDetailUiState(loading = false, error = "Couldn't load album")
                }
        }
    }

    /** Like all album tracks, or unlike all if already favorited. */
    fun toggleFavorite() {
        viewModelScope.launch {
            val songs = _state.value.songs
            if (songs.isEmpty()) return@launch
            val likedIds = libraryRepository.likedSongs().first().mapTo(HashSet()) { it.id }
            val allLiked = songs.all { it.id in likedIds }
            songs.forEach { song ->
                val isLiked = song.id in likedIds
                // toggleLike flips state — only call when it moves toward the target.
                if (allLiked && isLiked) libraryRepository.toggleLike(song)
                else if (!allLiked && !isLiked) libraryRepository.toggleLike(song)
            }
        }
    }

    /** Save the album as a playlist, or delete that playlist if already added. */
    fun toggleAdd() {
        viewModelScope.launch {
            val st = _state.value
            if (st.title.isBlank() || st.songs.isEmpty()) return@launch
            val existing = libraryRepository.playlists().first().firstOrNull { it.name == st.title }
            if (existing != null) {
                existing.id.toLongOrNull()?.let { libraryRepository.deletePlaylist(it) }
            } else {
                val id = libraryRepository.createPlaylist(st.title)
                st.songs.forEach { libraryRepository.addToPlaylist(id, it) }
            }
        }
    }
}
