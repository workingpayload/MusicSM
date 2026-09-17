package com.example.musicsm.domain.source

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song

/**
 * Abstraction over the remote music catalog + audio. The concrete implementation is
 * YouTube-backed (InnerTube for metadata, NewPipeExtractor for the audio URL), but nothing
 * above this interface knows that — swap the impl to change providers.
 *
 * All functions are blocking network work; callers must invoke them off the main thread.
 */
interface MusicSource {
    suspend fun homeFeed(): HomeFeed
    suspend fun search(query: String): SearchResults
    suspend fun album(id: String): Album
    suspend fun artist(id: String): Artist
    suspend fun playlist(id: String): Playlist

    /** Real, freshly-updated trending music (YouTube trending_music kiosk). */
    suspend fun trending(limit: Int): List<Song>

    /** Songs related to [songId], used to seed/extend the play queue (radio). */
    suspend fun relatedTo(songId: String): List<Song>

    /** Metadata for a single track id, used by deep links and inbound shares. */
    suspend fun song(songId: String): Song

    /** Resolve a fresh, directly-playable audio stream for [songId]. URLs are short-lived. */
    suspend fun resolveStream(songId: String): PlayableStream
}
