package com.example.musicsm.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.E
import kotlin.math.sqrt

class BounceMathTest {

    private val height = 2_000f

    @Test
    fun `the pull is resisted more the further it goes and never reaches the container size`() {
        val small = BounceMath.rubberBand(100f, height)
        val big = BounceMath.rubberBand(1_000f, height)
        val huge = BounceMath.rubberBand(1_000_000f, height)
        assertTrue("small $small", small in 45f..100f)
        assertTrue("diminishing returns", big / 1_000f < small / 100f)
        assertTrue("huge $huge", huge < height)
    }

    @Test
    fun `pulling either way is symmetric`() {
        assertEquals(-BounceMath.rubberBand(300f, height), BounceMath.rubberBand(-300f, height), 0.001f)
        assertEquals(0f, BounceMath.rubberBand(0f, height), 0f)
    }

    @Test
    fun `inverse rubber band gives back the pull`() {
        for (pull in listOf(-2_500f, -40f, 1f, 350f, 4_000f)) {
            val shown = BounceMath.rubberBand(pull, height)
            assertEquals(pull, BounceMath.inverseRubberBand(shown, height), 0.05f)
        }
    }

    @Test
    fun `dragging the same way as the pull leaves it to overscroll`() {
        assertEquals(120f to 0f, BounceMath.relax(120f, 30f))
        assertEquals(0f to 0f, BounceMath.relax(0f, -30f))
    }

    @Test
    fun `dragging back first undoes the pull`() {
        assertEquals(90f to -30f, BounceMath.relax(120f, -30f))
        assertEquals(-90f to 30f, BounceMath.relax(-120f, 30f))
    }

    @Test
    fun `dragging back past the edge only uses up the pull and leaves the rest to scroll`() {
        assertEquals(0f to -120f, BounceMath.relax(120f, -200f))
        assertEquals(0f to -120f, BounceMath.relax(120f, -120f))
    }

    @Test
    fun `hard flings bounce no further than the cap`() {
        val max = height * BounceMath.MAX_BOUNCE_FRACTION * sqrt(BounceMath.STIFFNESS) * E.toFloat()
        assertEquals(max, BounceMath.capVelocity(50_000f, height), 0.01f)
        assertEquals(-max, BounceMath.capVelocity(-50_000f, height), 0.01f)
        assertEquals(800f, BounceMath.capVelocity(800f, height), 0f)
    }

    @Test
    fun `a fling that uses up its speed mid list does not bounce`() {
        assertEquals(0f, BounceMath.edgeHitVelocity(-4_000f, -4_000f), 0f)
        // Decay ends with a slow last frame reported as leftover.
        assertEquals(0f, BounceMath.edgeHitVelocity(-4_000f, -3_960f), 0f)
    }

    @Test
    fun `a fling that hits the edge bounces with the speed it had left`() {
        assertEquals(-2_500f, BounceMath.edgeHitVelocity(-4_000f, -1_500f), 0f)
        assertEquals(3_000f, BounceMath.edgeHitVelocity(3_000f, 0f), 0f)
    }

    @Test
    fun `an unmeasured container still bounces`() {
        assertTrue(BounceMath.rubberBand(200f, 0f) > 0f)
        assertTrue(BounceMath.capVelocity(5_000f, 0f) > 0f)
    }
}
