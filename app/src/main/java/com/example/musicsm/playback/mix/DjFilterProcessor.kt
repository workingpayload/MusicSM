package com.example.musicsm.playback.mix

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** A gain (0..1) over a track's media time. */
fun interface GainCurve {
    fun gainAt(ms: Double): Double
}

/**
 * High-pass cutoff over a track's media time, as breakpoints interpolated on a log-frequency
 * scale (so sweeps sound even). Before the first point / after the last, the end values hold.
 */
class FilterAutomation private constructor(
    private val timesMs: DoubleArray,
    private val hz: DoubleArray,
) {
    fun cutoffAt(ms: Double): Double {
        if (ms <= timesMs.first()) return hz.first()
        if (ms >= timesMs.last()) return hz.last()
        var i = 1
        while (timesMs[i] < ms) i++
        val t0 = timesMs[i - 1]
        val t1 = timesMs[i]
        val f = if (t1 > t0) (ms - t0) / (t1 - t0) else 1.0
        return exp(ln(hz[i - 1]) + (ln(hz[i]) - ln(hz[i - 1])) * f)
    }

    companion object {
        fun of(vararg points: Pair<Double, Double>): FilterAutomation {
            require(points.isNotEmpty())
            val sorted = points.sortedBy { it.first }
            return FilterAutomation(
                DoubleArray(sorted.size) { sorted[it].first },
                DoubleArray(sorted.size) { sorted[it].second.coerceAtLeast(1.0) },
            )
        }
    }
}

/**
 * The opening of the next track, decoded ahead of time, to be mixed under the end of the one
 * playing. Timed by the playing track's media time, so it lands to the sample.
 *
 * @property pcm interleaved samples in [-1, 1]
 * @property startMs playing track's media time at which frame 0 is heard
 * @property nextMediaId the track this is the opening of
 * @property nextStartMs that track's media time of frame 0
 * @property outgoingEndMs the outgoing track's end in its own media time
 * @property gain level of the mixed-in audio over the playing track's media time
 * @property cutoff its high-pass over the playing track's media time
 */
class MixIn(
    val pcm: FloatArray,
    val channels: Int,
    val sampleRate: Int,
    val startMs: Double,
    val nextMediaId: String,
    val nextStartMs: Double,
    val outgoingEndMs: Double,
    val gain: GainCurve,
    val cutoff: FilterAutomation? = null,
) {
    val frames: Int = pcm.size / channels

    /** The next track's media time reached when the playing one is at [ms]. */
    fun nextMsAt(ms: Double): Double = nextStartMs + (ms - startMs)

    /** Sample at fractional frame [pos] for output channel [c] of [outChannels]; 0 outside. */
    internal fun sample(pos: Double, c: Int, outChannels: Int): Double {
        if (pos < 0 || pos >= frames - 1) return 0.0
        val i = pos.toInt()
        val frac = pos - i
        fun at(ch: Int): Double {
            val a = pcm[i * channels + ch]
            val b = pcm[(i + 1) * channels + ch]
            return a + (b - a) * frac
        }
        if (outChannels == 1 && channels > 1) {
            var sum = 0.0
            for (ch in 0 until channels) sum += at(ch)
            return sum / channels
        }
        return at(min(c, channels - 1))
    }
}

/**
 * What [DjFilterProcessor] does to a stream: a [gain] envelope and/or a high-pass [cutoff], both
 * read against the media time of each sample, plus optionally the next track's opening mixed in
 * ([mixIn]). [mediaId] limits it to one playlist item (null applies to whatever is playing).
 */
class Automation(
    val gain: GainCurve? = null,
    val cutoff: FilterAutomation? = null,
    val mediaId: String? = null,
    val mixIn: MixIn? = null,
)

/**
 * How the next track joined on after a [MixIn]: [gapMs] is where it really started minus where
 * the mixed-in audio had got to (+ = it started later). Up to a small amount that is patched over
 * exactly ([fixed]); beyond, the join is left alone.
 */
class Join(val gapMs: Double, val fixed: Boolean)

/**
 * A DJ-style volume + sweepable high-pass stage (24 dB/oct: two cascaded Butterworth biquads) at
 * the head of the player's audio chain, which can also mix in the opening of the next track.
 * Everything is keyed to the media time of the samples being processed rather than wall time, so
 * fades, bass swaps and the mix land exactly where they were planned no matter how far ahead the
 * audio sink is buffering.
 *
 * When the player then moves on to the next track by itself (gapless), that track is expected to
 * start where the mixed-in part ended; any small difference is patched here (a few ms of the
 * mixed-in audio added, or of the new stream dropped), so it continues seamlessly.
 *
 * With no automation (the normal state) audio passes through untouched.
 */
@UnstableApi
class DjFilterProcessor : BaseAudioProcessor() {

    @Volatile private var automations: List<Automation> = emptyList()
    @Volatile private var encoderTrimFrames = 0L

    /**
     * mediaId of the playlist item whose audio is flowing through, or null when the sink didn't
     * say (then only unscoped automations apply).
     */
    @Volatile var streamMediaId: String? = null
        private set

    /** How the last track change after a [MixIn] went (null if none since the last call). */
    fun takeJoin(): Join? = lastJoin.also { lastJoin = null }

    @Volatile private var lastJoin: Join? = null

    private var streamStartUs = 0L
    private var streamPeriodUid: Any? = null
    private var streamSampleRate = 0
    private var framesSinceFlush = 0L
    private var endedStreamMediaId: String? = null
    private var endedStreamEndMs = Double.NaN
    private val main = HighPass()
    private val mixed = HighPass()
    private var dropFrames = 0L
    private var pad: MixIn? = null
    private var padFromMs = 0.0
    private var padFrames = 0
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    /**
     * Follows the first of [value] that applies to the current item. Takes effect for audio
     * processed from now on, which is typically a few hundred ms ahead of what is audible.
     */
    fun automate(vararg value: Automation) {
        automations = value.toList()
    }

    /** Back to pass-through. */
    fun clear() {
        automations = emptyList()
    }

    val isAutomated: Boolean get() = automations.isNotEmpty()

    fun setEncoderTrim(encoderDelayFrames: Int, encoderPaddingFrames: Int) {
        encoderTrimFrames = encoderDelayFrames.coerceAtLeast(0).toLong() +
            encoderPaddingFrames.coerceAtLeast(0).toLong()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val channels = format.channelCount
        val sampleRate = format.sampleRate
        val bytesPerFrame = format.bytesPerFrame
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        if (dropFrames > 0) {
            val drop = min(dropFrames, (inputBuffer.remaining() / bytesPerFrame).toLong()).toInt()
            inputBuffer.position(inputBuffer.position() + drop * bytesPerFrame)
            framesSinceFlush += drop
            dropFrames -= drop
        }
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames + padFrames == 0) return
        val out = replaceOutputBuffer((frames + padFrames) * bytesPerFrame)
        writePad(out, channels, sampleRate, isFloat)
        val active = activeAutomation()

        if (active == null) {
            out.put(inputBuffer)
            out.flip()
            framesSinceFlush += frames
            main.tune(0.0, sampleRate, channels)
            mixed.tune(0.0, sampleRate, channels)
            return
        }

        val gain = active.gain
        val cutoff = active.cutoff
        val mix = active.mixIn
        val step = if (mix != null) mix.sampleRate.toDouble() / sampleRate else 0.0
        var done = 0
        while (done < frames) {
            val block = min(BLOCK_FRAMES, frames - done)
            val startMs = mediaMs(framesSinceFlush + done, sampleRate)
            val endMs = mediaMs(framesSinceFlush + done + block, sampleRate)
            val filtered = main.tune(cutoff?.cutoffAt(startMs) ?: 0.0, sampleRate, channels)
            // Gains ramp linearly across the block, so even a fast envelope has no zipper steps.
            val g0 = gain?.gainAt(startMs) ?: 1.0
            val gStep = ((gain?.gainAt(endMs) ?: 1.0) - g0) / block
            var m0 = 0.0
            var mStep = 0.0
            var pos0 = 0.0
            var mixFiltered = false
            if (mix != null) {
                m0 = mix.gain.gainAt(startMs)
                mStep = (mix.gain.gainAt(endMs) - m0) / block
                pos0 = (startMs - mix.startMs) * mix.sampleRate / 1000.0
                mixFiltered = mixed.tune(mix.cutoff?.cutoffAt(startMs) ?: 0.0, sampleRate, channels)
            }
            for (f in 0 until block) {
                val g = g0 + gStep * f
                val mg = m0 + mStep * f
                val pos = pos0 + step * f
                for (c in 0 until channels) {
                    val x = if (isFloat) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                    var y = (if (filtered) main.process(x, c) else x) * g
                    if (mix != null) {
                        val s = mix.sample(pos, c, channels)
                        y += (if (mixFiltered) mixed.process(s, c) else s) * mg
                    }
                    put(out, y, isFloat)
                }
            }
            done += block
        }
        framesSinceFlush += frames
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val previousId = streamMediaId
        val previousEndMs = if (streamSampleRate > 0) mediaMs(framesSinceFlush, streamSampleRate) else Double.NaN
        val uid = streamMetadata.periodUid
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount.coerceAtLeast(0)
        val reportedOffsetUs = streamMetadata.positionOffsetUs.coerceAtLeast(0)
        val offsetUs = correctedPositionOffsetUs(reportedOffsetUs, sampleRate)
        // Playback-speed and skip-silence changes flush the chain mid-stream. When the new stream
        // simply continues the old one, keep the filter state so an active sweep doesn't click.
        val continuous = streamSampleRate > 0 && sampleRate == streamSampleRate && uid == streamPeriodUid &&
            abs(offsetUs - (streamStartUs + framesSinceFlush * 1_000_000L / streamSampleRate)) <= CONTINUITY_US
        if (!continuous && framesSinceFlush > 0 && !previousEndMs.isNaN()) {
            endedStreamMediaId = previousId
            endedStreamEndMs = previousEndMs
        }
        streamStartUs = offsetUs
        framesSinceFlush = 0
        streamPeriodUid = uid
        streamSampleRate = sampleRate
        streamMediaId = runCatching { mediaIdOf(streamMetadata) }.getOrNull()
        if (continuous) return
        dropFrames = 0
        padFrames = 0
        pad = null
        main.reset(channels)
        mixed.reset(channels)
        if (!endedStreamEndMs.isNaN() && join(endedStreamMediaId, endedStreamEndMs, sampleRate)) {
            endedStreamMediaId = null
            endedStreamEndMs = Double.NaN
        }
    }

    override fun onReset() {
        // Automations belong to whoever set them; only the stream bookkeeping and DSP state go.
        streamStartUs = 0
        framesSinceFlush = 0
        streamPeriodUid = null
        streamSampleRate = 0
        streamMediaId = null
        endedStreamMediaId = null
        endedStreamEndMs = Double.NaN
        encoderTrimFrames = 0
        dropFrames = 0
        padFrames = 0
        pad = null
        main.reset(0)
        mixed.reset(0)
    }

    /**
     * The player moved from [previousId] (processed up to [previousEndMs]) to a new stream. If that
     * is the track a [MixIn] was playing the opening of, line its start up with where the mix got to.
     */
    private fun join(previousId: String?, previousEndMs: Double, sampleRate: Int): Boolean {
        val mix = automations.firstOrNull { it.mediaId != null && it.mediaId == previousId }?.mixIn ?: return false
        if (streamMediaId != mix.nextMediaId) return false
        if (previousEndMs < mix.outgoingEndMs - JOIN_END_TOLERANCE_MS) return true
        val reached = mix.nextMsAt(previousEndMs)
        val gap = streamStartUs / 1000.0 - reached
        val fixed = when {
            // Started a little late: fill in the missing bit from the mixed-in audio.
            gap > 0 && gap <= MAX_JOIN_PAD_MS && reached >= mix.nextStartMs -> {
                pad = mix
                padFromMs = reached
                padFrames = (gap * sampleRate / 1000.0).roundToInt()
                true
            }
            // Started early: drop what the mix already played.
            gap < 0 && -gap <= MAX_JOIN_DROP_MS -> {
                dropFrames = (-gap * sampleRate / 1000.0).roundToLong()
                true
            }
            else -> gap == 0.0
        }
        lastJoin = Join(gap, fixed)
        return true
    }

    /** Writes the pending [padFrames] of mixed-in audio (at full level) ahead of the stream. */
    private fun writePad(out: ByteBuffer, channels: Int, sampleRate: Int, isFloat: Boolean) {
        val mix = pad
        if (mix == null || padFrames == 0) {
            padFrames = 0
            return
        }
        val pos0 = (padFromMs - mix.nextStartMs) * mix.sampleRate / 1000.0
        val step = mix.sampleRate.toDouble() / sampleRate
        for (f in 0 until padFrames) {
            for (c in 0 until channels) put(out, mix.sample(pos0 + step * f, c, channels), isFloat)
        }
        padFrames = 0
        pad = null
    }

    private fun put(out: ByteBuffer, y: Double, isFloat: Boolean) {
        if (isFloat) {
            out.putFloat(y.toFloat())
        } else {
            out.putShort((y * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun activeAutomation(): Automation? {
        val list = automations
        if (list.isEmpty()) return null
        val id = streamMediaId
        return list.firstOrNull { it.mediaId == null || it.mediaId == id }
    }

    private fun mediaMs(frames: Long, sampleRate: Int): Double =
        (streamStartUs + frames * 1_000_000L / sampleRate) / 1000.0

    private fun correctedPositionOffsetUs(reportedOffsetUs: Long, sampleRate: Int): Long {
        val trimFrames = encoderTrimFrames
        if (reportedOffsetUs == 0L || trimFrames == 0L || sampleRate <= 0) return reportedOffsetUs
        return reportedOffsetUs + trimFrames * 1_000_000L / sampleRate
    }

    private fun mediaIdOf(meta: AudioProcessor.StreamMetadata): String? {
        val uid = meta.periodUid ?: return null
        val timeline = meta.timeline
        if (timeline.isEmpty) return null
        val index = timeline.getIndexOfPeriod(uid)
        if (index == C.INDEX_UNSET) return null
        val windowIndex = timeline.getPeriod(index, period).windowIndex
        return timeline.getWindow(windowIndex, window).mediaItem.mediaId
    }

    companion object {
        /** Cutoffs at or below this are treated as "filter off". */
        const val BYPASS_HZ = 12.0
        const val OFF_HZ = 10.0
        private const val BLOCK_FRAMES = 64
        private const val CONTINUITY_US = 5_000L
        private const val MAX_JOIN_PAD_MS = 250.0
        private const val MAX_JOIN_DROP_MS = 15_000.0
        private const val JOIN_END_TOLERANCE_MS = 250.0
    }
}

/** Two identical transposed-direct-form-II Butterworth high-pass biquads in series, per channel. */
private class HighPass {
    private var state = DoubleArray(0)
    private var hz = -1.0
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    fun reset(channels: Int) {
        state = DoubleArray(channels * 4)
        hz = -1.0
    }

    /** Sets the cutoff for the next block; false means the filter is off (pass-through). */
    fun tune(cutoffHz: Double, sampleRate: Int, channels: Int): Boolean {
        if (state.size != channels * 4) reset(channels)
        val target = cutoffHz.coerceAtMost(sampleRate * 0.45)
        if (target <= DjFilterProcessor.BYPASS_HZ) {
            if (hz >= 0) reset(channels)
            return false
        }
        if (target != hz) {
            val w0 = 2 * PI * target / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2 * Q)
            val a0 = 1 + alpha
            b0 = (1 + cosW) / 2 / a0
            b1 = -(1 + cosW) / a0
            b2 = b0
            a1 = -2 * cosW / a0
            a2 = (1 - alpha) / a0
            hz = target
        }
        return true
    }

    fun process(x: Double, c: Int): Double {
        val i = c * 4
        val y1 = b0 * x + state[i]
        state[i] = b1 * x - a1 * y1 + state[i + 1]
        state[i + 1] = b2 * x - a2 * y1
        val y2 = b0 * y1 + state[i + 2]
        state[i + 2] = b1 * y1 - a1 * y2 + state[i + 3]
        state[i + 3] = b2 * y1 - a2 * y2
        return y2
    }

    private companion object {
        val Q = 1 / sqrt(2.0)
    }
}
