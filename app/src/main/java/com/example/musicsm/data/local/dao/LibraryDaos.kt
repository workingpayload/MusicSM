package com.example.musicsm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.musicsm.data.local.entity.DownloadEntity
import com.example.musicsm.data.local.entity.LikedArtistEntity
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlayHistoryEntity
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

    @Query("UPDATE playlists SET artworkUrl = :url WHERE playlistId = :id")
    suspend fun setArtwork(id: Long, url: String?)

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

@Dao
interface ArtistDao {
    @Query("SELECT * FROM liked_artists ORDER BY likedAt DESC")
    fun likedArtists(): Flow<List<LikedArtistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_artists WHERE artistId = :id)")
    fun isLiked(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_artists WHERE artistId = :id)")
    suspend fun isLikedNow(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedArtistEntity)

    @Query("DELETE FROM liked_artists WHERE artistId = :id")
    suspend fun unlike(id: String)
}

@Dao
interface HistoryDao {
    /** Records a play (moves the song to the top), then trims to the most recent [limit]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlayHistoryEntity)

    @Query(
        "DELETE FROM play_history WHERE songId NOT IN " +
            "(SELECT songId FROM play_history ORDER BY playedAt DESC LIMIT :limit)",
    )
    suspend fun trim(limit: Int)

    @Query(
        "SELECT s.* FROM songs s INNER JOIN play_history h ON s.songId = h.songId " +
            "ORDER BY h.playedAt DESC",
    )
    fun recent(): Flow<List<SongEntity>>
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT filePath FROM downloads WHERE songId = :id")
    suspend fun pathOf(id: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE songId = :id)")
    fun isDownloaded(id: String): Flow<Boolean>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN downloads d ON s.songId = d.songId " +
            "ORDER BY d.downloadedAt DESC",
    )
    fun downloadedSongs(): Flow<List<SongEntity>>
}
