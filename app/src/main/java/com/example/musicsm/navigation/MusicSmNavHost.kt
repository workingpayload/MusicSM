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
import com.example.musicsm.ui.importer.ImportPlaylistScreen
import com.example.musicsm.ui.library.LibraryScreen
import com.example.musicsm.ui.library.PlaylistDetailScreen
import com.example.musicsm.ui.player.DownloadsScreen
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.search.SearchScreen
import com.example.musicsm.ui.settings.EqualizerScreen
import com.example.musicsm.ui.settings.SettingsScreen
import com.example.musicsm.ui.jam.JamScreen
import com.example.musicsm.ui.share.SharedPlaylistScreen
import com.example.musicsm.ui.stats.StatsScreen

@Composable
fun MusicSmNavHost(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.HOME,
) {
    val dur = 300
    NavHost(
        navController = navController,
        startDestination = startDestination,
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
            HomeScreen(
                onPlaySongs = { songs, index -> playerViewModel.play(songs, index) },
                onOpenJam = { navController.navigate(Routes.JAM) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                playerViewModel = playerViewModel,
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
                onImportPlaylist = { navController.navigate(Routes.IMPORT) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStats = { navController.navigate(Routes.STATS) },
            )
        }
        composable(
            route = Routes.STATS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            StatsScreen(
                onBack = { navController.popBackStack() },
                onPlaySongs = { songs, index -> playerViewModel.play(songs, index) },
                onOpenSearch = {
                    navController.navigate(Routes.SEARCH) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.SHARED_PLAYLIST,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            SharedPlaylistScreen(
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { id ->
                    // Replace the preview: backing out should not offer to import again.
                    navController.navigate(Routes.localPlaylist(id)) {
                        popUpTo(Routes.SHARED_PLAYLIST) { inclusive = true }
                    }
                },
                onPlay = { songs, index -> playerViewModel.play(songs, index) },
            )
        }
        composable(
            route = Routes.JAM,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            JamScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SETTINGS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            )
        }
        composable(
            route = Routes.EQUALIZER,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.IMPORT,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            ImportPlaylistScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DOWNLOADS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            DownloadsScreen(playerViewModel = playerViewModel, onBack = { navController.popBackStack() })
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
