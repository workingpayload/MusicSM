package com.example.musicsm.domain.model

/** One line of lyrics. [timeMs] is the start time for synced lyrics, or null for plain text. */
data class LyricLine(
    val timeMs: Long?,
    val text: String,
)

/**
 * Lyrics for a track. [synced] is true when [lines] carry timestamps (karaoke-style highlight),
 * false when only plain text is available.
 */
data class Lyrics(
    val synced: Boolean,
    val lines: List<LyricLine>,
)
