package com.example.musicsm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.ui.theme.ThemeMode
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
    val playbackSpeed: Float = 1f,
    val crossfadeMs: Int = 0,
    val appearance: AppearanceUiState = AppearanceUiState(),
)

/** The Appearance section's state, kept separate so the 5-flow `combine` limit stays workable. */
data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val materialYou: Boolean = false,
    val themeFromArtwork: Boolean = false,
    /** Packed ARGB, or [AppPreferences.NO_ACCENT] for the palette default. */
    val accentColor: Int = AppPreferences.NO_ACCENT,
) {
    /** Material You and the accent picker both drive the accent, so only one may win. */
    val accentPickerEnabled: Boolean get() = !materialYou && !themeFromArtwork
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val playbackToggles = combine(
        preferences.restoreQueue,
        preferences.skipSilence,
        preferences.autoplayRadio,
        preferences.sleepTimerFadeOut,
        preferences.wifiOnlyDownloads,
    ) { restore, skipSilence, autoplay, fade, wifiOnly ->
        SettingsUiState(restore, skipSilence, autoplay, fade, wifiOnly)
    }

    private val appearance = combine(
        preferences.themeMode,
        preferences.amoled,
        preferences.materialYou,
        preferences.themeFromArtwork,
        preferences.accentColor,
    ) { mode, amoled, materialYou, fromArtwork, accent ->
        AppearanceUiState(ThemeMode.fromKey(mode), amoled, materialYou, fromArtwork, accent)
    }

    val state: StateFlow<SettingsUiState> = combine(
        playbackToggles,
        preferences.playbackSpeed,
        preferences.crossfadeMs,
        appearance,
    ) { toggles, speed, crossfade, look ->
        toggles.copy(playbackSpeed = speed, crossfadeMs = crossfade, appearance = look)
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
    fun setPlaybackSpeed(value: Float) = preferences.setPlaybackSpeed(value)
    fun setCrossfadeMs(value: Int) = preferences.setCrossfadeMs(value)

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)
    fun setAmoled(value: Boolean) = preferences.setAmoled(value)

    /** Material You and artwork theming are mutually exclusive accent sources. */
    fun setMaterialYou(value: Boolean) {
        preferences.setMaterialYou(value)
        if (value) preferences.setThemeFromArtwork(false)
    }

    fun setThemeFromArtwork(value: Boolean) {
        preferences.setThemeFromArtwork(value)
        if (value) preferences.setMaterialYou(false)
    }

    /** Choosing an explicit accent turns off the automatic sources so the pick actually sticks. */
    fun setAccentColor(argb: Int) {
        preferences.setAccentColor(argb)
        if (argb != AppPreferences.NO_ACCENT) {
            preferences.setMaterialYou(false)
            preferences.setThemeFromArtwork(false)
        }
    }

    fun clearSearchHistory() = preferences.clearRecentSearches()

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteAll()
            refreshStorage()
        }
    }
}
