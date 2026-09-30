package com.example.musicsm.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.example.musicsm.data.cache.AdjustableLruCacheEvictor
import com.example.musicsm.data.prefs.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * App-wide media cache. ExoPlayer streams read through this, so tracks the user plays are cached
 * to disk (LRU, capped by the user's "Cache size" setting) and replay — even offline — without
 * re-fetching. Spotify-style caching, separate from explicit
 * [com.example.musicsm.domain.repository.DownloadRepository] downloads.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaCacheModule {

    @Provides
    @Singleton
    @UnstableApi
    fun provideCacheEvictor(preferences: AppPreferences): AdjustableLruCacheEvictor =
        AdjustableLruCacheEvictor { preferences.cacheLimitBytesNow }

    @Provides
    @Singleton
    @UnstableApi
    fun provideMediaCache(
        @ApplicationContext context: Context,
        evictor: AdjustableLruCacheEvictor,
    ): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "media"),
            evictor,
            StandaloneDatabaseProvider(context),
        )
}
