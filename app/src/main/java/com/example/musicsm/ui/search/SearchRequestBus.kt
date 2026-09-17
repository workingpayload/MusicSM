package com.example.musicsm.ui.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot channel for "open Search pre-filled with this query", used by deep links, launcher
 * shortcuts and voice search. App-scoped so the request survives until [SearchViewModel] exists
 * — the Search tab may not be composed yet when the request arrives.
 */
@Singleton
class SearchRequestBus @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(query: String) {
        _pending.value = query
    }

    /** Called by the view model once the query has been applied. */
    fun consume(query: String) {
        _pending.compareAndSet(query, null)
    }
}
