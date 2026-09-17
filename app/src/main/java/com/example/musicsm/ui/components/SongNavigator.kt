package com.example.musicsm.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets deeply-nested UI (song option sheets, the Now-Playing sheet) jump to an album or artist
 * page without every screen having to thread navigation callbacks down. Provided once by the
 * nav root, which also collapses the player sheet before navigating.
 */
@Immutable
data class SongNavigator(
    val openAlbum: (String) -> Unit = {},
    val openArtist: (String) -> Unit = {},
)

val LocalSongNavigator = staticCompositionLocalOf { SongNavigator() }
