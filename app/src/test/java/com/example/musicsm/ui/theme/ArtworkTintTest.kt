package com.example.musicsm.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArtworkTintTest {

    private fun Color.hsl() = toHsl()

    @Test
    fun `a washed out cover colour is deepened enough to carry white text`() {
        val pale = Color(0xFFF3E7DC)
        val hsl = pale.asDeepTint().hsl()
        assertTrue("saturation ${hsl[1]}", hsl[1] >= 0.27f)
        assertTrue("lightness ${hsl[2]}", hsl[2] <= 0.35f)
    }

    @Test
    fun `a colour that is already deep is left where it is`() {
        val deep = Color(0xFF3A1F5C)
        val before = deep.hsl()
        val after = deep.asDeepTint().hsl()
        assertEquals(before[0], after[0], 0.01f)
        assertEquals(before[1], after[1], 0.01f)
        assertEquals(before[2], after[2], 0.01f)
    }

    @Test
    fun `deepening keeps the hue that makes the cover recognisable`() {
        val teal = Color(0xFF7FD8D0)
        assertEquals(teal.hsl()[0], teal.asDeepTint().hsl()[0], 0.01f)
    }

    @Test
    fun `a near black cover is lifted rather than left invisible`() {
        val almostBlack = Color(0xFF0A0510)
        val lifted = almostBlack.asDeepTint().hsl()[2]
        assertTrue("lightness $lifted", lifted > almostBlack.hsl()[2])
        assertEquals(0.14f, lifted, 0.01f)
    }

    @Test
    fun `grey is recognised as having no hue worth theming from`() {
        assertTrue(Color(0xFF808080).isHueless())
        assertTrue(Color.Black.isHueless())
        assertTrue(Color.White.isHueless())
    }

    @Test
    fun `a faint but real tint is not mistaken for grey`() {
        assertFalse(Color(0xFF6E5A8C).isHueless())
    }

    @Test
    fun `text on a dark tint comes out light`() {
        val onDark = Color(0xFF241238).onTint()
        assertTrue(onDark.hsl()[2] > 0.8f)
    }

    @Test
    fun `text on a light tint comes out dark`() {
        val onLight = Color(0xFFEFD9C0).onTint()
        assertTrue(onLight.hsl()[2] < 0.2f)
    }

    @Test
    fun `emphasis separates a heading from the body text under it`() {
        val tint = Color(0xFF241238)
        val heading = tint.onTint(emphasis = 1f).hsl()
        val body = tint.onTint(emphasis = 0f).hsl()
        assertTrue("heading should carry more hue", heading[1] > body[1])
        assertTrue("heading should sit off the extreme", heading[2] < body[2])
    }

    @Test
    fun `text on a grey tint is plain rather than dirty`() {
        assertEquals(0f, Color(0xFF3C3C3C).onTint(emphasis = 1f).hsl()[1], 0.001f)
    }

    @Test
    fun `hsl survives a round trip`() {
        listOf(0xFF7FD8D0, 0xFF3A1F5C, 0xFFF3E7DC, 0xFF000000, 0xFFFFFFFF, 0xFFFF0000)
            .map { Color(it) }
            .forEach { original ->
                val hsl = original.hsl()
                val back = hslColor(hsl[0], hsl[1], hsl[2])
                assertTrue(
                    "round trip of $original gave $back",
                    abs(original.red - back.red) < 0.01f &&
                        abs(original.green - back.green) < 0.01f &&
                        abs(original.blue - back.blue) < 0.01f,
                )
            }
    }
}
