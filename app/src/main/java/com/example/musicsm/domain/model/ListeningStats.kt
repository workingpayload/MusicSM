package com.example.musicsm.domain.model

/** Which slice of history the listening-stats screen is showing. */
enum class StatsRange {
    LAST_4_WEEKS,
    LAST_6_MONTHS,
    THIS_YEAR,
    ALL_TIME,
}

/** A song together with how often it was played in the selected range. */
data class SongPlayCount(
    val song: Song,
    val playCount: Int,
)

/** An artist aggregated across every one of their songs played in the selected range. */
data class ArtistPlayCount(
    val name: String,
    val artworkUrl: String?,
    val playCount: Int,
    val songCount: Int,
    val totalMs: Long,
)

/** One bar of the activity chart: plays on a single local calendar day. */
data class DailyPlayCount(
    val epochDay: Long,
    val playCount: Int,
)

/** Plays that started within a given local hour (0-23), used for the "listening clock". */
data class HourlyPlayCount(
    val hour: Int,
    val playCount: Int,
)

/**
 * Everything the listening-stats screen renders for one [range]. [totalMs] is the sum of track
 * durations, i.e. an upper bound that assumes each started track was heard to the end.
 */
data class ListeningStats(
    val range: StatsRange = StatsRange.LAST_4_WEEKS,
    val totalPlays: Int = 0,
    val totalMs: Long = 0L,
    val distinctSongs: Int = 0,
    val distinctArtists: Int = 0,
    val firstPlayedAt: Long? = null,
    val topSongs: List<SongPlayCount> = emptyList(),
    val topArtists: List<ArtistPlayCount> = emptyList(),
    val byDay: List<DailyPlayCount> = emptyList(),
    val byHour: List<HourlyPlayCount> = emptyList(),
) {
    val isEmpty: Boolean get() = totalPlays == 0

    /** The hour of day with the most plays, or null when there is nothing to show. */
    val peakHour: Int? get() = byHour.maxByOrNull { it.playCount }?.hour

    /** Longest run of consecutive days with at least one play, ending at the most recent one. */
    val currentStreakDays: Int
        get() {
            if (byDay.isEmpty()) return 0
            val days = byDay.filter { it.playCount > 0 }.map { it.epochDay }.toSortedSet()
            if (days.isEmpty()) return 0
            var streak = 1
            var cursor = days.last()
            while (days.contains(cursor - 1)) {
                streak++
                cursor--
            }
            return streak
        }
}
