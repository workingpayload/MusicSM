package com.example.musicsm.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.UpdateInfo
import com.example.musicsm.update.AppUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for update checks. [Idle] renders nothing. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Quiet check on launch: only surfaces a version the user hasn't already dismissed. */
    fun checkOnLaunch() {
        viewModelScope.launch {
            val info = runCatching { updater.check() }.getOrNull() ?: return@launch
            if (info.versionName == preferences.dismissedUpdateVersion) return@launch
            _state.value = UpdateState.Available(info)
        }
    }

    /** Explicit check from Settings: always reports a result. */
    fun checkNow() {
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { updater.check() }.fold(
                onSuccess = { if (it != null) UpdateState.Available(it) else UpdateState.UpToDate },
                onFailure = { UpdateState.Failed },
            )
        }
    }

    fun install(info: UpdateInfo) {
        updater.downloadAndInstall(info)
        _state.value = UpdateState.Idle
    }

    /** Don't offer this version again on launch. */
    fun dismissVersion(info: UpdateInfo) {
        preferences.dismissedUpdateVersion = info.versionName
        _state.value = UpdateState.Idle
    }

    fun clear() {
        _state.value = UpdateState.Idle
    }
}
