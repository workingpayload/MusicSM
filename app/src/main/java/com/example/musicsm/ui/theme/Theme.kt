package com.example.musicsm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** How the app decides between its light and dark palettes. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

/** User-controlled appearance settings, resolved once and handed to [MusicSMTheme]. */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    /** Collapse dark surfaces to pure black for OLED panels. */
    val amoled: Boolean = false,
    /** Seed the accent from the system wallpaper (Android 12+). */
    val materialYou: Boolean = false,
    /** Explicit accent override from the picker or current artwork; `null` keeps the default. */
    val accent: Color? = null,
)

private fun MusicSmPalette.toColorScheme() = if (isLight) {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = lavender,
        onSecondary = Color.White,
        secondaryContainer = purpleContainer,
        onSecondaryContainer = Color(0xFF29003F),
        tertiary = teal,
        onTertiary = Color.White,
        background = background,
        onBackground = onSurface,
        surface = surfaceLow,
        onSurface = onSurface,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        surfaceBright = surfaceBright,
        outline = outline,
        outlineVariant = divider,
    )
} else {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = lavender,
        onSecondary = Color(0xFF490080),
        secondaryContainer = purpleContainer,
        onSecondaryContainer = Color(0xFFF0DBFF),
        tertiary = teal,
        onTertiary = Color(0xFF00382F),
        background = background,
        onBackground = onSurface,
        surface = surfaceLow,
        onSurface = onSurface,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        surfaceBright = surfaceBright,
        outline = outline,
        outlineVariant = divider,
    )
}

/**
 * App theme, built on the Stitch "Glassmorphic Music Streamer" design.
 *
 * The dark palette is the default and is byte-identical to the original hardcoded scheme, so
 * leaving every appearance setting untouched keeps the app looking exactly as it did. Light,
 * AMOLED, Material You and the accent picker layer on top of that via [LocalMusicSmPalette].
 */
@Composable
fun MusicSMTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val palette = remember(dark, settings, supportsDynamic, context) {
        val base = when {
            !dark -> LightPalette
            settings.amoled -> AmoledPalette
            else -> DarkPalette
        }
        val seed = when {
            settings.accent != null -> settings.accent
            settings.materialYou && supportsDynamic -> {
                val scheme =
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                scheme.primary
            }
            else -> null
        }
        if (seed == null) base else base.withAccent(seed)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Light backgrounds need dark system-bar icons, and vice versa.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalMusicSmPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = Typography,
            content = content,
        )
    }
}
