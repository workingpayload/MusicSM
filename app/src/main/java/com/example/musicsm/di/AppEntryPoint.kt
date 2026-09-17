package com.example.musicsm.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

/**
 * Bridge for components Hilt cannot inject into directly — plain `object`s and framework types
 * such as widget providers. Lets them reuse the app-wide singletons instead of building their own.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun okHttpClient(): OkHttpClient
}
