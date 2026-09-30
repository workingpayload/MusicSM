package com.example.musicsm.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.theme.GlassFillStrong
import kotlin.math.roundToInt

/**
 * The floating bottom chrome: tab pill + search button, with the mini player above them.
 *
 * Scrolling content minimises it, Apple Music style: the tab pill shrinks to a single icon, and
 * the mini player drops out of its own row to sit inline between that icon and search, handing
 * the row it occupied back to the content.
 *
 * [collapse] runs 0 (expanded) → 1 (minimised) and is read only in layout, placement and draw, so
 * the morph costs relayouts, not recompositions. The bar always reports its expanded height: the
 * lists underneath pad themselves by it, and a padding that followed the animation would shove
 * their content around under the user's finger mid-scroll.
 */
@Composable
fun FloatingBottomBar(
    collapse: () -> Float,
    miniPlayerCompact: Boolean,
    onExpandBar: () -> Unit,
    tabs: @Composable () -> Unit,
    collapsedTab: @Composable () -> Unit,
    search: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable (compact: Boolean, modifier: Modifier) -> Unit)? = null,
) {
    // Threshold flips, not per-frame values: each one recomposes exactly once per morph.
    val showTabs by remember { derivedStateOf { collapse() < 0.999f } }
    val showCollapsedTab by remember { derivedStateOf { collapse() > 0.001f } }

    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = BAR_MAX_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        val inner = maxWidth
        val tabsFull = inner - SEARCH_SIZE - GAP
        val inlineMini = inner - COLLAPSED_TAB_WIDTH - SEARCH_SIZE - GAP * 2
        val hasMini = miniPlayer != null
        val totalHeight = if (hasMini) MINI_HEIGHT + ROW_GAP + BAR_HEIGHT else BAR_HEIGHT

        Box(Modifier.fillMaxWidth().height(totalHeight)) {
            GlassPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .animatedWidth(BAR_HEIGHT) { lerp(tabsFull, COLLAPSED_TAB_WIDTH, collapse()) },
                shape = RoundedCornerShape(50),
                tint = GlassFillStrong,
                liquid = true,
            ) {
                if (showTabs) {
                    // Laid out at full width and clipped by the shrinking pill, so the tabs fade
                    // out in place instead of squeezing together.
                    Box(
                        Modifier
                            .fixedWidth(tabsFull)
                            .graphicsLayer { alpha = (1f - collapse() * 2f).coerceIn(0f, 1f) },
                    ) { tabs() }
                }
                if (showCollapsedTab) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = (collapse() * 2f - 1f).coerceIn(0f, 1f) }
                            .clickable(onClick = onExpandBar),
                        contentAlignment = Alignment.Center,
                    ) { collapsedTab() }
                }
            }

            Box(Modifier.align(Alignment.BottomEnd).size(SEARCH_SIZE)) { search() }

            // Last, so it draws over the tab pill while it travels down into the bar's row.
            miniPlayer?.invoke(
                miniPlayerCompact,
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val p = collapse()
                        val x = lerp(0.dp, COLLAPSED_TAB_WIDTH + GAP, p)
                        val inlineY = totalHeight - BAR_HEIGHT + (BAR_HEIGHT - MINI_HEIGHT) / 2
                        val y = lerp(0.dp, inlineY, p)
                        IntOffset(x.roundToPx(), y.roundToPx())
                    }
                    .animatedWidth(MINI_HEIGHT) { lerp(inner, inlineMini, collapse()) },
            )
        }
    }
}

/** Exact size whose width is re-read on every layout pass, without recomposing. */
private fun Modifier.animatedWidth(height: Dp, width: () -> Dp) = layout { measurable, _ ->
    val w = width().roundToPx().coerceAtLeast(0)
    val h = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}

/** Measures at [width] even when the parent is narrower; the parent's clip hides the overflow. */
private fun Modifier.fixedWidth(width: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints.fixed(width.roundToPx(), constraints.maxHeight),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
}

private val BAR_HEIGHT = 64.dp
private val MINI_HEIGHT = 60.dp
private val SEARCH_SIZE = 64.dp
private val COLLAPSED_TAB_WIDTH = 64.dp
private val GAP = 10.dp
private val ROW_GAP = 8.dp

/** Keeps the pills thumb-reachable, and reading as one unit, on a tablet. */
private val BAR_MAX_WIDTH = 520.dp
