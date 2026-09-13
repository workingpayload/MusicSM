package com.example.musicsm.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _state.value = runCatching { HomeUiState.Content(buildFeed()) }
                .getOrElse { HomeUiState.Error(it.message ?: "Couldn't load home") }
        }
    }

    /** Pull-to-refresh: rebuild the feed while keeping current content on screen. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { buildFeed() }.onSuccess { _state.value = HomeUiState.Content(it) }
            _refreshing.value = false
        }
    }

    /** Personalized shelves first (from liked songs), then real trending, then curated genres. */
    private suspend fun buildFeed(): HomeFeed {
        val liked = runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
        val sections = mutableListOf<HomeSection>()

        if (liked.isNotEmpty()) {
            val seeds = liked.shuffled().take(SEED_COUNT)
            val recs = runCatching { repository.recommendations(seeds, SHELF_SIZE) }.getOrDefault(emptyList())
            if (recs.isNotEmpty()) sections += shelf("Recommended for you", recs)

            val topSeed = liked.first()
            val because = runCatching { repository.relatedTo(topSeed.id) }.getOrDefault(emptyList())
                .filter { it.id != topSeed.id }
                .take(SHELF_SIZE)
            if (because.isNotEmpty()) sections += shelf("Because you liked ${topSeed.title}", because)
        }

        val trending = runCatching { repository.trending() }.getOrDefault(emptyList())
        if (trending.isNotEmpty()) sections += shelf("Trending now", trending)

        sections += runCatching { repository.homeFeed().sections }.getOrDefault(emptyList())

        return HomeFeed(sections)
    }

    private fun shelf(title: String, songs: List<Song>) =
        HomeSection(title, songs.map { HomeItem.SongItem(it) })

    companion object {
        private const val SEED_COUNT = 4
        private const val SHELF_SIZE = 12
    }
}
