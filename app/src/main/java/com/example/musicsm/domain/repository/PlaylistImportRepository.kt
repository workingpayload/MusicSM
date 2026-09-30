package com.example.musicsm.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Result of importing an external playlist into the local library. */
data class ImportResult(
    val playlistId: Long,
    val name: String,
    val matched: Int,
    val total: Int,
)

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val done: Int, val total: Int) : ImportState
    data class Finished(val result: ImportResult) : ImportState
    data class Failed(val message: String?) : ImportState
}

/**
 * Imports a playlist from a public share link (Spotify, Apple Music, YouTube / YouTube Music) into
 * the local library. YouTube tracks are saved as they are; others are matched against YouTube.
 */
interface PlaylistImportRepository {
    /** The running or last finished import, app-wide, so leaving the screen doesn't stop it. */
    val state: StateFlow<ImportState>

    /** Starts importing [link]; false (and nothing happens) if an import is already running. */
    fun start(link: String): Boolean

    /** Forgets a finished or failed import; a running one carries on. */
    fun acknowledge()
}
