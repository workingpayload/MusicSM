package com.example.musicsm.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionArtworkTest {

    @Test
    fun `square video in square box is not scaled`() {
        assertEquals(Scale(1f, 1f), cropScaleFor(videoAspect = 1f, boxAspect = 1f))
    }

    @Test
    fun `wide video in tall box is widened so the sides crop`() {
        // 16:9 inside a 9:16 phone screen: the width has to grow by the full aspect gap.
        val scale = cropScaleFor(videoAspect = 16f / 9f, boxAspect = 9f / 16f)
        assertEquals(1f, scale.y, 0.0001f)
        assertEquals((16f / 9f) / (9f / 16f), scale.x, 0.0001f)
    }

    @Test
    fun `tall video in wide box is heightened so the top and bottom crop`() {
        val scale = cropScaleFor(videoAspect = 9f / 16f, boxAspect = 16f / 9f)
        assertEquals(1f, scale.x, 0.0001f)
        assertEquals((16f / 9f) / (9f / 16f), scale.y, 0.0001f)
    }

    @Test
    fun `square video in a phone screen grows horizontally only`() {
        // A 1:1 video stretched into a 1:2 box is too narrow, so the width doubles and spills.
        val scale = cropScaleFor(videoAspect = 1f, boxAspect = 0.5f)
        assertEquals(2f, scale.x, 0.0001f)
        assertEquals(1f, scale.y, 0.0001f)
    }

    /** Both sizes arrive asynchronously, so the identity keeps the view stable until they do. */
    @Test
    fun `unknown sizes fall back to no scaling`() {
        assertEquals(Scale(1f, 1f), cropScaleFor(videoAspect = 0f, boxAspect = 1.5f))
        assertEquals(Scale(1f, 1f), cropScaleFor(videoAspect = 1.5f, boxAspect = 0f))
        assertEquals(Scale(1f, 1f), cropScaleFor(videoAspect = -1f, boxAspect = -1f))
    }

    /** Scaling below 1 would letterbox rather than crop, leaving bars the fill is meant to avoid. */
    @Test
    fun `scale never shrinks the video`() {
        val cases = listOf(0.3f to 1.9f, 1.9f to 0.3f, 1f to 1.7f, 2.4f to 1f, 1.33f to 1.34f)
        for ((video, box) in cases) {
            val scale = cropScaleFor(video, box)
            assertTrue("x shrank for $video in $box", scale.x >= 1f)
            assertTrue("y shrank for $video in $box", scale.y >= 1f)
        }
    }
}
