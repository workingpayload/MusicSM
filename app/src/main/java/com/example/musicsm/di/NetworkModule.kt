package com.example.musicsm.di

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Disk budget for cacheable HTTP responses (metadata, lyrics, Spotify pages). */
    private const val HTTP_CACHE_BYTES = 20L * 1024 * 1024

    /**
     * The one HTTP client for the whole app. Sharing it means a single connection pool,
     * thread pool and response cache instead of one per collaborator, which is both faster
     * (connection reuse across NewPipe / lyrics / Spotify calls) and much lighter on memory.
     *
     * Bulk track downloads opt out of the cache explicitly so a single large file cannot
     * evict every cached metadata response.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_BYTES))
            .retryOnConnectionFailure(true)
            .build()
}
