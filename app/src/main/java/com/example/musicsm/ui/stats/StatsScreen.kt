package com.example.musicsm.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.ArtistPlayCount
import com.example.musicsm.domain.model.DailyPlayCount
import com.example.musicsm.domain.model.HourlyPlayCount
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongPlayCount
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.currentLocale
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.StitchBackground
import com.example.musicsm.ui.theme.SurfaceLow
import com.example.musicsm.ui.theme.Teal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

/**
 * "Your year in music"-style summary built from the `play_events` log: totals, top songs and
 * artists, a day-by-day activity chart and a 24-hour listening clock.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(StitchBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(OverlayTint.copy(alpha = 0.05f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.action_back),
                    tint = OnDark,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnDark,
                modifier = Modifier.weight(1f),
            )
            if (!stats.isEmpty) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { confirmClear = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.stats_clear_title),
                        tint = OnDarkVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        RangeChips(selected = range, onSelect = viewModel::setRange)

        if (stats.isEmpty) {
            EmptyStats(Modifier.weight(1f))
        } else {
            StatsBody(
                stats = stats,
                onPlaySongs = onPlaySongs,
                onSearchArtist = { name ->
                    viewModel.searchArtist(name)
                    onOpenSearch()
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.stats_clear_confirm_title)) },
            text = { Text(stringResource(R.string.stats_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClear = false
                    },
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StatsBody(
    stats: ListeningStats,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onSearchArtist: (String) -> Unit,
) {
    val topSongList = remember(stats.topSongs) { stats.topSongs.map { it.song } }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp + LocalBottomBarPadding.current)) {
        item(key = "summary") { SummaryGrid(stats) }
        item(key = "highlights") { Highlights(stats) }

        if (stats.byDay.isNotEmpty()) {
            item(key = "activity-header") { StatsSectionHeader(stringResource(R.string.stats_section_activity)) }
            item(key = "activity") { ActivityChart(stats.byDay) }
        }

        if (stats.topArtists.isNotEmpty()) {
            item(key = "artists-header") { StatsSectionHeader(stringResource(R.string.stats_section_top_artists)) }
            item(key = "artists") { TopArtistsRow(stats.topArtists, onSearchArtist) }
        }

        if (stats.byHour.isNotEmpty()) {
            item(key = "clock-header") { StatsSectionHeader(stringResource(R.string.stats_section_clock)) }
            item(key = "clock") { ListeningClock(stats.byHour) }
        }

        if (stats.topSongs.isNotEmpty()) {
            item(key = "songs-header") { StatsSectionHeader(stringResource(R.string.stats_section_top_songs)) }
            itemsIndexed(stats.topSongs, key = { _, it -> it.song.id }) { index, entry ->
                TopSongRow(
                    rank = index + 1,
                    entry = entry,
                    onClick = { onPlaySongs(topSongList, index) },
                )
            }
        }
    }
}

@Composable
private fun RangeChips(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { range ->
            val active = range == selected
            Text(
                text = stringResource(range.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) OnAccent else OnDarkVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Coral else SurfaceLow)
                    .clickable { onSelect(range) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryGrid(stats: ListeningStats) {
    val minutes = (stats.totalMs / 60_000L).toInt()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(formatCount(minutes), stringResource(R.string.stats_metric_minutes), Coral, Modifier.weight(1f))
            MetricCard(formatCount(stats.totalPlays), stringResource(R.string.stats_metric_plays), Lavender, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(formatCount(stats.distinctSongs), stringResource(R.string.stats_metric_songs), Teal, Modifier.weight(1f))
            MetricCard(formatCount(stats.distinctArtists), stringResource(R.string.stats_metric_artists), OnDark, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
        Text(label.uppercase(currentLocale()), style = MaterialTheme.typography.labelSmall, color = OnDarkVariant)
    }
}

@Composable
private fun Highlights(stats: ListeningStats) {
    val streak = stats.currentStreakDays
    val streakLine = if (streak > 1) {
        stringResource(
            R.string.stats_streak,
            pluralStringResource(R.plurals.stats_streak_days, streak, streak),
        )
    } else {
        null
    }
    val peakLine = stats.peakHour?.let { stringResource(R.string.stats_peak_hour, formatHour(it)) }
    val sinceLine = stats.firstPlayedAt
        ?.takeIf { stats.range == StatsRange.ALL_TIME }
        ?.let { stringResource(R.string.stats_since, formatDate(it)) }

    val lines = listOfNotNull(streakLine, peakLine, sinceLine)
    if (lines.isEmpty()) return
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = OnDark) }
    }
}

/**
 * One bar per day between the first and last day that has data, so gaps read as gaps rather
 * than being silently collapsed.
 */
@Composable
private fun ActivityChart(byDay: List<DailyPlayCount>) {
    val bars = remember(byDay) {
        val counts = byDay.associate { it.epochDay to it.playCount }
        val first = byDay.minOf { it.epochDay }
        val last = byDay.maxOf { it.epochDay }
        // Cap the span so "all time" on an old library stays readable.
        val start = max(first, last - MAX_CHART_DAYS + 1)
        (start..last).map { day -> DailyPlayCount(day, counts[day] ?: 0) }
    }
    val peak = remember(bars) { bars.maxOf { it.playCount }.coerceAtLeast(1) }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLow)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            bars.forEach { bar ->
                val fraction by animateFloatAsState(
                    targetValue = bar.playCount.toFloat() / peak,
                    animationSpec = tween(400),
                    label = "activityBar",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceIn(MIN_BAR_FRACTION, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (bar.playCount > 0) Coral else OverlayTint.copy(alpha = 0.08f)),
                )
            }
        }
    }
}

/** 24 bars, midnight to midnight, showing which part of the day the user listens in. */
@Composable
private fun ListeningClock(byHour: List<HourlyPlayCount>) {
    val counts = remember(byHour) { byHour.associate { it.hour to it.playCount } }
    val peak = remember(counts) { (counts.values.maxOrNull() ?: 0).coerceAtLeast(1) }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLow)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            (0..23).forEach { hour ->
                val count = counts[hour] ?: 0
                val fraction by animateFloatAsState(
                    targetValue = count.toFloat() / peak,
                    animationSpec = tween(400),
                    label = "clockBar",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceIn(MIN_BAR_FRACTION, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (count > 0) Lavender else OverlayTint.copy(alpha = 0.08f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18).forEach {
                Text(formatHour(it), style = MaterialTheme.typography.labelSmall, color = OnDarkVariant)
            }
            Text(formatHour(23), style = MaterialTheme.typography.labelSmall, color = OnDarkVariant)
        }
    }
}

@Composable
private fun TopArtistsRow(artists: List<ArtistPlayCount>, onSearchArtist: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(artists, key = { it.name }) { artist ->
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSearchArtist(artist.name) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArtworkImage(
                    url = artist.artworkUrl,
                    shape = CircleShape,
                    modifier = Modifier.size(84.dp),
                    contentDescription = artist.name,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    pluralStringResource(R.plurals.stats_play_count, artist.playCount, artist.playCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TopSongRow(rank: Int, entry: SongPlayCount, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) Coral else OnDarkVariant,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            modifier = Modifier.width(28.dp),
        )
        ArtworkImage(url = entry.song.artworkUrl, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = OnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            pluralStringResource(R.plurals.stats_play_count, entry.playCount, entry.playCount),
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkVariant,
        )
    }
}

@Composable
private fun StatsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = OnDark,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnDark,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.stats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun StatsRange.labelRes(): Int = when (this) {
    StatsRange.LAST_4_WEEKS -> R.string.stats_range_4_weeks
    StatsRange.LAST_6_MONTHS -> R.string.stats_range_6_months
    StatsRange.THIS_YEAR -> R.string.stats_range_this_year
    StatsRange.ALL_TIME -> R.string.stats_range_all_time
}

/** 12 345 -> "12.3K" so the metric cards never wrap. */
private fun formatCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000.0)
    else -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
}

private fun formatHour(hour: Int): String =
    LocalDate.now().atStartOfDay().withHour(hour.coerceIn(0, 23)).format(HOUR_FORMAT)

private fun formatDate(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(DATE_FORMAT)

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private const val MAX_CHART_DAYS = 90L
private const val MIN_BAR_FRACTION = 0.04f
