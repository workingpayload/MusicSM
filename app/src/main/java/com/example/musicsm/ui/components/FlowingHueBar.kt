package com.example.musicsm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.Teal

/**
 * A capsule bar whose fill ([fraction] 0..1) is a continuously flowing multi-hue gradient —
 * the same travelling Coral → Lavender → Teal effect used by the seek bar.
 */
@Composable
fun FlowingHueBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val infinite = rememberInfiniteTransition(label = "flowHue")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "flowPhase",
    )
    val colors = listOf(Coral, Lavender, Teal, Lavender, Coral)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(OverlayTint.copy(alpha = 0.22f)),
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val shift = phase * w
        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(shift - w, 0f),
            end = Offset(shift, 0f),
            tileMode = TileMode.Mirror,
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(brush),
        )
    }
}
