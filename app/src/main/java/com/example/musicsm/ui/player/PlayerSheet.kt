package com.example.musicsm.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Full-screen Now-Playing that lives permanently in the composition and slides up from the mini
 * player. [progress] is 0 when collapsed (off-screen) and 1 when fully expanded; reading it inside
 * [graphicsLayer] keeps the drag/animation on the layer thread (no recomposition), so opening and
 * collapsing stay smooth. Drag the grabber to dismiss.
 */
@Composable
fun ExpandedPlayer(
    viewModel: PlayerViewModel,
    progress: () -> Float,
    onCollapse: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val playerState by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Slide fully below the screen when collapsed, up to the top when expanded.
                translationY = (1f - progress()) * size.height
            }
            // Drag down anywhere on the sheet to dismiss (vertical drags on buttons/seek still
            // reach those children first).
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy -> onDragDelta(dy) },
                    onDragEnd = { onDragFinished() },
                    onDragCancel = { onDragFinished() },
                )
            }
            // Swallow stray taps so they can't fall through to the content behind the sheet
            // (which was accidentally changing the track when tapping the artwork).
            .pointerInput(Unit) {
                detectTapGestures {}
            },
    ) {
        NowPlayingScreen(
            viewModel = viewModel,
            onBack = onCollapse,
            onOpenQueue = { showQueue = true },
            onOpenLyrics = { showLyrics = true },
            modifier = Modifier.fillMaxSize(),
        )

        // Visual grabber pill (drag handled by the whole sheet above).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
                .size(width = 40.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.5f)),
        )

        // In-sheet "Up Next" queue (Apple Music-style), instead of a separate nav screen.
        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                QueueScreen(
                    viewModel = viewModel,
                    onBack = { showQueue = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // In-sheet lyrics (synced highlight + auto-scroll).
        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val smoothPos = rememberSmoothPosition(
                positionMs = playerState.positionMs,
                isPlaying = playerState.isPlaying,
                durationMs = playerState.durationMs,
            )
            LyricsPanel(
                lyricsState = lyricsState,
                positionMs = smoothPos,
                song = playerState.currentSong,
                onSeekMs = viewModel::seekToMs,
                onClose = { showLyrics = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
