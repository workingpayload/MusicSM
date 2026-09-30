package com.example.musicsm.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** Lyrics for the current track. Synced lyrics highlight and auto-scroll to the current line. */
@Composable
fun WearLyricsScreen(
    lyrics: WearLyrics?,
    isLoading: Boolean,
    playbackState: WearPlaybackState,
    accent: Color,
) {
    Scaffold(timeText = { TimeText() }) {
        when {
            isLoading -> CenterBox { CircularProgressIndicator() }

            lyrics == null || lyrics.lines.isEmpty() -> CenterBox {
                Text(
                    text = stringResource(R.string.no_lyrics),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LyricsList(lyrics, playbackState, accent)
        }
    }
}

@Composable
private fun LyricsList(lyrics: WearLyrics, playbackState: WearPlaybackState, accent: Color) {
    val listState = rememberScalingLazyListState()
    var currentIndex by remember(lyrics) { mutableIntStateOf(-1) }

    if (lyrics.synced) {
        LaunchedEffect(lyrics, playbackState) {
            while (true) {
                val pos = playbackState.estimatedPositionMs()
                currentIndex = lyrics.lines.indexOfLast { (it.timeMs ?: 0L) <= pos }
                delay(TICK_MS)
            }
        }
        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0) runCatching { listState.animateScrollToItem(currentIndex) }
        }
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            if (line.text.isNotBlank()) {
                val isCurrent = lyrics.synced && index == currentIndex
                Text(
                    text = line.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                    color = if (isCurrent) accent else OnDarkVariant,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    style = if (isCurrent) MaterialTheme.typography.title3 else MaterialTheme.typography.body2,
                )
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private const val TICK_MS = 400L
