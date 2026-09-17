package com.example.musicsm.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

private val Coral = Color(0xFFFF525E)
private val Surface = Color(0xFF1A1B21)
private val OnDarkVariant = Color(0xFFA9AAB4)

@Composable
fun MusicSmWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Coral,
            onPrimary = Color.White,
            surface = Surface,
            onSurface = Color.White,
            background = Color.Black,
            onBackground = Color.White,
        ),
        content = content,
    )
}

/** Remote control for whatever the phone is playing. */
@Composable
fun WearPlayerScreen(
    state: WearPlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenOnPhone: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.nothing_playing) },
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.artist.isNotBlank()) {
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.caption1,
                    color = OnDarkVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TransportButton(
                    iconRes = R.drawable.ic_prev,
                    descriptionRes = R.string.cd_previous,
                    enabled = state.hasTrack,
                    size = 40.dp,
                    onClick = onPrevious,
                )
                Spacer(Modifier.width(8.dp))
                TransportButton(
                    iconRes = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    descriptionRes = R.string.cd_play_pause,
                    enabled = state.hasTrack,
                    size = 56.dp,
                    primary = true,
                    onClick = onPlayPause,
                )
                Spacer(Modifier.width(8.dp))
                TransportButton(
                    iconRes = R.drawable.ic_next,
                    descriptionRes = R.string.cd_next,
                    enabled = state.hasTrack,
                    size = 40.dp,
                    onClick = onNext,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onOpenOnPhone,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(width = 120.dp, height = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.open_on_phone),
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TransportButton(
    iconRes: Int,
    descriptionRes: Int,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (primary) {
            ButtonDefaults.primaryButtonColors()
        } else {
            ButtonDefaults.secondaryButtonColors()
        },
        modifier = Modifier.size(size),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.size(size / 2),
        )
    }
}
