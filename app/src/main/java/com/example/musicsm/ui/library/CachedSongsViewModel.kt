package com.example.musicsm.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.CachedSongsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CachedSongsViewModel @Inject constructor(
    private val repository: CachedSongsRepository,
) : ViewModel() {

    /** null while the cache is first being scanned. */
    private val _songs = MutableStateFlow<List<Song>?>(null)
    val songs = _songs.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _songs.value = repository.cachedSongs() }
    }

    fun remove(songId: String) {
        _songs.update { list -> list?.filterNot { it.id == songId } }
        viewModelScope.launch { repository.remove(songId) }
    }
}
