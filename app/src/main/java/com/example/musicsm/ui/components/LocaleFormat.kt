package com.example.musicsm.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The locale to format user-visible numbers and text with.
 *
 * Reads from [LocalConfiguration] rather than [Locale.getDefault] so that a per-app language
 * change (or a system locale change) recomposes and reformats everything, instead of leaving
 * stale text behind until the process restarts.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales.get(0) ?: fallbackLocale

/** `Configuration.getLocales()` is documented as never empty; this only guards against OEM bugs. */
private val fallbackLocale: Locale get() = Locale.getDefault()
