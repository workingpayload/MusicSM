package com.example.musicsm.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MediaItemMapper
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceLow
import com.example.musicsm.ui.util.formatDuration

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrackCreditsSheet(
    song: Song,
    durationMs: Long,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = trackDetailRows(song, durationMs, isDownloaded)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = Coral, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.track_details_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDark,
                    )
                    Text(
                        stringResource(R.string.track_details_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            rows.forEach { row ->
                DetailRow(row.label, row.value)
            }

            Text(
                stringResource(R.string.track_details_available_note),
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(OverlayTint.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = OnDark,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun trackDetailRows(song: Song, durationMs: Long, isDownloaded: Boolean): List<TrackDetailRow> = buildList {
    song.title.takeIf { it.isNotBlank() }?.let {
        add(TrackDetailRow(stringResource(R.string.track_details_title_label), it))
    }
    // Tracks with no artist of their own fall back to the album title upstream, so guard
    // against listing the same value as both the artist and the album.
    song.artist
        .takeIf { it.isNotBlank() && !it.equals(song.album?.trim(), ignoreCase = true) }
        ?.let {
            add(TrackDetailRow(stringResource(R.string.track_details_artist_label), it))
        }
    song.album?.takeIf { it.isNotBlank() }?.let {
        add(TrackDetailRow(stringResource(R.string.track_details_album_label), it))
    }
    val shownDuration = durationMs.takeIf { it > 0L } ?: song.durationMs.takeIf { it > 0L }
    if (shownDuration != null) {
        add(TrackDetailRow(stringResource(R.string.track_details_duration_label), formatDuration(shownDuration)))
    }
    val sourceLabel = when {
        MediaItemMapper.isLocal(song.id) -> stringResource(R.string.track_details_source_local)
        isDownloaded -> stringResource(R.string.track_details_source_downloaded)
        else -> stringResource(R.string.track_details_source_streamed)
    }
    add(TrackDetailRow(stringResource(R.string.track_details_source_label), sourceLabel))
}

private data class TrackDetailRow(
    val label: String,
    val value: String,
)
