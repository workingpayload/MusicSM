package com.example.musicsm.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** True while the Now-Playing sheet is (even partly) expanded over the nav host. */
val LocalPlayerExpanded = compositionLocalOf { false }

/**
 * Back handler for a nav destination. Back callbacks fire newest-first, so a screen navigated to
 * after the player sheet appeared would otherwise outrank the sheet's collapse handler and pop
 * itself from behind the expanded player. Standing down while the sheet is open fixes that.
 */
@Composable
fun ScreenBackHandler(onBack: () -> Unit) {
    BackHandler(enabled = !LocalPlayerExpanded.current, onBack = onBack)
}
