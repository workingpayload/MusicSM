package com.example.musicsm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.playback.AudioEffectsManager
import com.example.musicsm.playback.EqualizerCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Everything the equalizer screen renders. Levels are in millibels. */
data class EqualizerUiState(
    val enabled: Boolean = false,
    val preset: Int = AppPreferences.CUSTOM_PRESET,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessMb: Int = 0,
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val effects: AudioEffectsManager,
) : ViewModel() {

    val capabilities: StateFlow<EqualizerCapabilities> = effects.capabilities

    val state: StateFlow<EqualizerUiState> = combine(
        preferences.effectsEnabled,
        preferences.equalizerPreset,
        preferences.bassBoost,
        preferences.virtualizer,
        preferences.loudnessGainMb,
    ) { enabled, preset, bass, virt, loudness ->
        EqualizerUiState(enabled, preset, bass, virt, loudness)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqualizerUiState())

    /**
     * Band levels are held locally rather than read back from preferences so dragging a slider
     * stays smooth; the value is written through on every change and the effect follows.
     */
    private val _bands = MutableStateFlow(effects.currentBandLevels())
    val bands: StateFlow<List<Int>> = _bands.asStateFlow()

    fun setEnabled(value: Boolean) = preferences.setEffectsEnabled(value)

    fun setBand(index: Int, levelMb: Int) {
        val updated = _bands.value.toMutableList()
        while (updated.size <= index) updated.add(0)
        updated[index] = levelMb
        _bands.value = updated
        preferences.setEqualizerBands(updated)
    }

    fun selectPreset(preset: Int) {
        preferences.setEqualizerPreset(preset)
        effects.apply()
        // The device just rewrote every band; mirror the new curve into the sliders.
        _bands.value = effects.currentBandLevels()
    }

    fun setBassBoost(value: Int) = preferences.setBassBoost(value)
    fun setVirtualizer(value: Int) = preferences.setVirtualizer(value)
    fun setLoudness(value: Int) = preferences.setLoudnessGainMb(value)

    fun reset() {
        preferences.resetAudioEffects()
        effects.apply()
        _bands.value = List(capabilities.value.bandFrequencies.size) { 0 }
    }
}
