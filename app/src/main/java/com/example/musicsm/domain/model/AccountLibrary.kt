package com.example.musicsm.domain.model

/**
 * The signed-in listener's YouTube Music library: saved playlists (Liked music first) and the
 * artists they subscribe to. Empty when personalisation is off or nobody is signed in.
 */
data class AccountLibrary(
    val playlists: List<Playlist> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = playlists.isEmpty() && artists.isEmpty()
}
