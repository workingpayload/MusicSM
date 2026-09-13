package com.example.musicsm.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri

/** Route constants + builders for the whole app. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"

    const val PLAYER = "player"
    const val QUEUE = "queue"

    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"
    const val LOCAL_PLAYLIST = "local_playlist/{playlistId}"

    fun album(albumId: String) = "album/${Uri.encode(albumId)}"
    fun artist(artistId: String) = "artist/${Uri.encode(artistId)}"
    fun localPlaylist(playlistId: Long) = "local_playlist/$playlistId"

    /** Sentinel playlist id for the built-in "Liked Songs" collection. */
    const val LIKED_PLAYLIST_ID = -1L
    fun liked() = localPlaylist(LIKED_PLAYLIST_ID)

    const val ARG_ALBUM_ID = "albumId"
    const val ARG_ARTIST_ID = "artistId"
    const val ARG_PLAYLIST_ID = "playlistId"
}

/** The three bottom-navigation tabs. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, "Listen", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    LIBRARY(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
}
