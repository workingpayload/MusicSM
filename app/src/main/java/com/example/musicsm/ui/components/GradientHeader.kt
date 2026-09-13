package com.example.musicsm.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.musicsm.ui.theme.AppBackground
import kotlin.math.abs

/** Vertical gradient from an accent color down into the app background, for screen headers. */
fun gradientBrush(accent: Color): Brush = Brush.verticalGradient(
    0.0f to accent.copy(alpha = 0.85f),
    0.5f to accent.copy(alpha = 0.35f),
    1.0f to AppBackground,
)

/** Deterministic vibrant accent color derived from a string (e.g. a song/album id or title). */
fun accentColorFor(seed: String?): Color {
    if (seed.isNullOrEmpty()) return Color(0xFF3A3A3A)
    val palette = listOf(
        0xFF1E3264, 0xFF8D67AB, 0xFFBA5D07, 0xFFE13300, 0xFF27856A,
        0xFF503750, 0xFFDC148C, 0xFF477D95, 0xFF7358FF, 0xFF608108,
    )
    val idx = abs(seed.hashCode()) % palette.size
    return Color(palette[idx])
}
