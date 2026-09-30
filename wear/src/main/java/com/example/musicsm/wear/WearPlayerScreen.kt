package com.example.musicsm.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Typography

// Colours lifted from the phone app's DarkPalette so both surfaces read as one product.
private val Coral = Color(0xFFFF525E)
private val CoralDark = Color(0xFFD8323E)
private val SurfaceLow = Color(0xFF1A1B21)
private val OnSurface = Color(0xFFE3E1E9)
internal val OnDarkVariant = Color(0x80FFFFFF)

// Glass tokens — translucent fills + hairline strokes, same values the app uses for frosted panels.
private val GlassFill = Color(0x1FFFFFFF)
private val GlassFillStrong = Color(0x40292A2F)
private val GlassStroke = Color(0x33FFFFFF)

// Plus Jakarta Sans — the app's typeface, mirrored into the wear module.
internal val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_extrabold, FontWeight.ExtraBold),
)

@Composable
fun MusicSmWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Coral,
            primaryVariant = CoralDark,
            onPrimary = Color.White,
            surface = SurfaceLow,
            onSurface = OnSurface,
            background = Color.Black,
            onBackground = OnSurface,
        ),
        typography = Typography(defaultFontFamily = JakartaSans),
        content = content,
    )
}

/** Glass button colours for non-accent controls — translucent over the blurred cover. */
@Composable
private fun glassButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(backgroundColor = GlassFillStrong, contentColor = OnSurface)

/** Remote control for whatever the phone is playing. */
@Composable
fun WearPlayerScreen(
    state: WearPlaybackState,
    accent: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenOnPhone: () -> Unit,
    onSearch: () -> Unit,
    onLyrics: () -> Unit,
) {
    val accentButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = accent,
        contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White,
    )
    Scaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Frosted base: the cover, blurred and darkened, fills the whole face.
            if (state.hasTrack && state.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = state.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(10.dp),
                )
            }
            // Accent glow lit by the cover — the watch's echo of the app's aurora.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title + artist float on a translucent glass card that spans the usable width, so a
                // long title wraps and shrinks to fit the face instead of overflowing.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShape)
                        .background(GlassFill)
                        .border(1.dp, GlassStroke, GlassShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.title.ifBlank { stringResource(R.string.nothing_playing) },
                        style = MaterialTheme.typography.title3.copy(
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                        ),
                        color = OnSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.artist.isNotBlank()) {
                        Text(
                            text = state.artist,
                            style = MaterialTheme.typography.caption2,
                            color = OnDarkVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransportButton(
                        iconRes = R.drawable.ic_prev,
                        descriptionRes = R.string.cd_previous,
                        enabled = state.hasTrack,
                        size = 40.dp,
                        colors = glassButtonColors(),
                        onClick = onPrevious,
                    )
                    Spacer(Modifier.width(8.dp))
                    TransportButton(
                        iconRes = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        descriptionRes = R.string.cd_play_pause,
                        enabled = state.hasTrack,
                        size = 56.dp,
                        colors = accentButtonColors,
                        onClick = onPlayPause,
                    )
                    Spacer(Modifier.width(8.dp))
                    TransportButton(
                        iconRes = R.drawable.ic_next,
                        descriptionRes = R.string.cd_next,
                        enabled = state.hasTrack,
                        size = 40.dp,
                        colors = glassButtonColors(),
                        onClick = onNext,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        iconRes = R.drawable.ic_search,
                        descriptionRes = R.string.cd_search,
                        colors = accentButtonColors,
                        onClick = onSearch,
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        iconRes = R.drawable.ic_lyrics,
                        descriptionRes = R.string.cd_lyrics,
                        enabled = state.hasTrack,
                        colors = glassButtonColors(),
                        onClick = onLyrics,
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        iconRes = R.drawable.ic_open_phone,
                        descriptionRes = R.string.cd_open_phone,
                        colors = glassButtonColors(),
                        onClick = onOpenOnPhone,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    iconRes: Int,
    descriptionRes: Int,
    colors: ButtonColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TransportButton(
    iconRes: Int,
    descriptionRes: Int,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    colors: ButtonColors,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.size(size),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.size(size / 2),
        )
    }
}

private val GlassShape = RoundedCornerShape(18.dp)
