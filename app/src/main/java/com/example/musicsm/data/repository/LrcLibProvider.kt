package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Lyrics from LRCLIB (https://lrclib.net) — free, key-less, returns time-synced LRC when available.
 *
 * Sync accuracy depends almost entirely on picking the *same recording* that is playing: an album
 * cut, a radio edit, a live take and a music video with a spoken intro all share a title but not
 * their timings. So the lookup is driven by the real length of the audio being played:
 * 1. LRCLIB's exact-match endpoint, which resolves track + artist + duration to one recording.
 * 2. Otherwise a search, ranked by [chooseLyrics] — title-checked, then closest duration.
 */
@Singleton
class LrcLibProvider @Inject constructor(
    private val client: OkHttpClient,
) : LyricsProvider {

    override val source = LyricsSource.LRCLIB

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? = withContext(Dispatchers.IO) {
        val targetSec = song.durationMs / 1000.0
        val exact = if (targetSec > 0 && artist.isNotBlank()) getExact(track, artist, targetSec) else null
        val exactChoice = chooseLyrics(listOfNotNull(exact), track, artist, targetSec)
        if (exactChoice?.timingVerified == true) return@withContext toLyrics(exactChoice)

        val candidates = buildList {
            exact?.let(::add)
            addAll(search(track, artist))
        }
        var choice = chooseLyrics(candidates, track, artist, targetSec)
        // Looser metadata sometimes surfaces a synced entry the artist-qualified search misses.
        if (choice?.timingVerified != true && artist.isNotBlank()) {
            choice = chooseLyrics(candidates + search(track, ""), track, artist, targetSec)
        }
        choice?.let(::toLyrics)
    }

    /** LRCLIB's exact lookup: returns the recording matching track + artist + duration, or null. */
    private suspend fun getExact(track: String, artist: String, targetSec: Double): LyricsCandidate? {
        val url = "https://lrclib.net/api/get" +
            "?track_name=${enc(track)}&artist_name=${enc(artist)}&duration=${targetSec.roundToInt()}"
        val json = runCatching { get(url) }.getOrNull() ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        // The server already matched name and artist, so this candidate is trusted on both.
        return o.toCandidate(trustedMatch = true)
    }

    private suspend fun search(track: String, artist: String): List<LyricsCandidate> {
        val url = "https://lrclib.net/api/search" +
            "?track_name=${enc(track)}" + if (artist.isNotBlank()) "&artist_name=${enc(artist)}" else ""
        val json = runCatching { get(url) }.getOrNull() ?: return emptyList()
        val results = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            results.optJSONObject(i)?.toCandidate(trustedMatch = false)
        }
    }

    private fun JSONObject.toCandidate(trustedMatch: Boolean) = LyricsCandidate(
        trackName = optString("trackName"),
        artistName = optString("artistName"),
        durationSec = optDouble("duration", 0.0),
        syncedLrc = optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" },
        plainLyrics = optString("plainLyrics").takeUnless { it.isBlank() || it == "null" },
        trustedMatch = trustedMatch,
    )

    private fun toLyrics(choice: LyricsChoice): Lyrics? {
        val c = choice.candidate
        c.syncedLrc?.let { synced ->
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) {
                return Lyrics(synced = true, lines = lines, timingVerified = choice.timingVerified, source = source)
            }
        }
        c.plainLyrics?.let { plain ->
            return Lyrics(synced = false, lines = plainLyricLines(plain), source = source)
        }
        return null
    }

    private suspend fun get(url: String): String? = client.getText(url)

    /** Parse an LRC blob into timestamped lines, expanding multi-timestamp lines. */
    private fun parseLrc(lrc: String): List<LyricLine> = parseLrcLines(lrc)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
