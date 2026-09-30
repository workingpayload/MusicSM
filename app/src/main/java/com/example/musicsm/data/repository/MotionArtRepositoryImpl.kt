package com.example.musicsm.data.repository

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.net.ConnectivityManagerCompat
import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import com.example.motionart.MotionArtSource
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.MotionArtRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves motion covers, remembering both hits and misses.
 *
 * Misses are cached as deliberately as hits. Most of the catalog has no motion cover, so without a
 * negative cache the common case would be a fresh network round trip every time a track came back
 * around in the queue — repeated work to learn the same "no" over and over.
 */
@Singleton
class MotionArtRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: MotionArtSource,
    private val prefs: AppPreferences,
) : MotionArtRepository {

    /**
     * Access-ordered so the cache evicts what has not been looked at recently.
     *
     * Keyed by track *and* source: pinning a different catalog has to be able to produce a
     * different answer for a track already looked up under the previous one.
     *
     * A miss is stored as [NONE] rather than as an absent key, which is what makes the negative
     * caching work; callers only ever see null.
     */
    private val cache = object : LinkedHashMap<String, MotionArt>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MotionArt>) = size > CACHE_SIZE
    }

    override suspend fun forSong(song: Song): MotionArt? {
        if (!prefs.animatedArtworkNow) return null
        val preferred = MotionArtProvider.fromName(prefs.animatedArtworkSourceNow)
        val key = "${song.id}#${preferred.name}"
        synchronized(cache) { cache[key] }?.let { return it.takeIf { v -> v !== NONE } }
        if (!isAllowedOnCurrentNetwork()) return null

        val found = withContext(Dispatchers.IO) {
            source.lookup(
                artist = song.artist,
                title = song.title,
                album = song.album,
                preferred = preferred,
            )
        }
        synchronized(cache) { cache[key] = found ?: NONE }
        return found
    }

    /**
     * Whether a cover loop is worth fetching over the connection in use right now.
     *
     * Deliberately not cached: the answer changes when the listener walks out of Wi-Fi range, and
     * a stale "yes" would start pulling video over mobile data.
     */
    private fun isAllowedOnCurrentNetwork(): Boolean {
        if (!prefs.animatedArtworkWifiOnlyNow) return true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return !ConnectivityManagerCompat.isActiveNetworkMetered(cm)
    }

    private companion object {
        const val CACHE_SIZE = 256

        /** Sentinel for "looked this up, there is nothing"; compared by identity, never shown. */
        val NONE = MotionArt(videoUrl = "", provider = MotionArtProvider.AUTO)
    }
}
