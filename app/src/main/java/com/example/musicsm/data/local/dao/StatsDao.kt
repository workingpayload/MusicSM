package com.example.musicsm.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.example.musicsm.data.local.entity.PlayEventEntity
import com.example.musicsm.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/** A song plus how many times it appears in `play_events` for the queried window. */
data class SongPlayCountRow(
    @Embedded val song: SongEntity,
    val playCount: Int,
)

/** An artist aggregated across every song of theirs that was played. */
data class ArtistPlayCountRow(
    val artist: String,
    val artworkUrl: String?,
    val playCount: Int,
    val songCount: Int,
    val totalMs: Long,
)

/** Number of plays on one local calendar day, keyed `yyyy-MM-dd`. */
data class DailyPlayCountRow(
    val day: String,
    val playCount: Int,
)

/** Number of plays that started in a given local hour, 0-23. */
data class HourlyPlayCountRow(
    val hour: Int,
    val playCount: Int,
)

/**
 * Aggregations over the append-only `play_events` log that back the listening-stats screen.
 *
 * Every query is bounded by `playedAt >= :since` so the UI can switch between "last 4 weeks",
 * "this year" and "all time" without changing shape. Day/hour buckets use SQLite's `localtime`
 * modifier so a play at 11pm lands on the day the user actually heard it.
 */
@Dao
interface StatsDao {

    @Insert
    suspend fun record(event: PlayEventEntity)

    @Query("SELECT COUNT(*) FROM play_events WHERE playedAt >= :since")
    fun totalPlays(since: Long): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(s.durationMs), 0) FROM play_events e " +
            "INNER JOIN songs s ON s.songId = e.songId WHERE e.playedAt >= :since",
    )
    fun totalListeningMs(since: Long): Flow<Long>

    @Query("SELECT COUNT(DISTINCT songId) FROM play_events WHERE playedAt >= :since")
    fun distinctSongs(since: Long): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT s.artist) FROM play_events e " +
            "INNER JOIN songs s ON s.songId = e.songId " +
            "WHERE e.playedAt >= :since AND TRIM(s.artist) != ''",
    )
    fun distinctArtists(since: Long): Flow<Int>

    /** Timestamp of the very first recorded play, or null when the log is empty. */
    @Query("SELECT MIN(playedAt) FROM play_events")
    fun firstPlayedAt(): Flow<Long?>

    @Query(
        "SELECT s.*, COUNT(*) AS playCount FROM play_events e " +
            "INNER JOIN songs s ON s.songId = e.songId " +
            "WHERE e.playedAt >= :since " +
            "GROUP BY e.songId ORDER BY playCount DESC, MAX(e.playedAt) DESC LIMIT :limit",
    )
    fun topSongs(since: Long, limit: Int): Flow<List<SongPlayCountRow>>

    @Query(
        "SELECT s.artist AS artist, MAX(s.artworkUrl) AS artworkUrl, COUNT(*) AS playCount, " +
            "COUNT(DISTINCT s.songId) AS songCount, COALESCE(SUM(s.durationMs), 0) AS totalMs " +
            "FROM play_events e INNER JOIN songs s ON s.songId = e.songId " +
            "WHERE e.playedAt >= :since AND TRIM(s.artist) != '' " +
            "GROUP BY s.artist ORDER BY playCount DESC, songCount DESC LIMIT :limit",
    )
    fun topArtists(since: Long, limit: Int): Flow<List<ArtistPlayCountRow>>

    @Query(
        "SELECT strftime('%Y-%m-%d', playedAt / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS playCount FROM play_events WHERE playedAt >= :since " +
            "GROUP BY day ORDER BY day ASC",
    )
    fun playsByDay(since: Long): Flow<List<DailyPlayCountRow>>

    @Query(
        "SELECT CAST(strftime('%H', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
            "COUNT(*) AS playCount FROM play_events WHERE playedAt >= :since " +
            "GROUP BY hour ORDER BY hour ASC",
    )
    fun playsByHour(since: Long): Flow<List<HourlyPlayCountRow>>

    @Query("DELETE FROM play_events")
    suspend fun clear()
}
