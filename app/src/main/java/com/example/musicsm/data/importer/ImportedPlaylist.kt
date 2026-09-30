package com.example.musicsm.data.importer

/** A track as another service lists it, to be found on YouTube by name. */
data class ImportedTrack(
    val title: String,
    val artist: String,
    /** Length in ms, or 0 when unknown; tells the song apart from live cuts and remixes. */
    val durationMs: Long = 0L,
) {
    val searchQuery: String get() = "$artist $title".trim()
}

/** A public playlist read from another service (Spotify, Apple Music). */
data class ImportedPlaylist(
    val name: String,
    val coverUrl: String?,
    val tracks: List<ImportedTrack>,
)
