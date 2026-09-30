package com.example.musicsm.playback.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AudioAlignmentTest {

    private val hop = AudioAlignment.HOP_MS

    /** Loudness frames of made-up "music": irregular hits that ring out, over a quiet bed. */
    private fun music(seconds: Int, seed: Int): FloatArray {
        val random = Random(seed)
        val frames = FloatArray(seconds * 1000 / hop) { 0.001f }
        var i = 0
        while (i < frames.size) {
            val level = 0.2f + random.nextFloat()
            for (k in 0 until 30) if (i + k < frames.size) frames[i + k] += level * (1f - k / 30f)
            i += 12 + random.nextInt(40)
        }
        return frames
    }

    /** [reference] frames as another cut hears them: [gain] louder, noisy, [offsetMs] later. */
    private fun anotherCut(reference: FloatArray, fromMs: Long, lengthMs: Long, offsetMs: Long, gain: Float): FloatArray {
        val random = Random(7)
        val first = ((fromMs - offsetMs) / hop).toInt()
        return FloatArray((lengthMs / hop).toInt()) { i -> reference[first + i] * gain * (0.9f + 0.2f * random.nextFloat()) }
    }

    private fun alignAt(album: FloatArray, windowStartMs: Long, offsetMs: Long, gain: Float = 2.5f): AudioAlignment.Match? {
        val playing = anotherCut(album, windowStartMs, AudioAlignment.WINDOW_MS, offsetMs, gain)
        val referenceStart = (windowStartMs - AudioAlignment.MAX_OFFSET_MS).coerceAtLeast(0)
        val referenceEnd = windowStartMs + AudioAlignment.WINDOW_MS - AudioAlignment.MIN_OFFSET_MS
        val reference = album.copyOfRange((referenceStart / hop).toInt(), (referenceEnd / hop).toInt())
        return AudioAlignment.align(
            AudioAlignment.onsets(playing), windowStartMs,
            AudioAlignment.onsets(reference), referenceStart,
            AudioAlignment.MIN_OFFSET_MS, AudioAlignment.MAX_OFFSET_MS,
        )
    }

    @Test
    fun `onsets mark rises in loudness, not falls`() {
        val onsets = AudioAlignment.onsets(floatArrayOf(0.01f, 0.01f, 1f, 0.5f, 0.1f))
        assertEquals(0f, onsets[1], 0f)
        assertTrue(onsets[2] > 3f)
        assertEquals(0f, onsets[3], 0f)
        assertEquals(0f, onsets[4], 0f)
    }

    @Test
    fun `finds a music video's intro, whatever its loudness`() {
        val album = music(200, seed = 1)
        for (offset in listOf(22_650L, 6_100L, 40L, -12_000L)) {
            val match = alignAt(album, windowStartMs = 90_000, offsetMs = offset)!!
            assertEquals("offset $offset", offset, match.offsetMs)
            assertTrue("score ${match.score}", match.score > 0.9f)
        }
    }

    @Test
    fun `different music doesn't line up`() {
        val playing = AudioAlignment.onsets(music(30, seed = 2))
        val album = AudioAlignment.onsets(music(110, seed = 3))
        val match = AudioAlignment.align(playing, 60_000, album, 0, AudioAlignment.MIN_OFFSET_MS, AudioAlignment.MAX_OFFSET_MS)!!
        assertTrue("score ${match.score}", match.score < AudioAlignment.MIN_SCORE)
        assertNull(AudioAlignment.decide(listOf(match, match.copy(offsetMs = match.offsetMs + 5_000))))
    }

    @Test
    fun `silence can't be lined up`() {
        val silence = FloatArray(3000)
        assertNull(AudioAlignment.align(silence, 30_000, AudioAlignment.onsets(music(110, 4)), 0, -20_000, 60_000))
    }

    @Test
    fun `two agreeing windows are trusted, disagreeing ones are not`() {
        val a = AudioAlignment.Match(offsetMs = 22_650, score = 0.57f, runnerUp = 0.44f)
        // The stronger of the two windows gives the answer.
        assertEquals(22_630L, AudioAlignment.decide(listOf(a, a.copy(offsetMs = 22_630, score = 0.74f))))
        assertNull(AudioAlignment.decide(listOf(a, a.copy(offsetMs = 21_790))))
        assertNull(AudioAlignment.decide(listOf(a, a.copy(score = 0.2f))))
        assertNull(AudioAlignment.decide(listOf(a, null)))
        assertNull(AudioAlignment.decide(emptyList()))
    }

    @Test
    fun `a lone window must stand out on its own`() {
        assertEquals(500L, AudioAlignment.decide(listOf(AudioAlignment.Match(500, 0.8f, 0.4f))))
        assertNull(AudioAlignment.decide(listOf(AudioAlignment.Match(500, 0.8f, 0.75f))))
        assertNull(AudioAlignment.decide(listOf(AudioAlignment.Match(500, 0.4f, 0.1f))))
    }

    @Test
    fun `windows skip the intro and follow the listener`() {
        assertEquals(listOf(30_000L, 75_000L), AudioAlignment.windowStarts(positionMs = 0, durationMs = 263_000))
        assertEquals(listOf(140_000L, 185_000L), AudioAlignment.windowStarts(positionMs = 200_000, durationMs = 263_000))
        // Near the end the windows slide back so both still fit.
        assertEquals(listOf(188_000L, 233_000L), AudioAlignment.windowStarts(positionMs = 250_000, durationMs = 263_000))
        assertEquals(listOf(30_000L, 75_000L), AudioAlignment.windowStarts(positionMs = 0, durationMs = 0))
    }

    @Test
    fun `short tracks get fewer windows`() {
        assertEquals(listOf(15_000L, 60_000L), AudioAlignment.windowStarts(0, 90_000))
        assertEquals(listOf(30_000L), AudioAlignment.windowStarts(0, 60_000))
        assertEquals(listOf(10_000L), AudioAlignment.windowStarts(0, 40_000))
        assertTrue(AudioAlignment.windowStarts(0, 20_000).isEmpty())
    }

    @Test
    fun `a whole found offset works end to end`() {
        val album = music(260, seed = 5)
        val matches = AudioAlignment.windowStarts(0, 263_000).map { alignAt(album, it, offsetMs = 21_160) }
        matches.forEach { assertNotNull(it) }
        assertEquals(21_160L, AudioAlignment.decide(matches))
    }
}
