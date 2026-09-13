package com.example.musicsm.domain.model

/**
 * A playable track. [id] is the YouTube videoId; the actual stream URL is resolved
 * on demand (see MusicSource.resolveStream) because googlevideo URLs expire.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
)
