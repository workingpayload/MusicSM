package com.example.musicsm.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    @Test
    fun `dark palette keeps the original stitch colours`() {
        // The default theme must stay pixel-identical to the pre-theming hardcoded scheme.
        assertEquals(Color(0xFF121318), DarkPalette.background)
        assertEquals(Color(0xFFFF525E), DarkPalette.accent)
        assertEquals(Color(0xFFE3E1E9), DarkPalette.onSurface)
        assertFalse(DarkPalette.isLight)
    }

    @Test
    fun `amoled variant blacks out the base tiers but keeps the accent`() {
        assertEquals(Color.Black, AmoledPalette.background)
        assertEquals(Color.Black, AmoledPalette.surfaceLowest)
        assertEquals(DarkPalette.accent, AmoledPalette.accent)
        assertNotEquals(Color.Black, AmoledPalette.surfaceHigh)
    }

    @Test
    fun `light palette inverts the overlay tint so hairlines stay visible`() {
        assertTrue(LightPalette.isLight)
        assertEquals(Color.White, DarkPalette.overlayTint)
        assertNotEquals(Color.White, LightPalette.overlayTint)
    }

    @Test
    fun `withAccent re-tints the accent family and leaves surfaces alone`() {
        val seed = Color(0xFF3DDC84)
        val themed = DarkPalette.withAccent(seed)

        assertEquals(seed, themed.accent)
        assertEquals(DarkPalette.background, themed.background)
        assertEquals(DarkPalette.surfaceHigh, themed.surfaceHigh)
        assertEquals(DarkPalette.onSurface, themed.onSurface)
    }

    @Test
    fun `withAccent darkens and lightens around the seed`() {
        val seed = Color(0xFF4DA3FF)
        val themed = DarkPalette.withAccent(seed)

        assertTrue("accentDark should be darker", themed.accentDark.red < seed.red)
        assertTrue("accentLight should be lighter", themed.accentLight.red > seed.red)
    }

    @Test
    fun `on-accent content flips to dark text for pale accents`() {
        assertEquals(Color.White, DarkPalette.withAccent(Color(0xFF1E3264)).onAccent)
        assertNotEquals(Color.White, DarkPalette.withAccent(Color(0xFFFFC53D)).onAccent)
    }

    @Test
    fun `luminance test matches the extremes`() {
        assertTrue(Color.Black.isDarkEnoughForWhiteText())
        assertFalse(Color.White.isDarkEnoughForWhiteText())
        // The stock coral is dark enough to carry white labels — the app relies on this.
        assertTrue(Color(0xFFFF525E).isDarkEnoughForWhiteText())
    }

    @Test
    fun `theme mode parsing is forgiving and defaults to system`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("nonsense"))
    }

    @Test
    fun `accent presets are unique so the picker cannot show duplicates`() {
        assertEquals(AccentPresets.size, AccentPresets.toSet().size)
    }
}
