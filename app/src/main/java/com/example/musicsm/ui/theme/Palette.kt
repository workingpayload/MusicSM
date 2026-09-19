package com.example.musicsm.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's bespoke colour tokens, resolved per theme.
 *
 * Material's [androidx.compose.material3.ColorScheme] does not cover the glassmorphic surfaces,
 * overlay tints and secondary accents this design needs, so they live here and are published
 * through [LocalMusicSmPalette]. Every token in `Color.kt` is a thin composable accessor over this
 * object, which means a screen can keep writing `Coral` or `SurfaceLow` and still follow the
 * active theme.
 */
@Immutable
data class MusicSmPalette(
    val isLight: Boolean,
    // Base surfaces (container tiers, low → high).
    val background: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val surfaceBright: Color,
    // Accents.
    val accent: Color,
    val accentDark: Color,
    val accentLight: Color,
    val teal: Color,
    val lavender: Color,
    val purpleContainer: Color,
    // Foreground.
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val onSurfaceMuted: Color,
    val divider: Color,
    // Glass tokens — translucent fills + hairline strokes for frosted panels.
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Color,
    val glassStrokeSoft: Color,
    /**
     * Tint used for translucent overlay fills drawn *on top of* app surfaces (hairline dividers,
     * chip backgrounds, shimmer). White on dark themes, black on light ones, so
     * `OverlayTint.copy(alpha = 0.08f)` reads correctly either way.
     */
    val overlayTint: Color,
    /** Content colour on top of a filled [accent] surface. */
    val onAccent: Color,
    /** Backdrop tint for the frosted-glass blur effect. */
    val hazeTint: Color,
)

/** The original Stitch "Glassmorphic Music Streamer" palette — the app's default look. */
val DarkPalette = MusicSmPalette(
    isLight = false,
    background = Color(0xFF121318),
    surfaceLowest = Color(0xFF0D0E13),
    surfaceLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceHigh = Color(0xFF292A2F),
    surfaceHighest = Color(0xFF34343A),
    surfaceBright = Color(0xFF38393F),
    accent = Color(0xFFFF525E),
    accentDark = Color(0xFFD8323E),
    accentLight = Color(0xFFFFB3B2),
    teal = Color(0xFF00DFC1),
    lavender = Color(0xFFDDB7FF),
    purpleContainer = Color(0xFF6F00BE),
    onSurface = Color(0xFFE3E1E9),
    onSurfaceVariant = Color(0xFFE6BDBC),
    outline = Color(0xFFAD8887),
    onSurfaceMuted = Color(0x80FFFFFF),
    divider = Color(0x1FFFFFFF),
    glassFill = Color(0x1FFFFFFF),
    glassFillStrong = Color(0x40292A2F),
    glassStroke = Color(0x33FFFFFF),
    glassStrokeSoft = Color(0x1AFFFFFF),
    overlayTint = Color.White,
    onAccent = Color.White,
    hazeTint = Color(0x1AFFFFFF),
)

/** Pure-black variant for OLED panels: only the *base* tiers collapse to black. */
val AmoledPalette = DarkPalette.copy(
    background = Color.Black,
    surfaceLowest = Color.Black,
    surfaceLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF121214),
    surfaceHigh = Color(0xFF1C1C1F),
    surfaceHighest = Color(0xFF26262A),
    surfaceBright = Color(0xFF2B2B30),
    glassFillStrong = Color(0x401C1C1F),
)

/** Light counterpart, tuned to keep the warm coral identity. */
val LightPalette = MusicSmPalette(
    isLight = true,
    background = Color(0xFFFDF8F8),
    surfaceLowest = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F0F0),
    surfaceHigh = Color(0xFFF1E8E8),
    surfaceHighest = Color(0xFFEAE0E0),
    surfaceBright = Color(0xFFFFFBFB),
    accent = Color(0xFFD1263A),
    accentDark = Color(0xFFA5102A),
    accentLight = Color(0xFF8C1226),
    teal = Color(0xFF00695C),
    lavender = Color(0xFF7231A8),
    purpleContainer = Color(0xFFEDDCFF),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF574144),
    outline = Color(0xFF85736F),
    onSurfaceMuted = Color(0x991B1B1F),
    divider = Color(0x1F000000),
    glassFill = Color(0x14000000),
    glassFillStrong = Color(0x66FFFFFF),
    glassStroke = Color(0x26000000),
    glassStrokeSoft = Color(0x14000000),
    overlayTint = Color(0xFF1B1B1F),
    onAccent = Color.White,
    hazeTint = Color(0x26FFFFFF),
)

/**
 * Re-tints a palette around [seed] while keeping its surface tiers.
 *
 * Used by both the accent picker and Material You: the wallpaper/artwork only drives the accent
 * family, so the glassmorphic surfaces stay recognisably MusicSM.
 */
fun MusicSmPalette.withAccent(seed: Color): MusicSmPalette = copy(
    accent = seed,
    accentDark = seed.shade(if (isLight) 0.28f else 0.16f),
    accentLight = if (isLight) seed.shade(0.45f) else seed.tint(0.55f),
    onAccent = if (seed.isDarkEnoughForWhiteText()) Color.White else Color(0xFF1B1B1F),
)

/** Mixes [this] toward black by [amount]. */
private fun Color.shade(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

/** Mixes [this] toward white by [amount]. */
private fun Color.tint(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

/** Relative luminance test (WCAG-ish) deciding whether white or near-black content reads better. */
fun Color.isDarkEnoughForWhiteText(): Boolean {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(
        ((c + 0.055f) / 1.055f).toDouble(), 2.4,
    ).toFloat()
    val luminance = 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
    return luminance < 0.45f
}

/**
 * Accent presets offered by the settings picker. The first entry means "use the palette default",
 * which is what keeps the stock coral identity when nothing has been chosen.
 */
val AccentPresets: List<Color> = listOf(
    Color(0xFFFF525E), // Coral (default)
    Color(0xFFFF8A3D), // Ember
    Color(0xFFFFC53D), // Amber
    Color(0xFF3DDC84), // Mint
    Color(0xFF00DFC1), // Teal
    Color(0xFF4DA3FF), // Azure
    Color(0xFF8B7CFF), // Indigo
    Color(0xFFDDB7FF), // Lavender
    Color(0xFFFF6FD8), // Magenta
)

val LocalMusicSmPalette = staticCompositionLocalOf { DarkPalette }
