package com.example.musicsm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.Teal
import com.example.musicsm.ui.util.formatDuration

/**
 * Apple Music-style scrubber: a rounded capsule with no thumb that thickens while dragging,
 * with elapsed time on the left and remaining time (as `-m:ss`) on the right.
 *
 * @param progress current playback fraction (0..1) from the player.
 * @param durationMs total track duration, for the time labels.
 * @param onSeek called with the target fraction when the user finishes scrubbing / taps.
 */
@Composable
fun AppleSeekBar(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    playing: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = (if (dragging) dragFraction else progress).coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(if (dragging) 9.dp else 5.dp, label = "seekHeight")
    val trackColor = Color.White.copy(alpha = if (dragging) 0.30f else 0.22f)

    // Animated hue sweep: a repeating multi-hue gradient that flows sideways while playing.
    val infinite = rememberInfiniteTransition(label = "seekHue")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "seekPhase",
    )
    val hueColors = listOf(Coral, Lavender, Teal, Lavender, Coral)

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp), // generous touch target
            contentAlignment = Alignment.Center,
        ) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            // Flow the gradient by translating its start/end; mirror-tile for a seamless loop.
            val fillBrush = if (playing) {
                val shift = phase * widthPx
                Brush.linearGradient(
                    colors = hueColors,
                    start = Offset(shift - widthPx, 0f),
                    end = Offset(shift, 0f),
                    tileMode = TileMode.Mirror,
                )
            } else {
                Brush.horizontalGradient(listOf(Coral, Lavender))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
                    .pointerInput(widthPx) {
                        detectTapGestures { offset ->
                            onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            },
                            onHorizontalDrag = { change, _ ->
                                dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                dragging = false
                                onSeek(dragFraction)
                            },
                            onDragCancel = { dragging = false },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shown)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(fillBrush),
                )
            }
        }

        val elapsed = (shown * durationMs).toLong()
        val remaining = (durationMs - elapsed).coerceAtLeast(0L)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(elapsed),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            if (centerLabel != null) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                )
            }
            Text(
                text = "-" + formatDuration(remaining),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
