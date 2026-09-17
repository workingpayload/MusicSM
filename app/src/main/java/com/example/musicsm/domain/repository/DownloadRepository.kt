package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A song currently being downloaded, with its progress (0f..1f). */
data class DownloadingSong(val song: Song, val progress: Float)

/** A download that stopped with an error; the partial file is kept so it can resume. */
data class FailedDownload(val song: Song, val reason: String)

/** Offline downloads: fetches a song's audio to local storage for offline playback. */
interface DownloadRepository {
    /** Per-song download progress (songId -> 0f..1f) while a download is in flight. */
    val progress: StateFlow<Map<String, Float>>

    /** In-flight downloads with full song metadata (thumbnail, title) and progress. */
    val activeDownloads: StateFlow<List<DownloadingSong>>

    /** Downloads that failed and can be resumed from where they stopped. */
    val failedDownloads: StateFlow<List<FailedDownload>>

    fun isDownloaded(songId: String): Flow<Boolean>
    fun downloads(): Flow<List<Song>>

    suspend fun download(song: Song)

    /** Queue a batch (album, playlist, liked songs) for offline storage. */
    suspend fun downloadAll(songs: List<Song>)

    /** Stop an in-flight download, keeping the partial file so it can resume later. */
    fun cancel(songId: String)

    /** Clear a failure and start the download again, resuming from the partial file. */
    suspend fun retry(songId: String)

    suspend fun delete(songId: String)

    /** Delete every downloaded file and its database rows. */
    suspend fun deleteAll()

    /** Total bytes used by downloaded audio, including partial files. */
    suspend fun storageUsedBytes(): Long

    /** Absolute path to the local audio file if downloaded and present, else null. */
    suspend fun localPath(songId: String): String?
}
