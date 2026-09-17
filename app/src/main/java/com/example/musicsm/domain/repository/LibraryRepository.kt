package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.flow.Flow

/** User's local library: liked songs/artists, playlists, and play history, persisted in Room. */
interface LibraryRepository {
    fun likedSongs(): Flow<List<Song>>
    fun playlists(): Flow<List<Playlist>>
    fun isLiked(songId: String): Flow<Boolean>

    suspend fun toggleLike(song: Song)

    // Liked artists.
    fun likedArtists(): Flow<List<Artist>>
    fun isArtistLiked(artistId: String): Flow<Boolean>
    suspend fun toggleArtistLike(artist: Artist)

    // Play history (most-recent first, capped).
    fun recentlyPlayed(): Flow<List<Song>>
    suspend fun recordPlay(song: Song)

    /** @return the new playlist's id. */
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun renamePlaylist(playlistId: Long, name: String)

    /** Set (or clear) a playlist's cover image URL. */
    suspend fun setPlaylistArtwork(playlistId: Long, url: String?)

    fun playlist(playlistId: Long): Flow<Playlist?>
    suspend fun addToPlaylist(playlistId: Long, song: Song)
    suspend fun removeFromPlaylist(playlistId: Long, songId: String)
    suspend fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int)
}
