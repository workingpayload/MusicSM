package com.example.musicsm.ui.importer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.R
import com.example.musicsm.domain.repository.ImportState
import com.example.musicsm.domain.repository.PlaylistImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    val state: StateFlow<ImportUiState> = repository.state
        .map { it.toUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.state.value.toUiState(),
        )

    fun import(link: String) {
        if (link.isBlank()) return
        repository.start(link.trim())
    }

    fun reset() {
        repository.acknowledge()
    }

    private fun ImportState.toUiState(): ImportUiState = when (this) {
        ImportState.Idle -> ImportUiState.Idle
        is ImportState.Running -> ImportUiState.Importing(done, total)
        is ImportState.Finished -> ImportUiState.Done(result.name, result.matched, result.total)
        is ImportState.Failed -> ImportUiState.Error(message ?: context.getString(R.string.import_error))
    }
}
