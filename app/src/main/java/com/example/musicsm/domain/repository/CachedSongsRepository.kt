package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.Song

/**
 * Songs whose audio is fully held in the player's rolling stream cache, so they play without a
 * connection even though they were never explicitly downloaded.
 */
interface CachedSongsRepository {
    /** Fully cached songs, most recently used first. */
    suspend fun cachedSongs(): List<Song>

    /** Drop a song's cached audio. */
    suspend fun remove(songId: String)

    /** Disk space the song cache currently takes, in bytes. */
    suspend fun usedBytes(): Long

    /** Evicts least recently played songs until the cache fits the current size limit. */
    suspend fun trimToLimit()

    /** Drops every cached song. Explicit downloads are unaffected. */
    suspend fun clear()
}
