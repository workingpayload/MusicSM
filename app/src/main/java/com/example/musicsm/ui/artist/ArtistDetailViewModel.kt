package com.example.musicsm.ui.artist

import android.content.Context
import com.example.musicsm.R
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val name: String = "",
    val artworkUrl: String? = null,
    val subscribers: String? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>(Routes.ARG_ARTIST_ID).orEmpty()

    private val _state = MutableStateFlow(ArtistDetailUiState())
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    val liked: StateFlow<Boolean> = libraryRepository.isArtistLiked(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    /** Re-fetch the artist after a failure. */
    fun retry() = load()

    fun toggleLike() {
        val s = _state.value
        val name = s.name.ifBlank { artistId }
        viewModelScope.launch {
            libraryRepository.toggleArtistLike(Artist(id = artistId, name = name, artworkUrl = s.artworkUrl))
        }
    }

    private fun load() {
        viewModelScope.launch {
            // Seed the name (the nav arg IS the artist name) so the screen shows who is loading
            // straight away instead of a blank canvas while the page is fetched.
            _state.value = ArtistDetailUiState(loading = true, name = artistId)
            runCatching { musicRepository.artist(artistId) }
                .onSuccess { artist ->
                    _state.value = ArtistDetailUiState(
                        name = artist.name,
                        artworkUrl = artist.artworkUrl,
                        subscribers = artist.subscribers,
                        topSongs = artist.topSongs,
                        albums = artist.albums,
                        loading = false,
                    )
                }
                .onFailure {
                    _state.value = ArtistDetailUiState(loading = false, error = context.getString(R.string.artist_error))
                }
        }
    }
}
