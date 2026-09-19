package com.example.musicsm.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * App-wide media cache. ExoPlayer streams read through this, so tracks the user plays are cached
 * to disk (LRU, capped) and replay — even offline — without re-fetching. Spotify-style caching,
 * separate from explicit [com.example.musicsm.domain.repository.DownloadRepository] downloads.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaCacheModule {

    /** Rolling disk budget for auto-cached audio; oldest tracks are evicted first. */
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    @Provides
    @Singleton
    @UnstableApi
    fun provideMediaCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
}
