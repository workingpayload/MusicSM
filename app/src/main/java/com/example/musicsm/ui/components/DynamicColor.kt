package com.example.musicsm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads [url] via Coil and extracts a vibrant dominant color for artwork-driven theming
 * (Apple Music-style). Smoothly animates between tracks and falls back to [fallback].
 */
/**
 * [State]-returning variant. Read the returned state's value inside a draw-phase lambda
 * (e.g. [androidx.compose.ui.draw.drawBehind]) so the color animation only re-draws instead
 * of recomposing the caller — important for large scrollable screens.
 */
@Composable
fun rememberDominantColorState(url: String?, fallback: Color): State<Color> {
    val context = LocalPlatformContext.current
    var target by remember { mutableStateOf(fallback) }

    LaunchedEffect(url) {
        if (url.isNullOrEmpty()) {
            target = fallback
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false) // Palette needs readable pixels
                    .size(200)
                    .build()
                val result = loader.execute(request)
                val bitmap = (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap
                bitmap?.let { Palette.from(it).clearFilters().generate() }
            }.getOrNull()
        }
        val swatch = extracted?.let {
            it.vibrantSwatch ?: it.dominantSwatch ?: it.darkVibrantSwatch ?: it.mutedSwatch
        }
        target = swatch?.let { Color(it.rgb) } ?: fallback
    }

    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "dominantColor",
    )
}

/** Convenience wrapper that reads the animated value in composition. Prefer
 * [rememberDominantColorState] + a draw-phase read on large/scrollable screens. */
@Composable
fun rememberDominantColor(url: String?, fallback: Color): Color =
    rememberDominantColorState(url, fallback).value
