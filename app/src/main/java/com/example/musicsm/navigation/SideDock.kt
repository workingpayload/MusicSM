package com.example.musicsm.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.components.isLowEndDevice
import com.example.musicsm.ui.theme.GlassFillStrong
import com.example.musicsm.ui.theme.LocalMusicSmPalette

/**
 * The landscape counterpart of the floating bottom bar, after Apple Music's iPad sidebar: one
 * vertical pane of glass docked to the start edge holding search and every top-level tab.
 *
 * Search sits in the dock rather than beside it. The bottom bar splits it off to keep the thumb
 * row short; a column has height to spare, and a second floating pill would only crowd the edge.
 *
 * Like [BottomNavBar], the selection is one puck that travels between items and is drawn rather
 * than laid out, so a tab change costs repaints of this node instead of recompositions. A detail
 * screen matches no item, so the puck stays on the item it was opened from.
 */
@Composable
fun SideDock(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember {
        listOf(
            DockItem(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
        ) + TopLevelDestination.entries.map {
            DockItem(it.route, it.labelRes, it.selectedIcon, it.unselectedIcon)
        }
    }
    var selected by remember {
        mutableIntStateOf(items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(1))
    }
    LaunchedEffect(currentRoute) {
        val match = items.indexOfFirst { it.route == currentRoute }
        if (match >= 0) selected = match
    }

    val cheap = isLowEndDevice()
    val travel = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected, cheap) {
        if (cheap) {
            travel.snapTo(selected.toFloat())
        } else {
            travel.animateTo(
                selected.toFloat(),
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val puckFill = LocalMusicSmPalette.current.surfaceLowest.copy(alpha = PUCK_ALPHA)

    GlassPanel(
        modifier = modifier.width(DOCK_WIDTH),
        shape = RoundedCornerShape(DOCK_CORNER),
        tint = GlassFillStrong,
        liquid = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DOCK_PADDING)
                .drawWithCache {
                    val itemHeight = ITEM_HEIGHT.toPx()
                    val insetX = PUCK_INSET_X.toPx()
                    val insetY = PUCK_INSET_Y.toPx()
                    val puckSize = Size(size.width - insetX * 2f, itemHeight - insetY * 2f)
                    val corner = CornerRadius(PUCK_CORNER.toPx())
                    val rimStroke = Stroke(PUCK_RIM_WIDTH.toPx())
                    // Fixed to the dock, not the puck, so the puck moves under a single light.
                    val rim = Brush.linearGradient(
                        0.0f to Color.White.copy(alpha = 0.34f),
                        0.6f to Color.White.copy(alpha = 0.06f),
                        1.0f to Color.White.copy(alpha = 0.18f),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind {
                        if (puckSize.minDimension <= 0f) return@onDrawBehind
                        val topLeft = Offset(insetX, travel.value * itemHeight + insetY)
                        drawRoundRect(puckFill, topLeft, puckSize, corner)
                        drawRoundRect(
                            brush = rim,
                            topLeft = topLeft,
                            size = puckSize,
                            cornerRadius = corner,
                            style = rimStroke,
                            blendMode = BlendMode.Plus,
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { index, item ->
                DockEntry(
                    item = item,
                    selected = index == selected,
                    animated = !cheap,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.height(ITEM_HEIGHT),
                )
            }
        }
    }
}

private class DockItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** Own composable so each colour animation invalidates one entry, not the whole dock. */
@Composable
private fun DockEntry(
    item: DockItem,
    selected: Boolean,
    animated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        LocalMusicSmPalette.current.onSurface.copy(alpha = 0.6f)
    }
    val color = if (animated) {
        animateColorAsState(targetValue = target, label = "dockItemColor").value
    } else {
        target
    }
    val label = stringResource(item.labelRes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PUCK_INSET_X)
            .clip(RoundedCornerShape(PUCK_CORNER))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Whether the root should swap the bottom bar for [SideDock]: landscape with room for a rail. */
fun useSideDock(maxWidth: Dp, maxHeight: Dp): Boolean =
    maxWidth > maxHeight && maxWidth >= SIDE_DOCK_MIN_WIDTH

private val SIDE_DOCK_MIN_WIDTH = 600.dp
private val DOCK_WIDTH = 84.dp
private val DOCK_CORNER = 32.dp
private val DOCK_PADDING = 8.dp
private val ITEM_HEIGHT = 60.dp
private val PUCK_INSET_X = 6.dp
private val PUCK_INSET_Y = 3.dp
private val PUCK_CORNER = 22.dp
private val PUCK_RIM_WIDTH = 0.8.dp

/** Matches the bottom bar's puck, so both chromes read as the same glass. */
private const val PUCK_ALPHA = 0.55f
