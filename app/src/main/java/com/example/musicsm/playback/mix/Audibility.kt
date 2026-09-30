package com.example.musicsm.playback.mix

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds where a snippet's content actually starts and ends, so a Mix doesn't blend over a silent
 * run-out or wait through a silent lead-in. Works on 50 ms RMS frames: a frame is audible when it
 * is within 40 dB of the snippet's loud parts (95th percentile) and above -54 dBFS.
 */
object Audibility {

    private const val FRAME_MS = 50.0
    private const val FLOOR_DB = -54.0
    private const val RANGE_DB = 40.0

    /** Media time where the first audible frame starts, or null if nothing is audible. */
    fun startMs(snippet: PcmSnippet): Double? {
        val levels = frameLevels(snippet) ?: return null
        val threshold = threshold(levels)
        val first = levels.indexOfFirst { it >= threshold }
        return if (first < 0) null else snippet.startMs + first * FRAME_MS
    }

    /** Media time where the last audible frame ends, or null if nothing is audible. */
    fun endMs(snippet: PcmSnippet): Double? {
        val levels = frameLevels(snippet) ?: return null
        val threshold = threshold(levels)
        val last = levels.indexOfLast { it >= threshold }
        return if (last < 0) null else minOf(snippet.startMs + (last + 1) * FRAME_MS, snippetEndMs(snippet))
    }

    fun snippetEndMs(snippet: PcmSnippet): Double =
        snippet.startMs + snippet.samples.size * 1000.0 / snippet.sampleRate

    private fun frameLevels(snippet: PcmSnippet): DoubleArray? {
        val n = (snippet.sampleRate * FRAME_MS / 1000).toInt()
        if (n <= 0) return null
        val count = snippet.samples.size / n
        if (count == 0) return null
        return DoubleArray(count) { f ->
            var sum = 0.0
            for (i in f * n until (f + 1) * n) {
                val s = snippet.samples[i].toDouble()
                sum += s * s
            }
            20 * log10(sqrt(sum / n) + 1e-9)
        }
    }

    private fun threshold(levels: DoubleArray): Double {
        val sorted = levels.sortedArray()
        val p95 = sorted[((sorted.size - 1) * 0.95).toInt()]
        return max(FLOOR_DB, p95 - RANGE_DB)
    }
}
