package com.example.musicsm.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.Teal

/**
 * A determinate circular progress ring whose color continuously cycles Coral → Lavender → Teal,
 * matching the multi-hue effect used on the seek bar.
 */
@Composable
fun HueCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
) {
    val infinite = rememberInfiniteTransition(label = "hueRing")
    val hue by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "hueRingPhase",
    )
    val color = if (hue < 0.5f) lerp(Coral, Lavender, hue * 2f) else lerp(Lavender, Teal, (hue - 0.5f) * 2f)
    CircularProgressIndicator(
        progress = { progress },
        color = color,
        strokeWidth = strokeWidth,
        modifier = modifier,
    )
}
