package com.example.musicsm.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware colour tokens.
 *
 * These read from [LocalMusicSmPalette], so the same name resolves to the dark, AMOLED, light or
 * dynamically re-tinted value depending on the user's settings. They are `@Composable` getters,
 * which means they can only be read from a composable scope — inside a `DrawScope` or other
 * non-composable lambda, hoist the value out first:
 *
 * ```
 * val tint = OverlayTint
 * Canvas(Modifier) { drawRect(tint) }
 * ```
 */

// Base surfaces (deep blue-black + container tiers by default).
val StitchBackground: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.background
val SurfaceLowest: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceLowest
val SurfaceLow: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceLow
val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceContainer
val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceHigh
val SurfaceHighest: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceHighest
val SurfaceBright: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceBright

// Accents.
val Coral: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accent
val CoralDark: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accentDark
val CoralLight: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accentLight
val Teal: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.teal
val Lavender: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.lavender
val PurpleContainer: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.purpleContainer

// Foreground.
val OnSurfaceLight: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurface
val OnSurfaceVariantPink: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurfaceVariant
val OutlinePink: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.outline

// Glass tokens — translucent fills + hairline strokes for frosted panels.
val GlassFill: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassFill
val GlassFillStrong: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassFillStrong
val GlassStroke: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassStroke
val GlassStrokeSoft: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassStrokeSoft

/**
 * Tint for translucent overlays drawn on top of app surfaces — `OverlayTint.copy(alpha = 0.08f)`
 * is a hairline on dark themes and on light ones alike. Content drawn over *artwork* keeps using
 * literal `Color.White`, because artwork is always darkened by a scrim.
 */
val OverlayTint: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.overlayTint

/** Content colour on top of a filled [Coral] surface. */
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onAccent

// ---- Legacy names kept so existing references keep compiling; remapped to palette tokens. ----
val AppBackground: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.background
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceLow
val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceHigh
val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceContainer
val OnDark: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurface
val OnDarkVariant: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurfaceVariant
val OnDarkMuted: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurfaceMuted
val DividerColor: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.divider
val AppleRed: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accent
val AppleRedDark: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accentDark
val SpotifyGreen: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accent
val SpotifyGreenDark: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accentDark
