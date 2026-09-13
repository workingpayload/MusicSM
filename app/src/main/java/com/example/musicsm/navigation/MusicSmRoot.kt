package com.example.musicsm.navigation

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.LocalHazeState
import com.example.musicsm.ui.components.MiniPlayer
import com.example.musicsm.ui.components.glassBackdrop
import com.example.musicsm.ui.components.rememberHazeState
import com.example.musicsm.ui.player.ExpandedPlayer
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.GlassFillStrong
import kotlinx.coroutines.launch

@Composable
fun MusicSmRoot() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }

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

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalBottomBarPadding provides barHeight,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Content fills the whole screen and registers as the blur source.
                MusicSmNavHost(
                    navController = navController,
                    playerViewModel = playerViewModel,
                    onExpandPlayer = { expandNow() },
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        tint = GlassFillStrong,
                    ) {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { dest ->
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    }
                }

                // Full-screen expanding player — always composed, slides over everything.
                if (hasSong) {
                    ExpandedPlayer(
                        viewModel = playerViewModel,
                        progress = { expand.value },
                        onCollapse = { collapse() },
                        onDragDelta = onDragDelta,
                        onDragFinished = { onDragFinished() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    BackHandler(enabled = expand.value > 0.01f) { collapse() }
                }
            }
        }
    }
}
