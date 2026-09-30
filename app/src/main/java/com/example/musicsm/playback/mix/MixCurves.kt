package com.example.musicsm.playback.mix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How the outgoing track leaves a blend. */
enum class FadeShape {
    /** cos out against a sin in: constant power, the classic crossfade. */
    EQUAL_POWER,

    /** cos² out: holds near full briefly, then clears out faster so the next track takes over. */
    MIX,
}

/** Gain envelopes for a transition, over the outgoing track's media time. */
object MixCurves {

    /** Full until [startMs], then down to silence over [fadeMs]. */
    fun fadeOut(startMs: Double, fadeMs: Double, shape: FadeShape): GainCurve {
        require(fadeMs > 0)
        return GainCurve { t -> fadeOut((t - startMs) / fadeMs, shape) }
    }

    /** Silent before [startMs], then up to full over [fadeMs]. */
    fun fadeIn(startMs: Double, fadeMs: Double): GainCurve {
        require(fadeMs > 0)
        return GainCurve { t -> fadeIn((t - startMs) / fadeMs) }
    }

    /** Outgoing level at fraction [u] of the fade. */
    fun fadeOut(u: Double, shape: FadeShape): Double = when {
        u <= 0 -> 1.0
        u >= 1 -> 0.0
        else -> cos(PI / 2 * u).let { if (shape == FadeShape.MIX) it * it else it }
    }

    /** Incoming level at fraction [v] of the fade. */
    fun fadeIn(v: Double): Double = when {
        v <= 0 -> 0.0
        v >= 1 -> 1.0
        else -> sin(PI / 2 * v)
    }
}
