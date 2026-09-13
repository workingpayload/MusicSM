package com.example.musicsm.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral

/**
 * Apple Music / Stitch-style live lyrics. Colored artwork-driven backdrop, a now-playing header
 * chip, and a karaoke list: active line is big + bright, others dim. Auto-scrolls; taps seek.
 */
@Composable
fun LyricsPanel(
    lyricsState: LyricsState,
    positionMs: Long,
    song: Song?,
    onSeekMs: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rememberDominantColorState(song?.artworkUrl, fallback = Coral)

    Box(modifier = modifier.fillMaxSize()) {
        // Opaque frosted backdrop: solid base + blurred artwork + dominant tint (never see-through).
        Box(Modifier.matchParentSize().background(AppBackground))
        if (!song?.artworkUrl.isNullOrEmpty()) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(80.dp),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(accent.value.copy(alpha = 0.30f))
                    drawRect(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.6f to Color.Black.copy(alpha = 0.55f),
                            1.0f to Color.Black.copy(alpha = 0.85f),
                        ),
                    )
                },
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close lyrics", tint = Color.White)
            }
            Text("Live Lyrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Now-playing header chip
        if (song != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(url = song.artworkUrl, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        when (lyricsState) {
            is LyricsState.Loading, is LyricsState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            is LyricsState.None -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No lyrics found", color = Color.White.copy(alpha = 0.7f))
            }

            is LyricsState.Loaded -> {
                val lyrics = lyricsState.lyrics
                val activeIndex = if (lyrics.synced) {
                    lyrics.lines.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
                } else -1

                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 160.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        val color by animateColorAsState(
                            if (isActive) Color.White
                            else Color.White.copy(alpha = if (lyrics.synced) 0.35f else 0.75f),
                            label = "lyricColor",
                        )
                        Text(
                            text = line.text.ifBlank { "♪" },
                            color = color,
                            fontSize = if (isActive) 30.sp else 23.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            lineHeight = if (isActive) 36.sp else 30.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (lyrics.synced && line.timeMs != null) {
                                        Modifier.clickable { onSeekMs(line.timeMs) }
                                    } else Modifier,
                                )
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.navigationBarsPadding())
    }
    }
}
