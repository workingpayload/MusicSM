package com.example.musicsm.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Earbuds
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.playback.AudioOutputDevice
import com.example.musicsm.playback.AudioOutputKind
import com.example.musicsm.playback.AudioOutputState
import com.example.musicsm.ui.components.isGlassAllowed
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.SurfaceLow
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.roundToInt

/**
 * Output switcher as a floating Liquid Glass card: the screen behind it is blurred, saturated and
 * bent by a lens at the card's rounded edge, the way Apple's Liquid Glass refracts what it sits
 * on. The lens needs Android 13 (RuntimeShader); Android 12 keeps the blur and vibrancy.
 *
 * [backdrop] must record the Now Playing content (artwork *and* controls) — refraction of the
 * already-blurred artwork alone would be invisible. Drawn in-window rather than as a
 * `ModalBottomSheet`, whose separate dialog window could not sample it at all.
 */
@Composable
fun OutputPickerSheet(
    visible: Boolean,
    backdrop: Backdrop,
    state: AudioOutputState,
    onOpenSystemPicker: () -> Unit,
    onSelectOutput: (String) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SCRIM_FADE_MS)),
            exit = fadeOut(tween(SCRIM_FADE_MS)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } +
                fadeIn(tween(SCRIM_FADE_MS)),
            exit = slideOutVertically(tween(SHEET_EXIT_MS)) { it } + fadeOut(tween(SHEET_EXIT_MS)),
        ) {
            GlassCard(
                backdrop = backdrop,
                state = state,
                onOpenSystemPicker = onOpenSystemPicker,
                onSelectOutput = onSelectOutput,
                onRequestBluetoothPermission = onRequestBluetoothPermission,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun GlassCard(
    backdrop: Backdrop,
    state: AudioOutputState,
    onOpenSystemPicker: () -> Unit,
    onSelectOutput: (String) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissDistancePx = with(LocalDensity.current) { DRAG_DISMISS_DISTANCE.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    // Without a real blur behind it the pane would show the controls straight through the text,
    // so low-end and pre-Android 12 devices get an opaque surface instead.
    val surface = if (isGlassAllowed()) Color.Black.copy(alpha = 0.30f) else SurfaceLow

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .offset { IntOffset(0, dragY.roundToInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) },
                onDragStopped = { velocity ->
                    if (dragY > dismissDistancePx || velocity > DRAG_DISMISS_VELOCITY) onDismiss() else dragY = 0f
                },
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(SHEET_CORNER) },
                effects = {
                    vibrancy()
                    blur(BLUR_RADIUS.toPx())
                    lens(
                        refractionHeight = LENS_HEIGHT.toPx(),
                        refractionAmount = LENS_AMOUNT.toPx(),
                        depthEffect = true,
                    )
                },
                highlight = { Highlight.Default },
                onDrawSurface = { drawRect(surface) },
            )
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 16.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                stringResource(R.string.audio_output_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClickLabel = openSystemLabel(state), onClick = onOpenSystemPicker),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = openSystemLabel(state),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.outputs.isEmpty()) {
            Text(
                stringResource(R.string.audio_output_none),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        } else {
            state.outputs.forEach { output ->
                OutputDeviceRow(output = output, onClick = { onSelectOutput(output.id) })
            }
        }

        if (state.bluetoothNamePermissionMissing && state.hasBluetoothOutput) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            ) {
                Text(
                    stringResource(R.string.audio_output_bluetooth_permission_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRequestBluetoothPermission) {
                    Text(stringResource(R.string.audio_output_bluetooth_permission_allow), color = Coral)
                }
            }
        }
    }
}

@Composable
private fun openSystemLabel(state: AudioOutputState): String = stringResource(
    if (state.canOpenSystemSwitcher) R.string.audio_output_open_switcher else R.string.audio_output_open_settings,
)

@Composable
private fun OutputDeviceRow(
    output: AudioOutputDevice,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (output.isCurrent) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = output.canSelectInApp && !output.isCurrent, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (output.isCurrent) Coral else Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = outputIcon(output.kind),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            output.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (output.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (output.isCurrent) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.audio_output_current),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

private fun outputIcon(kind: AudioOutputKind): ImageVector = when (kind) {
    AudioOutputKind.BLUETOOTH -> Icons.Outlined.Earbuds
    AudioOutputKind.WIRED,
    AudioOutputKind.USB -> Icons.Outlined.Headphones
    else -> Icons.AutoMirrored.Outlined.VolumeUp
}

private const val SCRIM_FADE_MS = 200
private const val SHEET_EXIT_MS = 220
private val SHEET_CORNER = 32.dp
private val BLUR_RADIUS = 10.dp

// The lens band must stay within the corner radius, or the refraction breaks at the corners.
private val LENS_HEIGHT = 24.dp
private val LENS_AMOUNT = 48.dp
private val DRAG_DISMISS_DISTANCE = 96.dp
private const val DRAG_DISMISS_VELOCITY = 1_200f
