package com.example.musicsm.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>(Routes.ARG_ARTIST_ID).orEmpty()

    private val _state = MutableStateFlow(ArtistDetailUiState())
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = ArtistDetailUiState(loading = true)
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
                    _state.value = ArtistDetailUiState(loading = false, error = "Couldn't load artist")
                }
        }
    }
}
