package com.example.musicsm.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.musicsm.domain.model.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAt: Long,
    val artworkUrl: String? = null,
)

/**
 * Join table. Both sides cascade: deleting a playlist drops its membership rows, and deleting a
 * song removes it from every playlist, so the library can never show a row pointing at nothing.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String,
    val position: Int,
)

@Entity(
    tableName = "liked_songs",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LikedSongEntity(
    @PrimaryKey val songId: String,
    val likedAt: Long,
)

@Entity(tableName = "liked_artists")
data class LikedArtistEntity(
    @PrimaryKey val artistId: String,
    val name: String,
    val artworkUrl: String?,
    val likedAt: Long,
)

@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlayHistoryEntity(
    @PrimaryKey val songId: String,
    val playedAt: Long,
)

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val filePath: String,
    val mimeType: String?,
    val downloadedAt: Long,
)

fun SongEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
)

fun Song.toEntity() = SongEntity(
    songId = id,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
)
