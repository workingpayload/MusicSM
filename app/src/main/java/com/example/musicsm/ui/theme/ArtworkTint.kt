package com.example.musicsm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour maths for artwork-driven theming.
 *
 * HSL is converted here rather than through `androidx.core.graphics.ColorUtils` so the whole file
 * stays plain arithmetic on Compose [Color]s. That keeps it unit-testable on the JVM, which matters
 * because every rule in it is a judgement about how a screen should look and is therefore exactly
 * the kind of thing that gets quietly broken later.
 */

/**
 * Turns a colour sampled from artwork into one a whole screen can be painted with.
 *
 * A dominant colour is picked for being *present*, not for being usable: pale covers give back
 * near-white, photographic ones give back skin tone or sky, and monochrome sleeves give back grey.
 * Painted straight onto a background, all three read as a mistake rather than as theming. Clamping
 * saturation up and lightness down keeps the artwork's hue — the part the eye reads as "this is
 * that album" — while making it deep enough to put white text on.
 */
fun Color.asDeepTint(): Color {
    val hsl = toHsl()
    return hslColor(
        hue = hsl[0],
        saturation = hsl[1].coerceAtLeast(DEEP_TINT_MIN_SATURATION),
        lightness = hsl[2].coerceIn(DEEP_TINT_MIN_LIGHTNESS, DEEP_TINT_MAX_LIGHTNESS),
        alpha = alpha,
    )
}

/**
 * Whether this colour carries too little hue to theme from.
 *
 * Greys, blacks and near-whites do have a hue, but it is noise — a black-and-white sleeve whose
 * pixels lean a fraction blue will theme the screen blue, which reads as a bug rather than as a
 * choice. Callers use this to leave the default palette alone instead of deriving a worse one.
 */
fun Color.isHueless(): Boolean = toHsl()[1] < HUELESS_SATURATION

/**
 * A readable foreground for content sitting on [this] tint.
 *
 * Keeps a trace of the tint's hue rather than returning flat white or flat black, so text reads as
 * belonging to the artwork behind it. [emphasis] separates the roles: a title can carry more of the
 * colour than body text, which mostly needs to get out of the way and be legible.
 */
fun Color.onTint(emphasis: Float = 0f): Color {
    val hsl = toHsl()
    val onDark = hsl[2] <= ON_DARK_LIGHTNESS
    val e = emphasis.coerceIn(0f, 1f)
    val lightness = if (onDark) {
        ON_TINT_LIGHT - e * ON_TINT_LIGHT_EMPHASIS_DROP
    } else {
        ON_TINT_DARK + e * ON_TINT_DARK_EMPHASIS_RISE
    }
    // A grey tint has no hue worth keeping, so tinted text on it would just look dirty. Hand back
    // the plain extreme and let emphasis separate the roles by lightness alone.
    val saturation = if (hsl[1] < HUELESS_SATURATION) {
        0f
    } else {
        hsl[1].coerceAtMost(ON_TINT_BASE_SATURATION + e * ON_TINT_SATURATION_RANGE)
    }
    return hslColor(hsl[0], saturation, lightness, alpha)
}

/** `[hue 0..1, saturation 0..1, lightness 0..1]`. */
internal fun Color.toHsl(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, lightness)

    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        red -> (green - blue) / delta + if (green < blue) 6f else 0f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } / 6f
    return floatArrayOf(hue, saturation, lightness)
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    if (saturation == 0f) return Color(lightness, lightness, lightness, alpha)
    val q = if (lightness < 0.5f) {
        lightness * (1f + saturation)
    } else {
        lightness + saturation - lightness * saturation
    }
    val p = 2f * lightness - q
    return Color(
        red = hueToChannel(p, q, hue + 1f / 3f),
        green = hueToChannel(p, q, hue),
        blue = hueToChannel(p, q, hue - 1f / 3f),
        alpha = alpha,
    )
}

private fun hueToChannel(p: Float, q: Float, rawT: Float): Float {
    var t = rawT
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }.coerceIn(0f, 1f)
}

/** Below this the colour is washed out enough that white text over it is uncomfortable. */
private const val DEEP_TINT_MIN_SATURATION = 0.28f

/** Dark enough to be a background, not so dark that different covers stop being distinguishable. */
private const val DEEP_TINT_MIN_LIGHTNESS = 0.14f
private const val DEEP_TINT_MAX_LIGHTNESS = 0.34f

private const val HUELESS_SATURATION = 0.08f

private const val ON_DARK_LIGHTNESS = 0.5f
private const val ON_TINT_LIGHT = 0.96f
private const val ON_TINT_DARK = 0.10f
private const val ON_TINT_LIGHT_EMPHASIS_DROP = 0.08f
private const val ON_TINT_DARK_EMPHASIS_RISE = 0.08f
private const val ON_TINT_BASE_SATURATION = 0.16f
private const val ON_TINT_SATURATION_RANGE = 0.39f
