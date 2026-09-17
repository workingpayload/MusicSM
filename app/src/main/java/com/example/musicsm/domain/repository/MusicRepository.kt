package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song

/**
 * Catalog + browse + stream resolution. Wraps a [com.example.musicsm.domain.source.MusicSource],
 * moving work to the IO dispatcher and caching resolved streams briefly (URLs expire).
 */
interface MusicRepository {
    suspend fun homeFeed(): HomeFeed
    suspend fun search(query: String): SearchResults
    suspend fun album(id: String): Album
    suspend fun artist(id: String): Artist
    suspend fun playlist(id: String): Playlist
    suspend fun relatedTo(songId: String): List<Song>

    /** Metadata for a single track id (deep links, inbound shares). */
    suspend fun song(songId: String): Song

    /** Real, freshly-updated trending music. */
    suspend fun trending(): List<Song>

    /**
     * Personalized picks: aggregates tracks related to [seeds] (e.g. the user's liked songs),
     * excluding the seeds themselves. Returns up to [limit] deduped songs.
     */
    suspend fun recommendations(seeds: List<Song>, limit: Int): List<Song>

    suspend fun resolveStream(songId: String): PlayableStream

    /** Static curated genre/mood tiles for the Search landing screen. */
    fun browseTiles(): List<BrowseTile>
}
