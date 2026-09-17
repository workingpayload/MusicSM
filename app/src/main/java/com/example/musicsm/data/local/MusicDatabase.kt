package com.example.musicsm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.musicsm.data.local.dao.ArtistDao
import com.example.musicsm.data.local.dao.DownloadDao
import com.example.musicsm.data.local.dao.HistoryDao
import com.example.musicsm.data.local.dao.LikeDao
import com.example.musicsm.data.local.dao.PlaylistDao
import com.example.musicsm.data.local.dao.SongDao
import com.example.musicsm.data.local.entity.DownloadEntity
import com.example.musicsm.data.local.entity.LikedArtistEntity
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlayHistoryEntity
import com.example.musicsm.data.local.entity.PlaylistEntity
import com.example.musicsm.data.local.entity.PlaylistSongCrossRef
import com.example.musicsm.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        LikedSongEntity::class,
        LikedArtistEntity::class,
        PlayHistoryEntity::class,
        DownloadEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun likeDao(): LikeDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun artistDao(): ArtistDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
}
