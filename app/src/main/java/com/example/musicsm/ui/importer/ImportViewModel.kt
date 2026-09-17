package com.example.musicsm.ui.importer

import android.content.Context
import com.example.musicsm.R
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.repository.PlaylistImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Importing(val done: Int, val total: Int) : ImportUiState
    data class Done(val name: String, val matched: Int, val total: Int) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PlaylistImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun import(link: String) {
        if (link.isBlank()) return
        viewModelScope.launch {
            _state.value = ImportUiState.Importing(0, 0)
            val result = repository.importFromLink(link.trim()) { done, total ->
                _state.value = ImportUiState.Importing(done, total)
            }
            _state.value = result.fold(
                onSuccess = { ImportUiState.Done(it.name, it.matched, it.total) },
                onFailure = { ImportUiState.Error(it.message ?: context.getString(R.string.import_error)) },
            )
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }
}
