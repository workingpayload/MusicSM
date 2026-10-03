package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.AccountLibrary
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.AlbumAudio
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

    /** The next batch of home shelves for a cursor from a previous [homeFeed]. */
    suspend fun moreHomeShelves(continuation: String): HomeFeed

    /** Songs, albums and artists for [query], plus videos when [includeVideos] (the Search screen). */
    suspend fun search(query: String, includeVideos: Boolean = false): SearchResults

    /** Songs only, for matching many tracks by name (playlist import). */
    suspend fun searchSongs(query: String): List<Song>

    suspend fun album(id: String): Album
    suspend fun artist(id: String): Artist
    suspend fun playlist(id: String): Playlist

    /** Every track of a YouTube / YouTube Music playlist, up to [maxTracks] (for importing it). */
    suspend fun fullPlaylist(id: String, maxTracks: Int): Playlist

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

    /**
     * A long radio queue grown from a single track.
     *
     * One hop of "related to this" is only ever a couple of dozen songs and all of it sits very
     * close to the seed, so a queue built that way is both short and repetitive. This widens the
     * net by expanding outward from the strongest early results as well, giving a queue that can
     * play for hours and drifts somewhere instead of circling.
     *
     * [exclude] holds ids already queued, so a top-up never re-adds what is still waiting to play.
     */
    suspend fun radio(seed: Song, limit: Int, exclude: Set<String> = emptySet()): List<Song>

    suspend fun resolveStream(songId: String): PlayableStream

    /**
     * Drops any cached stream URL for [songId] so the next [resolveStream] fetches a fresh one.
     * Used to recover from a URL that expired or was rejected mid-playback before its cached TTL.
     */
    fun invalidateStream(songId: String)

    /**
     * Whether [songId] is its release's own audio, another cut of it (music video, upload), or
     * unknown. Lyrics are timed to the release's audio, so this is what they get lined up against.
     */
    suspend fun albumAudio(songId: String): AlbumAudio

    /** Static curated genre/mood tiles for the Search landing screen. */
    fun browseTiles(): List<BrowseTile>

    /** The signed-in listener's YouTube Music playlists and artists; empty when not personalising. */
    suspend fun accountLibrary(): AccountLibrary

    /** The signed-in listener's recent plays on YouTube Music; empty when not personalising. */
    suspend fun accountHistory(): List<Song>
}
