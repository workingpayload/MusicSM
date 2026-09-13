package com.example.musicsm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.example.musicsm.ui.theme.OnDarkMuted
import com.example.musicsm.ui.theme.SurfaceCard

/**
 * Album/track artwork with a graceful placeholder when [url] is null or fails.
 *
 * Set [highRes] for large displays (e.g. Now Playing): it decodes at the source's original
 * resolution instead of the composable's measured size, so a small cached bitmap (from the
 * mini player) isn't reused and upscaled into a blur.
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentDescription: String? = null,
    highRes: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty()) {
            val model = if (highRes) {
                ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .size(Size.ORIGINAL)
                    .crossfade(true)
                    .build()
            } else {
                url
            }
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
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
