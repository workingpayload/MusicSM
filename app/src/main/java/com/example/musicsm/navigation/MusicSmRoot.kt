package com.example.musicsm.navigation

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.ui.util.isOnline
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.LocalHazeState
import com.example.musicsm.ui.components.LocalSongNavigator
import com.example.musicsm.ui.components.MiniPlayer
import com.example.musicsm.ui.components.SongNavigator
import com.example.musicsm.ui.components.glassBackdrop
import com.example.musicsm.ui.components.rememberHazeState
import com.example.musicsm.ui.player.DownloadStatusBar
import com.example.musicsm.ui.player.AmbientScreen
import com.example.musicsm.ui.player.ExpandedPlayer
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.share.rememberQrScanner
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.GlassFillStrong
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Short-lived feedback for link handling; the app has no snackbar host at the root. */
private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
fun MusicSmRoot(
    appIntents: StateFlow<AppIntent?>? = null,
    onIntentHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val intentViewModel: AppIntentViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val context = LocalContext.current
    var barHeight by remember { mutableStateOf(0.dp) }
    // Ambient mode is an overlay, not a destination: it has to cover the player sheet, which is
    // itself drawn above the nav host.
    var ambientMode by remember { mutableStateOf(false) }
    // Hoisted out of the intent handler below: resolving a string from a raw Context inside a
    // composable bypasses Compose's configuration tracking.
    val badLinkMessage = stringResource(R.string.shared_playlist_bad_link)
    val unknownCodeMessage = stringResource(R.string.scan_unknown_code)
    val scannerUnavailableMessage = stringResource(R.string.scan_unavailable)

    // Nothing to screensaver once playback is gone.
    LaunchedEffect(playerState.currentSong == null) {
        if (playerState.currentSong == null) ambientMode = false
    }

    // No internet on launch → open the Library (offline downloads) instead of an empty Home.
    val startDestination = remember { if (isOnline(context)) Routes.HOME else Routes.LIBRARY }

    val scope = rememberCoroutineScope()
    // 0 = collapsed (mini player), 1 = fully expanded Now-Playing sheet.
    val expand = remember { Animatable(0f) }
    val hasSong = playerState.currentSong != null

    val sheetSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        val fullPx = constraints.maxHeight.toFloat()

        val expandNow = { scope.launch { expand.animateTo(1f, sheetSpring) } }
        val collapse = { scope.launch { expand.animateTo(0f, sheetSpring) } }
        // Drag up on the mini bar (dy < 0) grows the sheet; drag down shrinks it.
        val onDragDelta: (Float) -> Unit = { dy ->
            scope.launch { expand.snapTo((expand.value - dy / fullPx).coerceIn(0f, 1f)) }
        }
        val onDragFinished = {
            scope.launch { expand.animateTo(if (expand.value > 0.35f) 1f else 0f, sheetSpring) }
        }

        // Lets the song options sheet (and the player) jump to a detail page from anywhere;
        // the player sheet slides away first so the destination isn't hidden behind it.
        val songNavigator = remember(navController) {
            SongNavigator(
                openAlbum = { id ->
                    collapse()
                    navController.navigate(Routes.album(id))
                },
                openArtist = { id ->
                    collapse()
                    navController.navigate(Routes.artist(id))
                },
            )
        }

        // Switching bottom-nav tabs: single-top, restoring each tab's own back stack.
        val navigateToTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // Launcher shortcuts, deep links, shared YouTube links, widget/tile taps, voice search —
        // and QR codes scanned in-app, which deliberately run through this same handler so a
        // scanned playlist behaves exactly like a tapped link.
        val handleAppIntent: suspend (AppIntent) -> Unit = { appIntent ->
            when (appIntent) {
                AppIntent.Resume -> {
                    if (playerViewModel.resumePlayback()) expandNow()
                }
                is AppIntent.OpenTab -> navigateToTab(appIntent.route)
                is AppIntent.Search -> {
                    intentViewModel.prefillSearch(appIntent.query)
                    navigateToTab(Routes.SEARCH)
                    if (appIntent.playFirst && appIntent.query.isNotBlank()) {
                        if (playerViewModel.searchAndPlay(appIntent.query)) expandNow()
                    }
                }
                is AppIntent.PlaySong -> {
                    if (playerViewModel.playSongId(appIntent.songId)) {
                        expandNow()
                    } else {
                        toast(context, context.getString(R.string.error_open_track))
                    }
                }
                is AppIntent.OpenAlbum -> navController.navigate(Routes.album(appIntent.albumId))
                is AppIntent.OpenArtist -> navController.navigate(Routes.artist(appIntent.artistId))
                is AppIntent.OpenPlaylist ->
                    navController.navigate(Routes.localPlaylist(appIntent.playlistId))
                AppIntent.OpenLiked -> navController.navigate(Routes.liked())
                AppIntent.OpenDownloads -> navController.navigate(Routes.DOWNLOADS)
                is AppIntent.ImportSharedPlaylist -> {
                    if (intentViewModel.offerSharedPlaylist(appIntent.payload)) {
                        collapse()
                        navController.navigate(Routes.SHARED_PLAYLIST)
                    } else {
                        toast(context, badLinkMessage)
                    }
                }
                is AppIntent.Unsupported -> toast(context, context.getString(appIntent.messageRes))
            }
        }
        val currentIntentHandler by rememberUpdatedState(handleAppIntent)

        LaunchedEffect(appIntents) {
            appIntents?.filterNotNull()?.collect { appIntent ->
                onIntentHandled()
                currentIntentHandler(appIntent)
            }
        }

        // Reading a playlist QR. Phone cameras ignore `musicsm://` codes, so this is the only way
        // a scanned playlist can actually open.
        val startScan = rememberQrScanner(
            onScanned = { raw ->
                val scanned = appIntentFromLink(raw)
                if (scanned == null) {
                    toast(context, unknownCodeMessage)
                } else {
                    scope.launch { currentIntentHandler(scanned) }
                }
            },
            onUnavailable = { toast(context, scannerUnavailableMessage) },
        )

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalBottomBarPadding provides barHeight,
            LocalSongNavigator provides songNavigator,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Content fills the whole screen and registers as the blur source.
                MusicSmNavHost(
                    navController = navController,
                    playerViewModel = playerViewModel,
                    onExpandPlayer = { expandNow() },
                    onScanCode = startScan,
                    startDestination = startDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .glassBackdrop(hazeState),
                )

                // Stitch bottom: a floating frosted mini-card above a full-width frosted tab bar.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                ) {
                    // Wavy download progress + completion toast, above the mini player.
                    DownloadStatusBar(
                        onClick = { navController.navigate(Routes.DOWNLOADS) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    if (hasSong) {
                        MiniPlayer(
                            state = playerState,
                            onClick = { expandNow() },
                            onTogglePlay = playerViewModel::togglePlayPause,
                            onNext = playerViewModel::next,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                // Fade out as the full sheet takes over.
                                .graphicsLayer {
                                    alpha = (1f - expand.value * 1.5f).coerceIn(0f, 1f)
                                }
                                // Drag up to expand into the full player.
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dy -> onDragDelta(dy) },
                                        onDragEnd = { onDragFinished() },
                                        onDragCancel = { onDragFinished() },
                                    )
                                },
                        )
                    }
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(50),
                        tint = GlassFillStrong,
                    ) {
                        BottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { dest -> navigateToTab(dest.route) },
                        )
                    }
                }

                // Full-screen expanding player — always composed, slides over everything.
                if (hasSong) {
                    // Composed BEFORE the player so the player's own back handlers (lyrics/queue)
                    // take priority; this one only collapses when nothing inner is showing.
                    BackHandler(enabled = expand.value > 0.01f) { collapse() }
                    ExpandedPlayer(
                        viewModel = playerViewModel,
                        progress = { expand.value },
                        onCollapse = { collapse() },
                        onDragDelta = onDragDelta,
                        onDragFinished = { onDragFinished() },
                        onEnterAmbient = { ambientMode = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Ambient mode sits above even the player sheet — it is a screensaver, so nothing
                // else may draw over it.
                if (ambientMode && hasSong) {
                    AmbientScreen(
                        viewModel = playerViewModel,
                        onExit = { ambientMode = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
