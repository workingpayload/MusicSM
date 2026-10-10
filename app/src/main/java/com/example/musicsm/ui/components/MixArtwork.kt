package com.example.musicsm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MixBlend
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * The next track's artwork fading in over the current one while their audio blends (crossfade or
 * Mix), driven by the playback position so the picture moves with the sound.
 */
@Stable
class MixArtState internal constructor() {
    /** The track fading in, or null when no blend is lined up. */
    var next: Song? by mutableStateOf(null)
        internal set

    internal val fraction = Animatable(0f)

    /** How much of [next] shows, 0..1. Read it in the draw phase (graphicsLayer) only. */
    val value: Float get() = fraction.value

    /** True when [next] has a cover to fade to. */
    val hasArtwork: Boolean get() = !next?.artworkUrl.isNullOrEmpty()
}

@Composable
fun rememberMixArtState(
    blend: MixBlend?,
    currentSongId: String?,
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
): MixArtState {
    val state = remember { MixArtState() }
    val currentId by rememberUpdatedState(currentSongId)
    val active = blend?.takeIf { it.outgoingId == currentSongId }
    LaunchedEffect(active, isPlaying) {
        if (active == null) {
            val faded = state.next
            if (faded != null && faded.id == currentId) {
                // Done: the incoming track is now the current one. The overlay matches what sits
                // under it, so hold it while the screen's own track-change animations (accent
                // colour, ambient cross-fade) catch up, then drop it unseen.
                delay(FINISH_HOLD_MS)
                state.fraction.snapTo(0f)
            } else {
                // Called off (a seek or queue change): ease back instead of snapping.
                state.fraction.animateTo(0f, tween(SETTLE_MS))
            }
            state.next = null
            return@LaunchedEffect
        }
        state.next = active.next
        positionFlow.collectLatest { anchor ->
            // Positions arrive about once a second; run the clock between them.
            val anchorFrame = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                val position = anchor + if (isPlaying) now - anchorFrame else 0L
                val target = FastOutSlowInEasing.transform(active.progressAt(position))
                if (state.fraction.value != target) state.fraction.snapTo(target)
                if (!isPlaying) break
                // Nothing moves until the blend starts; don't redraw every frame waiting for it.
                val untilStart = active.startMs - position
                if (untilStart > IDLE_AHEAD_MS) delay((untilStart - IDLE_AHEAD_MS / 2).toLong())
            }
        }
    }
    return state
}

/**
 * The incoming track's artwork, faded by [state]. It stays invisible until it has actually loaded,
 * so the blend never fades towards an empty placeholder. The fade is the outermost layer, so
 * [modifier] can carry the same treatment (blur, dimming) as the cover underneath.
 */
@Composable
fun MixArtOverlay(
    state: MixArtState,
    modifier: Modifier = Modifier,
    targetSizePx: Int? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = state.next?.artworkUrl?.takeIf { it.isNotEmpty() } ?: return
    var loaded by remember(url) { mutableStateOf(false) }
    ArtworkAsyncImage(
        url = url,
        targetSizePx = targetSizePx,
        contentDescription = null,
        contentScale = contentScale,
        onState = { loaded = it },
        modifier = Modifier
            .graphicsLayer { alpha = if (loaded) state.value else 0f }
            .then(modifier),
    )
}

/** [current] blended towards the incoming track's accent colour as the blend goes. */
@Composable
fun rememberMixedAccent(current: State<Color>, mixArt: MixArtState): State<Color> {
    val next = mixArt.next
    val nextAccent = rememberDominantColorState(
        url = next?.artworkUrl,
        fallback = accentColorFor(next?.id ?: next?.title),
    )
    return remember(current, mixArt, nextAccent) {
        derivedStateOf {
            val f = mixArt.value
            if (f <= 0f) current.value else lerp(current.value, nextAccent.value, f)
        }
    }
}

private const val SETTLE_MS = 450
private const val FINISH_HOLD_MS = 1_200L
private const val IDLE_AHEAD_MS = 1_000.0
