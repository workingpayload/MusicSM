package com.example.musicsm.domain.model

/**
 * A genre / mood browse tile shown on the Search landing screen.
 * [accentColor] is an ARGB color used for the tile background.
 */
data class BrowseTile(
    val id: String,
    val title: String,
    val accentColor: Long,
    val query: String,
    val artworkUrl: String? = null,
)
