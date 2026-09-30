package com.example.musicsm.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroZoomTest {

    private val limit = 2000f

    @Test
    fun `no drag means no give`() {
        assertEquals(0f, rubberBand(0f, limit), 0.0001f)
    }

    @Test
    fun `the first few pixels track the finger almost exactly`() {
        val d = 4f
        assertTrue("got ${rubberBand(d, limit)}", rubberBand(d, limit) > d * 0.99f)
    }

    @Test
    fun `resistance grows the further you pull`() {
        val near = rubberBand(100f, limit) / 100f
        val far = rubberBand(1000f, limit) / 1000f
        assertTrue("near=$near far=$far", far < near)
    }

    @Test
    fun `pulling further always moves further`() {
        var previous = 0f
        var d = 10f
        while (d <= 5000f) {
            val current = rubberBand(d, limit)
            assertTrue("not monotonic at $d", current > previous)
            previous = current
            d += 10f
        }
    }

    @Test
    fun `the band never stretches past its ceiling`() {
        assertTrue(rubberBand(1_000_000f, limit) < limit / 0.55f)
    }

    @Test
    fun `dragging the other way gives the mirror result`() {
        assertEquals(-rubberBand(300f, limit), rubberBand(-300f, limit), 0.0001f)
    }

    @Test
    fun `a tighter band gives less for the same pull`() {
        assertTrue(
            rubberBand(300f, limit, stiffness = 0.9f) < rubberBand(300f, limit, stiffness = 0.3f),
        )
    }

    @Test
    fun `a container with no height cannot be pulled against`() {
        assertEquals(0f, rubberBand(300f, 0f), 0.0001f)
    }

    @Test
    fun `an untouched hero sits at its natural size`() {
        assertEquals(1f, heroScaleFor(0f, 600f), 0.0001f)
    }

    @Test
    fun `scrolling down does not shrink the hero`() {
        assertEquals(1f, heroScaleFor(-500f, 600f), 0.0001f)
    }

    @Test
    fun `a full pull reaches the ceiling and stops there`() {
        assertEquals(1.18f, heroScaleFor(600f, 600f), 0.0001f)
        assertEquals(1.18f, heroScaleFor(5000f, 600f), 0.0001f)
    }

    @Test
    fun `growth between rest and the ceiling is proportional`() {
        assertEquals(1.09f, heroScaleFor(300f, 600f), 0.0001f)
    }

    @Test
    fun `a zero length pull cannot divide by zero`() {
        assertEquals(1f, heroScaleFor(100f, 0f), 0.0001f)
    }
}
