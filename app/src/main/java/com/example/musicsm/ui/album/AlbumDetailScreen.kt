package com.example.musicsm.ui.album

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.CoralLight
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
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
    downloadViewModel: com.example.musicsm.ui.player.DownloadViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val favorited by viewModel.favorited.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val accent = rememberDominantColorState(
        url = ui.artworkUrl ?: ui.songs.firstOrNull()?.artworkUrl,
        fallback = accentColorFor(ui.title),
    )

    // Ambient aurora backdrop (coral + lavender blooms), same as Home.
    // Palette tokens are composable reads, so they are hoisted out of the draw lambda.
    val backdrop = AppBackground
    val bloom = Lavender
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(backdrop)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.value.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.04f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(bloom.copy(alpha = 0.16f), Color.Transparent),
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
                    ErrorState(
                        message = ui.error!!,
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center),
                    )
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
                            onDownloadAll = { ui.songs.forEach(downloadViewModel::download) },
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.album_tracks),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnDarkVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                    }
                    if (ui.songs.isEmpty()) {
                        item { Text(stringResource(R.string.album_no_songs), color = OnDarkVariant, modifier = Modifier.padding(vertical = 8.dp)) }
                    } else {
                        itemsIndexed(ui.songs, key = { _, song -> song.id }) { index, song ->
                            val isCurrent = playerState.currentSong?.id == song.id
                            TrackRow(
                                index = index + 1,
                                title = song.title,
                                artist = song.artist,
                                duration = formatDuration(song.durationMs),
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && playerState.isPlaying,
                                onClick = { playerViewModel.play(ui.songs, index) },
                                onLongClick = { optionsSong = song },
                            )
                        }
                    }
                }
            }
        }
    }

    optionsSong?.let { song ->
        SongOptionsSheet(
            song = song,
            playerViewModel = playerViewModel,
            onDismiss = { optionsSong = null },
        )
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
                .background(OverlayTint.copy(alpha = 0.05f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.album_view_album),
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
    onDownloadAll: () -> Unit,
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
        ActionCluster(ui, playerViewModel, favorited, added, onToggleFavorite, onToggleAdd, onDownloadAll)
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
    onDownloadAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Play + Shuffle pills get the full width to themselves.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PlayArrow,
                iconTint = OnAccent,
                label = stringResource(R.string.action_play),
                labelColor = OnAccent,
                background = Coral,
                enabled = ui.songs.isNotEmpty(),
                onClick = { playerViewModel.play(ui.songs, 0) },
            )
            PillButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Shuffle,
                iconTint = Coral,
                label = stringResource(R.string.action_shuffle),
                labelColor = MaterialTheme.colorScheme.onBackground,
                background = SurfaceHighest.copy(alpha = 0.6f),
                enabled = ui.songs.isNotEmpty(),
                onClick = { playerViewModel.shufflePlay(ui.songs) },
            )
        }
        // Secondary actions on their own row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = if (added) Icons.Filled.Check else Icons.Filled.Add,
                tint = if (added) Teal else MaterialTheme.colorScheme.onBackground,
                contentDescription = stringResource(R.string.album_add_to_library),
                onClick = onToggleAdd,
            )
            CircleIconButton(
                icon = if (favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (favorited) Coral else MaterialTheme.colorScheme.onBackground,
                contentDescription = stringResource(R.string.album_favorite),
                onClick = onToggleFavorite,
            )
            CircleIconButton(
                icon = Icons.Filled.Download,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = stringResource(R.string.album_download),
                onClick = onDownloadAll,
            )
        }
    }
}

@Composable
private fun PillButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    labelColor: Color,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    index: Int,
    title: String,
    artist: String,
    duration: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) SurfaceHigh.copy(alpha = 0.8f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
