package com.example.musicsm.domain.source

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.AlbumAudio
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song

/**
 * Abstraction over the remote music catalog + audio. The concrete implementation is
 * YouTube-backed (YouTube Music's InnerTube API for metadata, NewPipeExtractor for the audio
 * URL), but nothing above this interface knows that — swap the impl to change providers.
 *
 * All functions are blocking network work; callers must invoke them off the main thread.
 */
interface MusicSource {
    suspend fun homeFeed(): HomeFeed

    /**
     * The next batch of home shelves for a cursor from a previous [homeFeed].
     *
     * Returns an empty feed when the provider cannot page, which lets the caller treat "no more"
     * and "not supported" the same way.
     */
    suspend fun moreHomeShelves(continuation: String): HomeFeed

    /** Songs, albums and artists for [query], plus videos when [includeVideos] (see [SearchResults.videos]). */
    suspend fun search(query: String, includeVideos: Boolean = false): SearchResults

    /** Songs only; one request instead of a full [search]'s three (bulk matching on import). */
    suspend fun searchSongs(query: String): List<Song> = search(query).songs

    suspend fun album(id: String): Album
    suspend fun artist(id: String): Artist
    suspend fun playlist(id: String): Playlist

    /**
     * Every track of playlist [id] (a YouTube / YouTube Music list id or URL), reading its pages
     * up to [maxTracks], for importing it whole; [playlist] stops at the first page. Tracks keep
     * only their own album, since they're saved as songs in their own right.
     */
    suspend fun fullPlaylist(id: String, maxTracks: Int): Playlist = playlist(id)

    /** Real, freshly-updated trending music (YouTube trending_music kiosk). */
    suspend fun trending(limit: Int): List<Song>

    /** Songs related to [songId], used to seed/extend the play queue (radio). */
    suspend fun relatedTo(songId: String): List<Song>

    /** Metadata for a single track id, used by deep links and inbound shares. */
    suspend fun song(songId: String): Song

    /** Resolve a fresh, directly-playable audio stream for [songId]. URLs are short-lived. */
    suspend fun resolveStream(songId: String): PlayableStream

    /** Whether [songId] is its release's own audio, another cut of it, or unknown. */
    suspend fun albumAudio(songId: String): AlbumAudio = AlbumAudio.Unknown
}
