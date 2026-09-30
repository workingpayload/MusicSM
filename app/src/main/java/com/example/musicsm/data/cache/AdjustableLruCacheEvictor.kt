package com.example.musicsm.data.cache

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Media3's `LeastRecentlyUsedCacheEvictor` with a budget read on every check instead of fixed at
 * construction, so the user can resize the song cache without restarting the app.
 *
 * `SimpleCache` calls every evictor callback while holding its own monitor, so [trim] takes that
 * same monitor rather than a separate lock (which could deadlock against the cache).
 */
@UnstableApi
class AdjustableLruCacheEvictor(private val maxBytes: () -> Long) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compare)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /** Evicts down to the current budget right away, e.g. after the user lowers it. */
    fun trim(cache: Cache) = synchronized(cache) { evict(cache, 0) }

    private fun evict(cache: Cache, requiredSpace: Long) {
        val budget = maxBytes()
        while (currentSize + requiredSpace > budget && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }

    private companion object {
        fun compare(lhs: CacheSpan, rhs: CacheSpan): Int = when {
            lhs.lastTouchTimestamp == rhs.lastTouchTimestamp -> lhs.compareTo(rhs)
            lhs.lastTouchTimestamp < rhs.lastTouchTimestamp -> -1
            else -> 1
        }
    }
}
