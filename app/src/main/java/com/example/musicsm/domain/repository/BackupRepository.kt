package com.example.musicsm.domain.repository

/** Counts of what a restore actually added, for a confirmation message. */
data class BackupSummary(
    val songs: Int,
    val likedSongs: Int,
    val playlists: Int,
    val playEvents: Int,
)

/**
 * Whole-library backup and restore. The app has no account login, so this is the only way to move
 * a library (liked songs, playlists, play history/stats and settings) to a new install. Backups are
 * a single portable JSON blob; restore MERGES into whatever is already there (never destructive).
 *
 * Downloaded audio files are intentionally NOT part of a backup — their paths are device-specific
 * and the bytes don't transfer — so downloads are re-fetched on demand after a restore.
 */
interface BackupRepository {

    /** Serializes the library + settings into a JSON string to be written to a backup file. */
    suspend fun export(): String

    /**
     * Merges a backup produced by [export] into the current data, de-duplicating. Throws
     * [IllegalArgumentException] if [json] is not a MusicSM backup.
     */
    suspend fun import(json: String): BackupSummary
}
