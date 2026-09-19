package com.example.musicsm.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.SortMenuButton
import com.example.musicsm.ui.components.WavyProgressBar
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint

@Composable
fun DownloadsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    downloadViewModel: DownloadViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val downloads by downloadViewModel.downloads.collectAsStateWithLifecycle()
    val active by downloadViewModel.activeDownloads.collectAsStateWithLifecycle()
    val failed by downloadViewModel.failedDownloads.collectAsStateWithLifecycle()
    val sort by downloadViewModel.sort.collectAsStateWithLifecycle()
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
                stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnDark,
                modifier = Modifier.weight(1f),
            )
            if (downloads.isNotEmpty()) {
                SortMenuButton(current = sort, onSelect = downloadViewModel::setSort)
            }
        }

        if (downloads.isEmpty() && active.isEmpty() && failed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.DownloadForOffline,
                    title = stringResource(R.string.downloads_empty_title),
                    subtitle = stringResource(R.string.downloads_empty_subtitle),
                )
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
            // In-progress downloads: filling thumbnail + wavy bar.
            if (active.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.downloads_in_progress),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(active, key = { it.song.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FillingThumb(url = item.song.artworkUrl, progress = item.progress)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.song.title, color = OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(item.song.artist, color = OnDarkVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            WavyProgressBar(progress = item.progress, height = 10.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${(item.progress * 100).toInt()}%", color = OnDarkVariant, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { downloadViewModel.cancel(item.song.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.downloads_cancel), tint = OnDarkVariant)
                        }
                    }
                }
            }

            if (failed.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.downloads_failed),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Coral,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(failed, key = { it.song.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.song.title, color = OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(item.reason, color = Coral, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { downloadViewModel.retry(item.song.id) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.downloads_retry), tint = OnDark)
                        }
                        IconButton(onClick = { downloadViewModel.delete(item.song.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss), tint = OnDarkVariant)
                        }
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                item {
                    Text(
                        "Saved offline (${downloads.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(downloads, key = { _, song -> song.id }) { index, song ->
                    DownloadRow(
                        song = song,
                        onPlay = { playerViewModel.play(downloads, index) },
                        onMore = { optionsSong = song },
                        onDelete = { downloadViewModel.delete(song.id) },
                    )
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

/** Song artwork that starts dim and fills brightly from the bottom as [progress] climbs. */
@Composable
private fun FillingThumb(url: String?, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
        // Dim base.
        ArtworkImage(url = url, shape = RectangleShape, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        // Bright reveal from the bottom, height = progress.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(p)
                .align(Alignment.BottomCenter)
                .clipToBounds(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ArtworkImage(url = url, shape = RectangleShape, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun DownloadRow(song: Song, onPlay: () -> Unit, onMore: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SongRow(song = song, onClick = onPlay, onMore = onMore, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.downloads_remove), tint = OnDarkVariant)
        }
    }
}
