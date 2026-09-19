package com.example.musicsm.ui.importer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.OnDarkVariant

@Composable
fun ImportPlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var link by remember { mutableStateOf("") }
    BackHandler { onBack() }

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row64 {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(OverlayTint.copy(alpha = 0.05f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.action_back), tint = OnDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnDark)
        }

        when (val s = state) {
            is ImportUiState.Idle, is ImportUiState.Error -> Column(Modifier.padding(20.dp)) {
                Text(
                    "Paste a public Spotify playlist link. Each track is matched on YouTube and saved to your library.",
                    color = OnDarkVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.import_link_hint)) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = OverlayTint.copy(alpha = 0.06f),
                        unfocusedContainerColor = OverlayTint.copy(alpha = 0.06f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                if (s is ImportUiState.Error) {
                    Spacer(Modifier.height(10.dp))
                    Text(s.message, color = Coral, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                PillButton(stringResource(R.string.import_action), enabled = link.isNotBlank()) { viewModel.import(link) }
            }

            is ImportUiState.Importing -> Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaveBars(bars = 5, maxHeight = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.import_in_progress), color = OnDark, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    val pct = if (s.total > 0) (s.done * 100 / s.total) else 0
                    Text("$pct%", color = OnDarkVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                ModernProgressBar(
                    progress = if (s.total > 0) s.done.toFloat() / s.total else 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.total > 0) stringResource(R.string.import_progress, s.done, s.total) else stringResource(R.string.import_reading),
                    color = OnDarkVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.import_while_you_wait), color = OnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                DinoGame()
            }

            is ImportUiState.Done -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Imported \"${s.name}\"", color = OnDark, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("${s.matched} of ${s.total} tracks matched", color = OnDarkVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(20.dp))
                    PillButton(stringResource(R.string.import_another), enabled = true) {
                        link = ""
                        viewModel.reset()
                    }
                }
            }
        }
    }
}

@Composable
private fun Row64(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** A row of equalizer bars that wave up and down — a music-flavored loading indicator. */
@Composable
private fun WaveBars(
    bars: Int = 7,
    maxHeight: androidx.compose.ui.unit.Dp = 48.dp,
    minHeight: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        modifier = Modifier.height(maxHeight),
    ) {
        repeat(bars) { i ->
            // Each bar is offset along the sine so the row reads as a travelling wave.
            val t = (kotlin.math.sin(phase + i * 0.7f) + 1f) / 2f // 0..1
            val h = minHeight + (maxHeight - minHeight) * t
            Box(
                Modifier
                    .width(6.dp)
                    .height(h)
                    .clip(RoundedCornerShape(50))
                    .background(Coral),
            )
        }
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (enabled) Coral else Coral.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = OnAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}
