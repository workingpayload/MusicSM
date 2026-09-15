package com.example.musicsm.ui.importer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDarkVariant
import kotlin.random.Random

/**
 * A Chrome-dino-style endless runner to pass the time while a playlist imports. Tap to jump the
 * dino over the cacti; it speeds up as your score climbs. Purely for fun.
 */
@Composable
fun DinoGame(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1D24), Color(0xFF121318)))),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val groundY = heightPx - 24f
        val dinoW = 34f
        val dinoH = 38f
        val dinoX = 40f
        val cactusW = 22f
        val cactusH = 36f

        var jump by remember { mutableFloatStateOf(0f) }   // height above ground
        var vel by remember { mutableFloatStateOf(0f) }
        var obstacles by remember { mutableStateOf(listOf(widthPx)) }
        var score by remember { mutableIntStateOf(0) }
        var gameOver by remember { mutableStateOf(false) }
        var runKey by remember { mutableIntStateOf(0) }
        var legPhase by remember { mutableFloatStateOf(0f) }
        var groundScroll by remember { mutableFloatStateOf(0f) }
        var nextGap by remember { mutableFloatStateOf(Random.nextInt(150, 360).toFloat()) }
        var clouds by remember {
            mutableStateOf(listOf(Offset(widthPx * 0.35f, 26f), Offset(widthPx * 0.8f, 46f), Offset(widthPx * 1.2f, 20f)))
        }

        fun reset() {
            jump = 0f; vel = 0f; obstacles = listOf(widthPx); score = 0; gameOver = false
            nextGap = Random.nextInt(150, 380).toFloat(); runKey++
        }
        fun tap() {
            if (gameOver) { reset(); return }
            if (jump <= 0.5f) vel = JUMP_VELOCITY
        }

        LaunchedEffect(runKey) {
            var last = withFrameNanos { it }
            while (!gameOver) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                last = now

                // Runner physics.
                vel -= GRAVITY * dt
                jump += vel * dt
                if (jump <= 0f) { jump = 0f; vel = 0f }

                val speed = BASE_SPEED + score * 9f
                legPhase += dt * 10f
                groundScroll = (groundScroll + speed * dt) % 28f
                clouds = clouds.map { c ->
                    if (c.x < -70f) Offset(widthPx + 30f, Random.nextInt(16, 56).toFloat())
                    else Offset(c.x - CLOUD_SPEED * dt, c.y)
                }

                // Move obstacles; score when an obstacle's right edge crosses past the dino.
                var scored = false
                obstacles = obstacles.mapNotNull { x ->
                    val nx = x - speed * dt
                    val oldRight = x + cactusW
                    val newRight = nx + cactusW
                    if (oldRight >= dinoX && newRight < dinoX) scored = true
                    if (nx < -cactusW) null else nx
                }
                if (scored) score++

                // Spawn once the last cactus is `nextGap` px in, then roll a fresh gap so spacing varies.
                val lastX = obstacles.maxOrNull() ?: 0f
                if (widthPx - lastX >= nextGap) {
                    obstacles = obstacles + widthPx
                    nextGap = Random.nextInt(150, 380).toFloat()
                }

                // Collision (slightly forgiving box).
                val pad = 4f
                val rLeft = dinoX + pad; val rRight = dinoX + dinoW - pad
                val rBottom = groundY - jump; val rTop = rBottom - (dinoH - pad)
                for (x in obstacles) {
                    val oLeft = x + 3f; val oRight = x + cactusW - 3f
                    val oTop = groundY - cactusH; val oBottom = groundY
                    if (rRight > oLeft && rLeft < oRight && rBottom > oTop && rTop < oBottom) {
                        gameOver = true
                        break
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { tap() },
        ) {
            // Clouds (background).
            clouds.forEach { drawCloud(it) }
            // Ground.
            drawGround(groundY, groundScroll)
            // Cacti.
            obstacles.forEach { drawCactus(it, groundY, cactusW, cactusH) }
            // Dino.
            drawDino(dinoX, groundY - jump, dinoW, dinoH, legPhase, grounded = jump <= 0.5f)
        }

        Text(
            "Score $score",
            color = OnDarkVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp).align(Alignment.TopEnd),
        )

        if (gameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Game over • tap to restart",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        } else {
            Text(
                "Tap to jump",
                color = OnDarkVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
            )
        }
    }
}

// --- drawing ---------------------------------------------------------------

private fun DrawScope.drawCloud(pos: Offset) {
    val c = Color.White.copy(alpha = 0.10f)
    drawRoundRect(c, topLeft = pos, size = Size(46f, 16f), cornerRadius = CornerRadius(8f, 8f))
    drawRoundRect(c, topLeft = Offset(pos.x + 10f, pos.y - 8f), size = Size(26f, 16f), cornerRadius = CornerRadius(8f, 8f))
}

private fun DrawScope.drawGround(groundY: Float, scroll: Float) {
    drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 2f)
    // Scrolling dashes below the line for a sense of speed.
    var x = -scroll
    while (x < size.width) {
        drawLine(Color.White.copy(alpha = 0.16f), Offset(x, groundY + 8f), Offset(x + 10f, groundY + 8f), strokeWidth = 2f)
        x += 28f
    }
}

private fun DrawScope.drawCactus(x: Float, groundY: Float, w: Float, h: Float) {
    val green = Color(0xFF3DDC84)
    val top = groundY - h
    // Trunk.
    drawRoundRect(green, topLeft = Offset(x + w / 2 - 3f, top), size = Size(6f, h), cornerRadius = CornerRadius(3f, 3f))
    // Left arm.
    drawRoundRect(green, topLeft = Offset(x + 2f, top + h * 0.35f), size = Size(4f, h * 0.30f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(green, topLeft = Offset(x + 2f, top + h * 0.35f), size = Size(w / 2 - 4f, 4f), cornerRadius = CornerRadius(2f, 2f))
    // Right arm.
    drawRoundRect(green, topLeft = Offset(x + w - 6f, top + h * 0.25f), size = Size(4f, h * 0.30f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(green, topLeft = Offset(x + w / 2 + 2f, top + h * 0.25f), size = Size(w / 2 - 4f, 4f), cornerRadius = CornerRadius(2f, 2f))
}

private fun DrawScope.drawDino(x: Float, bottomY: Float, w: Float, h: Float, legPhase: Float, grounded: Boolean) {
    val body = Coral
    fun r(dx: Float, dy: Float, rw: Float, rh: Float, color: Color = body) =
        drawRoundRect(color, topLeft = Offset(x + dx, bottomY - dy), size = Size(rw, rh), cornerRadius = CornerRadius(3f, 3f))

    // tail, body, head, snout
    r(-4f, 20f, 10f, 7f)
    r(2f, 24f, 18f, 20f)
    r(16f, 34f, 15f, 15f)
    r(28f, 26f, 6f, 7f)
    // eye
    drawRoundRect(Color.White, topLeft = Offset(x + 25f, bottomY - 30f), size = Size(3f, 3f), cornerRadius = CornerRadius(2f, 2f))
    // legs — alternate while running on the ground, together while airborne
    val step = ((legPhase).toInt() % 2 == 0)
    if (grounded) {
        r(8f, if (step) 8f else 5f, 4f, if (step) 8f else 5f)
        r(16f, if (step) 5f else 8f, 4f, if (step) 5f else 8f)
    } else {
        r(9f, 6f, 4f, 6f)
        r(16f, 6f, 4f, 6f)
    }
    // little arm
    r(20f, 18f, 5f, 3f)
}

private const val GRAVITY = 2600f
private const val JUMP_VELOCITY = 780f
private const val BASE_SPEED = 260f
private const val CLOUD_SPEED = 40f
