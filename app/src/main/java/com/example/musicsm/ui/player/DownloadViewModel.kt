package com.example.musicsm.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongSort
import com.example.musicsm.domain.model.sortedFor
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.DownloadingSong
import com.example.musicsm.domain.repository.FailedDownload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    val progress: StateFlow<Map<String, Float>> = repository.progress

    val activeDownloads: StateFlow<List<DownloadingSong>> = repository.activeDownloads

    val failedDownloads: StateFlow<List<FailedDownload>> = repository.failedDownloads

    val sort: StateFlow<SongSort> = preferences.downloadsSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongSort.DEFAULT)

    val downloads: StateFlow<List<Song>> =
        combine(repository.downloads(), preferences.downloadsSort) { songs, order -> songs.sortedFor(order) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun isDownloaded(songId: String): Flow<Boolean> = repository.isDownloaded(songId)

    fun download(song: Song) {
        viewModelScope.launch { repository.download(song) }
    }

    /** Download a whole album/playlist/liked list in one go. */
    fun downloadAll(songs: List<Song>) {
        viewModelScope.launch { repository.downloadAll(songs) }
    }

    fun cancel(songId: String) = repository.cancel(songId)

    fun retry(songId: String) {
        viewModelScope.launch { repository.retry(songId) }
    }

    fun delete(songId: String) {
        viewModelScope.launch { repository.delete(songId) }
    }

    fun setSort(sort: SongSort) = preferences.setDownloadsSort(sort)
}
