package com.example.musicsm.playback.mix

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class BeatMixTest {

    private val sr = 44_100

    /** Kick on every beat (louder on the one), hats on the off-beats, a little noise. */
    private fun track(bpm: Double, seconds: Double, firstBeatMs: Double, seed: Int = 1): FloatArray {
        val rnd = Random(seed)
        val n = (seconds * sr).toInt()
        val out = FloatArray(n) { (rnd.nextFloat() - 0.5f) * 0.02f }
        val period = 60.0 / bpm
        var beat = 0
        var t = firstBeatMs / 1000.0
        while (t < seconds) {
            val amp = if (beat % 4 == 0) 0.9 else 0.5
            val start = (t * sr).toInt()
            for (i in 0 until (0.12 * sr).toInt()) {
                val idx = start + i
                if (idx >= n) break
                val tt = i.toDouble() / sr
                out[idx] += (amp * exp(-tt * 30) * sin(2 * PI * (60 + 90 * exp(-tt * 40)) * tt)).toFloat()
            }
            val hat = ((t + period / 2) * sr).toInt()
            var last = 0f
            for (i in 0 until (0.03 * sr).toInt()) {
                val idx = hat + i
                if (idx >= n) break
                // Differenced noise: bright like a real hat, with next to no low end.
                val w = rnd.nextFloat() - 0.5f
                out[idx] += ((w - last) * 0.3f * exp(-i / (0.005 * sr))).toFloat()
                last = w
            }
            beat++
            t += period
        }
        return out
    }

    private fun phaseErrorMs(grid: BeatGrid, trueFirstBeatMs: Double, periodMs: Double = grid.periodMs): Double {
        val d = ((grid.anchorMs - trueFirstBeatMs) % periodMs + periodMs) % periodMs
        return minOf(d, periodMs - d)
    }

    @Test
    fun detectsTempoAndPhase() {
        for ((bpm, offset) in listOf(124.0 to 310.0, 96.0 to 50.0, 140.0 to 0.0, 174.0 to 200.0)) {
            val grid = BeatAnalyzer.analyze(track(bpm, 20.0, offset), sr)
            assertNotNull("no grid at $bpm", grid)
            grid!!
            // Half/double time is an acceptable reading (the planner treats them as compatible).
            val octave = listOf(0.5, 1.0, 2.0).minBy { abs(grid.bpm - bpm * it) }
            assertEquals("bpm at $bpm", bpm * octave, grid.bpm, bpm * octave * 0.004)
            val err = phaseErrorMs(grid, offset, 60_000.0 / bpm)
            assertTrue("phase at $bpm: $err", err < 20.0)
            assertTrue("confidence at $bpm: ${grid.confidence}", grid.confidence >= TransitionPlanner.MIN_CONFIDENCE)
        }
    }

    @Test
    fun findsTheAccentedDownbeat() {
        val grid = BeatAnalyzer.analyze(track(120.0, 20.0, 250.0), sr)!!
        val first = grid.downbeatAtOrAfter(0.0)
        assertTrue("downbeat $first ($grid)", abs(first - 250.0) < 20.0)
    }

    @Test
    fun startOffsetShiftsTheGrid() {
        val grid = BeatAnalyzer.analyze(track(120.0, 15.0, 100.0), sr, startMs = 60_000.0)!!
        assertTrue("$grid", phaseErrorMs(grid, 60_100.0) < 20.0)
    }

    @Test
    fun noiseHasLowConfidence() {
        for (seed in 1..5) {
            val rnd = Random(seed)
            val noise = FloatArray(sr * 20) { (rnd.nextFloat() - 0.5f) * 0.5f }
            val grid = BeatAnalyzer.analyze(noise, sr)
            assertTrue("noise $grid", grid == null || grid.confidence < TransitionPlanner.MIN_CONFIDENCE)

            // Transients at random intervals: busy, but no pulse.
            val hits = FloatArray(sr * 20) { (rnd.nextFloat() - 0.5f) * 0.02f }
            var t = 0
            while (t < hits.size) {
                for (i in 0 until 3000) {
                    if (t + i < hits.size) hits[t + i] += ((rnd.nextFloat() - 0.5f) * exp(-i / 400.0)).toFloat()
                }
                t += rnd.nextInt(sr / 8, sr)
            }
            val h = BeatAnalyzer.analyze(hits, sr)
            assertTrue("hits $h", h == null || h.confidence < TransitionPlanner.MIN_CONFIDENCE)
        }
    }

    @Test
    fun plannerAlignsDownbeatsAndStretchesOutgoing() {
        val out = BeatGrid(periodMs = 60_000.0 / 126, anchorMs = 170_123.0, downbeatPhase = 0, confidence = 0.8f)
        val inc = BeatGrid(periodMs = 60_000.0 / 124, anchorMs = 400.0, downbeatPhase = 0, confidence = 0.8f)
        val duration = 200_000L
        val plan = TransitionPlanner.plan(duration, 10_000, 150_000, out, inc)
        assertTrue(plan.beatMatched)
        assertEquals(out.periodMs / inc.periodMs, plan.outgoingRate.toDouble(), 1e-4)
        // The incoming first downbeat (400 ms in) lands on an outgoing downbeat.
        val outgoingAtIncomingDownbeat = plan.handoffAtMs + 400.0 * plan.outgoingRate
        val fromBar = ((outgoingAtIncomingDownbeat - out.anchorMs) % (4 * out.periodMs))
        assertTrue(minOf(fromBar, 4 * out.periodMs - fromBar) < 2.0)
        // The outgoing tail lasts for the whole blend and the fade is whole bars.
        assertTrue(plan.handoffAtMs + plan.fadeMs * plan.outgoingRate <= duration + 1)
        val bars = plan.fadeMs / (4 * inc.periodMs)
        assertEquals(Math.round(bars).toDouble(), bars, 0.01)
        // Bass swap on an incoming downbeat.
        val swapBars = (plan.bassSwapMs - 400.0) / (4 * inc.periodMs)
        assertEquals(Math.round(swapBars).toDouble(), swapBars, 0.01)
    }

    @Test
    fun plannerTreatsDoubleTimeAsCompatible() {
        val out = BeatGrid(60_000.0 / 70, 0.0, 0, 0.8f)
        val inc = BeatGrid(60_000.0 / 140, 0.0, 0, 0.8f)
        val plan = TransitionPlanner.plan(240_000, 10_000, 0, out, inc)
        assertTrue(plan.beatMatched)
        assertEquals(1.0, plan.outgoingRate.toDouble(), 1e-3)
    }

    @Test
    fun plannerFallsBackWhenTemposClashOrUnsure() {
        val out = BeatGrid(60_000.0 / 100, 0.0, 0, 0.8f)
        val inc = BeatGrid(60_000.0 / 128, 0.0, 0, 0.8f)
        assertFalse(TransitionPlanner.plan(200_000, 10_000, 0, out, inc).beatMatched)
        assertFalse(TransitionPlanner.plan(200_000, 10_000, 0, out.copy(confidence = 0.05f), inc).beatMatched)
        assertFalse(TransitionPlanner.plan(200_000, 10_000, 0, null, inc).beatMatched)
        val fallback = TransitionPlanner.plan(200_000, 10_000, 0, null, null)
        assertEquals(194_000L, fallback.handoffAtMs)
        assertEquals(6_000L, fallback.fadeMs)
    }

    @Test
    fun automationInterpolatesOnLogFrequency() {
        val a = FilterAutomation.of(0.0 to 100.0, 1000.0 to 1600.0)
        assertEquals(100.0, a.cutoffAt(-5.0), 1e-6)
        assertEquals(400.0, a.cutoffAt(500.0), 1e-6)
        assertEquals(1600.0, a.cutoffAt(9999.0), 1e-6)
    }

    @Test
    fun plannerSkipsSilentRunOutAndLeadIn() {
        // Timed blend: ends where the outgoing audio does; the next track starts past its silence.
        val fallback = TransitionPlanner.plan(
            200_000, 8_000, 0, null, null, outgoingEndMs = 185_000, incomingStartMs = 1_200,
        )
        assertFalse(fallback.beatMatched)
        assertEquals(179_000L, fallback.handoffAtMs)
        assertEquals(1_200L, fallback.incomingStartMs)

        // Matched: the first incoming downbeat after the lead-in lands on an outgoing downbeat,
        // and the tail is done before the run-out.
        val out = BeatGrid(60_000.0 / 120, 0.0, 0, 0.8f)
        val inc = BeatGrid(60_000.0 / 120, 300.0, 0, 0.8f)
        val plan = TransitionPlanner.plan(
            200_000, 8_000, 100_000, out, inc, outgoingEndMs = 185_000, incomingStartMs = 1_200,
        )
        assertTrue(plan.beatMatched)
        assertEquals(1_200L, plan.incomingStartMs)
        assertTrue(plan.handoffAtMs + plan.fadeMs * plan.outgoingRate <= 185_001)
        val lead = inc.downbeatAtOrAfter(1_200.0) - 1_200.0
        val fromBar = (plan.handoffAtMs + lead * plan.outgoingRate) % (4 * out.periodMs)
        assertTrue("off by $fromBar", minOf(fromBar, 4 * out.periodMs - fromBar) < 2.0)
    }

    @Test
    fun fadeOutIsKeyedToMediaTime() {
        for (shape in FadeShape.entries) {
            val out = MixCurves.fadeOut(180_000.0, 8_000.0, shape)
            assertEquals(1.0, out.gainAt(179_999.0), 0.0)
            assertEquals(MixCurves.fadeOut(0.5, shape), out.gainAt(184_000.0), 1e-9)
            assertEquals(0.0, out.gainAt(188_000.0), 0.0)
        }
    }

    @Test
    fun blendIsEqualPowerAndMixClearsOutFaster() {
        val fadeIn = MixCurves.fadeIn(1_000.0, 8_000.0)
        assertEquals(0.0, fadeIn.gainAt(999.0), 0.0)
        assertEquals(1.0, fadeIn.gainAt(9_000.0), 0.0)
        for (u in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
            val incoming = MixCurves.fadeIn(u)
            val outgoing = MixCurves.fadeOut(u, FadeShape.EQUAL_POWER)
            assertEquals(1.0, incoming * incoming + outgoing * outgoing, 1e-9)
            assertTrue(MixCurves.fadeOut(u, FadeShape.MIX) < outgoing)
        }
    }

    @Test
    fun audibilityFindsWhereContentStartsAndEnds() {
        // From 10 s into a track: 1 s of noise floor, 3 s of tone, 2 s of noise floor.
        val rnd = Random(3)
        val samples = FloatArray(6 * sr) { i ->
            val t = i.toDouble() / sr
            if (t >= 1.0 && t < 4.0) (0.5 * sin(2 * PI * 440 * t)).toFloat() else (rnd.nextFloat() - 0.5f) * 1e-4f
        }
        val snippet = PcmSnippet(samples, sr, 10_000.0)
        assertEquals(11_000.0, Audibility.startMs(snippet)!!, 50.0)
        assertEquals(14_000.0, Audibility.endMs(snippet)!!, 50.0)
        assertEquals(16_000.0, Audibility.snippetEndMs(snippet), 1e-6)
        assertNull(Audibility.startMs(PcmSnippet(FloatArray(sr), sr, 0.0)))
    }

    /** Runs [ms] of full-scale mono audio that starts [offsetMs] into its track through [p]. */
    private fun process(p: DjFilterProcessor, offsetMs: Long, ms: Int): FloatArray {
        p.flush(AudioProcessor.StreamMetadata.Builder().setPositionOffsetUs(offsetMs * 1000).build())
        return feed(p, ms)
    }

    /** Queues [ms] of full-scale mono audio at 48 kHz, continuing the current stream. */
    private fun feed(p: DjFilterProcessor, ms: Int): FloatArray {
        val frames = ms * 48
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putFloat(1f) }
        input.flip()
        p.queueInput(input)
        val out = p.output
        return FloatArray(out.remaining() / 4) { out.getFloat() }
    }

    /** Stream metadata for playlist item [id], starting [offsetMs] into it. */
    private fun meta(id: String, offsetMs: Long): AudioProcessor.StreamMetadata {
        val timeline = SinglePeriodTimeline(60_000_000L, true, false, false, null, MediaItem.Builder().setMediaId(id).build())
        return AudioProcessor.StreamMetadata.Builder()
            .setTimeline(timeline)
            .setPeriodUid(timeline.getUidOfPeriod(0))
            .setPositionOffsetUs(offsetMs * 1000)
            .build()
    }

    @Test
    fun mixesTheNextTrackInAndJoinsItExactly() {
        val p = DjFilterProcessor()
        p.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        // Track B's opening, a steady 0.5, decoded from 100 ms in; blended over A's 1000..1500 ms.
        val head = FloatArray(2 * 48_000) { 0.5f }
        p.automate(
            Automation(
                gain = MixCurves.fadeOut(1_000.0, 500.0, FadeShape.EQUAL_POWER),
                mediaId = "A",
                mixIn = MixIn(
                    pcm = head,
                    channels = 1,
                    sampleRate = 48_000,
                    startMs = 1_000.0,
                    nextMediaId = "B",
                    nextStartMs = 100.0,
                    outgoingEndMs = 1_600.0,
                    gain = MixCurves.fadeIn(1_000.0, 500.0),
                ),
            ),
        )
        p.flush(meta("A", 900))
        val out = feed(p, ms = 700)
        fun at(ms: Double) = out[((ms - 900) * 48).toInt()].toDouble()
        assertEquals(1.0, at(950.0), 1e-6)
        assertEquals(cos(PI / 4) + 0.5 * sin(PI / 4), at(1_250.0), 0.01)
        assertEquals(0.5, at(1_550.0), 1e-6)

        // A ended at 1600, where the mix had got B to 700 ms. B starting 20 ms late: filled in.
        p.flush(meta("B", 720))
        val late = feed(p, ms = 100)
        assertEquals(4_800 + 960, late.size)
        assertTrue(late.take(960).all { abs(it - 0.5f) < 1e-6 })
        assertTrue(late.drop(960).all { it == 1f })
        assertEquals(20.0, p.takeJoin()!!.gapMs, 1e-6)

        // Starting 20 ms early: the repeat is dropped.
        p.flush(meta("A", 900))
        feed(p, ms = 700)
        p.flush(meta("B", 680))
        assertEquals(4_800 - 960, feed(p, ms = 100).size)
        assertEquals(-20.0, p.takeJoin()!!.gapMs, 1e-6)

        // Skipping to B mid-blend isn't a join; it's left alone.
        p.flush(meta("A", 900))
        feed(p, ms = 200)
        p.flush(meta("B", 5_000))
        assertEquals(4_800, feed(p, ms = 100).size)
        assertNull(p.takeJoin())
    }

    @Test
    fun encoderTrimIsAddedBackToTheProcessorClock() {
        val p = DjFilterProcessor()
        p.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        p.setEncoderTrim(2_400, 0)
        p.automate(Automation(gain = MixCurves.fadeOut(950.0, 50.0, FadeShape.EQUAL_POWER)))

        val trimmed = process(p, offsetMs = 900, ms = 100)
        assertEquals(1.0, trimmed[0].toDouble(), 1e-6)
        assertEquals(cos(PI / 4), trimmed[25 * 48].toDouble(), 0.01)
        assertEquals(0.0, trimmed[51 * 48].toDouble(), 1e-6)

        val zero = DjFilterProcessor()
        zero.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        zero.setEncoderTrim(2_400, 0)
        zero.automate(Automation(gain = MixCurves.fadeOut(50.0, 50.0, FadeShape.EQUAL_POWER)))
        val fromZero = process(zero, offsetMs = 0, ms = 100)
        assertEquals(1.0, fromZero[25 * 48].toDouble(), 1e-6)
        assertEquals(cos(PI / 4), fromZero[75 * 48].toDouble(), 0.01)
    }

    @Test
    fun intermediateEmptyFlushDoesNotHideThePreviousStreamAtJoin() {
        val p = DjFilterProcessor()
        p.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        p.automate(
            Automation(
                mediaId = "A",
                mixIn = MixIn(
                    pcm = FloatArray(2 * 48_000) { 0.5f },
                    channels = 1,
                    sampleRate = 48_000,
                    startMs = 1_000.0,
                    nextMediaId = "B",
                    nextStartMs = 100.0,
                    outgoingEndMs = 1_600.0,
                    gain = GainCurve { 1.0 },
                ),
            ),
        )

        p.flush(meta("A", 900))
        feed(p, ms = 700)
        p.flush(meta("A", 0))
        p.flush(meta("B", 720))

        val late = feed(p, ms = 100)
        assertEquals(4_800 + 960, late.size)
        assertTrue(late.take(960).all { abs(it - 0.5f) < 1e-6 })
        assertEquals(20.0, p.takeJoin()!!.gapMs, 1e-6)

        p.flush(meta("A", 900))
        feed(p, ms = 700)
        p.flush(meta("A", 0))
        p.flush(meta("B", 680))
        assertEquals(4_800 - 960, feed(p, ms = 100).size)
        assertEquals(-20.0, p.takeJoin()!!.gapMs, 1e-6)
    }

    @Test
    fun noJoinWhenOutgoingStreamDidNotReachItsEnd() {
        val p = DjFilterProcessor()
        p.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        p.automate(
            Automation(
                mediaId = "A",
                mixIn = MixIn(
                    pcm = FloatArray(2 * 48_000) { 0.5f },
                    channels = 1,
                    sampleRate = 48_000,
                    startMs = 1_000.0,
                    nextMediaId = "B",
                    nextStartMs = 100.0,
                    outgoingEndMs = 1_600.0,
                    gain = GainCurve { 1.0 },
                ),
            ),
        )

        p.flush(meta("A", 900))
        feed(p, ms = 200)
        p.flush(meta("B", 680))

        assertEquals(4_800, feed(p, ms = 100).size)
        assertNull(p.takeJoin())
    }

    @Test
    fun processorFadesByMediaTimeNotBufferTime() {
        val p = DjFilterProcessor()
        p.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        p.automate(Automation(gain = MixCurves.fadeOut(950.0, 50.0, FadeShape.EQUAL_POWER)))
        // Level 1 until 950 ms into the track, silent from 1000 ms - wherever the buffer starts.
        val out = process(p, offsetMs = 900, ms = 200)
        fun at(ms: Double) = out[((ms - 900) * 48).toInt()].toDouble()
        assertEquals(1.0, at(940.0), 1e-6)
        assertEquals(cos(PI / 4), at(975.0), 0.01)
        assertEquals(0.0, at(1_001.0), 1e-6)
        assertEquals(0.0, at(1_099.0), 1e-6)

        // Scoped to another item: this stream isn't it, so it passes through untouched.
        p.automate(Automation(gain = GainCurve { 0.0 }, mediaId = "other"))
        assertTrue(process(p, offsetMs = 900, ms = 50).all { it == 1f })
        p.clear()
        assertFalse(p.isAutomated)
        assertTrue(process(p, offsetMs = 1_000, ms = 50).all { it == 1f })
    }
}
