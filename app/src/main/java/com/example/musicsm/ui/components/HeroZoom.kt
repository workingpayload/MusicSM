package com.example.musicsm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Damps a raw drag distance into the distance content should actually move past its own end.
 *
 * Dragging past the top of a list has to move *something*, or the gesture feels like it hit a wall,
 * but it cannot move freely or the header runs off the screen. This is the curve that resolves it:
 * it starts out one-to-one with the finger, so the first moment of the pull feels directly
 * connected, and then bends away, approaching [limit] / [stiffness] without ever reaching it. No
 * clamp is needed, and none should be added — a clamp is felt as the wall this exists to avoid.
 *
 * Applying a constant resistance instead is the tempting simplification and it is the wrong one:
 * content that moves at half finger speed from the very first pixel reads as sluggish rather than
 * as resistant.
 *
 * @param distance raw accumulated drag, in pixels.
 * @param limit the dimension the pull is measured against, usually the container's height.
 */
internal fun rubberBand(distance: Float, limit: Float, stiffness: Float = RUBBER_BAND): Float {
    if (distance == 0f || limit <= 0f) return 0f
    val magnitude = abs(distance)
    val damped = (1f - 1f / (magnitude * stiffness / limit + 1f)) * limit / stiffness
    return if (distance < 0f) -damped else damped
}

/** The scale a hero should draw at for a given [pull], easing in over [maxPull] to [maxScale]. */
internal fun heroScaleFor(pull: Float, maxPull: Float, maxScale: Float = MAX_HERO_SCALE): Float {
    if (pull <= 0f || maxPull <= 0f) return 1f
    return 1f + (pull / maxPull).coerceIn(0f, 1f) * (maxScale - 1f)
}

/**
 * Tracks an over-pull at the top of a list and exposes it as a scale for a header to grow by.
 *
 * Read [scale] inside a `graphicsLayer` block so the gesture drives the draw phase directly. Read
 * in composition instead, it would rebuild the list on every frame of a drag.
 */
@Stable
class HeroZoomState internal constructor(private val maxPullPx: Float) {

    /**
     * The live pull, written synchronously from the scroll callback.
     *
     * Deliberately a plain float state and not an [Animatable]: routing every delta of a fast drag
     * through a coroutine queues them behind one another, and the zoom ends up lagging the finger
     * and settling wherever the backlog ran out.
     */
    private var pull by mutableFloatStateOf(0f)

    /** Only used to spring back on release, which is the one part of this that is an animation. */
    internal val release = Animatable(0f)

    internal var releasing = false

    val scale: Float
        get() = heroScaleFor(if (releasing) release.value else pull, maxPullPx)

    internal fun drag(deltaPx: Float): Float {
        if (deltaPx <= 0f && pull <= 0f) return 0f
        releasing = false
        val consumed = if (deltaPx < 0f) minOf(-deltaPx, pull) else deltaPx
        pull = (pull + if (deltaPx < 0f) -consumed else consumed).coerceAtLeast(0f)
        return if (deltaPx < 0f) -consumed else consumed
    }

    internal fun takePull(): Float {
        val held = pull
        pull = 0f
        return held
    }
}

/**
 * Remembers a [HeroZoomState] and the connection that feeds it.
 *
 * Attach the returned modifier to the scrollable container. The gesture is intentionally not wired
 * to anything else: the same pull used to double as pull-to-refresh, which turned a glance at the
 * top of a page into a network reload.
 */
@Composable
fun rememberHeroZoom(maxPull: Dp = MAX_PULL): Pair<HeroZoomState, Modifier> {
    val density = LocalDensity.current
    val maxPullPx = with(density) { maxPull.toPx() }
    val containerPx = with(density) { PULL_REFERENCE.toPx() }
    val state = remember(maxPullPx) { HeroZoomState(maxPullPx) }
    val scope = rememberCoroutineScope()

    val connection = remember(state, containerPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back down has to unwind the pull before the list starts moving,
                // otherwise the header stays zoomed while the content slides out from under it.
                if (available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, state.drag(available.y))
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Fling leftover is ignored on purpose. Absorbing it keeps the decay animation
                // alive against a fully stretched header, which is felt as the pull sticking and
                // then letting go late.
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, state.drag(rubberBand(available.y, containerPx)))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val held = state.takePull()
                if (held <= 0f) return Velocity.Zero
                state.releasing = true
                scope.launch {
                    state.release.snapTo(held)
                    state.release.animateTo(
                        targetValue = 0f,
                        // No bounce: a header that wobbles on the way back reads as a toy. It
                        // should return and stop, the way the surface it is imitating does.
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                    state.releasing = false
                }
                return Velocity.Zero
            }
        }
    }

    return state to Modifier.nestedScroll(connection)
}

/**
 * How far the pull is measured against.
 *
 * A fixed reference rather than the real container height: the curve only needs a scale to bend
 * against, and measuring the list adds a layout read for a value the finger cannot tell apart.
 */
private val PULL_REFERENCE = 640.dp

/** Roughly a thumb's comfortable drag — past this the hero is at full size and stays there. */
private val MAX_PULL = 220.dp

/** Enough growth to be unmistakably responsive, little enough that the crop stays sensible. */
private const val MAX_HERO_SCALE = 1.18f

private const val RUBBER_BAND = 0.55f
