package com.example.musicsm.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.musicsm.ui.album.AlbumDetailScreen
import com.example.musicsm.ui.artist.ArtistDetailScreen
import com.example.musicsm.ui.home.HomeScreen
import com.example.musicsm.ui.library.LibraryScreen
import com.example.musicsm.ui.library.PlaylistDetailScreen
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.search.SearchScreen

@Composable
fun MusicSmNavHost(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dur = 300
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(onPlaySongs = { songs, index -> playerViewModel.play(songs, index) })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onPlaySong = { playerViewModel.playWithRadio(it) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                playerViewModel = playerViewModel,
                onOpenLiked = { navController.navigate(Routes.liked()) },
                onOpenPlaylist = { navController.navigate(Routes.localPlaylist(it)) },
            )
        }
        composable(
            route = Routes.ALBUM,
            arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            AlbumDetailScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ARTIST,
            arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            ArtistDetailScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
            )
        }
        composable(
            route = Routes.LOCAL_PLAYLIST,
            arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            PlaylistDetailScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
