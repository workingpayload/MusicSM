package com.example.musicsm.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.OnDarkVariant

@Composable
fun PlaylistDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val accent = rememberDominantColorState(
        url = ui.songs.firstOrNull()?.artworkUrl,
        fallback = accentColorFor(ui.title),
    )

    Column(modifier = modifier.fillMaxWidth().background(AppBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .drawBehind {
                            val c = accent.value
                            drawRect(
                                Brush.verticalGradient(
                                    0.0f to c.copy(alpha = 0.85f),
                                    0.5f to c.copy(alpha = 0.35f),
                                    1.0f to AppBackground,
                                ),
                            )
                        },
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = ui.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "${ui.songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnDarkVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = {
                            if (ui.songs.isNotEmpty()) {
                                playerViewModel.toggleShuffle()
                                playerViewModel.play(ui.songs, 0)
                            }
                        },
                        color = Color.Transparent,
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle play", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = { if (ui.songs.isNotEmpty()) playerViewModel.play(ui.songs, 0) },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            if (ui.songs.isEmpty()) {
                item {
                    Text(
                        "No songs yet",
                        color = OnDarkVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                itemsIndexed(ui.songs) { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SongRow(
                            song = song,
                            onClick = { playerViewModel.play(ui.songs, index) },
                            modifier = Modifier.weight(1f),
                        )
                        if (!ui.isLiked) {
                            IconButton(onClick = { viewModel.remove(song) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = OnDarkVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
