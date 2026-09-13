package com.example.musicsm.domain.model

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val subscribers: String? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
)
