package com.example.musicsm.domain.model

/** A resolved, directly-playable audio stream for a [Song]. URLs are short-lived. */
data class PlayableStream(
    val url: String,
    val mimeType: String? = null,
    val bitrate: Int = 0,
    val expiresAtMs: Long = Long.MAX_VALUE,
)
