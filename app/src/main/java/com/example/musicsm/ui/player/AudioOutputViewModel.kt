package com.example.musicsm.ui.player

import androidx.lifecycle.ViewModel
import com.example.musicsm.playback.AudioOutputManager
import com.example.musicsm.playback.AudioOutputState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AudioOutputViewModel @Inject constructor(
    private val manager: AudioOutputManager,
) : ViewModel() {
    val state: StateFlow<AudioOutputState> = manager.state

    fun refresh() = manager.refresh()

    fun selectOutput(outputId: String): Boolean = manager.selectOutput(outputId)

    fun openSystemOutputSwitcher(): Boolean = manager.openSystemOutputSwitcher()
}
