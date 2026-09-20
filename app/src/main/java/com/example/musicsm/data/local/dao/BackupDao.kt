package com.example.musicsm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicsm.data.local.entity.LikedArtistEntity
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlayEventEntity
import com.example.musicsm.data.local.entity.PlayHistoryEntity
import com.example.musicsm.data.local.entity.PlaylistEntity
import com.example.musicsm.data.local.entity.PlaylistSongCrossRef
import com.example.musicsm.data.local.entity.SongEntity

/**
 * Bulk read/write for backup + restore. Reads pull whole tables in one shot; inserts use
 * `IGNORE` so a restore adds what's missing without clobbering (or colliding with) existing rows.
 * Playlists are handled by the repository, not here, because their auto-generated ids are remapped.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM songs")
    suspend fun allSongs(): List<SongEntity>

    @Query("SELECT * FROM liked_songs")
    suspend fun allLikedSongs(): List<LikedSongEntity>

    @Query("SELECT * FROM liked_artists")
    suspend fun allLikedArtists(): List<LikedArtistEntity>

    @Query("SELECT * FROM playlists")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs")
    suspend fun allPlaylistSongs(): List<PlaylistSongCrossRef>

    @Query("SELECT * FROM play_history")
    suspend fun allPlayHistory(): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_events")
    suspend fun allPlayEvents(): List<PlayEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(rows: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLikedSongs(rows: List<LikedSongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLikedArtists(rows: List<LikedArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlayHistory(rows: List<PlayHistoryEntity>)

    // eventId auto-generates, so imported events get fresh ids and never collide with existing ones.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlayEvents(rows: List<PlayEventEntity>)
}
