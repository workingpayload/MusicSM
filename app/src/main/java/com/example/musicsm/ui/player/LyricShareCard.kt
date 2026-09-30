package com.example.musicsm.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.rememberArtworkPx
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.asDeepTint
import com.example.musicsm.ui.util.shareImage
import kotlinx.coroutines.launch

/**
 * Full-screen preview + share sheet for a lyric card built from the lines the user selected.
 *
 * The card is drawn into an off-screen [rememberGraphicsLayer] as well as on screen, so "Share"
 * captures the exact pixels the user is previewing and hands them to the system share sheet.
 */
@Composable
fun LyricShareDialog(
    song: Song,
    lines: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val accent = rememberDominantColorState(song.artworkUrl, fallback = Coral)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            // Record into the off-screen layer (for capture) and also paint to screen.
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawContent()
                        },
                ) {
                    LyricCard(song = song, lines = lines, accent = accent.value)
                }

                Spacer(Modifier.size(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                shareImage(context, bitmap, "${song.title} — ${song.artist}")
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_share_action))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The shareable card: a deep artwork-tinted gradient, an oversized quote glyph, the chosen lines as
 * the hero, and a clean footer (cover, title/artist, wordmark pill). No blurred photo — a solid
 * tint reads more modern and never turns muddy behind the text.
 */
@Composable
private fun LyricCard(
    song: Song,
    lines: List<String>,
    accent: Color,
) {
    val lyricSize = when {
        lines.size <= 2 -> 30.sp
        lines.size <= 4 -> 25.sp
        lines.size <= 6 -> 21.sp
        else -> 17.sp
    }
    val deep = accent.asDeepTint()
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(32.dp))
            .drawBehind {
                // Deep tint top → near-black bottom, with a soft accent glow in the corner.
                drawRect(
                    Brush.verticalGradient(
                        listOf(deep, lerp(deep, Color.Black, 0.62f)),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.14f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 30.dp),
        ) {
            // Oversized opening quote as a design mark.
            Text(
                text = "“",
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 90.sp,
                lineHeight = 70.sp,
                fontWeight = FontWeight.Black,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        color = Color.White,
                        fontSize = lyricSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = lyricSize * 1.32f,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.padding(vertical = 5.dp),
                    )
                }
            }

            // Hairline accent divider.
            Box(
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .size(width = 44.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(accent),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    url = song.artworkUrl,
                    shape = RoundedCornerShape(12.dp),
                    targetSizePx = rememberArtworkPx(52.dp),
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Wordmark pill.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = onAccent,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
