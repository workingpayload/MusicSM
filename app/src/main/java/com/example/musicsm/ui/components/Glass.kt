package com.example.musicsm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.GlassFill
import com.example.musicsm.ui.theme.GlassStroke
import com.example.musicsm.ui.theme.GlassStrokeSoft
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Shared [HazeState] for the whole app: background content registers as the blur source,
 * glass panels sample it. Provided at the root so any panel can frost the scrolling content.
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState?> = compositionLocalOf { null }

/**
 * Height (in dp) occupied by the floating glass bottom bar, so scrollable screens can add
 * matching bottom content padding and let their last item scroll out from under the glass.
 */
val LocalBottomBarPadding: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

@Composable
fun rememberHazeState(): HazeState = remember { HazeState() }

/** Marks this content as the backdrop that glass panels blur. No-op if no haze state is present. */
fun Modifier.glassBackdrop(state: HazeState?): Modifier =
    if (state != null) this.hazeSource(state) else this

/**
 * A frosted-glass panel: blurs whatever [LocalHazeState] content sits behind it, with a subtle
 * white veil and a hairline highlight stroke. Degrades to a translucent surface when no blur
 * source is available (e.g. previews).
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GlassFill,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    // Cheaper than HazeMaterials: modest blur, no per-frame noise shader. Remembered so the
    // style isn't reallocated on every recomposition/scroll frame.
    val style = remember {
        HazeStyle(
            blurRadius = 20.dp,
            tint = HazeTint(Color.White.copy(alpha = 0.10f)),
            noiseFactor = 0f,
        )
    }
    val stroke = remember { Brush.verticalGradient(listOf(GlassStroke, GlassStrokeSoft)) }

    val base = modifier.clip(shape)
    val frosted = if (hazeState != null) {
        base.hazeEffect(state = hazeState, style = style)
    } else {
        base.background(Color.White.copy(alpha = 0.08f))
    }

    Box(
        modifier = frosted
            .background(tint)
            .border(BorderStroke(0.5.dp, stroke), shape),
        content = content,
    )
}
