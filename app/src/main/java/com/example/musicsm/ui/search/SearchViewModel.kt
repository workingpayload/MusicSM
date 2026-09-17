package com.example.musicsm.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.R
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
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
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val preferences: AppPreferences,
    private val requests: SearchRequestBus,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle(repository.browseTiles()))
    val state = _state.asStateFlow()

    /** Previous queries, most recent first. */
    val recentSearches: StateFlow<List<String>> = preferences.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var retryJob: Job? = null

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
                            .onSuccess { if (!it.results.isEmpty) preferences.addRecentSearch(q) }
                            .getOrElse { SearchUiState.Error(it.message ?: context.getString(R.string.search_error)) }
                    }
                }
        }
        // A deep link / shortcut / voice command asked us to search for something.
        viewModelScope.launch {
            requests.pending.collect { pending ->
                if (pending.isNullOrBlank()) return@collect
                _query.value = pending
                requests.consume(pending)
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** The user explicitly submitted the query (keyboard "search" action). */
    fun onSubmit() {
        val q = _query.value.trim()
        if (q.isNotBlank()) preferences.addRecentSearch(q)
    }

    fun onRecentSearchClick(value: String) {
        _query.value = value
    }

    fun removeRecentSearch(value: String) = preferences.removeRecentSearch(value)

    fun clearRecentSearches() = preferences.clearRecentSearches()

    /** Re-run the current query after a failure. */
    fun retry() {
        val q = _query.value
        retryJob?.cancel()
        if (q.isBlank()) {
            _state.value = SearchUiState.Idle(repository.browseTiles())
            return
        }
        retryJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = runCatching { SearchUiState.Results(repository.search(q)) }
                .getOrElse { SearchUiState.Error(it.message ?: context.getString(R.string.search_error)) }
        }
    }

    fun onTileClick(tile: BrowseTile) {
        _query.value = tile.query
    }

    fun clear() {
        _query.value = ""
    }
}
