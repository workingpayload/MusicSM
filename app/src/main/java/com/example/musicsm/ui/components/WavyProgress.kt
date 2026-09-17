package com.example.musicsm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.Teal
import kotlin.math.PI
import kotlin.math.sin

/**
 * A rounded progress bar whose filled portion is a live sine wave that undulates while active.
 * The fill width animates to [progress] (0..1); the wave travels continuously.
 */
@Composable
fun WavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val target = progress.coerceIn(0f, 1f)
    val fill by animateFloatAsState(target, tween(300), label = "wavyFill")
    val infinite = rememberInfiniteTransition(label = "wavy")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "wavyPhase",
    )
    // Travelling multi-hue gradient (Coral → Lavender → Teal), same flow as the seek/volume bars.
    val huePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "wavyHue",
    )
    val hueColors = listOf(Coral, Lavender, Teal, Lavender, Coral)

    Canvas(modifier.fillMaxWidth().height(height).clip(CircleShape)) {
        // Track.
        drawRect(Color.White.copy(alpha = 0.08f))

        val end = size.width * fill
        if (end <= 0f) return@Canvas
        val mid = size.height / 2f
        val amp = size.height * 0.22f
        val waves = 6f
        val k = waves * 2f * PI.toFloat() / size.width

        val shift = huePhase * size.width
        val brush = Brush.linearGradient(
            colors = hueColors,
            start = Offset(shift - size.width, 0f),
            end = Offset(shift, 0f),
            tileMode = TileMode.Mirror,
        )

        val path = Path().apply {
            moveTo(0f, mid)
            var x = 0f
            val step = 3f
            while (x < end) {
                lineTo(x, mid + amp * sin(k * x + phase))
                x += step
            }
            lineTo(end, mid + amp * sin(k * end + phase))
            lineTo(end, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, brush = brush)
    }
}
