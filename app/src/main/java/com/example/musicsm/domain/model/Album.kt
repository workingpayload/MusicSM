package com.example.musicsm.domain.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val year: String? = null,
    val songs: List<Song> = emptyList(),
)
