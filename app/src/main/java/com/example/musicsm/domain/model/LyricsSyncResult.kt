package com.example.musicsm.domain.model

/** What measuring a track against its album audio found, for lining up lyrics. */
sealed interface LyricsSyncResult {
    /** The playing track runs [offsetMs] behind the album audio (negative: ahead). */
    data class Measured(val offsetMs: Long) : LyricsSyncResult

    /** The track is the album audio itself, so there is nothing to line it up against. */
    data object AlreadyAlbumAudio : LyricsSyncResult

    /** No album audio could be found for the track. */
    data object NoAlbumAudio : LyricsSyncResult

    /** Both were heard but didn't line up with confidence (a different mix, a live take…). */
    data object NoMatch : LyricsSyncResult

    /** The audio couldn't be loaded (offline, stream refused). */
    data object Failed : LyricsSyncResult
}
