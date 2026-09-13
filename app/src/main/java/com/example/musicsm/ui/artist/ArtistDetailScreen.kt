package com.example.musicsm.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.SurfaceHighest

@Composable
fun ArtistDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val accent = rememberDominantColorState(
        url = ui.artworkUrl ?: ui.topSongs.firstOrNull()?.artworkUrl,
        fallback = accentColorFor(ui.name),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(AppBackground)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.value.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.04f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Lavender.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.25f),
                        radius = size.width * 0.7f,
                    ),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            when {
                ui.loading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.align(Alignment.Center))
                }

                ui.error != null -> Box(Modifier.fillMaxSize()) {
                    Text(ui.error!!, color = OnDarkVariant, modifier = Modifier.align(Alignment.Center))
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
                ) {
                    item { ArtistHeader(ui, playerViewModel) }

                    if (ui.topSongs.isNotEmpty()) {
                        item { SectionHeader("Top songs") }
                        itemsIndexed(ui.topSongs) { index, song ->
                            SongRow(
                                song = song,
                                onClick = { playerViewModel.play(ui.topSongs, index) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            )
                        }
                    }

                    if (ui.albums.isNotEmpty()) {
                        item { SectionHeader("Albums") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                items(ui.albums) { album ->
                                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(ui: ArtistDetailUiState, playerViewModel: PlayerViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            url = ui.artworkUrl,
            shape = CircleShape,
            modifier = Modifier.padding(top = 8.dp).size(180.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = ui.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!ui.subscribers.isNullOrBlank()) {
            Text(
                text = "${ui.subscribers} subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Coral)
                    .clickable(enabled = ui.topSongs.isNotEmpty()) { playerViewModel.play(ui.topSongs, 0) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceHighest.copy(alpha = 0.6f))
                    .clickable(enabled = ui.topSongs.isNotEmpty()) {
                        playerViewModel.toggleShuffle()
                        playerViewModel.play(ui.topSongs, 0)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shuffle", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
