package com.example.musicsm.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicsm.R
import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.ArtworkAsyncImage
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ArtworkSize
import com.example.musicsm.ui.components.rememberArtworkPx
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceLow
import java.util.Locale
import kotlin.math.abs

/**
 * Apple Music / Stitch-style live lyrics. Colored artwork-driven backdrop, a now-playing header
 * chip, and a karaoke list: active line is big + bright, others dim. Auto-scrolls; taps seek.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsPanel(
    lyricsState: LyricsState,
    positionMs: Long,
    song: Song?,
    onSeekMs: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    lyricsOffsetMs: Long = 0L,
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onSetLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {},
    lyricsSync: LyricsSyncState = LyricsSyncState.Idle,
    onSyncNow: () -> Unit = {},
    onAutoSync: () -> Unit = {},
) {
    val accent = rememberDominantColorState(song?.artworkUrl, fallback = Coral)
    val loadedLyrics = (lyricsState as? LyricsState.Loaded)?.lyrics
    // Seen lyrics that may be off get lined up with the album audio on their own.
    LaunchedEffect(song?.id, loadedLyrics) { onAutoSync() }
    // A finished sync says so briefly, then the offset chip in the top bar carries the result.
    var showSyncResult by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsSync) {
        showSyncResult = lyricsSync is LyricsSyncState.Synced || lyricsSync is LyricsSyncState.InTime
        if (showSyncResult) {
            kotlinx.coroutines.delay(SYNC_RESULT_VISIBLE_MS)
            showSyncResult = false
        }
    }
    val syncedPositionMs = (positionMs - lyricsOffsetMs).coerceAtLeast(0L)
    val activeIndex = if (loadedLyrics?.synced == true) {
        loadedLyrics.lines.indexOfLast { it.timeMs != null && it.timeMs <= syncedPositionMs }
    } else {
        -1
    }
    val activeLineTimeMs = loadedLyrics?.lines?.getOrNull(activeIndex)?.timeMs
    var showSyncSheet by remember { mutableStateOf(false) }

    // Lyric-card selection: long-press a line to start picking, tap to add/remove.
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }
    var showShareDialog by remember { mutableStateOf(false) }
    val exitSelection = {
        selectionMode = false
        selected.clear()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Opaque frosted backdrop: solid base + blurred artwork + dominant tint (never see-through).
        Box(Modifier.matchParentSize().background(AppBackground))
        if (!song?.artworkUrl.isNullOrEmpty()) {
            ArtworkAsyncImage(
                url = song.artworkUrl,
                targetSizePx = ArtworkSize.TILE,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(80.dp),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(accent.value.copy(alpha = 0.30f))
                    drawRect(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.6f to Color.Black.copy(alpha = 0.55f),
                            1.0f to Color.Black.copy(alpha = 0.85f),
                        ),
                    )
                },
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                IconButton(onClick = exitSelection) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
                Text(
                    stringResource(R.string.lyrics_selected_count, selected.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { if (selected.isNotEmpty()) showShareDialog = true },
                    enabled = selected.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.lyrics_share_action),
                        tint = if (selected.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                    )
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.lyrics_close), tint = Color.White)
                }
                Text(stringResource(R.string.lyrics_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (loadedLyrics != null) {
                    IconButton(onClick = { selectionMode = true }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.lyrics_share_select), tint = Color.White)
                    }
                }
                if (loadedLyrics?.synced == true) {
                    // Matched lyrics sync on their own, so the offset only earns a chip once the
                    // listener has actually changed it.
                    if (lyricsOffsetMs != 0L) {
                        Text(
                            formatLyricsOffset(lyricsOffsetMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { showSyncSheet = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    IconButton(onClick = { showSyncSheet = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.lyrics_sync), tint = Color.White)
                    }
                }
            }
        }

        val syncNote: String? = when {
            loadedLyrics?.synced != true -> null
            lyricsSync is LyricsSyncState.Syncing -> stringResource(R.string.lyrics_syncing)
            showSyncResult && lyricsSync is LyricsSyncState.Synced ->
                stringResource(R.string.lyrics_synced, formatLyricsOffset(lyricsSync.offsetMs))
            showSyncResult && lyricsSync is LyricsSyncState.InTime -> stringResource(R.string.lyrics_sync_in_time)
            !loadedLyrics.timingVerified && lyricsOffsetMs == 0L -> stringResource(R.string.lyrics_timing_approximate)
            else -> null
        }
        if (syncNote != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { showSyncSheet = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (lyricsSync is LyricsSyncState.Syncing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    syncNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }

        // Now-playing header chip
        if (song != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(url = song.artworkUrl, shape = RoundedCornerShape(10.dp), targetSizePx = rememberArtworkPx(44.dp), modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        when (lyricsState) {
            is LyricsState.Loading, is LyricsState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            is LyricsState.None -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.lyrics_empty), color = Color.White.copy(alpha = 0.7f))
            }

            is LyricsState.Loaded -> {
                val lyrics = lyricsState.lyrics
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 160.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        val isSelected = selected.contains(index)
                        // Smoothly interpolate 0 (inactive) -> 1 (active) and drive scale + fade
                        // off it, so the highlight glides between lines instead of snapping.
                        // Fixed font size (no per-frame reflow) ? emphasis comes from a gentle
                        // center scale + fade, eased over a longer duration so it glides.
                        val t by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0f,
                            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                            label = "lyricT",
                        )
                        // While picking lines, keep every line legible so the user can read what
                        // they're selecting; otherwise dim inactive synced lines as before.
                        val baseAlpha = if (selectionMode) 0.9f else if (lyrics.synced) 0.35f else 0.85f
                        val lineModifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val s = 1f + 0.08f * t   // bounded: stays within the side padding
                                    scaleX = s
                                    scaleY = s
                                    alpha = baseAlpha + (1f - baseAlpha) * t
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accent.value.copy(alpha = 0.28f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            if (isSelected) selected.remove(index) else selected.add(index)
                                        } else if (lyrics.synced && line.timeMs != null) {
                                            onSeekMs((line.timeMs + lyricsOffsetMs).coerceAtLeast(0L))
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) selectionMode = true
                                        if (!isSelected) selected.add(index)
                                    },
                                )
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                        if (isActive && lyrics.synced && line.words.isNotEmpty() && !selectionMode) {
                            KaraokeLineText(
                                text = line.text,
                                words = line.words,
                                positionMs = syncedPositionMs,
                                layerAlpha = baseAlpha + (1f - baseAlpha) * t,
                                modifier = lineModifier,
                            )
                        } else {
                            Text(
                                text = line.text.ifBlank { "?" },
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 32.sp,
                                modifier = lineModifier,
                            )
                        }
                    }
                    lyrics.source?.let { source ->
                        item {
                            Text(
                                stringResource(R.string.lyrics_source_credit, source.label),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 24.dp, start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.navigationBarsPadding())
    }
    }

    if (showSyncSheet && loadedLyrics?.synced == true) {
        LyricsSyncSheet(
            offsetMs = lyricsOffsetMs,
            currentLineTimeMs = activeLineTimeMs,
            positionMs = positionMs,
            syncState = lyricsSync,
            onSyncNow = onSyncNow,
            onAdjust = onAdjustLyricsOffset,
            onSetOffset = onSetLyricsOffset,
            onReset = onResetLyricsOffset,
            onDismiss = { showSyncSheet = false },
        )
    }

    if (showShareDialog && song != null) {
        val selectedTexts = selected.sorted()
            .mapNotNull { loadedLyrics?.lines?.getOrNull(it)?.text?.takeIf { t -> t.isNotBlank() } }
        if (selectedTexts.isEmpty()) {
            showShareDialog = false
        } else {
            LyricShareDialog(
                song = song,
                lines = selectedTexts,
                onDismiss = {
                    showShareDialog = false
                    exitSelection()
                },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LyricsSyncSheet(
    offsetMs: Long,
    currentLineTimeMs: Long?,
    positionMs: Long,
    syncState: LyricsSyncState,
    onSyncNow: () -> Unit,
    onAdjust: (Long) -> Unit,
    onSetOffset: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = Coral, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.lyrics_sync_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDark,
                    )
                    Text(
                        stringResource(R.string.lyrics_sync_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkVariant,
                    )
                }
            }

            Spacer(Modifier.size(16.dp))

            Text(
                stringResource(R.string.lyrics_current_offset, formatLyricsOffset(offsetMs)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = OnDark,
            )

            Spacer(Modifier.size(14.dp))

            val syncing = syncState is LyricsSyncState.Syncing
            Button(
                onClick = onSyncNow,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (syncing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(if (syncing) R.string.lyrics_syncing_short else R.string.lyrics_auto_sync))
            }
            Text(
                when (syncState) {
                    LyricsSyncState.Idle, LyricsSyncState.Syncing -> stringResource(R.string.lyrics_auto_sync_hint)
                    is LyricsSyncState.Synced -> stringResource(R.string.lyrics_synced, formatLyricsOffset(syncState.offsetMs))
                    LyricsSyncState.InTime -> stringResource(R.string.lyrics_sync_in_time)
                    LyricsSyncState.AlreadyAlbumAudio -> stringResource(R.string.lyrics_sync_album_audio)
                    LyricsSyncState.NoAlbumAudio -> stringResource(R.string.lyrics_sync_no_album)
                    LyricsSyncState.NoMatch -> stringResource(R.string.lyrics_sync_no_match)
                    LyricsSyncState.Failed -> stringResource(R.string.lyrics_sync_failed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.size(18.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LyricsSyncChip(stringResource(R.string.lyrics_nudge_earlier_1s)) { onAdjust(-1_000L) }
                LyricsSyncChip(stringResource(R.string.lyrics_nudge_earlier_100ms)) { onAdjust(-100L) }
                LyricsSyncChip(stringResource(R.string.lyrics_nudge_later_100ms)) { onAdjust(100L) }
                LyricsSyncChip(stringResource(R.string.lyrics_nudge_later_1s)) { onAdjust(1_000L) }
            }

            Spacer(Modifier.size(12.dp))

            OutlinedButton(
                onClick = { currentLineTimeMs?.let { onSetOffset(positionMs - it) } },
                enabled = currentLineTimeMs != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.lyrics_sync_now), color = OnDark)
            }
            Text(
                stringResource(
                    if (currentLineTimeMs == null) R.string.lyrics_sync_now_unavailable else R.string.lyrics_sync_now_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_reset))
            }

            Spacer(Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LyricsSyncChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = OnDark,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(OverlayTint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

private fun formatLyricsOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return "0.0s"
    val sign = if (offsetMs > 0) "+" else "-"
    return "%s%.1fs".format(Locale.US, sign, abs(offsetMs) / 1_000f)
}

/** How bright the not-yet-sung part of the karaoke line is, before the line fade is applied. */
private const val KARAOKE_UNSUNG_ALPHA = 0.45f

/** How long "Lyrics synced" stays on screen after a sync finishes. */
private const val SYNC_RESULT_VISIBLE_MS = 4_000L

/**
 * The active line with a word-by-word wipe: a dim copy of the text underneath, and a bright copy on
 * top whose unsung part is erased with a soft edge that follows [positionMs] through [words].
 * Both copies share one style, so their layouts (and line wraps) are identical.
 */
@Composable
private fun KaraokeLineText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    layerAlpha: Float,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fadePx = with(LocalDensity.current) { 16.dp.toPx() }
    val sung = sungCharOffset(words, positionMs)
    // The row fades in through [layerAlpha]; cap the dim copy so it never looks brighter than the
    // plain line it replaced, then settles at KARAOKE_UNSUNG_ALPHA once the row is fully shown.
    val unsungAlpha = KARAOKE_UNSUNG_ALPHA.coerceAtMost(layerAlpha) / layerAlpha.coerceAtLeast(0.01f)
    Box(modifier) {
        Text(
            text = text,
            color = Color.White.copy(alpha = unsungAlpha.coerceIn(0f, 1f)),
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    layout?.let { eraseUnsung(it, sung, fadePx) }
                },
        )
    }
}

/**
 * How many characters of the line have been sung at [positionMs], counting fractionally through the
 * word being sung. Infinity once the last word is done.
 */
internal fun sungCharOffset(words: List<LyricWord>, positionMs: Long): Float {
    var sung = 0f
    for (w in words) {
        if (positionMs >= w.endMs) {
            sung = w.charEnd.toFloat()
        } else {
            if (positionMs > w.startMs) {
                val f = (positionMs - w.startMs).toFloat() / (w.endMs - w.startMs)
                sung = w.charStart + (w.charEnd - w.charStart) * f
            }
            return sung
        }
    }
    return Float.POSITIVE_INFINITY
}

/** Erases (DstOut) everything after [sung] characters from the bright layer, feathering the edge. */
private fun DrawScope.eraseUnsung(layout: TextLayoutResult, sung: Float, fadePx: Float) {
    val len = layout.layoutInput.text.length
    if (len == 0 || sung >= len) return
    val firstErasedLine: Int
    if (sung <= 0f) {
        firstErasedLine = 0
    } else {
        val idx = sung.toInt().coerceIn(0, len - 1)
        val frac = sung - idx
        val box = layout.getBoundingBox(idx)
        val rtl = layout.getParagraphDirection(idx) == ResolvedTextDirection.Rtl
        val line = layout.getLineForOffset(idx)
        val top = layout.getLineTop(line)
        val height = layout.getLineBottom(line) - top
        if (rtl) {
            val x = box.right - box.width * frac
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = x - fadePx / 2, endX = x + fadePx / 2),
                topLeft = Offset(0f, top),
                size = Size((x + fadePx / 2).coerceAtLeast(0f), height),
                blendMode = BlendMode.DstOut,
            )
        } else {
            val x = box.left + box.width * frac
            val left = (x - fadePx / 2).coerceAtLeast(0f)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = x - fadePx / 2, endX = x + fadePx / 2),
                topLeft = Offset(left, top),
                size = Size((size.width - left).coerceAtLeast(0f), height),
                blendMode = BlendMode.DstOut,
            )
        }
        firstErasedLine = line + 1
    }
    for (l in firstErasedLine until layout.lineCount) {
        val top = layout.getLineTop(l)
        drawRect(
            color = Color.Black,
            topLeft = Offset(0f, top),
            size = Size(size.width, layout.getLineBottom(l) - top),
            blendMode = BlendMode.DstOut,
        )
    }
}
