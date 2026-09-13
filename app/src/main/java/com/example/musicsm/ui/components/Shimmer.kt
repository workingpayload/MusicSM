package com.example.musicsm.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A moving diagonal highlight overlaid on a translucent base — the standard shimmer placeholder.
 * Apply to any sized box to make it read as "loading".
 */
fun Modifier.shimmer(progress: Float): Modifier = this.drawWithContent {
    drawContent()
    val sweep = size.width * 1.5f
    val x = (progress * (size.width + sweep)) - sweep
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + sweep, size.height),
        ),
    )
}

@Composable
fun rememberShimmerProgress(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    return progress
}

/** A single shimmering placeholder block. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    progress: Float,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .shimmer(progress),
    )
}
