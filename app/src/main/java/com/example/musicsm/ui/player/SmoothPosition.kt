package com.example.musicsm.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.isActive

/**
 * Interpolates playback position between the player's coarse (~500ms) state ticks.
 *
 * While playing, it advances once per frame using the frame clock, re-anchoring to the real
 * [positionMs] every time a new tick arrives (which corrects any drift and handles seeks). This
 * gives a smooth progress bar and frame-accurate lyric highlighting instead of 500ms steps.
 */
@Composable
fun rememberSmoothPosition(
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
): Long {
    var smooth by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, durationMs) {
        smooth = positionMs
        if (isPlaying) {
            val anchorPos = positionMs
            val anchorFrame = withFrameMillis { it }
            while (isActive) {
                val now = withFrameMillis { it }
                val next = anchorPos + (now - anchorFrame)
                smooth = if (durationMs > 0) next.coerceIn(0L, durationMs) else next.coerceAtLeast(0L)
            }
        }
    }
    return smooth
}
