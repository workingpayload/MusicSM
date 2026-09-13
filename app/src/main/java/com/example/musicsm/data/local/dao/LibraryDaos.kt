package com.example.musicsm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlaylistEntity
import com.example.musicsm.data.local.entity.PlaylistSongCrossRef
import com.example.musicsm.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Upsert
    suspend fun upsert(song: SongEntity)

    @Query("SELECT * FROM songs WHERE songId = :id")
    suspend fun getById(id: String): SongEntity?
}

@Dao
interface LikeDao {
    @Query(
        "SELECT s.* FROM songs s INNER JOIN liked_songs l ON s.songId = l.songId ORDER BY l.likedAt DESC",
    )
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :id)")
    fun isLiked(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :id)")
    suspend fun isLikedNow(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :id")
    suspend fun unlike(id: String)
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.songId = ps.songId " +
            "WHERE ps.playlistId = :id ORDER BY ps.position ASC",
    )
    fun playlistSongs(id: Long): Flow<List<SongEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCrossRef(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid")
    suspend fun removeCrossRef(pid: Long, sid: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position ASC")
    suspend fun crossRefs(id: Long): List<PlaylistSongCrossRef>

    @Update
    suspend fun updateCrossRefs(refs: List<PlaylistSongCrossRef>)
}
