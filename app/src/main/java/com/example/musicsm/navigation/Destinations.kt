package com.example.musicsm.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import com.example.musicsm.R

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
    const val IMPORT = "import"
    const val DOWNLOADS = "downloads"
    const val LOCAL = "local"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val EQUALIZER = "equalizer"
    const val SHARED_PLAYLIST = "shared_playlist"

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
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_listen, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    LIBRARY(Routes.LIBRARY, R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
}
