package com.example.musicsm.playback

import kotlin.math.roundToInt

internal fun systemVolumeFraction(index: Int, min: Int, max: Int, muted: Boolean): Float {
    if (muted || max <= min) return 0f
    return ((index.coerceIn(min, max) - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
}

internal fun systemVolumeIndex(fraction: Float, min: Int, max: Int): Int {
    if (max <= min) return min
    val clamped = fraction.coerceIn(0f, 1f)
    return (min + clamped * (max - min)).roundToInt().coerceIn(min, max)
}
