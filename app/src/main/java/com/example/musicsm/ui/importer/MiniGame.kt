package com.example.musicsm.ui.importer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnDarkVariant
import kotlinx.coroutines.delay
import kotlin.random.Random

/** A modern rounded progress bar: translucent track + coral→lavender gradient fill, animated. */
@Composable
fun ModernProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(350), label = "progress")
    Box(
        modifier = modifier
            .height(12.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Coral, Lavender))),
        )
    }
}

/**
 * A tiny time-killer while a playlist imports: tap the notes as they pop around the board to
 * score. Purely for fun — no effect on the import.
 */
@Composable
fun MiniGame(modifier: Modifier = Modifier) {
    var score by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val targetPx = with(density) { 52.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f)),
    ) {
        val maxX = (constraints.maxWidth - targetPx).coerceAtLeast(1f)
        val maxY = (constraints.maxHeight - targetPx).coerceAtLeast(1f)

        var pos by remember { mutableStateOf(Random.nextFloat() * maxX to Random.nextFloat() * maxY) }
        fun move() { pos = Random.nextFloat() * maxX to Random.nextFloat() * maxY }

        // The note relocates on its own if you're too slow — keeps it lively.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1300)
                move()
            }
        }

        Text(
            "Tap the notes  •  $score",
            color = OnDarkVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
        )

        Box(
            Modifier
                .offset { IntOffset(pos.first.toInt(), pos.second.toInt()) }
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Coral, Lavender)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { score++; move() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = "note", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
