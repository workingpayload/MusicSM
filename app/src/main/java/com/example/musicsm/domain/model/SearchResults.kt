package com.example.musicsm.domain.model

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /**
     * Videos: music videos and uploads that aren't released songs (unreleased tracks, leaks,
     * covers, live recordings). Played as audio like any song.
     */
    val videos: List<Song> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() && videos.isEmpty()
}
