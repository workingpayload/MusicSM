package com.example.musicsm.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.material.icons.outlined.Earbuds
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clipToBounds
import com.example.musicsm.ui.components.LocalPlayerExpanded
import com.example.musicsm.ui.components.isLowEndDevice
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.musicsm.R
import com.example.musicsm.ui.actions.SongActionsViewModel
import com.example.musicsm.ui.actions.SongExtraAction
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.ArtworkAsyncImage
import com.example.musicsm.ui.components.LocalSongNavigator
import com.example.musicsm.ui.components.rememberArtworkPx
import com.example.musicsm.ui.library.AddToPlaylistSheet
import com.example.musicsm.ui.library.LibraryViewModel
import kotlinx.coroutines.flow.flowOf
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ArtworkSize
import com.example.musicsm.ui.components.MixArtOverlay
import com.example.musicsm.ui.components.rememberMixArtState
import com.example.musicsm.ui.components.rememberMixedAccent
import com.example.musicsm.ui.components.HueCircularProgress
import com.example.musicsm.ui.components.LocalHazeState
import com.example.musicsm.ui.components.PlayPauseButton
import com.example.musicsm.ui.components.glassBackdrop
import com.example.musicsm.ui.components.rememberHazeState
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.playback.AudioOutputKind
import com.example.musicsm.playback.AudioOutputState
import com.example.musicsm.ui.theme.OnDarkVariant

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onEnterAmbient: () -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    downloadViewModel: DownloadViewModel = hiltViewModel(),
    actionsViewModel: SongActionsViewModel = hiltViewModel(),
    audioOutputViewModel: AudioOutputViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.currentSong
    val motionArt by viewModel.motionArt.collectAsStateWithLifecycle()
    val motionArtStyle by viewModel.motionArtStyle.collectAsStateWithLifecycle()
    // One placement at a time, never two: the same loop in two places at once reads as a
    // rendering fault instead of as one effect.
    val backdropArt = motionArt?.takeIf { motionArtStyle == MotionArtStyle.FULL_SCREEN }
    val coverArt = motionArt?.takeIf { motionArtStyle == MotionArtStyle.CARD }
    val edgeArt = motionArt?.takeIf { motionArtStyle == MotionArtStyle.EDGE }
    // Keyed on the URL so each track has to earn the immersive layout again; a release with no
    // loop, or one whose loop fails to decode, keeps the ordinary cover.
    var backdropShowing by remember(backdropArt?.videoUrl) { mutableStateOf(false) }
    val immersive = backdropArt != null && backdropShowing
    // Top style (Apple Music): the loop runs borderless across the top of the player instead of
    // in the card, fading out into the backdrop. Same rule — the card stays until a frame arrives.
    var edgeShowing by remember(edgeArt?.videoUrl) { mutableStateOf(false) }
    val edgeLayout = edgeArt != null && edgeShowing
    // Card style (Apple Music): the loop plays inside the cover square and the blurred backdrop
    // behind it drifts slowly, so the whole screen feels alive without a second video decoding.
    var cardShowing by remember(coverArt?.videoUrl) { mutableStateOf(false) }
    // Cosmetic video and drift only run while the sheet is actually on screen; the player stays
    // composed (translated off-screen) while collapsed, and decoding there is pure battery cost.
    val sheetVisible = LocalPlayerExpanded.current
    val cheap = isLowEndDevice()
    val driftActive = cardShowing || edgeShowing
    val drifting = driftActive && state.isPlaying && sheetVisible && !cheap
    val driftPhase = remember { Animatable(0f) }
    LaunchedEffect(drifting) {
        // Resumes from wherever it stopped, so pausing freezes the colours instead of snapping them.
        while (drifting) {
            val remaining = 1f - driftPhase.value
            driftPhase.animateTo(
                1f,
                tween((DRIFT_PERIOD_MS * remaining).toInt().coerceAtLeast(1), easing = LinearEasing),
            )
            driftPhase.snapTo(0f)
        }
    }
    val navigator = LocalSongNavigator.current
    val sleepTimerState by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val audioOutputState by audioOutputViewModel.state.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showOutputPicker by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf(false) }

    // Android 12+ hides Bluetooth device names without "Nearby devices"; ask once, the moment
    // there is a headset whose name we can't show.
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { audioOutputViewModel.refresh() }
    val requestBluetoothPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    var askedBluetoothPermission by rememberSaveable { mutableStateOf(false) }
    val needsBluetoothName = audioOutputState.bluetoothNamePermissionMissing && audioOutputState.hasBluetoothOutput
    LaunchedEffect(needsBluetoothName) {
        if (needsBluetoothName && !askedBluetoothPermission) {
            askedBluetoothPermission = true
            requestBluetoothPermission()
        }
    }

    /**
     * Open the artist page. The page is loaded by artist name (the name IS the id used by the
     * repository), so navigate straight there instead of doing a separate lookup first — that
     * removes a whole network round-trip and the blank wait before the screen appears.
     */
    val openArtist = {
        song?.artist?.takeIf { it.isNotBlank() }?.let { navigator.openArtist(it) }
        Unit
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
    val liquidBackdrop = rememberLayerBackdrop()

    val mixBlend by viewModel.mixBlend.collectAsStateWithLifecycle()
    val mixArt = rememberMixArtState(
        blend = mixBlend,
        currentSongId = song?.id,
        positionFlow = viewModel.position,
        isPlaying = state.isPlaying && sheetVisible,
    )
    // The backdrop's tint follows the blend too (read in the draw phase only).
    val backdropAccent = rememberMixedAccent(accent, mixArt)
    // The blurred backdrop's slow drift, shared by the cover fading in during a blend.
    val backdropDrift = Modifier.graphicsLayer {
        // Read here, in the layer, so each drift frame is a repaint only.
        if (!driftActive) return@graphicsLayer
        val a = driftPhase.value * 2f * PI.toFloat()
        // Oversized enough that a ±10° tilt plus the orbit never uncovers a corner of a tall screen.
        val s = DRIFT_SCALE + 0.06f * sin(2f * a)
        scaleX = s
        scaleY = s
        rotationZ = 10f * sin(a)
        translationX = cos(a) * size.width * 0.06f
        translationY = sin(a) * size.height * 0.04f
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Captured up front so the immersive gap below can size itself against the real screen
        // rather than a guessed one; the scrolling column's own height is unbounded.
        val screenHeight = maxHeight
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // Everything on screen, recorded for the output picker's Liquid Glass to refract.
        Box(Modifier.fillMaxSize().layerBackdrop(liquidBackdrop)) {
        // Backdrop layers registered as the blur source so the glass play button can frost them.
        Box(Modifier.matchParentSize().glassBackdrop(haze)) {
            // Opaque base so the sheet is never see-through over the content behind it.
            Box(Modifier.matchParentSize().background(AppBackground))
            // Blurred artwork backdrop (Apple Music-style frosted look; blur is a no-op below API 31).
            if (!song?.artworkUrl.isNullOrEmpty()) {
                // Clipped: the drift oversizes the image, and while the sheet slides in anything
                // spilling past its top edge would draw over the screen behind it.
                Box(Modifier.matchParentSize().clipToBounds()) {
                    ArtworkAsyncImage(
                        url = song.artworkUrl,
                        targetSizePx = ArtworkSize.TILE,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .then(backdropDrift)
                            .blur(60.dp),
                    )
                }
            }
            // The cover loop, filling the screen. It sits over the blurred still so that the still
            // covers the gap before the first frame arrives and stays put if the video never loads.
            backdropArt?.let { art ->
                MotionArtwork(
                    url = art.videoUrl,
                    isHls = art.isHls,
                    playing = state.isPlaying && sheetVisible,
                    fadeMillis = BACKDROP_FADE_MS,
                    onRenderedChange = { backdropShowing = it },
                    modifier = Modifier.matchParentSize(),
                )
            }
            // During a crossfade/Mix the next track's blurred cover fades in with its audio.
            if (mixArt.next != null) {
                Box(Modifier.matchParentSize().clipToBounds()) {
                    MixArtOverlay(
                        state = mixArt,
                        targetSizePx = ArtworkSize.TILE,
                        modifier = Modifier
                            .matchParentSize()
                            .then(backdropDrift)
                            .blur(60.dp),
                    )
                }
            }
            // Dominant-color tint + vertical darkening for legibility (color read in draw phase).
            // The tint is dropped over a cover loop, which supplies its own colour and would only
            // be muddied by a wash of the still's dominant one.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        if (backdropArt == null) drawRect(backdropAccent.value.copy(alpha = 0.35f))
                        // Once the video carries the screen the scrim pulls back to the two bands
                        // that actually sit under text, leaving the middle clear to be looked at.
                        val stops = if (immersive) {
                            arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.45f),
                                0.30f to Color.Black.copy(alpha = 0.08f),
                                0.58f to Color.Black.copy(alpha = 0.40f),
                                1.00f to Color.Black.copy(alpha = 0.88f),
                            )
                        } else {
                            arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.20f),
                                0.6f to Color.Black.copy(alpha = 0.45f),
                                1.0f to Color.Black.copy(alpha = 0.80f),
                            )
                        }
                        drawRect(Brush.verticalGradient(*stops))
                    },
            )
            // Top style (Apple Music layout): the video fills the upper EDGE_HEIGHT_FRACTION of the
            // player edge to edge, cropped to fit, drawn above the scrim so it keeps its own
            // colour. Masked in an offscreen layer so its lower edge dissolves into the backdrop.
            edgeArt?.let { art ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(EDGE_HEIGHT_FRACTION)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (edgeShowing) {
                                // Keeps the status bar and the top buttons legible over bright art.
                                drawRect(
                                    Brush.verticalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.35f),
                                        0.20f to Color.Transparent,
                                    ),
                                )
                            }
                            drawRect(
                                Brush.verticalGradient(
                                    0.00f to Color.Black,
                                    EDGE_FADE_START to Color.Black,
                                    0.92f to Color.Black.copy(alpha = 0.4f),
                                    1.00f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    MotionArtwork(
                        url = art.videoUrl,
                        isHls = art.isHls,
                        playing = state.isPlaying && sheetVisible,
                        fadeMillis = BACKDROP_FADE_MS,
                        onRenderedChange = { edgeShowing = it },
                        // Gives way to the backdrop as it blends into the next track's cover.
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { if (mixArt.hasArtwork) alpha = 1f - mixArt.value },
                    )
                }
            }
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
        // Top bar: kept to close + overflow. Lyrics and queue sit by the output picker at the
        // bottom; share, details and the sleep timer live in the overflow sheet.
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
            }
            IconButton(onClick = { if (song != null) showOptions = true }, enabled = song != null) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options), tint = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Apple Music-style motion: art springs large while playing, shrinks when paused, with a
        // subtle continuous "breathing" so it never feels static.
        val artShape = RoundedCornerShape(16.dp)

        // With a loop playing behind everything the cover square is a smaller, boxed copy of what
        // is already on screen, so it gets out of the way and the space it held becomes the view.
        AnimatedVisibility(
            visible = !immersive && !edgeLayout,
            enter = fadeIn(tween(ART_SWAP_MS)) + expandVertically(tween(ART_SWAP_MS)),
            exit = fadeOut(tween(ART_SWAP_MS)) + shrinkVertically(tween(ART_SWAP_MS)),
        ) {
            // Kept inside so the endless breathing animation stops driving recomposition once the
            // square is gone; nothing below it reads these values.
            val playing = state.isPlaying
            val artScale by animateFloatAsState(
                targetValue = if (playing) 1f else 0.82f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "artScale",
            )
            val breatheTransition = rememberInfiniteTransition(label = "artBreathe")
            val breath by breatheTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "breath",
            )
            val finalArtScale = artScale * (1f + if (playing) 0.012f * breath else 0f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        scaleX = finalArtScale
                        scaleY = finalArtScale
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = artShape,
                    )
                    .clip(artShape),
            ) {
                ArtworkImage(
                    url = song?.artworkUrl,
                    shape = artShape,
                    targetSizePx = ArtworkSize.HERO,
                    modifier = Modifier.matchParentSize(),
                )
                // Cross-fades in over the still cover once its first frame decodes; absent for the
                // majority of releases, which simply keep the image above.
                coverArt?.let { art ->
                    MotionArtwork(
                        url = art.videoUrl,
                        isHls = art.isHls,
                        playing = playing && sheetVisible,
                        onRenderedChange = { cardShowing = it },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                // The next track's cover, fading in as its audio blends in.
                MixArtOverlay(
                    state = mixArt,
                    targetSizePx = ArtworkSize.HERO,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        // The gap that replaces it. Sized from what the controls below actually need rather than
        // as a fraction, so the same layout holds on a short phone and a tall one.
        AnimatedVisibility(
            visible = immersive,
            enter = fadeIn(tween(ART_SWAP_MS)) + expandVertically(tween(ART_SWAP_MS)),
            exit = fadeOut(tween(ART_SWAP_MS)) + shrinkVertically(tween(ART_SWAP_MS)),
        ) {
            Spacer(Modifier.height((screenHeight - IMMERSIVE_CHROME_HEIGHT).coerceAtLeast(96.dp)))
        }

        // Top style: room for the video above, so the title lands on its faded lower edge. The
        // video starts at the very top of the player, while this column starts below the status
        // bar and the top bar, hence the subtraction.
        AnimatedVisibility(
            visible = edgeLayout,
            enter = fadeIn(tween(ART_SWAP_MS)) + expandVertically(tween(ART_SWAP_MS)),
            exit = fadeOut(tween(ART_SWAP_MS)) + shrinkVertically(tween(ART_SWAP_MS)),
        ) {
            Spacer(
                Modifier.height(
                    (screenHeight * EDGE_HEIGHT_FRACTION - statusBarTop - EDGE_CONTENT_OFFSET).coerceAtLeast(96.dp),
                ),
            )
        }

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
                        enabled = song != null && song.artist.isNotBlank(),
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

        // Apple Music-style scrubber. Position is collected and frame-interpolated INSIDE
        // SmoothSeekBar (a leaf), so its ~60fps updates never recompose this whole screen.
        SmoothSeekBar(
            positionFlow = viewModel.position,
            isPlaying = state.isPlaying,
            durationMs = state.durationMs,
            onSeek = viewModel::seekToFraction,
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
        var volumeDragging by remember { mutableStateOf<Float?>(null) }
        val volumeValue = if (state.systemVolumeAvailable) {
            volumeDragging ?: state.systemVolume
        } else {
            state.volume
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            Slider(
                value = volumeValue,
                onValueChange = {
                    if (state.systemVolumeAvailable) {
                        volumeDragging = it
                        viewModel.setSystemVolume(it)
                    } else {
                        viewModel.setVolume(it)
                    }
                },
                onValueChangeFinished = { volumeDragging = null },
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

        Spacer(Modifier.height(10.dp))

        val secondaryTint = Color.White.copy(alpha = 0.72f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onOpenLyrics) {
                Icon(Icons.Filled.Lyrics, contentDescription = stringResource(R.string.player_lyrics), tint = secondaryTint)
            }
            Box(Modifier.weight(1f)) {
                AudioOutputIndicator(
                    state = audioOutputState,
                    onClick = {
                        audioOutputViewModel.refresh()
                        showOutputPicker = true
                    },
                )
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = stringResource(R.string.player_queue), tint = secondaryTint)
            }
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
                    ArtworkImage(url = s.artworkUrl, shape = RoundedCornerShape(8.dp), targetSizePx = rememberArtworkPx(44.dp), modifier = Modifier.size(44.dp))
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

    // Over the content and outside the recorded layer, so the glass refracts the screen, not itself.
    OutputPickerSheet(
        visible = showOutputPicker,
        backdrop = liquidBackdrop,
        state = audioOutputState,
        onOpenSystemPicker = { audioOutputViewModel.openSystemOutputSwitcher() },
        onSelectOutput = { id ->
            if (audioOutputViewModel.selectOutput(id)) showOutputPicker = false
        },
        onRequestBluetoothPermission = requestBluetoothPermission,
        onDismiss = { showOutputPicker = false },
    )
    }

    if (showTrackDetails && song != null) {
        TrackCreditsSheet(
            song = song,
            durationMs = state.durationMs,
            isDownloaded = isDownloaded,
            onDismiss = { showTrackDetails = false },
        )
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
            extraActions = listOf(
                SongExtraAction(
                    label = stringResource(R.string.player_sleep_timer),
                    icon = Icons.Filled.Bedtime,
                    onAction = {
                        showOptions = false
                        showSleepTimer = true
                    },
                ),
                SongExtraAction(
                    label = stringResource(R.string.track_details_title),
                    icon = Icons.Filled.Info,
                    onAction = {
                        showOptions = false
                        showTrackDetails = true
                    },
                ),
                SongExtraAction(
                    label = stringResource(R.string.ambient_mode),
                    icon = Icons.Filled.Nightlight,
                    onAction = {
                        showOptions = false
                        onEnterAmbient()
                    },
                ),
            ),
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


@Composable
private fun AudioOutputIndicator(
    state: AudioOutputState,
    onClick: () -> Unit,
) {
    val output = state.current
    val tint = Color.White.copy(alpha = 0.72f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.audio_output_title), onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (output?.kind) {
                AudioOutputKind.BLUETOOTH -> AnimatedBudsIcon(
                    playKey = output?.id,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                else -> Icon(
                    imageVector = when (output?.kind) {
                        AudioOutputKind.WIRED, AudioOutputKind.USB -> Icons.Outlined.Headphones
                        else -> Icons.AutoMirrored.Outlined.VolumeUp
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = output?.name ?: stringResource(R.string.audio_output_choose),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

/**
 * Earbuds glyph that pops in with a small wiggle a single time — when it first appears or when
 * the Bluetooth device changes ([playKey]) — then stays still.
 */
@Composable
private fun AnimatedBudsIcon(
    playKey: Any?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val scale = remember(playKey) { Animatable(0.3f) }
    val wiggle = remember(playKey) { Animatable(0f) }
    LaunchedEffect(playKey) {
        // Let the Now Playing enter transition finish first, or the pop plays unseen.
        delay(BUDS_ANIM_DELAY_MS)
        launch { scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
        wiggle.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    Icon(
        imageVector = Icons.Outlined.Earbuds,
        contentDescription = null,
        tint = tint,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = ((scale.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
            val p = wiggle.value
            // Three decaying swings that settle exactly at rest when p reaches 1.
            rotationZ = (sin(p * 3f * PI.toFloat()) * 22f * (1f - p))
        },
    )
}

private const val BUDS_ANIM_DELAY_MS = 350L


/** Slower than the cover-square fade: a full-screen change needs longer to read as a dissolve. */
private const val BACKDROP_FADE_MS = 800

/** Crossfade between the cover square and the immersive gap that replaces it. */
private const val ART_SWAP_MS = 450

/** One full orbit of the card-style backdrop drift: slow enough to read as ambience, not motion. */
private const val DRIFT_PERIOD_MS = 24_000

/** Base oversize of the drifting backdrop; see the cover arithmetic where it is applied. */
private const val DRIFT_SCALE = 1.6f

/** Share of the player's height the Top-style video covers, measured from the top. */
private const val EDGE_HEIGHT_FRACTION = 0.65f

/** Fraction of the Top-style video that stays solid before it starts fading into the backdrop. */
private const val EDGE_FADE_START = 0.75f

/**
 * What sits between the top of the column and the title besides the Top-style gap: top bar
 * (16 dp padding + 48 dp row), the 16 dp and 20 dp spacers around the artwork slot, and a 28 dp
 * overlap so the title rests on the video's faded lower edge instead of below it.
 */
private val EDGE_CONTENT_OFFSET = 128.dp

/**
 * Height the chrome below the artwork slot occupies: title, scrubber, transport and volume.
 *
 * The immersive gap is the screen minus this, which keeps the controls sitting just above the
 * bottom edge on any screen instead of floating at a fixed fraction of it.
 */
private val IMMERSIVE_CHROME_HEIGHT = 480.dp
