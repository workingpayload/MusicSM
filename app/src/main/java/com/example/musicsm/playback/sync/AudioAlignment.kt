package com.example.musicsm.playback.sync

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds how far apart two recordings of the same song run, e.g. a music video with an intro
 * against the album audio that lyrics are timed to, by lining up their loudness onsets (drum hits,
 * vocal entries). Works on per-[HOP_MS] energy frames, so no raw audio has to be kept around.
 *
 * Pure maths, no Android, so it is unit tested directly.
 */
object AudioAlignment {

    /** Length of one energy frame. Also the resolution of a measured offset. */
    const val HOP_MS = 10

    /** Below this correlation two windows aren't treated as the same music. */
    const val MIN_SCORE = 0.30f

    /** How clearly the best offset must beat the best one elsewhere (see [Match.distinctness]). */
    const val MIN_DISTINCTNESS = 1.25f

    /** Offsets within this of the best are its own peak, not a rival. */
    private const val PEAK_HALF_WIDTH_MS = 400

    /** Length of each stretch of the playing track that gets matched. */
    const val WINDOW_MS = 30_000L

    /** Start of the second window after the first; far enough apart to be independent checks. */
    const val WINDOW_GAP_MS = 45_000L

    /** The playing track's first seconds are skipped: intros and skits don't match the album. */
    const val SKIP_INTRO_MS = 30_000L

    /** Search range: the playing track may start up to this much earlier than the album audio… */
    const val MIN_OFFSET_MS = -20_000L

    /** …or this much later (a music video's intro). Also the lyrics offset limit. */
    const val MAX_OFFSET_MS = 60_000L

    /** Two windows count as agreeing when their offsets are this close. */
    const val AGREEMENT_MS = 60L

    /** With only one window to go on, it has to be this convincing on its own. */
    const val SINGLE_WINDOW_MIN_SCORE = 0.50f

    /** Energy floor, relative to the loudest frame (-70 dB), so silence doesn't read as onsets. */
    private const val FLOOR_RATIO = 1e-7f

    /**
     * The best line-up found: the second recording's time = the first's + [offsetMs].
     * [score] is the normalised correlation there; [runnerUp] the best score at least
     * [PEAK_HALF_WIDTH_MS] away from it.
     */
    data class Match(val offsetMs: Long, val score: Float, val runnerUp: Float) {
        val distinctness: Float get() = if (runnerUp <= 0f) Float.MAX_VALUE else score / runnerUp
        val isConfident: Boolean get() = score >= MIN_SCORE && distinctness >= MIN_DISTINCTNESS
    }

    /**
     * Onset strength per frame from mean-square energy frames: how much louder each frame is than
     * the one before, on a log scale (so a quieter or louder master of the same song matches).
     */
    fun onsets(energy: FloatArray): FloatArray {
        if (energy.isEmpty()) return FloatArray(0)
        val floor = max(energy.max() * FLOOR_RATIO, 1e-12f)
        val log = FloatArray(energy.size) { ln(max(energy[it], floor)) }
        val out = FloatArray(energy.size)
        for (i in 1 until log.size) {
            // Against the louder of the two frames before, so one frame's jitter isn't an onset.
            val before = if (i >= 2) max(log[i - 1], log[i - 2]) else log[i - 1]
            out[i] = max(0f, log[i] - before)
        }
        return out
    }

    /**
     * Where [target] (onsets starting at [targetStartMs] in its recording) sits inside [reference]
     * (onsets starting at [referenceStartMs] in the other recording), searching offsets from
     * [minOffsetMs] to [maxOffsetMs]. The offset maps reference time to target time:
     * `targetTime = referenceTime + offset`. Only offsets where all of [target] lies within
     * [reference] are tried. Null if none are, or [target] is flat (silence).
     */
    fun align(
        target: FloatArray,
        targetStartMs: Long,
        reference: FloatArray,
        referenceStartMs: Long,
        minOffsetMs: Long,
        maxOffsetMs: Long,
    ): Match? {
        val n = target.size
        val m = reference.size
        if (n < 2 || m < n) return null

        // Normalise the target once: zero mean, unit length. Pearson correlation then only needs
        // the dot product and the reference slice's own spread.
        val mean = target.average().toFloat()
        var norm = 0.0
        val t = FloatArray(n) { (target[it] - mean).also { d -> norm += d.toDouble() * d } }
        if (norm <= 1e-12) return null
        val tScale = (1.0 / sqrt(norm)).toFloat()
        for (i in 0 until n) t[i] *= tScale

        val prefix = DoubleArray(m + 1)
        val prefixSq = DoubleArray(m + 1)
        for (j in 0 until m) {
            prefix[j + 1] = prefix[j] + reference[j]
            prefixSq[j + 1] = prefixSq[j] + reference[j].toDouble() * reference[j]
        }

        // Frame shift s puts target[0] on reference[s]: targetStart = referenceStart + s*hop + offset.
        fun shiftFor(offset: Long): Long = Math.floorDiv(targetStartMs - offset - referenceStartMs, HOP_MS.toLong())
        val sMin = max(0L, shiftFor(maxOffsetMs)).toInt()
        val sMax = minOf((m - n).toLong(), shiftFor(minOffsetMs)).toInt()
        if (sMax < sMin) return null

        val scores = FloatArray(sMax - sMin + 1)
        for (s in sMin..sMax) {
            val sum = prefix[s + n] - prefix[s]
            val variance = (prefixSq[s + n] - prefixSq[s]) - sum * sum / n
            if (variance <= 1e-12) continue
            var dot = 0f
            for (i in 0 until n) dot += t[i] * reference[s + i]
            scores[s - sMin] = (dot / sqrt(variance)).toFloat()
        }

        val bestIdx = scores.indices.maxBy { scores[it] }
        val halfWidth = PEAK_HALF_WIDTH_MS / HOP_MS
        var runnerUp = 0f
        for (k in scores.indices) {
            if (k < bestIdx - halfWidth || k > bestIdx + halfWidth) runnerUp = max(runnerUp, scores[k])
        }
        val offset = targetStartMs - referenceStartMs - (sMin + bestIdx).toLong() * HOP_MS
        return Match(offset, scores[bestIdx], runnerUp)
    }

    /**
     * Where the windows of the playing track (length [durationMs], at [positionMs]) start: two,
     * [WINDOW_GAP_MS] apart, near where the listener is but past the intro; one if the track is too
     * short for two; none if it's shorter than a window. An unknown (0) length is treated as long.
     */
    fun windowStarts(positionMs: Long, durationMs: Long): List<Long> {
        val length = if (durationMs > 0) durationMs else Long.MAX_VALUE / 4
        if (length < WINDOW_MS) return emptyList()
        var first = max(SKIP_INTRO_MS, positionMs - WINDOW_GAP_MS - WINDOW_MS / 2)
        val lastEnd = first + WINDOW_GAP_MS + WINDOW_MS
        if (lastEnd > length) first -= lastEnd - length
        return if (first >= 0) {
            listOf(first, first + WINDOW_GAP_MS)
        } else {
            listOf(minOf(SKIP_INTRO_MS, length - WINDOW_MS))
        }
    }

    /**
     * The offset to trust from the per-window [matches], or null. Two windows must both match and
     * agree, because repetitive music (a beat that repeats every bar) can score well at the wrong
     * offset in one window, but not at the same wrong offset in two. A single window must be
     * clearly better than anywhere else.
     */
    fun decide(matches: List<Match?>): Long? {
        if (matches.isEmpty() || matches.any { it == null }) return null
        val found = matches.filterNotNull()
        if (found.size == 1) {
            val only = found.single()
            return only.offsetMs.takeIf { only.score >= SINGLE_WINDOW_MIN_SCORE && only.distinctness >= MIN_DISTINCTNESS }
        }
        if (found.any { it.score < MIN_SCORE }) return null
        val offsets = found.map { it.offsetMs }
        if (offsets.max() - offsets.min() > AGREEMENT_MS) return null
        return found.maxBy { it.score }.offsetMs
    }
}
