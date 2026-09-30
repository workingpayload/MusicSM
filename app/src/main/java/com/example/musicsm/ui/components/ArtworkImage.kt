package com.example.musicsm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.example.musicsm.data.source.youtube.YouTubeArtwork
import com.example.musicsm.ui.theme.OnDarkMuted
import com.example.musicsm.ui.theme.SurfaceCard

/**
 * Album/track artwork with a graceful placeholder when [url] is null or fails.
 *
 * [targetSizePx] is the decode size, and it matters more than it looks. YouTube artwork URLs carry
 * their own size, so asking for a header-sized image and drawing it into a 48 dp row does not just
 * waste bandwidth — it decodes and holds a bitmap a hundred times larger than the row can show, in
 * every row, while scrolling. Callers that draw small should say so.
 *
 * Pass [rememberArtworkPx] of the composable's own size for list rows and grid cells; leave it
 * unset for large surfaces, which decode at whatever the URL offers.
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentDescription: String? = null,
    targetSizePx: Int? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty()) {
            ArtworkAsyncImage(
                url = url,
                targetSizePx = targetSizePx,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = OnDarkMuted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * The raw image, with no placeholder box — for surfaces that supply their own backing, such as a
 * full-bleed header or a blurred backdrop.
 *
 * Handles the two things every artwork request in the app needs. It rewrites the URL to
 * [targetSizePx], and it walks down YouTube's still-image ladder when a load fails: the largest
 * rung is generated from the source upload and is simply missing for a good share of videos, so a
 * request for it has to be able to fail into the next size down rather than into a blank box.
 */
@Composable
fun ArtworkAsyncImage(
    url: String,
    modifier: Modifier = Modifier,
    targetSizePx: Int? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    onState: ((loaded: Boolean) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val requested = remember(url, targetSizePx) {
        targetSizePx?.let { YouTubeArtwork.resize(url, it) } ?: url
    }
    // Reset whenever the subject changes: a fallback earned by the previous track says nothing
    // about this one.
    var attempt by remember(requested) { mutableStateOf(requested) }

    val model = remember(attempt, targetSizePx, url) {
        ImageRequest.Builder(context)
            .data(attempt)
            // Keyed on the URL as stored, not as requested. Otherwise the same cover occupies a
            // separate disk entry for every distinct size any screen happens to ask for, and a
            // cover already downloaded for a list row has to be fetched again for the header.
            .diskCacheKey(url)
            .apply { if (targetSizePx != null) size(targetSizePx, targetSizePx) else size(Size.ORIGINAL) }
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onSuccess = { onState?.invoke(true) },
        onError = {
            val next = YouTubeArtwork.nextFallback(attempt)
            if (next != null) attempt = next else onState?.invoke(false)
        },
    )
}

/** The pixel size a [Dp]-sized artwork slot actually decodes to on this display. */
@Composable
fun rememberArtworkPx(size: Dp): Int = with(LocalDensity.current) { size.roundToPx() }

/** Named decode sizes, so the intent of a number at a call site is visible without a comment. */
object ArtworkSize {

    /**
     * Full-bleed headers and the now-playing surfaces.
     *
     * Above the threshold at which video stills switch to their largest rung, which is the point:
     * these are the only surfaces big enough to be worth gambling on an image that may not exist.
     */
    const val HERO = 1200

    /** Grid cells and carousel cards — big enough to stay sharp on a tablet, small to decode. */
    const val TILE = 544
}

