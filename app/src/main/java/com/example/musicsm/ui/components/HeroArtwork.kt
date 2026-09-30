package com.example.musicsm.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A full-bleed square header image that the page's content rises out of.
 *
 * Three things make this read as a header rather than as a large picture with a list under it.
 *
 * It is square and edge-to-edge, so the artwork is the page rather than an object on it. It is
 * pulled up underneath the status bar and the floating back button, so no strip of background
 * separates the art from the system chrome. And its bottom does not end — it ramps to nothing over
 * [fadeHeight], so the first rows of content emerge from the image instead of butting against a
 * hard edge.
 *
 * It occupies a layout slot shorter than it draws, so the content below sits where the fade has
 * already taken over. That is what lets a plain list item follow it with no special casing.
 *
 * @param scale from [HeroZoomState]; read in the draw phase, never in composition.
 * @param overlay drawn on top of the image at the same size, for motion artwork.
 */
@Composable
fun HeroArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    scale: () -> Float = { 1f },
    fadeHeight: Dp = HERO_FADE,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    if (url.isNullOrEmpty()) return
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val pullUpPx = topInset + with(density) { CHROME_CLEARANCE.roundToPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Reports a shorter slot than it measured and draws the child above the top of that
            // slot. An offset alone would leave the original height behind as a gap.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - pullUpPx).coerceAtLeast(0)) {
                    placeable.place(0, -pullUpPx)
                }
            }
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                // The image has to become transparent towards its bottom, which means the fade
                // must erase pixels already drawn rather than paint over them — so the whole thing
                // needs its own layer to erase within.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val fadePx = fadeHeight.toPx().coerceAtMost(size.height)
                val ramp = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fadePx,
                    endY = size.height,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(ramp, blendMode = BlendMode.DstIn)
                }
            },
    ) {
        ArtworkAsyncImage(
            url = url,
            targetSizePx = ArtworkSize.HERO,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        overlay?.invoke(this)
    }
}

/**
 * The dark wash that appears behind floating chrome once the artwork starts scrolling away.
 *
 * Over the artwork the back button usually has enough contrast on its own, and a permanent shade
 * behind it would be a visible rectangle sitting on the image. Once the page below — plain text on
 * a flat background — arrives under the button, it needs one. [progress] ties the change to the
 * scroll so it is never seen arriving.
 */
@Composable
fun ChromeScrim(progress: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val shade = Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.55f),
                    0.35f to Color.Black.copy(alpha = 0.40f),
                    0.70f to Color.Black.copy(alpha = 0.12f),
                    1.00f to Color.Transparent,
                )
                onDrawBehind { drawRect(shade, alpha = progress().coerceIn(0f, 1f)) }
            },
    )
}

/** Room above the artwork's own top edge for the status bar plus the floating back button. */
private val CHROME_CLEARANCE = 56.dp

/**
 * How much of the artwork's bottom is given over to the fade.
 *
 * Long enough that the eye cannot find the point where the image stops. A short ramp reads as a
 * gradient drawn on the artwork rather than as the artwork running out.
 */
private val HERO_FADE = 220.dp
