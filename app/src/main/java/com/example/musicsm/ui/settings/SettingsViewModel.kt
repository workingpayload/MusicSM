package com.example.musicsm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the settings screen renders. */
data class SettingsUiState(
    val restoreQueue: Boolean = true,
    val skipSilence: Boolean = false,
    val autoplayRadio: Boolean = false,
    val sleepTimerFadeOut: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        preferences.restoreQueue,
        preferences.skipSilence,
        preferences.autoplayRadio,
        preferences.sleepTimerFadeOut,
        preferences.wifiOnlyDownloads,
    ) { restore, skipSilence, autoplay, fade, wifiOnly ->
        SettingsUiState(restore, skipSilence, autoplay, fade, wifiOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val downloadCount: StateFlow<Int> = downloadRepository.downloads()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    val recentSearchCount: StateFlow<Int> = preferences.recentSearches
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch { _storageBytes.value = downloadRepository.storageUsedBytes() }
    }

    fun setRestoreQueue(value: Boolean) {
        preferences.setRestoreQueue(value)
        if (!value) preferences.clearQueue()
    }

    fun setSkipSilence(value: Boolean) = preferences.setSkipSilence(value)
    fun setAutoplayRadio(value: Boolean) = preferences.setAutoplayRadio(value)
    fun setSleepTimerFadeOut(value: Boolean) = preferences.setSleepTimerFadeOut(value)
    fun setWifiOnlyDownloads(value: Boolean) = preferences.setWifiOnlyDownloads(value)

    fun clearSearchHistory() = preferences.clearRecentSearches()

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteAll()
            refreshStorage()
        }
    }
}
