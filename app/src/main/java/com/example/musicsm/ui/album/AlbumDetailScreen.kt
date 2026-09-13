package com.example.musicsm.ui.album

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.CoralLight
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.SurfaceHigh
import com.example.musicsm.ui.theme.SurfaceHighest
import com.example.musicsm.ui.theme.Teal
import com.example.musicsm.ui.util.formatDuration

@Composable
fun AlbumDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val favorited by viewModel.favorited.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    val accent = rememberDominantColorState(
        url = ui.artworkUrl ?: ui.songs.firstOrNull()?.artworkUrl,
        fallback = accentColorFor(ui.title),
    )

    // Ambient aurora backdrop (coral + lavender blooms), same as Home.
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
            TopBar(onBack = onBack)

            when {
                ui.loading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.align(Alignment.Center))
                }

                ui.error != null -> Box(Modifier.fillMaxSize()) {
                    Text(ui.error!!, color = OnDarkVariant, modifier = Modifier.align(Alignment.Center))
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 24.dp + LocalBottomBarPadding.current,
                    ),
                ) {
                    item {
                        AlbumHeader(
                            ui = ui,
                            accent = accent.value,
                            playerViewModel = playerViewModel,
                            favorited = favorited,
                            added = added,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleAdd = viewModel::toggleAdd,
                        )
                    }
                    item {
                        Text(
                            "Tracks",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnDarkVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                    }
                    if (ui.songs.isEmpty()) {
                        item { Text("No songs", color = OnDarkVariant, modifier = Modifier.padding(vertical = 8.dp)) }
                    } else {
                        itemsIndexed(ui.songs) { index, song ->
                            val isCurrent = playerState.currentSong?.id == song.id
                            TrackRow(
                                index = index + 1,
                                title = song.title,
                                artist = song.artist,
                                duration = formatDuration(song.durationMs),
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && playerState.isPlaying,
                                onClick = { playerViewModel.play(ui.songs, index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
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
        Spacer(Modifier.width(8.dp))
        Text(
            "View Album",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AlbumHeader(
    ui: AlbumDetailUiState,
    accent: Color,
    playerViewModel: PlayerViewModel,
    favorited: Boolean,
    added: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleAdd: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            url = ui.artworkUrl,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(top = 8.dp).size(256.dp),
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = ui.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (ui.artist.isNotBlank()) {
            Text(
                text = ui.artist,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CoralLight,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val totalMs = ui.songs.sumOf { it.durationMs }
        val meta = buildList {
            ui.year?.let { add(it) }
            add("${ui.songs.size} songs")
            if (totalMs > 0) add("${totalMs / 60000} min")
        }.joinToString(" • ")
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(24.dp))
        ActionCluster(ui, playerViewModel, favorited, added, onToggleFavorite, onToggleAdd)
    }
}

@Composable
private fun ActionCluster(
    ui: AlbumDetailUiState,
    playerViewModel: PlayerViewModel,
    favorited: Boolean,
    added: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play pill.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(Coral)
                .clickable(enabled = ui.songs.isNotEmpty()) { playerViewModel.play(ui.songs, 0) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
        // Shuffle pill.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(SurfaceHighest.copy(alpha = 0.6f))
                .clickable(enabled = ui.songs.isNotEmpty()) {
                    playerViewModel.toggleShuffle()
                    playerViewModel.play(ui.songs, 0)
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shuffle", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
        // Add to library (creates/removes a playlist named after the album).
        CircleIconButton(
            icon = if (added) Icons.Filled.Check else Icons.Filled.Add,
            tint = if (added) Teal else MaterialTheme.colorScheme.onBackground,
            contentDescription = "Add to library",
            onClick = onToggleAdd,
        )
        // Favorite (likes/unlikes all album tracks).
        CircleIconButton(
            icon = if (favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            tint = if (favorited) Coral else MaterialTheme.colorScheme.onBackground,
            contentDescription = "Favorite",
            onClick = onToggleFavorite,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SurfaceHighest.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TrackRow(
    index: Int,
    title: String,
    artist: String,
    duration: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) SurfaceHigh.copy(alpha = 0.8f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Coral, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnDarkVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) Coral else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            duration,
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) Coral else OnDarkVariant,
        )
    }
}
