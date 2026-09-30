package com.example.musicsm.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake while this is in composition and [enabled]. Uses the view's own
 * `keepScreenOn` rather than the window flag, so it can't clear (or be cleared by) a screen that
 * manages `FLAG_KEEP_SCREEN_ON` itself, like Ambient mode.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    if (!enabled) return
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
