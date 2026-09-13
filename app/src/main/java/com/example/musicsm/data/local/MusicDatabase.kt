package com.example.musicsm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.musicsm.data.local.dao.LikeDao
import com.example.musicsm.data.local.dao.PlaylistDao
import com.example.musicsm.data.local.dao.SongDao
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlaylistEntity
import com.example.musicsm.data.local.entity.PlaylistSongCrossRef
import com.example.musicsm.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        LikedSongEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun likeDao(): LikeDao
    abstract fun playlistDao(): PlaylistDao
}
