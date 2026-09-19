package com.example.musicsm.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Resolves the user's appearance preferences into the [ThemeSettings] the root composable needs.
 *
 * Artwork-driven theming is done here rather than in composition so that extracting a palette from
 * a new cover doesn't animate a colour through the root — the whole app would recompose on every
 * frame of the transition. One emission per track is enough.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    controllerManager: MediaControllerManager,
) : ViewModel() {

    private val artworkAccent: StateFlow<Color?> = preferences.themeFromArtwork
        .flatMapLatest { enabled ->
            if (!enabled) {
                flowOf<Color?>(null)
            } else {
                controllerManager.state
                    .map { it.currentSong?.artworkUrl }
                    .distinctUntilChanged()
                    .flatMapLatest { url -> flow { emit(dominantColor(url)) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<ThemeSettings> = combine(
        preferences.themeMode,
        preferences.amoled,
        preferences.materialYou,
        preferences.accentColor,
        artworkAccent,
    ) { mode, amoled, materialYou, accentArgb, fromArtwork ->
        ThemeSettings(
            mode = ThemeMode.fromKey(mode),
            amoled = amoled,
            materialYou = materialYou,
            // Artwork wins while it is enabled and has produced a colour; otherwise the picker.
            accent = fromArtwork
                ?: accentArgb.takeIf { it != AppPreferences.NO_ACCENT }?.let { Color(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())

    private suspend fun dominantColor(url: String?): Color? {
        if (url.isNullOrEmpty()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false) // Palette needs readable pixels.
                    .size(200)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request)
                val bitmap = (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap
                val palette = bitmap?.let { Palette.from(it).clearFilters().generate() }
                val swatch = palette?.let {
                    it.vibrantSwatch ?: it.dominantSwatch ?: it.darkVibrantSwatch ?: it.mutedSwatch
                }
                swatch?.let { Color(it.rgb) }
            }.getOrNull()
        }
    }
}
