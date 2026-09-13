package com.example.musicsm.di

import android.content.Context
import androidx.room.Room
import com.example.musicsm.data.local.MusicDatabase
import com.example.musicsm.data.local.dao.LikeDao
import com.example.musicsm.data.local.dao.PlaylistDao
import com.example.musicsm.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "musicsm.db").build()

    @Provides
    fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()

    @Provides
    fun provideLikeDao(db: MusicDatabase): LikeDao = db.likeDao()

    @Provides
    fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()
}
