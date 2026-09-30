package com.example.musicsm.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.components.isLowEndDevice
import com.example.musicsm.ui.theme.LocalMusicSmPalette
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Stitch-style tab bar rendered inside a floating glass pill: icon + label over a single puck of
 * darker glass that slides to whichever tab is selected.
 *
 * One travelling puck rather than a highlight that appears and disappears per tab: the bar is a
 * single pane, so the selection should look like something moving within it. The puck also
 * stretches along its direction of travel and thins across it, which is what gives the movement
 * weight instead of making it look like a rectangle being teleported.
 *
 * The puck is drawn rather than laid out. A sliding child would mean the spring's position is read
 * during composition, and on a bar whose tabs are cheap but numerous that turns one tab change
 * into a full recomposition — subcomposition, measure and all — on every frame of the animation.
 * Drawing it behind the row keeps the whole animation inside the draw phase, so the spring costs
 * one repaint of one node per frame no matter how the bar is built.
 *
 * The bar can also be scrubbed: slide a finger across it and the puck lifts into a clear lens that
 * follows, lighting and ticking each tab it passes; releasing navigates to the tab underneath.
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val destinations = TopLevelDestination.entries
    // Falls back to the first tab so the puck always has somewhere to be; a detail screen pushed
    // over a tab leaves currentRoute matching nothing.
    val selected = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val palette = LocalMusicSmPalette.current
    val cheap = isLowEndDevice()
    val slots = destinations.size

    val travel = remember { Animatable(selected.toFloat()) }
    val tabSpring = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    LaunchedEffect(selected, cheap) {
        if (cheap) {
            travel.snapTo(selected.toFloat())
        } else {
            travel.animateTo(targetValue = selected.toFloat(), animationSpec = tabSpring)
        }
    }

    // Finger scrubbing (Liquid Glass style): press and slide across the bar, the puck lifts into a
    // larger, clearer lens that tracks the finger, and lifting the finger commits to the tab below.
    // `lift` is only ever read in draw, like `travel`, so the gesture costs no recomposition.
    val lift = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Derived so the icons only recompose when the finger crosses into another tab.
    val hovered by remember(slots) {
        derivedStateOf { travel.value.roundToInt().coerceIn(0, slots - 1) }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selected)
    val currentRouteState by rememberUpdatedState(currentRoute)
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    val puckFill = palette.surfaceLowest.copy(alpha = PUCK_ALPHA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .padding(horizontal = 8.dp)
            // After the padding, so pointer x and the puck's draw arithmetic share one space.
            .pointerInput(slots, cheap) {
                val follow = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh,
                )
                val liftSpring = spring<Float>(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
                var fingerSlot = 0f
                var lastTick = -1

                fun moveTo(x: Float) {
                    val slotWidth = size.width.toFloat() / slots
                    fingerSlot = (x / slotWidth - 0.5f).coerceIn(0f, (slots - 1).toFloat())
                    val target = fingerSlot
                    // A fresh stiff spring per move keeps the lens glued to the finger while
                    // inheriting velocity, so the squash still reacts to how fast it is dragged.
                    scope.launch {
                        if (cheap) travel.snapTo(target) else travel.animateTo(target, follow)
                    }
                    val index = target.roundToInt()
                    if (index != lastTick) {
                        lastTick = index
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }

                fun settle(commit: Boolean) {
                    dragging = false
                    val target = if (commit) fingerSlot.roundToInt() else currentSelected
                    scope.launch { if (cheap) lift.snapTo(0f) else lift.animateTo(0f, liftSpring) }
                    scope.launch {
                        if (cheap) travel.snapTo(target.toFloat())
                        else travel.animateTo(target.toFloat(), tabSpring)
                    }
                    // Compared by route, not index: on a detail screen no tab matches and the puck
                    // merely parks on the first one, which must still be a real destination.
                    if (commit && destinations[target].route != currentRouteState) {
                        currentOnNavigate(destinations[target])
                    }
                }

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        lastTick = currentSelected
                        if (!cheap) scope.launch { lift.animateTo(1f, liftSpring) }
                        moveTo(offset.x)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        moveTo(change.position.x)
                    },
                    onDragEnd = { settle(commit = true) },
                    onDragCancel = { settle(commit = false) },
                )
            }
            .drawWithCache {
                val slotWidth = size.width / slots
                val insetX = PUCK_INSET_X.toPx()
                val insetY = PUCK_INSET_Y.toPx()
                val puckSize = Size(slotWidth - insetX * 2f, size.height - insetY * 2f)
                val corner = CornerRadius(puckSize.height / 2f)
                val rimStroke = Stroke(PUCK_RIM_WIDTH.toPx())
                val liftedRimStroke = Stroke(PUCK_RIM_WIDTH.toPx() * 1.6f)
                // One light source across the whole bar, so the puck catches whichever stretch of
                // the diagonal it is currently under. A highlight anchored to the puck instead
                // would travel with it and read as a moving lamp rather than a moving object.
                val rim = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.34f),
                    0.6f to Color.White.copy(alpha = 0.06f),
                    1.0f to Color.White.copy(alpha = 0.18f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind {
                    if (puckSize.minDimension <= 0f) return@onDrawBehind
                    // The only state read in this whole animation, and it happens here, in draw.
                    val topLeft = Offset(travel.value * slotWidth + insetX, insetY)
                    val speed = if (cheap) {
                        0f
                    } else {
                        (abs(travel.velocity) / PUCK_VELOCITY_SCALE).coerceIn(0f, MAX_SQUASH)
                    }
                    val grow = lift.value.coerceAtLeast(0f)
                    scale(
                        scaleX = (1f + speed * 0.9f) * (1f + grow * LIFT_GROW_X),
                        scaleY = (1f - speed * 0.5f) * (1f + grow * LIFT_GROW_Y),
                        pivot = Offset(
                            topLeft.x + puckSize.width / 2f,
                            topLeft.y + puckSize.height / 2f,
                        ),
                    ) {
                        // Lifted, the recess turns into a clear lens: less tint, a faint sheen and
                        // a brighter rim, so the tabs underneath read through it.
                        drawRoundRect(
                            puckFill.copy(alpha = PUCK_ALPHA * (1f - grow * LIFT_CLEAR)),
                            topLeft,
                            puckSize,
                            corner,
                        )
                        if (grow > 0f) {
                            drawRoundRect(
                                Color.White.copy(alpha = LIFT_SHEEN_ALPHA * grow),
                                topLeft,
                                puckSize,
                                corner,
                            )
                        }
                        drawRoundRect(
                            brush = rim,
                            topLeft = topLeft,
                            size = puckSize,
                            cornerRadius = corner,
                            style = if (grow > 0f) liftedRimStroke else rimStroke,
                            blendMode = BlendMode.Plus,
                        )
                    }
                }
            },
        // Weights, not explicit widths: each tab then starts at exactly slot * slotWidth, which is
        // the arithmetic the puck draws with. Any distributed slack would leave the puck drifting
        // off the label it is supposed to be sitting under.
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, dest ->
            NavBarItem(
                destination = dest,
                // While scrubbing, the tab under the lens lights up before it is committed.
                selected = if (dragging) index == hovered else dest.route == currentRoute,
                animated = !cheap,
                onClick = onNavigate,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One tab.
 *
 * Its own composable purely so the colour animation has its own restart scope. Read in the bar's
 * body the same animation invalidates all of it, every frame, for the length of the transition —
 * which is the difference between two icons repainting and the entire bar being rebuilt.
 */
@Composable
private fun NavBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    animated: Boolean,
    onClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMusicSmPalette.current
    val target = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        palette.onSurface.copy(alpha = 0.6f)
    }
    // Animated rather than snapped: the puck arrives on a spring, and a colour that jumps ahead of
    // it reads as the two being unrelated.
    val color = if (animated) {
        animateColorAsState(targetValue = target, label = "navItemColor").value
    } else {
        target
    }

    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(destination) },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private val BAR_HEIGHT = 64.dp

/** Gap between the puck and its neighbours, so adjacent tabs never look joined. */
private val PUCK_INSET_X = 3.dp

/** Gap between the puck and the glass edge, so the puck reads as sitting inside the pill. */
private val PUCK_INSET_Y = 6.dp

private val PUCK_RIM_WIDTH = 0.8.dp

/**
 * Divides the spring's velocity (tabs per second) down to a stretch factor.
 *
 * Tuned so a one-tab hop peaks near the cap and a flick across the whole bar stays there, rather
 * than letting a long jump distort the puck into a streak.
 */
private const val PUCK_VELOCITY_SCALE = 14f

/** Past this the puck stops reading as a squashed object and starts reading as a glitch. */
private const val MAX_SQUASH = 0.2f

/** Dark enough to read as a recess in the glass, sheer enough that the blur still shows through. */
private const val PUCK_ALPHA = 0.55f

/** How much the lens swells while a finger is scrubbing; kept inside [PUCK_INSET_Y] vertically. */
private const val LIFT_GROW_X = 0.12f
private const val LIFT_GROW_Y = 0.18f

/** Fraction of the puck's tint that drains away when lifted, turning it into clear glass. */
private const val LIFT_CLEAR = 0.6f

private const val LIFT_SHEEN_ALPHA = 0.10f
