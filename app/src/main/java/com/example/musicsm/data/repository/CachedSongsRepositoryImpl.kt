package com.example.musicsm.data.repository

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.example.musicsm.data.cache.AdjustableLruCacheEvictor
import com.example.musicsm.data.local.dao.SongDao
import com.example.musicsm.data.local.entity.toSong
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.CachedSongsRepository
import com.example.musicsm.playback.MediaItemMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the shared [SimpleCache] the player streams through. Cache keys are
 * `CACHE_KEY_PREFIX + videoId`, so every entry maps back to a song; titles and artwork come from
 * the `songs` table, which records everything that has been played.
 */
@UnstableApi
@Singleton
class CachedSongsRepositoryImpl @Inject constructor(
    private val cache: SimpleCache,
    private val evictor: AdjustableLruCacheEvictor,
    private val songDao: SongDao,
) : CachedSongsRepository {

    override suspend fun cachedSongs(): List<Song> = withContext(Dispatchers.IO) {
        // Only whole tracks count: a half-cached song would cut out mid-way without a connection.
        val lastUsed = runCatching { cache.keys.toList() }.getOrDefault(emptyList())
            .filter { it.startsWith(MediaItemMapper.CACHE_KEY_PREFIX) }
            .mapNotNull { key ->
                runCatching {
                    val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                    if (length == C.LENGTH_UNSET.toLong() || length <= 0L) return@runCatching null
                    if (!cache.isCached(key, 0, length)) return@runCatching null
                    val touched = cache.getCachedSpans(key).maxOfOrNull { it.lastTouchTimestamp } ?: 0L
                    key.removePrefix(MediaItemMapper.CACHE_KEY_PREFIX) to touched
                }.getOrNull()
            }
            .filterNot { (id, _) -> MediaItemMapper.isLocal(id) }
            .toMap()
        if (lastUsed.isEmpty()) return@withContext emptyList()

        lastUsed.keys.chunked(SQL_BATCH)
            .flatMap { songDao.getByIds(it) }
            .map { it.toSong() }
            .sortedByDescending { lastUsed[it.id] ?: 0L }
    }

    override suspend fun remove(songId: String) = withContext(Dispatchers.IO) {
        runCatching { cache.removeResource(MediaItemMapper.cacheKey(songId)) }
        Unit
    }

    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { cache.cacheSpace }.getOrDefault(0L)
    }

    override suspend fun trimToLimit() = withContext(Dispatchers.IO) {
        runCatching { evictor.trim(cache) }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { cache.keys.toList() }.getOrDefault(emptyList())
            .forEach { key -> runCatching { cache.removeResource(key) } }
    }

    private companion object {
        /** Stays well under SQLite's bound-variable limit. */
        const val SQL_BATCH = 500
    }
}
