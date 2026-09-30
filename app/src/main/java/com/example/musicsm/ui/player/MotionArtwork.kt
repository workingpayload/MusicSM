package com.example.musicsm.ui.player

import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.max

/**
 * Plays a track's looping cover video.
 *
 * This is decoration, so it is built to lose gracefully: until the first frame is actually on
 * screen the view stays fully transparent, and any playback error leaves it that way permanently.
 * In both cases whatever sits underneath — a still cover, a blurred backdrop — simply remains
 * visible and nothing is reported.
 *
 * The video always fills [modifier]'s bounds and is centre-cropped to do it. Cover loops arrive in
 * several shapes (square from one catalogue, portrait from another) and letterboxing them inside a
 * player background would frame the decoration instead of hiding it.
 *
 * [onRenderedChange] reports whether frames are actually reaching the screen, so callers that want
 * to rearrange around the video can wait for proof it arrived instead of acting on a URL that may
 * still turn out to be unplayable.
 */
@OptIn(UnstableApi::class)
@Composable
fun MotionArtwork(
    url: String,
    isHls: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
    fadeMillis: Int = FADE_MS,
    onRenderedChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keyed on the url so switching tracks starts clean rather than inheriting the previous
    // cover's readiness or its failure.
    var rendered by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var videoAspect by remember(url) { mutableFloatStateOf(0f) }
    var boxAspect by remember { mutableFloatStateOf(0f) }
    var foregrounded by remember { mutableStateOf(true) }

    val opacity by animateFloatAsState(
        targetValue = if (rendered && !failed) 1f else 0f,
        animationSpec = tween(durationMillis = fadeMillis),
        label = "motionArtAlpha",
    )

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            // Audio is switched off outright rather than just muted, so this player can never open
            // an output or contend with the music it is decorating. The bitrate ceiling keeps a
            // decorative loop from competing with the audio stream for bandwidth.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(MAX_VIDEO_PX, MAX_VIDEO_PX)
                .setMaxVideoBitrate(MAX_VIDEO_BITRATE)
                .setMaxVideoFrameRate(MAX_FPS)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            // Told explicitly rather than inferred: these URLs carry query strings and CDN paths
            // that make extension sniffing unreliable.
            setMediaItem(
                MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
                    .build(),
            )
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                rendered = true
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foregrounded = true
                Lifecycle.Event.ON_STOP -> foregrounded = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The loop follows the music: a cover animating over a paused track reads as a bug, and
    // decoding video for a screen nobody is looking at is pure battery cost.
    LaunchedEffect(player, playing, foregrounded, failed) {
        player.playWhenReady = playing && foregrounded && !failed
    }

    // Reported rather than derived by the caller so that a URL which never decodes, or one whose
    // player errors out mid-loop, hands the layout back to whatever it displaced.
    val onRendered by rememberUpdatedState(onRenderedChange)
    val visible = rendered && !failed
    LaunchedEffect(visible) { onRendered(visible) }
    DisposableEffect(Unit) { onDispose { onRendered(false) } }

    // The texture is stretched to the box, so the crop is recovered by scaling the shorter axis
    // back out until the video's real proportions are restored and the excess spills past the clip.
    val cropScale = cropScaleFor(videoAspect, boxAspect)

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                boxAspect = if (size.height > 0) size.width.toFloat() / size.height else 0f
            },
    ) {
        key(url) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) },
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = opacity
                        scaleX = cropScale.x
                        scaleY = cropScale.y
                    },
            )
        }
    }
}

/** Per-axis scale that restores [videoAspect] inside a box of [boxAspect] by cropping the overflow. */
internal fun cropScaleFor(videoAspect: Float, boxAspect: Float): Scale {
    if (videoAspect <= 0f || boxAspect <= 0f) return Scale(1f, 1f)
    val ratio = videoAspect / boxAspect
    return if (ratio > 1f) Scale(x = ratio, y = 1f) else Scale(x = 1f, y = max(1f / ratio, 1f))
}

internal data class Scale(val x: Float, val y: Float)

/** Long enough to read as a dissolve from the still cover rather than a cut. */
private const val FADE_MS = 500

/** Covers render at a fraction of the screen; anything beyond this is wasted bandwidth. */
private const val MAX_VIDEO_PX = 1280
private const val MAX_VIDEO_BITRATE = 2_500_000
private const val MAX_FPS = 30
