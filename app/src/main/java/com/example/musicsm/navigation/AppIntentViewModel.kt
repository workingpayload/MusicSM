package com.example.musicsm.navigation

import androidx.lifecycle.ViewModel
import com.example.musicsm.ui.search.SearchRequestBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Root-level plumbing for [AppIntent] handling that doesn't belong to the player. */
@HiltViewModel
class AppIntentViewModel @Inject constructor(
    private val searchRequests: SearchRequestBus,
) : ViewModel() {

    /** Pre-fill the Search tab with [query] (the search runs automatically). */
    fun prefillSearch(query: String) {
        if (query.isNotBlank()) searchRequests.request(query)
    }
}
