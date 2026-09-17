package com.example.musicsm.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.musicsm.R
import com.example.musicsm.ui.actions.SongActionsViewModel
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.LocalSongNavigator
import com.example.musicsm.ui.library.AddToPlaylistSheet
import com.example.musicsm.ui.library.LibraryViewModel
import com.example.musicsm.ui.util.shareSong
import kotlinx.coroutines.flow.flowOf
import com.example.musicsm.ui.components.AppleSeekBar
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.HueCircularProgress
import com.example.musicsm.ui.components.LocalHazeState
import com.example.musicsm.ui.components.PlayPauseButton
import com.example.musicsm.ui.components.glassBackdrop
import com.example.musicsm.ui.components.rememberHazeState
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.OnDarkVariant

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    downloadViewModel: DownloadViewModel = hiltViewModel(),
    actionsViewModel: SongActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.currentSong
    val context = LocalContext.current
    val navigator = LocalSongNavigator.current
    val resolvingNav by actionsViewModel.resolving.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimer.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    /** Look up the artist page for the playing track and open it. */
    val openArtist = {
        if (song != null) {
            actionsViewModel.resolveArtist(song.artist) { id ->
                if (id != null) {
                    navigator.openArtist(id)
                } else {
                    Toast.makeText(context, context.getString(R.string.error_artist_not_found), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isDownloaded by remember(song?.id) {
        song?.let { downloadViewModel.isDownloaded(it.id) } ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    val downloadProgress by downloadViewModel.progress.collectAsStateWithLifecycle()
    val downloading = song?.id?.let { downloadProgress[it] }

    val accent = rememberDominantColorState(
        url = song?.artworkUrl,
        fallback = accentColorFor(song?.id ?: song?.title),
    )

    val isLiked by remember(song?.id) {
        song?.let { libraryViewModel.isLiked(it.id) } ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    val haze = rememberHazeState()

    Box(modifier = modifier.fillMaxSize()) {
        // Backdrop layers registered as the blur source so the glass play button can frost them.
        Box(Modifier.matchParentSize().glassBackdrop(haze)) {
            // Opaque base so the sheet is never see-through over the content behind it.
            Box(Modifier.matchParentSize().background(AppBackground))
            // Blurred artwork backdrop (Apple Music-style frosted look; blur is a no-op below API 31).
            if (!song?.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = song?.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(60.dp),
                )
            }
            // Dominant-color tint + vertical darkening for legibility (color read in draw phase).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(accent.value.copy(alpha = 0.35f))
                        drawRect(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.20f),
                                0.6f to Color.Black.copy(alpha = 0.45f),
                                1.0f to Color.Black.copy(alpha = 0.80f),
                            ),
                        )
                    },
            )
        }

    CompositionLocalProvider(LocalHazeState provides haze) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (sleepTimerState.isActive) {
                Text(
                    formatSleepRemaining(sleepTimerState),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent.value,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showSleepTimer = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                IconButton(onClick = { showSleepTimer = true }) {
                    Icon(Icons.Filled.Bedtime, contentDescription = stringResource(R.string.player_sleep_timer), tint = Color.White)
                }
            }
            IconButton(onClick = { song?.let { shareSong(context, it) } }, enabled = song != null) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share), tint = Color.White)
            }
            IconButton(onClick = onOpenLyrics) {
                Icon(Icons.Filled.Lyrics, contentDescription = stringResource(R.string.player_lyrics), tint = Color.White)
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = stringResource(R.string.player_queue), tint = Color.White)
            }
            IconButton(onClick = { if (song != null) showOptions = true }, enabled = song != null) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options), tint = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))

        ArtworkImage(
            url = song?.artworkUrl,
            shape = RoundedCornerShape(16.dp),
            highRes = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 8.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(16.dp),
                ),
        )

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: stringResource(R.string.player_nothing_playing),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song?.artist.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        enabled = song != null && song.artist.isNotBlank() && !resolvingNav,
                        onClick = openArtist,
                    ),
                )
            }
            IconButton(onClick = { song?.let(libraryViewModel::toggleLike) }) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.player_like),
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = { if (song != null) showSheet = true }) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist), tint = MaterialTheme.colorScheme.onBackground)
            }
            // Download / offline toggle.
            when {
                downloading != null -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    HueCircularProgress(
                        progress = downloading!!,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
                isDownloaded -> IconButton(onClick = { song?.let { downloadViewModel.delete(it.id) } }) {
                    Icon(Icons.Filled.DownloadDone, contentDescription = stringResource(R.string.player_downloaded_tap_to_remove), tint = MaterialTheme.colorScheme.primary)
                }
                else -> IconButton(onClick = { song?.let(downloadViewModel::download) }) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.player_download), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Apple Music-style scrubber, fed a frame-interpolated position for smooth motion.
        val smoothPos = rememberSmoothPosition(state.positionMs, state.isPlaying, state.durationMs)
        val smoothProgress = if (state.durationMs > 0) {
            (smoothPos.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else 0f
        AppleSeekBar(
            progress = smoothProgress,
            durationMs = state.durationMs,
            onSeek = viewModel::seekToFraction,
            playing = state.isPlaying,
        )

        Spacer(Modifier.height(16.dp))

        // Controls, in a frosted card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.action_shuffle),
                    tint = if (state.shuffleOn) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            IconButton(onClick = viewModel::previous, enabled = state.hasPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(36.dp))
            }
            // Play/pause, ringed by a progress indicator while the stream buffers.
            Box(contentAlignment = Alignment.Center) {
                PlayPauseButton(isPlaying = state.isPlaying, onClick = viewModel::togglePlayPause, size = 72.dp)
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(84.dp),
                    )
                }
            }
            IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = viewModel::cycleRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.player_repeat),
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Volume — glassy track tinted by the album-art accent color.
        val volAccent = accent.value
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            Slider(
                value = state.volume,
                onValueChange = viewModel::setVolume,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                thumb = {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(volAccent)
                            .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    )
                },
                track = { sliderState ->
                    val fraction = sliderState.value.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(volAccent.copy(alpha = 0.55f), volAccent),
                                    ),
                                ),
                        )
                    }
                },
            )
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }

        // Playing Next
        val upNext = state.queue.drop(state.currentIndex + 1).take(4)
        if (upNext.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.player_up_next),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            upNext.forEachIndexed { i, s ->
                val queueIndex = state.currentIndex + 1 + i
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.seekToIndex(queueIndex) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(url = s.artworkUrl, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
    }
    }

    if (showSleepTimer) {
        SleepTimerSheet(
            state = sleepTimerState,
            onPick = viewModel::startSleepTimer,
            onEndOfTrack = viewModel::startSleepTimerAtEndOfTrack,
            onCancel = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimer = false },
        )
    }
    if (showOptions && song != null) {
        SongOptionsSheet(
            song = song,
            playerViewModel = viewModel,
            onDismiss = { showOptions = false },
            libraryViewModel = libraryViewModel,
            downloadViewModel = downloadViewModel,
            actionsViewModel = actionsViewModel,
        )
    }
    if (showSheet && song != null) {
        AddToPlaylistSheet(
            playlists = playlists,
            onPick = {
                libraryViewModel.addToPlaylist(it, song)
                showSheet = false
            },
            onCreateNew = {
                showSheet = false
                showCreate = true
            },
            onDismiss = { showSheet = false },
        )
    }
    if (showCreate && song != null) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.library_new_playlist)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryViewModel.createPlaylistWithSong(name, song)
                        showCreate = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

