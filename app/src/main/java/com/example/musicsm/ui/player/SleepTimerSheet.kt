package com.example.musicsm.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.playback.SleepTimerState
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceLow

/** Remaining sleep-timer time as "M:SS", or "12 min" style for longer waits. */
@Composable
fun formatSleepRemaining(state: SleepTimerState): String = when {
    !state.isActive -> ""
    state.endOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
    else -> {
        val totalSeconds = (state.remainingMs + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "%d:%02d".format(minutes, seconds)
    }
}

/** Sleep-timer picker: preset durations, "end of track", and a cancel action when running. */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onPick: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Coral, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sleep_timer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDark,
                    )
                    Text(
                        if (state.isActive) {
                            stringResource(R.string.sleep_timer_stops_in, formatSleepRemaining(state))
                        } else {
                            stringResource(R.string.sleep_timer_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkVariant,
                    )
                }
                if (state.isActive) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.sleep_timer_cancel),
                        tint = OnDarkVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                onCancel()
                                onDismiss()
                            }
                            .padding(6.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PRESETS.forEach { minutes ->
                    TimerChip(label = pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes), selected = false) {
                        onPick(minutes)
                        onDismiss()
                    }
                }
                TimerChip(label = stringResource(R.string.sleep_timer_end_of_track), selected = state.endOfTrack) {
                    onEndOfTrack()
                    onDismiss()
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TimerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) OnAccent else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Coral else OverlayTint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

private val PRESETS = listOf(5, 15, 30, 45, 60, 90)
