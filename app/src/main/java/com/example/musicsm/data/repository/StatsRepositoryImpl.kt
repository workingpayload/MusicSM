package com.example.musicsm.data.repository

import com.example.musicsm.data.local.dao.StatsDao
import com.example.musicsm.data.local.entity.toSong
import com.example.musicsm.domain.model.ArtistPlayCount
import com.example.musicsm.domain.model.DailyPlayCount
import com.example.musicsm.domain.model.HourlyPlayCount
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.SongPlayCount
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
) : StatsRepository {

    override fun stats(range: StatsRange): Flow<ListeningStats> {
        val since = startOf(range)
        // Two combines because combine() tops out at five flows.
        val totals = combine(
            statsDao.totalPlays(since),
            statsDao.totalListeningMs(since),
            statsDao.distinctSongs(since),
            statsDao.distinctArtists(since),
            statsDao.firstPlayedAt(),
        ) { plays, ms, songs, artists, first -> Totals(plays, ms, songs, artists, first) }

        return combine(
            totals,
            statsDao.topSongs(since, TOP_LIMIT),
            statsDao.topArtists(since, TOP_LIMIT),
            statsDao.playsByDay(since),
            statsDao.playsByHour(since),
        ) { t, songs, artists, days, hours ->
            ListeningStats(
                range = range,
                totalPlays = t.plays,
                totalMs = t.totalMs,
                distinctSongs = t.songs,
                distinctArtists = t.artists,
                firstPlayedAt = t.firstPlayedAt,
                topSongs = songs.map { SongPlayCount(it.song.toSong(), it.playCount) },
                topArtists = artists.map {
                    ArtistPlayCount(
                        name = it.artist,
                        artworkUrl = it.artworkUrl,
                        playCount = it.playCount,
                        songCount = it.songCount,
                        totalMs = it.totalMs,
                    )
                },
                byDay = days.mapNotNull { row ->
                    parseEpochDay(row.day)?.let { DailyPlayCount(it, row.playCount) }
                },
                byHour = hours.map { HourlyPlayCount(it.hour, it.playCount) },
            )
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun clear() = statsDao.clear()

    private data class Totals(
        val plays: Int,
        val totalMs: Long,
        val songs: Int,
        val artists: Int,
        val firstPlayedAt: Long?,
    )

    /** Inclusive lower bound for [range], in epoch millis. */
    private fun startOf(range: StatsRange): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = when (range) {
            StatsRange.LAST_4_WEEKS -> today.minusWeeks(4)
            StatsRange.LAST_6_MONTHS -> today.minusMonths(6)
            StatsRange.THIS_YEAR -> today.withDayOfYear(1)
            StatsRange.ALL_TIME -> return 0L
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun parseEpochDay(day: String): Long? =
        runCatching { LocalDate.parse(day, DAY_FORMAT).toEpochDay() }.getOrNull()

    private companion object {
        const val TOP_LIMIT = 25
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
