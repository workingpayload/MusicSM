package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.source.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val source: MusicSource,
) : MusicRepository {

    private val streamCache = ConcurrentHashMap<String, PlayableStream>()

    override suspend fun homeFeed(): HomeFeed = withContext(Dispatchers.IO) { source.homeFeed() }

    override suspend fun search(query: String): SearchResults =
        withContext(Dispatchers.IO) { source.search(query) }

    override suspend fun album(id: String): Album = withContext(Dispatchers.IO) { source.album(id) }

    override suspend fun artist(id: String): Artist = withContext(Dispatchers.IO) { source.artist(id) }

    override suspend fun playlist(id: String): Playlist =
        withContext(Dispatchers.IO) { source.playlist(id) }

    override suspend fun relatedTo(songId: String): List<Song> =
        withContext(Dispatchers.IO) { source.relatedTo(songId) }

    override suspend fun song(songId: String): Song =
        withContext(Dispatchers.IO) { source.song(songId) }

    override suspend fun trending(): List<Song> =
        withContext(Dispatchers.IO) { source.trending(20) }

    override suspend fun recommendations(seeds: List<Song>, limit: Int): List<Song> =
        withContext(Dispatchers.IO) {
            if (seeds.isEmpty()) return@withContext emptyList()
            val seedIds = seeds.mapTo(HashSet()) { it.id }
            // Fan out to relatedTo for each seed in parallel, then merge + dedup.
            coroutineScope {
                seeds.map { seed ->
                    async { runCatching { source.relatedTo(seed.id) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }.flatten()
                .filter { it.id !in seedIds }
                .distinctBy { it.id }
                .shuffled()
                .take(limit)
        }

    override suspend fun resolveStream(songId: String): PlayableStream = withContext(Dispatchers.IO) {
        val cached = streamCache[songId]
        if (cached != null && isStreamUsable(cached.expiresAtMs, System.currentTimeMillis(), REFRESH_MARGIN_MS)) {
            cached
        } else {
            source.resolveStream(songId).also { streamCache[songId] = it }
        }
    }

    override fun browseTiles(): List<BrowseTile> = BROWSE_TILES

    companion object {
        private const val REFRESH_MARGIN_MS = 60_000L

        private val BROWSE_TILES = listOf(
            BrowseTile("pop", "Pop", 0xFF1E3264, "pop hits"),
            BrowseTile("hiphop", "Hip-Hop", 0xFFBA5D07, "hip hop rap hits"),
            BrowseTile("rock", "Rock", 0xFFE13300, "rock anthems"),
            BrowseTile("lofi", "Lo-Fi", 0xFF8D67AB, "lofi hip hop beats"),
            BrowseTile("workout", "Workout", 0xFF777777, "workout gym music"),
            BrowseTile("chill", "Chill", 0xFF27856A, "chill relax music"),
            BrowseTile("focus", "Focus", 0xFF503750, "focus study instrumental"),
            BrowseTile("party", "Party", 0xFFDC148C, "party dance hits"),
            BrowseTile("jazz", "Jazz", 0xFF477D95, "smooth jazz"),
            BrowseTile("classical", "Classical", 0xFF7358FF, "classical music"),
            BrowseTile("indie", "Indie", 0xFF608108, "indie hits"),
            BrowseTile("throwback", "Throwback", 0xFF9CF0E1, "2000s throwback hits"),
        )
    }
}

/**
 * Whether a cached stream URL is still worth reusing. googlevideo URLs are time-limited, so a
 * [marginMs] safety window means playback never starts with a URL that is about to expire.
 *
 * Top-level and internal so the expiry rule can be unit-tested without a real extractor.
 */
internal fun isStreamUsable(expiresAtMs: Long, nowMs: Long, marginMs: Long): Boolean =
    expiresAtMs > nowMs + marginMs