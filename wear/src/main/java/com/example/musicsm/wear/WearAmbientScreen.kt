package com.example.musicsm.wear

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.wear.compose.material.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AmbientPrimary = Color(0xFFCED0D6)
private val AmbientSecondary = Color(0x80FFFFFF)

/**
 * Always-on (ambient) face for the player and lyrics. Deliberately spare: a mostly-black surface,
 * dim thin text, no artwork or accent fills, and a small per-minute position nudge so nothing burns
 * into an OLED panel. The system updates ambient content roughly once a minute, so this can't do a
 * live lyric scroll — it shows the line under the extrapolated position at each ambient tick.
 */
@Composable
fun WearAmbientScreen(
    showLyrics: Boolean,
    state: WearPlaybackState,
    lyrics: WearLyrics?,
    tick: Long,
) {
    val context = LocalContext.current
    val nothingPlaying = stringResource(R.string.nothing_playing)
    // Refreshes on each ambient update (tick) rather than every second.
    val time = remember(tick) {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
    // Burn-in guard: shift content within a few px, stepping once per ambient update.
    val step = ((tick / 60_000L) % 6L).toInt() - 3
    val nudge = step.dp
    val nudgePx = with(LocalDensity.current) { nudge.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Dimmed, blurred cover behind the ambient content. Kept low-alpha (and nudged with the
        // content) so it reads on an always-on panel without risking burn-in.
        if (state.hasTrack && state.artworkUrl.isNotBlank()) {
            AsyncImage(
                model = state.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Overscan so the burn-in nudge never exposes an edge.
                        scaleX = 1.15f
                        scaleY = 1.15f
                        translationX = nudgePx
                        translationY = nudgePx
                        alpha = 0.60f
                    }
                    .blur(12.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .offset(x = nudge, y = nudge)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = time,
                color = AmbientPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(10.dp))

            val currentLine = if (showLyrics && lyrics != null && lyrics.synced) {
                val pos = state.estimatedPositionMs()
                lyrics.lines.lastOrNull { (it.timeMs ?: 0L) <= pos }?.text
            } else {
                null
            }

            if (showLyrics && currentLine != null) {
                Text(
                    text = currentLine,
                    color = AmbientPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = state.title.ifBlank { nothingPlaying },
                    color = AmbientPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.artist.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.artist,
                        color = AmbientSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
