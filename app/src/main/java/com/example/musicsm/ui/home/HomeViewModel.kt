package com.example.musicsm.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.R
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.recommend.ShelfRanker
import com.example.musicsm.domain.recommend.TasteProfile
import com.example.musicsm.domain.recommend.TasteProfiles
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
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
    private val statsRepository: StatsRepository,
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

    /**
     * Personalized shelves first, then the real chart, then the provider's generic feed.
     *
     * Ordering is fully determined by the listener's history, so pulling to refresh no longer
     * reshuffles the page — it only picks up genuinely new listening.
     */
    private suspend fun buildOnlineSections(): List<HomeSection> {
        val followed = runCatching { libraryRepository.likedArtists().first() }.getOrDefault(emptyList())
        val profile = buildProfile(followed)

        val sections = mutableListOf<HomeSection>()
        val shown = mutableSetOf<String>()

        fun add(title: String, songs: List<Song>) {
            if (songs.isEmpty()) return
            shown += songs.map { it.id }
            sections += HomeSection(title, songs.map { HomeItem.SongItem(it) })
        }

        // Straight from history and needs no network, so it renders even on a flaky connection.
        add(context.getString(R.string.shelf_listen_again), profile.heavyRotation)

        if (profile.seeds.isNotEmpty()) {
            // Ask for more than fits: ranking and de-duplication both discard candidates.
            val recs = runCatching { repository.recommendations(profile.seeds, SHELF_SIZE * 2) }
                .getOrDefault(emptyList())
            add(
                context.getString(R.string.shelf_recommended),
                ShelfRanker.rank(recs, profile, SHELF_SIZE, exclude = shown, excludeKnown = true),
            )
        }

        profile.topSeed?.let { seed ->
            val related = runCatching { repository.relatedTo(seed.id) }.getOrDefault(emptyList())
            val titleRes =
                if (profile.hasHistory) R.string.home_more_like else R.string.home_because_you_liked
            add(
                context.getString(titleRes, seed.title),
                ShelfRanker.rank(
                    related,
                    profile,
                    SHELF_SIZE,
                    exclude = shown + seed.id,
                    excludeKnown = true,
                ),
            )
        }

        if (followed.isNotEmpty()) {
            // Most-listened follows first rather than whichever happened to be stored first.
            val picks = followed
                .sortedByDescending { profile.affinity(it.name) }
                .take(SEED_COUNT)
                .flatMap {
                    runCatching { repository.artist(it.id).topSongs.take(ARTIST_PICKS) }
                        .getOrDefault(emptyList())
                }
            add(
                context.getString(R.string.shelf_from_followed_artists),
                ShelfRanker.rank(picks, profile, SHELF_SIZE, exclude = shown, maxPerArtist = ARTIST_PICKS),
            )
        }

        val trending = runCatching { repository.trending() }.getOrDefault(emptyList())
        add(context.getString(R.string.shelf_trending), ShelfRanker.dedupe(trending, SHELF_SIZE, shown))

        // The provider's generic feed is the same for everybody, so it goes last — and once there
        // is real history to personalize from, it is trimmed so it can't dominate the page.
        val generic = runCatching { repository.homeFeed().sections }.getOrDefault(emptyList())
        sections += if (profile.hasHistory) generic.take(MAX_GENERIC_SECTIONS) else generic

        return sections
    }

    /** Fold history, likes and follows into one deterministic picture of the listener's taste. */
    private suspend fun buildProfile(followed: List<Artist>): TasteProfile {
        val recent = statsOrEmpty(StatsRange.LAST_4_WEEKS)
        val lifetime = statsOrEmpty(StatsRange.ALL_TIME)
        val liked = runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
        val recentlyPlayed =
            runCatching { libraryRepository.recentlyPlayed().first() }.getOrDefault(emptyList())
        return TasteProfiles.build(
            recent = recent,
            lifetime = lifetime,
            liked = liked,
            followedArtists = followed,
            recentlyPlayed = recentlyPlayed,
            seedCount = SEED_COUNT,
            rotationSize = SHELF_SIZE,
        )
    }

    private suspend fun statsOrEmpty(range: StatsRange): ListeningStats =
        runCatching { statsRepository.stats(range).first() }.getOrDefault(ListeningStats())

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

        /** Tracks pulled from each followed artist, and the per-artist cap on that shelf. */
        private const val ARTIST_PICKS = 4

        /** How much of the provider's one-size-fits-all feed survives once we know the listener. */
        private const val MAX_GENERIC_SECTIONS = 4
    }
}
