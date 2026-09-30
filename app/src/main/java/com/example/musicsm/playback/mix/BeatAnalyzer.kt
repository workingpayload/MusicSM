package com.example.musicsm.playback.mix

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A constant-tempo beat grid over a stretch of a track. Beat `i` sits at `anchorMs + i * periodMs`
 * (media time); every fourth beat starting at [downbeatPhase] is treated as the bar's "one".
 */
data class BeatGrid(
    val periodMs: Double,
    val anchorMs: Double,
    val downbeatPhase: Int,
    val confidence: Float,
) {
    val bpm: Double get() = 60_000.0 / periodMs

    fun beatTime(index: Int): Double = anchorMs + index * periodMs

    fun isDownbeat(index: Int): Boolean = Math.floorMod(index - downbeatPhase, 4) == 0

    fun downbeatAtOrAfter(ms: Double): Double {
        var i = ceil((ms - anchorMs) / periodMs - EPS).toInt()
        while (!isDownbeat(i)) i++
        return beatTime(i)
    }

    fun downbeatAtOrBefore(ms: Double): Double {
        var i = floor((ms - anchorMs) / periodMs + EPS).toInt()
        while (!isDownbeat(i)) i--
        return beatTime(i)
    }

    private companion object {
        const val EPS = 1e-6
    }
}

/**
 * Lightweight tempo/beat-phase estimator for mixing: spectral-flux onset envelope, tempo from a
 * prior-weighted autocorrelation, then a joint period/phase comb search for a sub-frame grid.
 * Assumes a roughly steady tempo over the analysed snippet (a track's intro or outro), which is
 * all a DJ-style transition needs.
 */
object BeatAnalyzer {

    private const val FRAME = 1024
    private const val HOP = 256
    private const val MIN_BPM = 70.0
    private const val MAX_BPM = 190.0
    private const val PRIOR_BPM = 120.0
    private const val PRIOR_OCTAVES = 1.0
    private const val MIN_SECONDS = 6.0
    private const val LOW_BAND_HZ = 180.0
    private const val LOW_WEIGHT = 1.5

    private val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / FRAME)).toFloat() }

    /**
     * @param mono mono PCM in [-1, 1]
     * @param startMs media time of `mono[0]`, so the returned grid is in track time
     * @return the grid, or null if the snippet is too short or has no usable pulse
     */
    fun analyze(mono: FloatArray, sampleRate: Int, startMs: Double = 0.0): BeatGrid? {
        if (sampleRate <= 0) return null
        val env = onsetEnvelope(mono, sampleRate)
        val fps = sampleRate.toDouble() / HOP
        if (env.size < fps * MIN_SECONDS) return null

        val minLag = floor(fps * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = ceil(fps * 60.0 / MIN_BPM).toInt()
        if (maxLag * 3 >= env.size) return null
        val ac = DoubleArray(maxLag * 2 + 2)
        for (lag in 1 until ac.size) ac[lag] = autocorr(env, lag)

        var bestLag = -1
        var bestScore = 0.0
        for (lag in minLag..maxLag) {
            val bpm = fps * 60.0 / lag
            val octaves = ln(bpm / PRIOR_BPM) / ln(2.0)
            val prior = exp(-0.5 * (octaves / PRIOR_OCTAVES) * (octaves / PRIOR_OCTAVES))
            // Pulse-train check: a real beat period also repeats at twice the lag, and usually
            // has something on the half-beat. Neighbourhood max absorbs integer-lag rounding.
            val score = prior * (ac[lag] + 0.5 * peakAc(ac, lag * 2) + 0.25 * peakAc(ac, lag / 2))
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        // Joint period/phase comb refinement around the autocorrelation peak.
        var bestPeriod = bestLag.toDouble()
        var bestPhase = 0.0
        var bestComb = -1.0
        var bestMean = 1.0
        val lo = bestLag * 0.97
        val hi = bestLag * 1.03
        val steps = 60
        for (s in 0..steps) {
            val period = lo + (hi - lo) * s / steps
            var sum = 0.0
            var count = 0
            var localBest = -1.0
            var localPhase = 0.0
            var phase = 0.0
            while (phase < period) {
                val c = comb(env, period, phase)
                sum += c
                count++
                if (c > localBest) {
                    localBest = c
                    localPhase = phase
                }
                phase += 0.5
            }
            if (localBest > bestComb) {
                bestComb = localBest
                bestPeriod = period
                bestPhase = localPhase
                bestMean = sum / count
            }
        }
        if (bestComb <= 0.0 || bestMean <= 0.0) return null

        val confidence = ((pulseClarity(env, bestPeriod) + pulseClarity(env, bestPeriod * 2)) / 2)
            .coerceIn(0.0, 1.0).toFloat()

        // Downbeat guess: the beat slot (mod 4) carrying the most onset energy (kicks on the one).
        val strength = DoubleArray(4)
        var k = 0
        var t = bestPhase
        while (t < env.size) {
            strength[k % 4] += peakNear(env, t)
            k++
            t += bestPeriod
        }
        val downbeat = strength.indices.maxBy { strength[it] }

        val msPerFrame = HOP * 1000.0 / sampleRate
        val centerMs = FRAME / 2 * 1000.0 / sampleRate
        return BeatGrid(
            periodMs = bestPeriod * msPerFrame,
            anchorMs = startMs + bestPhase * msPerFrame + centerMs,
            downbeatPhase = downbeat,
            confidence = confidence,
        )
    }

    /**
     * Onset strength per hop: half-wave-rectified log-spectral flux, local mean removed. The
     * kick/bass band is mixed in on top of the full band, so the grid locks to the beat rather
     * than to off-beat hi-hats (which dominate a flat, all-bins flux).
     */
    internal fun onsetEnvelope(mono: FloatArray, sampleRate: Int): FloatArray {
        val frames = if (mono.size < FRAME) 0 else (mono.size - FRAME) / HOP + 1
        if (frames < 2) return FloatArray(0)
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        val half = FRAME / 2
        val lowBins = ceil(LOW_BAND_HZ * FRAME / sampleRate).toInt().coerceIn(2, half - 1)
        var prev = DoubleArray(half)
        var cur = DoubleArray(half)
        val full = FloatArray(frames)
        val low = FloatArray(frames)
        for (f in 0 until frames) {
            val off = f * HOP
            for (i in 0 until FRAME) {
                re[i] = (mono[off + i] * window[i]).toDouble()
                im[i] = 0.0
            }
            fft(re, im)
            var sum = 0.0
            var lowSum = 0.0
            for (b in 1 until half) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                val v = ln(1.0 + 100.0 * mag)
                cur[b] = v
                if (f > 0) {
                    val d = max(0.0, v - prev[b])
                    sum += d
                    if (b <= lowBins) lowSum += d
                }
            }
            full[f] = sum.toFloat()
            low[f] = lowSum.toFloat()
            val tmp = prev
            prev = cur
            cur = tmp
        }
        val a = detrend(full)
        val b = detrend(low)
        val meanA = a.average().takeIf { it > 0 } ?: 1.0
        val meanB = b.average().takeIf { it > 0 } ?: 1.0
        return FloatArray(frames) { (a[it] / meanA + LOW_WEIGHT * b[it] / meanB).toFloat() }
    }

    /** Removes the slowly varying loudness trend so only transients remain. */
    private fun detrend(flux: FloatArray): FloatArray {
        val frames = flux.size
        val radius = 8
        val out = FloatArray(frames)
        var acc = 0.0
        var lo = 0
        var hi = -1
        for (i in 0 until frames) {
            val wantHi = min(frames - 1, i + radius)
            val wantLo = max(0, i - radius)
            while (hi < wantHi) acc += flux[++hi]
            while (lo < wantLo) acc -= flux[lo++]
            val mean = acc / (hi - lo + 1)
            out[i] = max(0.0, flux[i] - mean).toFloat()
        }
        return out
    }

    /**
     * Normalised autocorrelation of the (zero-mean) envelope at a lag: near 0 for unpulsed or
     * irregular audio, 0.3+ at the beat period of music with a clear beat.
     */
    private fun pulseClarity(env: FloatArray, period: Double): Double {
        val mean = env.average()
        val lag = period.roundToInt()
        if (lag <= 0 || lag >= env.size) return 0.0
        var num = 0.0
        var den = 0.0
        for (i in env.indices) {
            val x = env[i] - mean
            den += x * x
            if (i + lag < env.size) num += x * (env[i + lag] - mean)
        }
        return if (den <= 0.0) 0.0 else num / den
    }

    private fun peakAc(ac: DoubleArray, lag: Int): Double {
        var best = 0.0
        for (l in lag - 1..lag + 1) if (l in ac.indices && ac[l] > best) best = ac[l]
        return best
    }

    private fun autocorr(env: FloatArray, lag: Int): Double {
        if (lag >= env.size) return 0.0
        var s = 0.0
        for (i in 0 until env.size - lag) s += env[i] * env[i + lag]
        return s / (env.size - lag)
    }

    private fun comb(env: FloatArray, period: Double, phase: Double): Double {
        var s = 0.0
        var n = 0
        var t = phase
        val last = env.size - 1
        while (t < last) {
            val i = t.toInt()
            val frac = t - i
            s += env[i] * (1 - frac) + env[i + 1] * frac
            n++
            t += period
        }
        return if (n == 0) 0.0 else s / n
    }

    private fun peakNear(env: FloatArray, t: Double): Double {
        val c = t.roundToInt()
        var best = 0f
        for (i in c - 2..c + 2) if (i in env.indices && env[i] > best) best = env[i]
        return best.toDouble()
    }

    /** In-place iterative radix-2 FFT; [re]/[im] length must be a power of two. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
