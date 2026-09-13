package com.example.musicsm.domain.model

/**
 * A playlist — either a remote (YouTube Music) playlist or a user-created local one.
 * [isLocal] distinguishes user playlists persisted in Room from remote catalog playlists.
 */
data class Playlist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val songs: List<Song> = emptyList(),
    val isLocal: Boolean = false,
)
