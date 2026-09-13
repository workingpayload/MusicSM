package com.example.musicsm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MusicSmColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Coral,
    onPrimaryContainer = Color.White,
    secondary = Lavender,
    onSecondary = Color(0xFF490080),
    secondaryContainer = PurpleContainer,
    onSecondaryContainer = Color(0xFFF0DBFF),
    tertiary = Teal,
    onTertiary = Color(0xFF00382F),
    background = StitchBackground,
    onBackground = OnSurfaceLight,
    surface = SurfaceLow,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceVariantPink,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    surfaceBright = SurfaceBright,
    outline = OutlinePink,
    outlineVariant = DividerColor,
)

/**
 * App theme. Always dark, matching the Stitch "Glassmorphic Music Streamer" design.
 * Dynamic color intentionally disabled.
 */
@Composable
fun MusicSMTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MusicSmColorScheme,
        typography = Typography,
        content = content,
    )
}
