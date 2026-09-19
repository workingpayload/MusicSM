package com.example.musicsm.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.currentLocale
import com.example.musicsm.ui.theme.AccentPresets
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.ThemeMode
import com.example.musicsm.ui.theme.isDarkEnoughForWhiteText
import com.example.musicsm.ui.theme.SurfaceLow
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val recentSearchCount by viewModel.recentSearchCount.collectAsStateWithLifecycle()
    var confirmClearDownloads by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "\u2013"
    }

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(OverlayTint.copy(alpha = 0.05f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.action_back), tint = OnDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnDark)
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp + LocalBottomBarPadding.current)) {
            item { SettingsSection(stringResource(R.string.settings_section_playback)) }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_resume_title),
                    subtitle = stringResource(R.string.settings_resume_subtitle),
                    checked = ui.restoreQueue,
                    onCheckedChange = viewModel::setRestoreQueue,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_autoplay_title),
                    subtitle = stringResource(R.string.settings_autoplay_subtitle),
                    checked = ui.autoplayRadio,
                    onCheckedChange = viewModel::setAutoplayRadio,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_skip_silence_title),
                    subtitle = stringResource(R.string.settings_skip_silence_subtitle),
                    checked = ui.skipSilence,
                    onCheckedChange = viewModel::setSkipSilence,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_fade_out_title),
                    subtitle = stringResource(R.string.settings_fade_out_subtitle),
                    checked = ui.sleepTimerFadeOut,
                    onCheckedChange = viewModel::setSleepTimerFadeOut,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_audio)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_equalizer_title),
                    subtitle = stringResource(R.string.settings_equalizer_subtitle),
                    onClick = onOpenEqualizer,
                )
            }
            item {
                val locale = currentLocale()
                SettingsSlider(
                    title = stringResource(R.string.settings_speed_title),
                    subtitle = stringResource(
                        R.string.settings_speed_subtitle,
                        String.format(locale, "%.2f", ui.playbackSpeed).trimEnd('0').trimEnd('.'),
                    ),
                    value = ui.playbackSpeed,
                    valueRange = AppPreferences.MIN_SPEED..AppPreferences.MAX_SPEED,
                    // 0.05x increments across the 0.5x - 2.0x range.
                    steps = 29,
                    onValueChange = viewModel::setPlaybackSpeed,
                )
            }
            item {
                val seconds = ui.crossfadeMs / 1000f
                val locale = currentLocale()
                SettingsSlider(
                    title = stringResource(R.string.settings_crossfade_title),
                    subtitle = if (ui.crossfadeMs == 0) {
                        stringResource(R.string.settings_crossfade_off)
                    } else {
                        stringResource(
                            R.string.settings_crossfade_on,
                            String.format(locale, "%.1f", seconds).trimEnd('0').trimEnd('.'),
                        )
                    },
                    value = seconds,
                    valueRange = 0f..(AppPreferences.MAX_CROSSFADE_MS / 1000f),
                    // Half-second increments from 0 to 12s.
                    steps = 23,
                    onValueChange = { viewModel.setCrossfadeMs((it * 1000).roundToInt()) },
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_appearance)) }
            item {
                ThemeModePicker(
                    selected = ui.appearance.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_amoled_title),
                    subtitle = stringResource(R.string.settings_amoled_subtitle),
                    checked = ui.appearance.amoled,
                    enabled = ui.appearance.themeMode != ThemeMode.LIGHT,
                    onCheckedChange = viewModel::setAmoled,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_material_you_title),
                        subtitle = stringResource(R.string.settings_material_you_subtitle),
                        checked = ui.appearance.materialYou,
                        onCheckedChange = viewModel::setMaterialYou,
                    )
                }
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_artwork_theme_title),
                    subtitle = stringResource(R.string.settings_artwork_theme_subtitle),
                    checked = ui.appearance.themeFromArtwork,
                    onCheckedChange = viewModel::setThemeFromArtwork,
                )
            }
            item {
                AccentPicker(
                    selected = ui.appearance.accentColor,
                    enabled = ui.appearance.accentPickerEnabled,
                    onSelect = viewModel::setAccentColor,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_downloads)) }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_wifi_only_title),
                    subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                    checked = ui.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnlyDownloads,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_storage_title),
                    subtitle = stringResource(
                        R.string.settings_storage_subtitle,
                        pluralStringResource(R.plurals.track_count, downloadCount, downloadCount),
                        formatBytes(storageBytes),
                    ),
                    onClick = viewModel::refreshStorage,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_remove_all_title),
                    subtitle = stringResource(R.string.settings_remove_all_subtitle, formatBytes(storageBytes)),
                    destructive = true,
                    onClick = { confirmClearDownloads = true },
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_privacy)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_clear_history_title),
                    subtitle = if (recentSearchCount == 0) {
                        stringResource(R.string.settings_clear_history_empty)
                    } else {
                        pluralStringResource(R.plurals.saved_search_count, recentSearchCount, recentSearchCount)
                    },
                    onClick = viewModel::clearSearchHistory,
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    SettingsRow(
                        title = stringResource(R.string.settings_language_title),
                        subtitle = stringResource(R.string.settings_language_subtitle),
                        onClick = { openLanguageSettings(context) },
                    )
                }
            }

            item { SettingsSection(stringResource(R.string.settings_section_about)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.settings_version, versionName),
                    onClick = null,
                )
            }
        }
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text(stringResource(R.string.settings_remove_all_confirm_title)) },
            text = { Text(stringResource(R.string.settings_remove_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        confirmClearDownloads = false
                    },
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Opens the system per-app language screen (Android 13+). */
private fun openLanguageSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(currentLocale()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Coral,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Coral),
        )
    }
}

/** Segmented System / Light / Dark selector. */@Composable
private fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Coral else OverlayTint.copy(alpha = 0.08f))
                        .clickable { onSelect(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels.getValue(mode),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) OnAccent else OnDarkVariant,
                    )
                }
            }
        }
    }
}

/** Swatch grid for the accent override, with a "default" entry that clears it. */
@Composable
private fun AccentPicker(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Text(
            stringResource(R.string.settings_accent_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Text(
            stringResource(
                if (enabled) R.string.settings_accent_subtitle else R.string.settings_accent_locked,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Swatch(
                color = Coral,
                selected = selected == AppPreferences.NO_ACCENT,
                enabled = enabled,
                isDefault = true,
                onClick = { onSelect(AppPreferences.NO_ACCENT) },
            )
            AccentPresets.forEach { preset ->
                val argb = preset.toArgb()
                Swatch(
                    color = preset,
                    selected = selected == argb,
                    enabled = enabled,
                    isDefault = false,
                    onClick = { onSelect(argb) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.5.dp,
                color = if (selected) OnDark else OverlayTint.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isDefault) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.settings_accent_default),
                tint = OnAccent,
                modifier = Modifier.size(18.dp),
            )
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.isDarkEnoughForWhiteText()) Color.White else Color(0xFF1B1B1F),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) Coral else OnDark,
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = OnDark)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Coral)
                        .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                )
            },
            track = { sliderState ->
                val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
                val fraction = ((sliderState.value - valueRange.start) / span).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Coral, Lavender))),
                    )
                }
            },
        )
    }
}

/** Human-readable byte count, e.g. "142 MB". */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
