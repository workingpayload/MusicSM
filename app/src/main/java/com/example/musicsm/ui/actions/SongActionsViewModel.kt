package com.example.musicsm.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves the catalog ids needed by the "Go to artist / Go to album" actions.
 *
 * A [com.example.musicsm.domain.model.Song] only carries artist and album *names* (YouTube
 * gives us no ids on a track), so we look the page up by searching for it.
 */
@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _resolving = MutableStateFlow(false)

    /** True while a "go to …" lookup is in flight, so the sheet can show a spinner. */
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    /** Find the artist page for [name]; [onResult] gets null when nothing matched. */
    fun resolveArtist(name: String, onResult: (String?) -> Unit) {
        if (name.isBlank()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            _resolving.value = true
            val artists = runCatching { musicRepository.search(name).artists }
                .getOrDefault(emptyList())
            val match = artists.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: artists.firstOrNull()
            _resolving.value = false
            onResult(match?.id)
        }
    }

    /** Find the album page for [album] by [artist]; [onResult] gets null when nothing matched. */
    fun resolveAlbum(artist: String, album: String, onResult: (String?) -> Unit) {
        if (album.isBlank()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            _resolving.value = true
            val query = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
            val albums = runCatching { musicRepository.search(query).albums }
                .getOrDefault(emptyList())
            val match = albums.firstOrNull { it.title.equals(album, ignoreCase = true) }
                ?: albums.firstOrNull()
            _resolving.value = false
            onResult(match?.id)
        }
    }
}
