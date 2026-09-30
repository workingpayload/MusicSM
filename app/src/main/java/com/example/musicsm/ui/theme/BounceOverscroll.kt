package com.example.musicsm.ui.theme

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * iOS-style overscroll for every scrollable in the app (provided through `LocalOverscrollFactory`
 * in [MusicSMTheme]), replacing Android's stretch:
 * - dragging past an edge pulls the content along with rubber-band resistance, and it springs
 *   back on release;
 * - a fling that hits an edge carries on a little past it and springs back ("bounce").
 */
data object BounceOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = BounceOverscrollEffect()
}

/**
 * Android's own overscroll (stretch / glow), captured in [MusicSMTheme] before the bounce replaces
 * it. Screens that shouldn't bounce provide this back as `LocalOverscrollFactory`.
 */
val LocalPlatformOverscrollFactory = staticCompositionLocalOf<OverscrollFactory?> { null }

/** Gives [content] Android's stock overscroll instead of the app-wide bounce. */
@Composable
fun WithoutBounce(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOverscrollFactory provides LocalPlatformOverscrollFactory.current,
        content = content,
    )
}

private class BounceOverscrollEffect : OverscrollEffect {

    /** Raw finger travel past the edge, per axis. What the user feels is [BounceMath.rubberBand] of it. */
    private var pull = Offset.Zero

    /** How far the content is currently drawn from its resting place. */
    private var displacement by mutableStateOf(Offset.Zero)

    private var containerSize = IntSize.Zero
    private var springJob: Job? = null

    override val isInProgress: Boolean
        get() = displacement != Offset.Zero

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Flings report their leftover as velocity in applyToFling, where the bounce happens.
        if (source != NestedScrollSource.UserInput) return performScroll(delta)

        // A finger landing mid-spring catches the content where it is.
        springJob?.let {
            it.cancel()
            springJob = null
            pull = Offset(
                BounceMath.inverseRubberBand(displacement.x, width()),
                BounceMath.inverseRubberBand(displacement.y, height()),
            )
        }

        // Dragging back towards the content first undoes the pull, then scrolls.
        val (pullX, relaxedX) = BounceMath.relax(pull.x, delta.x)
        val (pullY, relaxedY) = BounceMath.relax(pull.y, delta.y)
        val relaxed = Offset(relaxedX, relaxedY)

        val left = delta - relaxed
        val consumed = performScroll(left)
        val unconsumed = left - consumed

        pull = Offset(
            pullX + unconsumed.x.takeIf { abs(it) > 0.5f }.orZero(),
            pullY + unconsumed.y.takeIf { abs(it) > 0.5f }.orZero(),
        )
        displacement = Offset(
            BounceMath.rubberBand(pull.x, width()),
            BounceMath.rubberBand(pull.y, height()),
        )
        return relaxed + consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (isInProgress) {
            // Let go while pulled: spring back. Keep only outward speed; inward speed would
            // overshoot the rest position and flash a gap at the other edge.
            val start = displacement
            springBack(
                Velocity(
                    velocity.x.takeIf { sign(it) == sign(start.x) }.orZero(),
                    velocity.y.takeIf { sign(it) == sign(start.y) }.orZero(),
                ),
            )
        } else {
            // performFling returns the velocity the fling *used*; only what is left over means
            // the content hit an edge still moving, so only that bounces.
            val consumed = performFling(velocity)
            val left = Velocity(
                BounceMath.edgeHitVelocity(velocity.x, consumed.x),
                BounceMath.edgeHitVelocity(velocity.y, consumed.y),
            )
            if (left != Velocity.Zero) springBack(left)
        }
    }

    private suspend fun springBack(velocity: Velocity) = coroutineScope {
        val start = displacement
        val initialVelocity = Offset(
            BounceMath.capVelocity(velocity.x, width()),
            BounceMath.capVelocity(velocity.y, height()),
        )
        if (start == Offset.Zero && initialVelocity == Offset.Zero) return@coroutineScope
        val job = launch {
            try {
                animate(
                    typeConverter = Offset.VectorConverter,
                    initialValue = start,
                    targetValue = Offset.Zero,
                    initialVelocity = initialVelocity,
                    animationSpec = spring(
                        dampingRatio = BounceMath.DAMPING_RATIO,
                        stiffness = BounceMath.STIFFNESS,
                        visibilityThreshold = Offset(0.5f, 0.5f),
                    ),
                ) { value, _ -> displacement = value }
                displacement = Offset.Zero
            } finally {
                // Also runs when a new gesture cancels the spring: pick up from where it stopped.
                pull = Offset(
                    BounceMath.inverseRubberBand(displacement.x, width()),
                    BounceMath.inverseRubberBand(displacement.y, height()),
                )
                if (springJob == coroutineContext[Job]) springJob = null
            }
        }
        springJob = job
    }

    private fun width() = containerSize.width.toFloat()
    private fun height() = containerSize.height.toFloat()

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            containerSize = IntSize(placeable.width, placeable.height)
            return layout(placeable.width, placeable.height) {
                // Read in the layer block, so moving the content only redraws; no relayout.
                placeable.placeWithLayer(0, 0) {
                    translationX = displacement.x
                    translationY = displacement.y
                }
            }
        }
    }
}

private fun Float?.orZero() = this ?: 0f

/** The bounce's maths, kept pure so it can be unit tested. */
internal object BounceMath {
    /** iOS's rubber-band constant: lower feels stiffer. */
    const val RESISTANCE = 0.55f

    /** Critically damped: out and back once, no wobble. */
    const val DAMPING_RATIO = 1f
    const val STIFFNESS = 240f

    /** Furthest a fling bounce may carry past the edge, as a share of the container. */
    const val MAX_BOUNCE_FRACTION = 0.12f

    /**
     * Leftover fling speed (px/s) below this doesn't bounce. A fling that dies out on its own still
     * reports its last, slow frame as leftover, and it would peak at only a few px anyway.
     */
    const val MIN_BOUNCE_VELOCITY = 150f

    /**
     * The speed a fling still had when it hit an edge: what it was given minus what it [consumed],
     * or 0 when that is too slow to be a real edge hit.
     */
    fun edgeHitVelocity(velocity: Float, consumed: Float): Float {
        val left = velocity - consumed
        return if (abs(left) < MIN_BOUNCE_VELOCITY) 0f else left
    }

    /** Used before the container has been measured. */
    private const val FALLBACK_DIMENSION = 1_000f

    /** Distance the content moves for [pull] px of finger travel past the edge: grows ever slower, never reaching [dimension]. */
    fun rubberBand(pull: Float, dimension: Float): Float {
        if (pull == 0f) return 0f
        val d = dimension.orFallback()
        val a = abs(pull)
        return sign(pull) * d * RESISTANCE * a / (d + RESISTANCE * a)
    }

    /** Inverse of [rubberBand]: the pull that shows [displacement]. */
    fun inverseRubberBand(displacement: Float, dimension: Float): Float {
        if (displacement == 0f) return 0f
        val d = dimension.orFallback()
        val a = abs(displacement).coerceAtMost(d * 0.999f)
        return sign(displacement) * d * a / (RESISTANCE * (d - a))
    }

    /**
     * Applies a drag of [delta] to an existing [pull] when it points back towards the content.
     * Returns the new pull and how much of [delta] that used up; any rest is left to scroll.
     */
    fun relax(pull: Float, delta: Float): Pair<Float, Float> {
        if (pull == 0f || delta == 0f || sign(delta) == sign(pull)) return pull to 0f
        val next = pull + delta
        return if (sign(next) != sign(pull) || next == 0f) 0f to -pull else next to delta
    }

    /**
     * A critically damped spring leaving 0 at speed v peaks at v / (ω·e), ω = √stiffness. Caps v so
     * a hard fling bounces at most [MAX_BOUNCE_FRACTION] of the container past the edge.
     */
    fun capVelocity(velocity: Float, dimension: Float): Float {
        val max = dimension.orFallback() * MAX_BOUNCE_FRACTION * sqrt(STIFFNESS) * E.toFloat()
        return velocity.coerceIn(-max, max)
    }

    private fun Float.orFallback() = if (this > 0f) this else FALLBACK_DIMENSION
}
