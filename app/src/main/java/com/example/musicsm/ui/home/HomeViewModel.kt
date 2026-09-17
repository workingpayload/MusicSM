package com.example.musicsm.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.R
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val feed: HomeFeed, val offline: Boolean = false) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
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
            _state.value = runCatching { buildState() }
                .getOrElse { HomeUiState.Error(it.message ?: context.getString(R.string.home_error)) }
        }
    }

    /** Pull-to-refresh: rebuild the feed while keeping current content on screen. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { buildState() }.onSuccess { _state.value = it }
            _refreshing.value = false
        }
    }

    private suspend fun buildState(): HomeUiState {
        val online = buildOnlineSections()
        return if (online.isNotEmpty()) {
            HomeUiState.Content(HomeFeed(online), offline = false)
        } else {
            // No network results — fall back to local content so Home isn't blank.
            HomeUiState.Content(buildOfflineFeed(), offline = true)
        }
    }

    /** Downloads / recently played / liked — playable without a connection. */
    private suspend fun buildOfflineFeed(): HomeFeed {
        val sections = mutableListOf<HomeSection>()
        val downloads = runCatching { downloadRepository.downloads().first() }.getOrDefault(emptyList())
        if (downloads.isNotEmpty()) sections += shelf(R.string.shelf_downloaded, downloads)
        val recent = runCatching { libraryRepository.recentlyPlayed().first() }.getOrDefault(emptyList())
        if (recent.isNotEmpty()) sections += shelf(R.string.shelf_recently_played, recent)
        val liked = runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
        if (liked.isNotEmpty()) sections += shelf(R.string.shelf_liked_songs, liked)
        return HomeFeed(sections)
    }

    /** Personalized shelves first (from liked songs), then real trending, then curated genres. */
    private suspend fun buildOnlineSections(): List<HomeSection> {
        val liked = runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
        val sections = mutableListOf<HomeSection>()

        if (liked.isNotEmpty()) {
            val seeds = liked.shuffled().take(SEED_COUNT)
            val recs = runCatching { repository.recommendations(seeds, SHELF_SIZE) }.getOrDefault(emptyList())
            if (recs.isNotEmpty()) sections += shelf(R.string.shelf_recommended, recs)

            val topSeed = liked.first()
            val because = runCatching { repository.relatedTo(topSeed.id) }.getOrDefault(emptyList())
                .filter { it.id != topSeed.id }
                .take(SHELF_SIZE)
            if (because.isNotEmpty()) {
                sections += HomeSection(
                    context.getString(R.string.home_because_you_liked, topSeed.title),
                    because.map { HomeItem.SongItem(it) },
                )
            }
        }

        // Shelf curated from artists the user follows.
        val artists = runCatching { libraryRepository.likedArtists().first() }.getOrDefault(emptyList())
        if (artists.isNotEmpty()) {
            val picks = artists.take(SEED_COUNT).flatMap { a ->
                runCatching { repository.artist(a.id).topSongs.take(4) }.getOrDefault(emptyList())
            }.distinctBy { it.id }.shuffled().take(SHELF_SIZE)
            if (picks.isNotEmpty()) sections += shelf(R.string.shelf_from_followed_artists, picks)
        }

        val trending = runCatching { repository.trending() }.getOrDefault(emptyList())
        if (trending.isNotEmpty()) sections += shelf(R.string.shelf_trending, trending)

        sections += runCatching { repository.homeFeed().sections }.getOrDefault(emptyList())

        return sections
    }

    /** Save a home shelf as a new local playlist. */
    fun saveShelf(title: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val id = libraryRepository.createPlaylist(title)
            songs.forEach { libraryRepository.addToPlaylist(id, it) }
        }
    }

    private fun shelf(@StringRes titleRes: Int, songs: List<Song>) =
        HomeSection(context.getString(titleRes), songs.map { HomeItem.SongItem(it) })

    companion object {
        private const val SEED_COUNT = 4
        private const val SHELF_SIZE = 12
    }
}
