package com.example.musicsm.playback.mix

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * How one Mix-mode transition plays out. All durations are in the incoming track's media time
 * (divide by the user's playback speed for wall time).
 *
 * @property handoffAtMs outgoing position where the blend starts and the next track joins at
 *   [incomingStartMs]
 * @property fadeMs blend length
 * @property outgoingRate tempo multiplier for the outgoing tail so its beats line up with the next
 * @property beatMatched whether beats and bars were aligned (false = timed fade only)
 * @property bassSwapMs when (from the start of the blend) the low end moves from the outgoing
 *   track to the incoming one
 * @property swapRampMs how long that bass swap takes (one beat when beat-matched)
 * @property beatPeriodMs the incoming beat length when beat-matched, else 0
 * @property incomingStartMs where the incoming track starts playing (past any silent lead-in)
 */
data class MixPlan(
    val handoffAtMs: Long,
    val fadeMs: Long,
    val outgoingRate: Float,
    val beatMatched: Boolean,
    val bassSwapMs: Long,
    val swapRampMs: Long,
    val beatPeriodMs: Double = 0.0,
    val incomingStartMs: Long = 0,
)

object TransitionPlanner {

    /** Largest tempo change applied to the outgoing tail (time-stretched, so pitch is kept). */
    const val MAX_STRETCH = 0.06
    const val MIN_CONFIDENCE = 0.12f
    /** Unaligned grooves clash (doubled kicks), so an un-matched blend is kept short. */
    const val FALLBACK_FADE_MS = 6_000L
    private const val DEFAULT_SWAP_RAMP_MS = 500L

    /** A timed blend that finishes as the outgoing track's audio ends at [outgoingEndMs]. */
    fun fallback(outgoingEndMs: Long, windowMs: Long, incomingStartMs: Long = 0): MixPlan {
        val fade = windowMs.coerceAtMost(FALLBACK_FADE_MS)
        return MixPlan(
            handoffAtMs = outgoingEndMs - fade,
            fadeMs = fade,
            outgoingRate = 1f,
            beatMatched = false,
            bassSwapMs = fade / 2,
            swapRampMs = DEFAULT_SWAP_RAMP_MS.coerceAtMost(fade / 4),
            incomingStartMs = incomingStartMs,
        )
    }

    /**
     * Aligns the incoming track's first downbeat (after [incomingStartMs]) with a downbeat of the
     * outgoing track near its end, stretching the outgoing tail (never the incoming track) to the
     * incoming tempo. Falls back to [fallback] if either grid is missing/unsure, the tempos are too
     * far apart, or no aligned handoff fits between [earliestHandoffMs] and [outgoingEndMs] (where
     * the outgoing audio actually ends; defaults to the full [durationMs]).
     */
    fun plan(
        durationMs: Long,
        windowMs: Long,
        earliestHandoffMs: Long,
        outgoing: BeatGrid?,
        incoming: BeatGrid?,
        outgoingEndMs: Long = durationMs,
        incomingStartMs: Long = 0,
        maxStretch: Double = MAX_STRETCH,
    ): MixPlan {
        val base = fallback(outgoingEndMs, windowMs, incomingStartMs)
        if (outgoing == null || incoming == null) return base
        if (outgoing.confidence < MIN_CONFIDENCE || incoming.confidence < MIN_CONFIDENCE) return base

        val inPeriod = incoming.periodMs
        // Treat half/double time as compatible (a 70 BPM ballad and a 140 BPM track share a grid).
        val rate = listOf(1.0, 2.0, 0.5)
            .map { m -> outgoing.periodMs / (inPeriod * m) }
            .minBy { abs(ln(it)) }
        if (abs(rate - 1.0) > maxStretch) return base

        val bar = 4 * inPeriod
        val bars = (windowMs / bar).roundToLong().coerceAtLeast(2)
        val fade = bars * bar

        // How far into the blend the incoming track's first downbeat falls.
        val start = incomingStartMs.toDouble()
        val lead = incoming.downbeatAtOrAfter(start) - start
        if (lead > fade - bar) return base

        // The outgoing tail must last the whole blend: handoff + fade * rate <= end, where
        // handoff = D - lead * rate for the chosen outgoing downbeat D.
        val latestDownbeat = outgoingEndMs - (fade - lead) * rate
        val downbeat = outgoing.downbeatAtOrBefore(latestDownbeat)
        val handoff = downbeat - lead * rate
        if (handoff < earliestHandoffMs || handoff <= 0) return base

        val swapTarget = fade / 2
        val swapBars = ((swapTarget - lead) / bar).roundToLong().coerceAtLeast(0)
        val swap = (lead + swapBars * bar).coerceAtMost(fade - bar)

        return MixPlan(
            handoffAtMs = handoff.roundToLong(),
            fadeMs = fade.roundToLong(),
            outgoingRate = rate.toFloat(),
            beatMatched = true,
            bassSwapMs = swap.roundToLong(),
            swapRampMs = inPeriod.roundToLong(),
            beatPeriodMs = inPeriod,
            incomingStartMs = incomingStartMs,
        )
    }
}
