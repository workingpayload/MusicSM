package com.example.musicsm.ui.theme

import androidx.compose.ui.graphics.Color

// Palette from the Stitch "Glassmorphic Music Streamer" design (Material 3 dark).

// Base surfaces (deep blue-black + container tiers).
val StitchBackground = Color(0xFF121318)
val SurfaceLowest = Color(0xFF0D0E13)
val SurfaceLow = Color(0xFF1A1B21)
val SurfaceContainer = Color(0xFF1E1F25)
val SurfaceHigh = Color(0xFF292A2F)
val SurfaceHighest = Color(0xFF34343A)
val SurfaceBright = Color(0xFF38393F)

// Accents.
val Coral = Color(0xFFFF525E)       // primary-container — play buttons, glow, progress
val CoralDark = Color(0xFFD8323E)
val CoralLight = Color(0xFFFFB3B2)  // primary — highlights
val Teal = Color(0xFF00DFC1)        // tertiary — spatial/live accents
val Lavender = Color(0xFFDDB7FF)    // secondary
val PurpleContainer = Color(0xFF6F00BE)

// Foreground.
val OnSurfaceLight = Color(0xFFE3E1E9)
val OnSurfaceVariantPink = Color(0xFFE6BDBC)
val OutlinePink = Color(0xFFAD8887)

// Glass tokens — translucent fills + hairline strokes for frosted panels.
val GlassFill = Color(0x1FFFFFFF)
val GlassFillStrong = Color(0x40292A2F)
val GlassStroke = Color(0x33FFFFFF)
val GlassStrokeSoft = Color(0x1AFFFFFF)

// ---- Legacy names kept so existing references keep compiling; remapped to Stitch tokens. ----
val AppBackground = StitchBackground
val Surface1 = SurfaceLow
val SurfaceCard = SurfaceHigh
val SurfaceElevated = SurfaceContainer
val OnDark = OnSurfaceLight
val OnDarkVariant = OnSurfaceVariantPink
val OnDarkMuted = Color(0x80FFFFFF)
val DividerColor = Color(0x1FFFFFFF)
val AppleRed = Coral
val AppleRedDark = CoralDark
val SpotifyGreen = Coral
val SpotifyGreenDark = CoralDark
