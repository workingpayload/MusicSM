package com.example.musicsm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.ui.theme.OnDarkVariant

/** A rounded album/playlist card for horizontal shelves. */
@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        ArtworkImage(
            url = artworkUrl,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        run {
            // Always render one subtitle line (even if blank) so every card is the same height
            // and the artwork stays aligned across a shelf.
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) =
    AlbumCard(album.title, album.artist, album.artworkUrl, onClick, modifier)

/** A circular artist avatar with a name below. */
@Composable
fun ArtistCircle(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            url = artist.artworkUrl,
            shape = CircleShape,
            modifier = Modifier.size(114.dp),
        )
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Picks an icon that represents a genre/mood from its title. */
private fun genreIcon(title: String): androidx.compose.ui.graphics.vector.ImageVector {
    val t = title.lowercase()
    return when {
        "hip" in t || "rap" in t -> Icons.Filled.Mic
        "rock" in t || "metal" in t -> Icons.Filled.Bolt
        "lo-fi" in t || "lofi" in t -> Icons.Filled.Headphones
        "workout" in t || "gym" in t -> Icons.Filled.FitnessCenter
        "chill" in t || "relax" in t -> Icons.Filled.Spa
        "focus" in t || "study" in t -> Icons.Filled.SelfImprovement
        "party" in t || "dance" in t -> Icons.Filled.Celebration
        "jazz" in t || "blues" in t -> Icons.Filled.Nightlife
        "classical" in t || "piano" in t -> Icons.Filled.Piano
        "indie" in t || "alt" in t -> Icons.Filled.Album
        "throwback" in t || "retro" in t || "80s" in t || "90s" in t -> Icons.Filled.Radio
        "pop" in t -> Icons.Filled.Star
        "country" in t -> Icons.Filled.Agriculture
        "electro" in t || "edm" in t || "house" in t -> Icons.Filled.GraphicEq
        else -> Icons.Filled.MusicNote
    }
}

/** A colored genre/mood tile for the Search landing screen (Apple Music-style). */
@Composable
fun BrowseTileCard(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(alpha = 0.55f)),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        // Diagonal light sheen for depth.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0.0f to Color.White.copy(alpha = 0.14f),
                        0.5f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.25f),
                    ),
                ),
        )
        // Genre-representative icon, cropped in the corner (Apple Music-style).
        Icon(
            imageVector = genreIcon(title),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.30f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 2.dp)
                .size(80.dp)
                .rotate(18f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}
