package com.example.musicsm.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsm.ui.search.SearchRequestBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository,
    private val searchRequests: SearchRequestBus,
) : ViewModel() {

    private val _range = MutableStateFlow(StatsRange.LAST_4_WEEKS)
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    /** Re-queries from scratch whenever the range changes; each range has its own `since`. */
    val stats: StateFlow<ListeningStats> = _range
        .flatMapLatest { repository.stats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningStats())

    fun setRange(range: StatsRange) {
        _range.value = range
    }

    /**
     * Stats only know an artist's *name* (play events join `songs`, which has no artist id), so
     * tapping one hands the name to Search rather than opening an artist page directly.
     */
    fun searchArtist(name: String) = searchRequests.request(name)

    fun clearHistory() {
        viewModelScope.launch { repository.clear() }
    }
}
