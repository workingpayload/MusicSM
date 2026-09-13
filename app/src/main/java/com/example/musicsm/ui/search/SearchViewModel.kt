package com.example.musicsm.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data class Idle(val tiles: List<BrowseTile>) : SearchUiState
    data object Loading : SearchUiState
    data class Results(val results: SearchResults) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle(repository.browseTiles()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _state.value = SearchUiState.Idle(repository.browseTiles())
                    } else {
                        _state.value = SearchUiState.Loading
                        _state.value = runCatching { SearchUiState.Results(repository.search(q)) }
                            .getOrElse { SearchUiState.Error(it.message ?: "Search failed") }
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onTileClick(tile: BrowseTile) {
        _query.value = tile.query
    }

    fun clear() {
        _query.value = ""
    }
}
