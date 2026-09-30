package com.example.musicsm.di

import com.example.musicsm.data.repository.BackupRepositoryImpl
import com.example.musicsm.data.repository.CachedSongsRepositoryImpl
import com.example.musicsm.data.repository.DownloadRepositoryImpl
import com.example.musicsm.data.repository.LibraryRepositoryImpl
import com.example.musicsm.data.repository.LocalMusicRepositoryImpl
import com.example.musicsm.data.repository.LyricsRepositoryImpl
import com.example.musicsm.data.repository.LyricsSyncRepositoryImpl
import com.example.musicsm.data.repository.MotionArtRepositoryImpl
import com.example.musicsm.data.repository.MusicRepositoryImpl
import com.example.musicsm.data.repository.PlaylistImportRepositoryImpl
import com.example.musicsm.data.repository.StatsRepositoryImpl
import com.example.musicsm.data.source.youtube.NewPipeMusicSource
import com.example.musicsm.data.source.youtube.YouTubeMusicSource
import com.example.musicsm.domain.repository.BackupRepository
import com.example.musicsm.domain.repository.CachedSongsRepository
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.LocalMusicRepository
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsm.domain.repository.LyricsSyncRepository
import com.example.musicsm.domain.repository.MotionArtRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.PlaylistImportRepository
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsm.domain.source.MusicSource
import com.example.innertube.InnerTube
import com.example.motionart.MotionArtSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMusicSource(impl: YouTubeMusicSource): MusicSource

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
    abstract fun bindPlaylistImportRepository(impl: PlaylistImportRepositoryImpl): PlaylistImportRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindLocalMusicRepository(impl: LocalMusicRepositoryImpl): LocalMusicRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindMotionArtRepository(impl: MotionArtRepositoryImpl): MotionArtRepository

    @Binds
    @Singleton
    abstract fun bindCachedSongsRepository(impl: CachedSongsRepositoryImpl): CachedSongsRepository

    @Binds
    @Singleton
    abstract fun bindLyricsSyncRepository(impl: LyricsSyncRepositoryImpl): LyricsSyncRepository

    companion object {
        /**
         * One client for the whole app: it owns an HTTP connection pool, so creating it per call
         * would mean a fresh TLS handshake on every shelf the home screen loads.
         */
        @Provides
        @Singleton
        fun provideInnerTube(): InnerTube = InnerTube()

        /**
         * Shared for the same reason, and additionally because it caches the credential it scrapes
         * — a second instance would repeat that scrape for nothing.
         */
        @Provides
        @Singleton
        fun provideMotionArtSource(): MotionArtSource = MotionArtSource()
    }
}
