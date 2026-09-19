package com.example.musicsm.ui.settings

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.playback.EqualizerCapabilities
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.currentLocale
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.StitchBackground
import com.example.musicsm.ui.theme.SurfaceLow
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Per-band equalizer plus bass boost, virtualizer and loudness, all bound to the player's audio
 * session via `AudioEffectsManager`. Controls that the device doesn't implement are hidden
 * rather than shown broken.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val bands by viewModel.bands.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().background(StitchBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(OverlayTint.copy(alpha = 0.05f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.action_back),
                    tint = OnDark,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.equalizer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnDark,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.equalizer_reset),
                style = MaterialTheme.typography.labelLarge,
                color = Coral,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { viewModel.reset() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp + LocalBottomBarPadding.current)) {
            item {
                EffectSwitch(
                    title = stringResource(R.string.equalizer_enable_title),
                    subtitle = stringResource(R.string.equalizer_enable_subtitle),
                    checked = ui.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }

            if (!caps.available && !caps.bassBoostSupported && !caps.loudnessSupported) {
                item { UnavailableNotice() }
            }

            if (caps.available && caps.presetNames.isNotEmpty()) {
                item { EffectSection(stringResource(R.string.equalizer_section_presets)) }
                item {
                    PresetChips(
                        presets = caps.presetNames,
                        selected = ui.preset,
                        enabled = ui.enabled,
                        onSelect = viewModel::selectPreset,
                    )
                }
            }

            if (caps.available) {
                item { EffectSection(stringResource(R.string.equalizer_section_bands)) }
                itemsIndexedBands(caps, bands, ui.enabled, viewModel::setBand)
            }

            val hasExtras = caps.bassBoostSupported || caps.virtualizerSupported || caps.loudnessSupported
            if (hasExtras) {
                item { EffectSection(stringResource(R.string.equalizer_section_effects)) }
            }
            if (caps.bassBoostSupported) {
                item {
                    StrengthSlider(
                        label = stringResource(R.string.equalizer_bass_boost),
                        value = ui.bassBoost,
                        valueRange = 0..1000,
                        enabled = ui.enabled,
                        display = { percent(it, 1000) },
                        onChange = viewModel::setBassBoost,
                    )
                }
            }
            if (caps.virtualizerSupported) {
                item {
                    StrengthSlider(
                        label = stringResource(R.string.equalizer_virtualizer),
                        value = ui.virtualizer,
                        valueRange = 0..1000,
                        enabled = ui.enabled,
                        display = { percent(it, 1000) },
                        onChange = viewModel::setVirtualizer,
                    )
                }
            }
            if (caps.loudnessSupported) {
                item {
                    StrengthSlider(
                        label = stringResource(R.string.equalizer_loudness),
                        value = ui.loudnessMb,
                        valueRange = 0..AppPreferences.MAX_LOUDNESS_MB,
                        enabled = ui.enabled,
                        display = { formatDb(it) },
                        onChange = viewModel::setLoudness,
                    )
                }
                item { EffectHint(stringResource(R.string.equalizer_loudness_hint)) }
            }

            item { EffectSection(stringResource(R.string.equalizer_section_system)) }
            item {
                EffectRow(
                    title = stringResource(R.string.equalizer_system_title),
                    subtitle = stringResource(R.string.equalizer_system_subtitle),
                    onClick = { openSystemEqualizer(context) },
                )
            }
        }
    }
}

/** One slider per equalizer band, labelled with its centre frequency. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedBands(
    caps: EqualizerCapabilities,
    bands: List<Int>,
    enabled: Boolean,
    onChange: (Int, Int) -> Unit,
) {
    items(caps.bandFrequencies.size) { index ->
        StrengthSlider(
            label = formatFrequency(caps.bandFrequencies[index]),
            value = bands.getOrNull(index) ?: 0,
            valueRange = caps.minLevelMb..caps.maxLevelMb,
            enabled = enabled,
            display = { formatDb(it, signed = true) },
            onChange = { onChange(index, it) },
        )
    }
}

@Composable
private fun PresetChips(
    presets: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(
            label = stringResource(R.string.equalizer_preset_custom),
            active = selected == AppPreferences.CUSTOM_PRESET,
            enabled = enabled,
            onClick = { onSelect(AppPreferences.CUSTOM_PRESET) },
        )
        presets.forEachIndexed { index, name ->
            Chip(label = name, active = selected == index, enabled = enabled) { onSelect(index) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) OnAccent else OnDarkVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Coral else SurfaceLow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun StrengthSlider(
    label: String,
    value: Int,
    valueRange: IntRange,
    enabled: Boolean,
    display: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Coral, activeTrackColor = Coral),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            display(value),
            style = MaterialTheme.typography.labelMedium,
            color = OnDark,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
private fun EffectSection(title: String) {
    Text(
        title.uppercase(currentLocale()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Coral,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun EffectHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = OnDarkVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun UnavailableNotice() {
    Text(
        stringResource(R.string.equalizer_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = OnDarkVariant,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(16.dp),
    )
}

@Composable
private fun EffectSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
            colors = SwitchDefaults.colors(checkedTrackColor = Coral),
        )
    }
}

@Composable
private fun EffectRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = OnDark)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
    }
}

/** Hands the user off to whatever system/OEM effect panel is installed, if any. */
private fun openSystemEqualizer(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) String.format(Locale.getDefault(), "%.0fk", hz / 1000.0) else "$hz"

private fun formatDb(millibels: Int, signed: Boolean = false): String {
    val db = millibels / 100.0
    val pattern = if (signed && millibels > 0) "+%.1f dB" else "%.1f dB"
    return String.format(Locale.getDefault(), pattern, db)
}

private fun percent(value: Int, max: Int): String = "${value * 100 / max}%"

private const val DISABLED_ALPHA = 0.4f
