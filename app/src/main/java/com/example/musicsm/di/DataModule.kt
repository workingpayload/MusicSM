package com.example.musicsm.di

import com.example.musicsm.data.repository.LibraryRepositoryImpl
import com.example.musicsm.data.repository.LyricsRepositoryImpl
import com.example.musicsm.data.repository.MusicRepositoryImpl
import com.example.musicsm.data.repository.SpotifyImportRepositoryImpl
import com.example.musicsm.data.source.youtube.NewPipeMusicSource
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.PlaylistImportRepository
import com.example.musicsm.domain.source.MusicSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMusicSource(impl: NewPipeMusicSource): MusicSource

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistImportRepository(impl: SpotifyImportRepositoryImpl): PlaylistImportRepository
}
