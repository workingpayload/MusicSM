package com.example.musicsm.ui.artist

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceHighest

@Composable
fun ArtistDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val accent = rememberDominantColorState(
        url = ui.artworkUrl ?: ui.topSongs.firstOrNull()?.artworkUrl,
        fallback = accentColorFor(ui.name),
    )

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
            }

            when {
                ui.loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (ui.name.isNotBlank()) {
                        Text(
                            text = ui.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    CircularProgressIndicator(color = Coral)
                }

                ui.error != null -> Box(Modifier.fillMaxSize()) {
                    ErrorState(
                        message = ui.error!!,
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
                ) {
                    item { ArtistHeader(ui, playerViewModel, liked, viewModel::toggleLike) }

                    if (ui.topSongs.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.artist_top_songs)) }
                        itemsIndexed(ui.topSongs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song,
                                onClick = { playerViewModel.play(ui.topSongs, index) },
                                onMore = { optionsSong = song },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            )
                        }
                    }

                    if (ui.albums.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.section_albums)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                items(ui.albums, key = { it.id }) { album ->
                                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                                }
                            }
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
private fun ArtistHeader(
    ui: ArtistDetailUiState,
    playerViewModel: PlayerViewModel,
    liked: Boolean,
    onToggleLike: () -> Unit,
) {
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

        Spacer(Modifier.height(12.dp))
        // Follow / like pill.
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (liked) Coral else SurfaceHighest.copy(alpha = 0.6f))
                .clickable(onClick = onToggleLike)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.artist_follow_action),
                tint = if (liked) OnAccent else Coral,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(if (liked) R.string.artist_following else R.string.artist_follow),
                color = if (liked) OnAccent else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(16.dp))
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
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_play), color = OnAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceHighest.copy(alpha = 0.6f))
                    .clickable(enabled = ui.topSongs.isNotEmpty()) {
                        playerViewModel.shufflePlay(ui.topSongs)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_shuffle), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
