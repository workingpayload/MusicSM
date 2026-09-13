package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Lyrics from LRCLIB (https://lrclib.net) — free, key-less, returns time-synced LRC when available.
 * Matches by track + artist, then prefers synced lyrics whose duration is closest to the track.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor() : LyricsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun forSong(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val track = clean(song.title)
        val artist = clean(song.artist)
        if (track.isBlank()) return@withContext null

        val url = "https://lrclib.net/api/search" +
            "?track_name=${enc(track)}&artist_name=${enc(artist)}"
        val json = runCatching { get(url) }.getOrNull() ?: return@withContext null

        val results = runCatching { JSONArray(json) }.getOrNull() ?: return@withContext null
        if (results.length() == 0) return@withContext null

        val targetSec = song.durationMs / 1000.0
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val hasSynced = !o.optString("syncedLyrics").isNullOrBlank()
            val dur = o.optDouble("duration", 0.0)
            // Prefer synced; then closest duration.
            val durPenalty = if (targetSec > 0) abs(dur - targetSec) else 0.0
            val score = (if (hasSynced) 0.0 else 10_000.0) + durPenalty
            if (score < bestScore) {
                bestScore = score
                best = o
            }
        }
        val chosen = best ?: return@withContext null

        val synced = chosen.optString("syncedLyrics")
        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return@withContext Lyrics(synced = true, lines = lines)
        }
        val plain = chosen.optString("plainLyrics")
        if (!plain.isNullOrBlank()) {
            val lines = plain.split("\n").map { LyricLine(timeMs = null, text = it.trim()) }
            return@withContext Lyrics(synced = false, lines = lines)
        }
        null
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusicSM/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    /** Parse an LRC blob into timestamped lines, expanding multi-timestamp lines. */
    private fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        val tagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        for (raw in lrc.split("\n")) {
            val matches = tagRegex.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                val ms = (min * 60 + sec) * 1000 + frac
                out.add(LyricLine(timeMs = ms, text = text))
            }
        }
        return out.sortedBy { it.timeMs ?: 0L }
    }

    /** Strip common noise from YouTube titles/artists to improve matching. */
    private fun clean(s: String): String {
        var r = s
        r = r.replace(Regex("""\((?:official|lyric|audio|video|visualizer|hd|4k|mv)[^)]*\)""", RegexOption.IGNORE_CASE), "")
        r = r.replace(Regex("""\[[^]]*]"""), "")
        r = r.replace(Regex(""" - Topic$""", RegexOption.IGNORE_CASE), "")
        r = r.replace(Regex("""(?:official|lyric[s]?|audio|video|visualizer)""", RegexOption.IGNORE_CASE), "")
        return r.replace(Regex("""\s+"""), " ").trim(' ', '-', '|', '·')
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
