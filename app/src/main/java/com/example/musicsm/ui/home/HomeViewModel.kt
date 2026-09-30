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
import com.example.musicsm.domain.recommend.DailyRotation
import com.example.musicsm.domain.recommend.ShelfRanker
import com.example.musicsm.domain.recommend.TasteProfile
import com.example.musicsm.domain.recommend.TasteProfiles
import com.example.musicsm.domain.repository.CachedSongsRepository
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * @param loadingMore the locally-derived shelves are on screen and the network ones are still
     *   in flight, so the page is usable but not yet complete.
     * @param canLoadMore more shelves can still be appended by scrolling to the bottom.
     */
    data class Content(
        val feed: HomeFeed,
        val offline: Boolean = false,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        /**
         * Scroll-paging attempts made so far.
         *
         * The list's paging trigger keys off this rather than the section count, so a page that
         * happens to add nothing (every shelf a title we already show) still arms the next attempt
         * instead of stalling the feed permanently.
         */
        val pages: Int = 0,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val statsRepository: StatsRepository,
    private val cachedSongsRepository: CachedSongsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Cursor into the provider's feed, and the artists not yet given a shelf of their own. */
    private var continuation: String? = null
    private var pendingArtists: List<Artist> = emptyList()
    private var paging = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            build(showLocalFirst = true)
        }
    }

    /** Pull-to-refresh: rebuild the feed while keeping current content on screen. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            // The page is already populated, so publishing a local-only feed here would visibly
            // tear the shelves down and rebuild them. Swap in the finished feed instead.
            build(showLocalFirst = false)
            _refreshing.value = false
        }
    }

    /**
     * Append the next batch of shelves, called as the bottom of the page comes into view.
     *
     * Two sources feed this, in order. The provider's own feed is paged first, since it is
     * effectively endless; once that is exhausted the remaining followed artists each get a
     * shelf, which keeps the page growing with material the listener actually cares about rather
     * than stopping dead.
     */
    fun loadMore() {
        val current = _state.value as? HomeUiState.Content ?: return
        if (paging || !current.canLoadMore) return
        paging = true
        viewModelScope.launch {
            try {
                val existing = current.feed.sections
                val shown = existing.songIds().toMutableSet()
                val next = nextShelves(shown)
                val merged = merge(existing, next)
                val pages = current.pages + 1
                _state.value = current.copy(
                    feed = HomeFeed(merged, continuation),
                    // Purely cursor-driven: a page that adds nothing is not the end of the road,
                    // but a feed with no cursor left and no artists queued definitely is.
                    canLoadMore = pages < MAX_PAGES &&
                        (continuation != null || pendingArtists.isNotEmpty()),
                    pages = pages,
                )
            } finally {
                paging = false
            }
        }
    }

    /** One more batch of shelves, from whichever source still has something to give. */
    private suspend fun nextShelves(shown: MutableSet<String>): List<HomeSection> {
        continuation?.let { token ->
            val feed = runCatching { repository.moreHomeShelves(token) }.getOrNull()
            continuation = feed?.continuation
            val sections = feed?.sections.orEmpty()
            if (sections.isNotEmpty()) return sections
        }
        return nextArtistShelves(shown)
    }

    /** A shelf per remaining followed artist, fetched a few at a time. */
    private suspend fun nextArtistShelves(shown: MutableSet<String>): List<HomeSection> =
        coroutineScope {
            val batch = pendingArtists.take(ARTIST_PAGE_SIZE)
            if (batch.isEmpty()) return@coroutineScope emptyList()
            pendingArtists = pendingArtists.drop(batch.size)

            batch
                .map { artist -> async { runCatching { repository.artist(artist.id) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
                .mapNotNull { page ->
                    val songs = page.topSongs.filter { it.id !in shown }.take(SHELF_SIZE)
                    if (songs.size < MIN_SONG_SHELF) return@mapNotNull null
                    shown += songs.map { it.id }
                    HomeSection(
                        context.getString(R.string.shelf_more_from_artist, page.name),
                        songs.map { HomeItem.SongItem(it) },
                    )
                }
        }

    /**
     * Builds the feed in two phases.
     *
     * Everything derived from the listener's own history is computed first and, on a cold start,
     * published straight away: it needs no network, so there is no reason to make someone watch a
     * skeleton while catalog requests they may not even scroll to complete. The network shelves
     * are then fetched concurrently and merged in.
     */
    private suspend fun build(showLocalFirst: Boolean) {
        continuation = null
        pendingArtists = emptyList()

        val local = runCatching { buildLocal() }.getOrNull()
        if (local == null) {
            _state.value = HomeUiState.Error(context.getString(R.string.home_error))
            return
        }

        if (showLocalFirst && local.sections.isNotEmpty()) {
            _state.value = HomeUiState.Content(
                feed = HomeFeed(local.sections),
                loadingMore = true,
            )
        }

        val shown = local.sections.songIds().toMutableSet()
        val network = runCatching { buildNetwork(local.profile, local.followed, shown) }
            .getOrDefault(emptyList())

        // Artists already given a shelf on the first page are not offered again while paging.
        pendingArtists = local.followed.drop(SEED_COUNT)

        _state.value = if (network.isEmpty()) {
            // Nothing came back from the catalog. Pad the local shelves with anything else that
            // is playable without a connection rather than leaving a near-empty page.
            HomeUiState.Content(
                feed = HomeFeed(merge(local.sections, buildOfflineSections())),
                offline = true,
            )
        } else {
            HomeUiState.Content(
                feed = HomeFeed(merge(local.sections, network), continuation),
                canLoadMore = continuation != null || pendingArtists.isNotEmpty(),
            )
        }
    }

    /** Shelves that come entirely from Room, so they render offline and instantly. */
    private suspend fun buildLocal(): LocalFeed {
        val followed = runCatching { libraryRepository.likedArtists().first() }
            .getOrDefault(emptyList())
        val profile = buildProfile(followed)
        val sections = mutableListOf<HomeSection>()
        val shown = mutableSetOf<String>()

        fun addSongs(title: String, songs: List<Song>) {
            val fresh = songs.filter { it.id !in shown }
            if (fresh.isEmpty()) return
            shown += fresh.map { it.id }
            sections += HomeSection(title, fresh.map { HomeItem.SongItem(it) })
        }

        addSongs(context.getString(R.string.shelf_listen_again), profile.heavyRotation)

        // Tracks that were once on repeat and then went quiet. Worth resurfacing precisely
        // because every other shelf is biased towards what is already in rotation.
        val forgotten = runCatching { statsRepository.forgottenFavorites(SHELF_SIZE) }
            .getOrDefault(emptyList())
            .map { it.song }
        addSongs(context.getString(R.string.shelf_forgotten_favorites), forgotten)

        // Follows ranked by what is actually played, not by whichever was saved first.
        val topFollowed = followed.sortedByDescending { profile.affinity(it.name) }
        if (topFollowed.size >= MIN_ARTIST_SHELF) {
            sections += HomeSection(
                context.getString(R.string.shelf_your_artists),
                topFollowed.take(ARTIST_SHELF_SIZE).map { HomeItem.ArtistItem(it) },
            )
        }

        return LocalFeed(sections, profile, topFollowed)
    }

    /**
     * Every catalog request the page needs, issued at once.
     *
     * These used to run one after another — including a per-artist fetch inside a `flatMap`, which
     * made the wait scale with the number of followed artists. They are independent, so the page
     * now takes about as long as its slowest single request instead of the sum of all of them.
     */
    private suspend fun buildNetwork(
        profile: TasteProfile,
        followed: List<Artist>,
        shown: MutableSet<String>,
    ): List<HomeSection> = coroutineScope {
        val seedArtists = followed.take(SEED_COUNT)

        val discoverAsync = async {
            val seeds = DailyRotation.pick(profile.discoveryPool(), DISCOVER_SEEDS)
            if (seeds.isEmpty()) {
                emptyList()
            } else {
                runCatching { repository.recommendations(seeds, SHELF_SIZE * 2) }
                    .getOrDefault(emptyList())
            }
        }
        val recommendedAsync = async {
            if (profile.seeds.isEmpty()) {
                emptyList()
            } else {
                // Ask for more than fits: ranking and de-duplication both discard candidates.
                runCatching { repository.recommendations(profile.seeds, SHELF_SIZE * 2) }
                    .getOrDefault(emptyList())
            }
        }
        val relatedAsync = async {
            val seed = profile.topSeed
            if (seed == null) {
                emptyList()
            } else {
                runCatching { repository.relatedTo(seed.id) }.getOrDefault(emptyList())
            }
        }
        val artistPagesAsync = async {
            seedArtists
                .map { artist -> async { runCatching { repository.artist(artist.id) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }
        val trendingAsync = async { runCatching { repository.trending() }.getOrDefault(emptyList()) }
        val genericAsync = async {
            runCatching { repository.homeFeed() }.getOrDefault(HomeFeed())
        }

        val sections = mutableListOf<HomeSection>()

        fun addSongs(title: String, songs: List<Song>) {
            if (songs.isEmpty()) return
            shown += songs.map { it.id }
            sections += HomeSection(title, songs.map { HomeItem.SongItem(it) })
        }

        addSongs(
            context.getString(R.string.shelf_daily_discover),
            ShelfRanker.rank(
                discoverAsync.await(),
                profile,
                SHELF_SIZE,
                exclude = shown,
                excludeKnown = true,
            ),
        )

        addSongs(
            context.getString(R.string.shelf_recommended),
            ShelfRanker.rank(
                recommendedAsync.await(),
                profile,
                SHELF_SIZE,
                exclude = shown,
                excludeKnown = true,
            ),
        )

        profile.topSeed?.let { seed ->
            val titleRes =
                if (profile.hasHistory) R.string.home_more_like else R.string.home_because_you_liked
            addSongs(
                context.getString(titleRes, seed.title),
                ShelfRanker.rank(
                    relatedAsync.await(),
                    profile,
                    SHELF_SIZE,
                    exclude = shown + seed.id,
                    excludeKnown = true,
                ),
            )
        }

        val artistPages = artistPagesAsync.await()
        addSongs(
            context.getString(R.string.shelf_from_followed_artists),
            ShelfRanker.rank(
                artistPages.flatMap { it.topSongs.take(ARTIST_PICKS) },
                profile,
                SHELF_SIZE,
                exclude = shown,
                maxPerArtist = ARTIST_PICKS,
            ),
        )

        // The artist pages have already been fetched for the shelf above, so an album row costs
        // nothing extra.
        val albums = artistPages
            .flatMap { it.albums }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .take(SHELF_SIZE)
        if (albums.size >= MIN_ALBUM_SHELF) {
            sections += HomeSection(
                context.getString(R.string.shelf_albums_from_artists),
                albums.map { HomeItem.AlbumItem(it) },
            )
        }

        addSongs(
            context.getString(R.string.shelf_trending),
            ShelfRanker.dedupe(trendingAsync.await(), SHELF_SIZE, shown),
        )

        // The provider's generic feed is the same for everybody, so it goes last — and once there
        // is real history to personalize from, it is trimmed so it can't dominate the page.
        val generic = genericAsync.await()
        continuation = generic.continuation
        sections += if (profile.hasHistory) {
            generic.sections.take(MAX_GENERIC_SECTIONS)
        } else {
            generic.sections
        }

        sections
    }

    /** Downloads / recently played / liked — playable without a connection. */
    private suspend fun buildOfflineSections(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        val downloads = runCatching { downloadRepository.downloads().first() }
            .getOrDefault(emptyList())
        if (downloads.isNotEmpty()) sections += shelf(R.string.shelf_downloaded, downloads)
        val cached = runCatching { cachedSongsRepository.cachedSongs() }.getOrDefault(emptyList())
            .take(SHELF_SIZE * 2)
        if (cached.isNotEmpty()) sections += shelf(R.string.shelf_cached, cached)
        val recent = runCatching { libraryRepository.recentlyPlayed().first() }
            .getOrDefault(emptyList())
        if (recent.isNotEmpty()) sections += shelf(R.string.shelf_recently_played, recent)
        val liked = runCatching { libraryRepository.likedSongs().first() }
            .getOrDefault(emptyList())
        if (liked.isNotEmpty()) sections += shelf(R.string.shelf_liked_songs, liked)
        return sections
    }

    /** Fold history, likes and follows into one deterministic picture of the listener's taste. */
    private suspend fun buildProfile(followed: List<Artist>): TasteProfile = coroutineScope {
        val recentAsync = async { statsOrEmpty(StatsRange.LAST_4_WEEKS) }
        val lifetimeAsync = async { statsOrEmpty(StatsRange.ALL_TIME) }
        val likedAsync = async {
            runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
        }
        val recentlyPlayedAsync = async {
            runCatching { libraryRepository.recentlyPlayed().first() }.getOrDefault(emptyList())
        }
        TasteProfiles.build(
            recent = recentAsync.await(),
            lifetime = lifetimeAsync.await(),
            liked = likedAsync.await(),
            followedArtists = followed,
            recentlyPlayed = recentlyPlayedAsync.await(),
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

    /** The locally-computed part of the page, plus what the network phase needs to continue. */
    private data class LocalFeed(
        val sections: List<HomeSection>,
        val profile: TasteProfile,
        val followed: List<Artist>,
    )

    companion object {
        private const val SEED_COUNT = 4
        private const val SHELF_SIZE = 12

        /** Tracks that seed "Daily discover"; kept small because each one is a request. */
        private const val DISCOVER_SEEDS = 3

        /** Tracks pulled from each followed artist, and the per-artist cap on that shelf. */
        private const val ARTIST_PICKS = 4

        /** A row of one or two avatars looks like a mistake, so don't show it at all. */
        private const val MIN_ARTIST_SHELF = 3
        private const val ARTIST_SHELF_SIZE = 12

        /** Same reasoning for the album row. */
        private const val MIN_ALBUM_SHELF = 3

        /** How much of the provider's one-size-fits-all feed survives once we know the listener. */
        private const val MAX_GENERIC_SECTIONS = 4

        /** Followed artists given a shelf per scroll-triggered page. Each one is a request. */
        private const val ARTIST_PAGE_SIZE = 3

        /** Below this, a shelf is too thin to be worth a row of its own. */
        private const val MIN_SONG_SHELF = 4

        /** A backstop on scroll paging, in case a provider hands back cursors forever. */
        private const val MAX_PAGES = 12
    }
}

/** Every song id on these shelves, used to stop later shelves repeating earlier ones. */
private fun List<HomeSection>.songIds(): Set<String> =
    flatMap { section -> section.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }.toSet()

/**
 * Concatenate shelves, dropping any later one that reuses an earlier title.
 *
 * The provider names its own rows, so its generic feed can collide with ours ("Trending now"
 * appearing twice). Titles key the Home list, and duplicate keys are a hard crash in Compose.
 */
private fun merge(first: List<HomeSection>, second: List<HomeSection>): List<HomeSection> {
    val seen = first.mapTo(mutableSetOf()) { it.title }
    return first + second.filter { it.items.isNotEmpty() && seen.add(it.title) }
}

/**
 * Tracks to seed discovery from, preferring things actually listened to over untouched likes.
 *
 * Heavy rotation is what the listener is into *now*, which is the better jumping-off point for
 * something new; seeds stand in for a listener with no real history yet.
 */
private fun TasteProfile.discoveryPool(): List<Song> = heavyRotation.ifEmpty { seeds }
