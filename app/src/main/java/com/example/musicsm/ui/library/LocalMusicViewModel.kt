package com.example.musicsm.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LocalMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val localMusicRepository: LocalMusicRepository,
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** (Re)scans MediaStore. Call once the audio-read permission has been granted. */
    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _songs.value = localMusicRepository.localSongs()
            _loading.value = false
        }
    }
}
