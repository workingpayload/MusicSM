package com.example.musicsm.ui.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant

/**
 * Preview of a playlist someone shared, with an explicit "add to library" step.
 *
 * The whole track list arrived inside the link, so this needs no network at all — but it is still
 * untrusted input from outside the app, which is why nothing is written until the user says so.
 */
@Composable
fun SharedPlaylistScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedPlaylistViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playlist = ui.playlist

    // System back must discard too, otherwise the stale link reappears on the next navigation.
    BackHandler {
        viewModel.discard()
        onBack()
    }

    // Jump straight into the imported playlist once it is saved.
    LaunchedEffect(ui.savedPlaylistId) {
        ui.savedPlaylistId?.let(onOpenPlaylist)
    }

    // A malformed or already-consumed link leaves nothing to show.
    if (playlist == null) {
        Box(modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.shared_playlist_expired),
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    Box(modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = LocalBottomBarPadding.current + 24.dp,
            ),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        viewModel.discard()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = OnDark,
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkImage(
                        url = playlist.songs.firstOrNull()?.artworkUrl,
                        shape = RoundedCornerShape(16.dp),
                        highRes = true,
                        modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(1f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.shared_playlist_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = Coral,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnDark,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(
                            R.plurals.share_playlist_tracks,
                            playlist.songs.size,
                            playlist.songs.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = viewModel::save,
                        enabled = !ui.saving && ui.savedPlaylistId == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Coral,
                            contentColor = OnAccent,
                        ),
                        shape = RoundedCornerShape(50),
                    ) {
                        if (ui.saving) {
                            CircularProgressIndicator(
                                color = OnAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.LibraryAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.shared_playlist_add))
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }

            // Keyed by position, not id: a shared list may legitimately contain the same track
            // twice, and duplicate keys crash Compose.
            itemsIndexed(playlist.songs, key = { index, _ -> index }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlay(playlist.songs, index) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
