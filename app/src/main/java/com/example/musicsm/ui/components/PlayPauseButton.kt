package com.example.musicsm.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.GlassStroke
import com.example.musicsm.ui.theme.GlassStrokeSoft
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.Teal
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Big circular transport button rendered as hue-tinted frosted glass: it blurs the artwork
 * backdrop behind it ([LocalHazeState]) with a cycling color tint, a hairline highlight stroke,
 * and a breathing glow halo. Presses spring-scale it; the icon crossfades between play/pause.
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val infinite = rememberInfiniteTransition(label = "playGlow")
    // Hue cycles Coral → Lavender → Teal → back while playing.
    val hue by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "hue",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val hueColor = if (hue < 0.5f) lerp(Coral, Lavender, hue * 2f) else lerp(Lavender, Teal, (hue - 0.5f) * 2f)
    // When paused hue is frozen conceptually; use a steady coral tint.
    val tintColor = if (isPlaying) hueColor else Coral

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "pressScale")

    val hazeState = LocalHazeState.current
    val strokeTop = GlassStroke
    val strokeBottom = GlassStrokeSoft
    val stroke = remember(strokeTop, strokeBottom) {
        Brush.verticalGradient(listOf(strokeTop, strokeBottom))
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        // Breathing hue glow halo, kept within the button footprint.
        if (isPlaying) {
            Box(
                Modifier
                    .size(size * 0.82f)
                    .graphicsLayer {
                        val s = 1f + pulse * 0.18f
                        scaleX = s
                        scaleY = s
                        alpha = 0.30f + pulse * 0.22f
                    }
                    .blur(14.dp)
                    .background(Brush.radialGradient(listOf(hueColor, Color.Transparent)), CircleShape),
            )
        }

        // Frosted-glass disc: blurs the backdrop with the cycling hue tint.
        val glassStyle = HazeStyle(
            blurRadius = 18.dp,
            tint = HazeTint(tintColor.copy(alpha = 0.28f)),
            noiseFactor = 0f,
        )
        val disc = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
        val frosted = if (hazeState != null) {
            disc.hazeEffect(state = hazeState, style = glassStyle)
        } else {
            disc.background(tintColor.copy(alpha = 0.28f))
        }

        Box(
            modifier = frosted
                .border(BorderStroke(0.6.dp, stroke), CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "iconSwap",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}
