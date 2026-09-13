package com.example.musicsm.domain.model

/** Ordered list of shelves shown on the Home screen. */
data class HomeFeed(
    val sections: List<HomeSection> = emptyList(),
)

data class HomeSection(
    val title: String,
    val items: List<HomeItem>,
)

/** A single card within a home shelf. */
sealed interface HomeItem {
    data class SongItem(val song: Song) : HomeItem
    data class AlbumItem(val album: Album) : HomeItem
    data class ArtistItem(val artist: Artist) : HomeItem
    data class PlaylistItem(val playlist: Playlist) : HomeItem
}
