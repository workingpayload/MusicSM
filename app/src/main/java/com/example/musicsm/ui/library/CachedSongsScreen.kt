package com.example.musicsm.ui.library

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OfflineBolt
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.ScreenBackHandler
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint

/**
 * Everything the player has fully cached while streaming. These play without a connection, so
 * this is the place to go when offline and the downloads alone feel thin.
 */
@Composable
fun CachedSongsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CachedSongsViewModel = hiltViewModel(),
) {
    ScreenBackHandler { onBack() }
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var optionsSong by remember { mutableStateOf<Song?>(null) }

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(OverlayTint.copy(alpha = 0.05f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.action_back), tint = OnDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.cached_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnDark,
                modifier = Modifier.weight(1f),
            )
        }

        val list = songs
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }

            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.OfflineBolt,
                    title = stringResource(R.string.cached_empty_title),
                    subtitle = stringResource(R.string.cached_empty_subtitle),
                )
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            pluralStringResource(R.plurals.cached_count, list.size, list.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnDarkVariant,
                        )
                        Text(
                            stringResource(R.string.cached_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnDarkVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(stringResource(R.string.action_play), Icons.Filled.PlayArrow, filled = true) {
                                playerViewModel.play(list, 0)
                            }
                            PillButton(stringResource(R.string.library_shuffle_all), Icons.Filled.Shuffle, filled = false) {
                                playerViewModel.shufflePlay(list)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                itemsIndexed(list, key = { _, song -> song.id }) { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SongRow(
                            song = song,
                            onClick = { playerViewModel.play(list, index) },
                            onMore = { optionsSong = song },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.remove(song.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cached_remove), tint = OnDarkVariant)
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
private fun PillButton(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val fg = if (filled) OnAccent else OnDark
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Coral else OverlayTint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
