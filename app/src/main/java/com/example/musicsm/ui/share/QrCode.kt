package com.example.musicsm.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** A QR symbol as a square grid of on/off modules, ready to draw. */
class QrMatrix internal constructor(private val rows: Array<BooleanArray>) {
    val size: Int get() = rows.size

    operator fun get(x: Int, y: Int): Boolean = rows[y][x]
}

/**
 * Encodes [content] as a QR symbol, or returns null when it simply will not fit — a QR code holds
 * about 2,950 bytes at the lowest error correction, and a long playlist can exceed that. Callers
 * are expected to fall back to sharing the plain link.
 *
 * Uses the low-level [Encoder] rather than `QRCodeWriter` so we get the exact module grid instead
 * of a pre-scaled bitmap, which keeps the code crisp at any size.
 */
fun qrMatrixOrNull(content: String): QrMatrix? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        // A phone screen is a clean, well-lit target, so trade redundancy for capacity.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
    )
    val matrix = Encoder.encode(content, ErrorCorrectionLevel.L, hints).matrix ?: return null
    val rows = Array(matrix.height) { y ->
        BooleanArray(matrix.width) { x -> matrix.get(x, y).toInt() == 1 }
    }
    QrMatrix(rows)
}.getOrNull()

/**
 * Draws a QR symbol.
 *
 * The light background and [quietZoneModules] border are part of the spec, not decoration:
 * scanners need the contrast and the margin, which is why this always paints its own light
 * backdrop instead of inheriting the app's dark surface.
 */
@Composable
fun QrCode(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
    foreground: Color = Color(0xFF111111),
    background: Color = Color.White,
    quietZoneModules: Int = 4,
) {
    // Nudge each module outward by a hair so neighbours meet cleanly on fractional pixel sizes;
    // hairline gaps between modules are a classic cause of failed scans.
    val overdraw = 0.5f

    Canvas(modifier.aspectRatio(1f)) {
        val modules = matrix.size + quietZoneModules * 2
        val scale = size.minDimension / modules
        val origin = quietZoneModules * scale

        drawRect(color = background, topLeft = Offset.Zero, size = size)

        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                drawRect(
                    color = foreground,
                    topLeft = Offset(origin + x * scale, origin + y * scale),
                    size = Size(scale + overdraw, scale + overdraw),
                )
            }
        }
    }
}

/** Convenience wrapper: encodes and remembers the matrix for [content]. */
@Composable
fun rememberQrMatrix(content: String): QrMatrix? = remember(content) { qrMatrixOrNull(content) }
