package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** One BiniLyrics search result, enough to reuse the exact recording in later sources. */
data class BiniHit(
    val isrc: String,
    val lyricsUrl: String?,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val durationSec: Double?,
    val timingType: String?,
)

/**
 * Apple Music TTML from BiniLyrics. A name search reports the matched recording's ISRC, while an
 * ISRC lookup returns that recording's TTML directly.
 */
@Singleton
class BiniLyricsProvider @Inject constructor(
    private val client: OkHttpClient,
) : IsrcLyricsProvider {

    override val source = LyricsSource.BINI_LYRICS

    suspend fun identify(track: String, artist: String, durationMs: Long, album: String?): BiniHit? =
        withContext(Dispatchers.IO) {
            val url = BASE.toHttpUrl().newBuilder()
                .addQueryParameter("track", track)
                .addQueryParameter("artist", artist)
                .apply {
                    val seconds = durationMs / 1000
                    if (seconds > 0) addQueryParameter("duration", seconds.toString())
                    album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album", it) }
                }
                .build()
            val body = client.getText(url.toString(), mapOf("Accept" to "application/json")) ?: return@withContext null
            chooseBiniHit(parseBiniHits(body), track, artist, durationMs)
        }

    override suspend fun fetch(song: Song, track: String, artist: String, isrc: String?): Lyrics? =
        withContext(Dispatchers.IO) {
            val hit = if (isrc.isNullOrBlank()) {
                identify(track, artist, song.durationMs, song.album)
            } else {
                BiniHit(
                    isrc = isrc,
                    lyricsUrl = null,
                    trackName = track,
                    artistName = artist,
                    albumName = null,
                    durationSec = null,
                    timingType = null,
                )
            } ?: return@withContext null

            val body = when {
                hit.lyricsUrl != null -> client.getText(hit.lyricsUrl, mapOf("Accept" to "application/xml, text/xml, */*"))
                else -> {
                    val url = BASE.toHttpUrl().newBuilder().addQueryParameter("isrc", hit.isrc).build()
                    client.getText(url.toString(), mapOf("Accept" to "application/xml, text/xml, */*"))
                }
            } ?: return@withContext null

            val doc = when {
                body.trimStart().startsWith("<") -> runCatching { parseTtml(body) }.getOrNull()
                else -> {
                    val chosen = chooseBiniHit(parseBiniHits(body), track, artist, song.durationMs, hit.isrc) ?: return@withContext null
                    val url = chosen.lyricsUrl ?: return@withContext null
                    val ttml = client.getText(url, mapOf("Accept" to "application/xml, text/xml, */*")) ?: return@withContext null
                    runCatching { parseTtml(ttml) }.getOrNull()
                }
            } ?: return@withContext null

            val lines = doc.lines.filter { it.text.isNotBlank() }
            if (lines.isEmpty()) return@withContext null
            val synced = lines.any { it.timeMs != null }
            Lyrics(
                synced = synced,
                lines = if (synced) lines.filter { it.timeMs != null } else lines,
                timingVerified = synced && biniTimingVerified(song.durationMs, doc.durationSec ?: hit.durationSec),
                source = source,
            )
        }

    private companion object {
        const val BASE = "https://lyrics-api.binimum.org/"
    }
}

internal fun parseBiniHits(json: String): List<BiniHit> = runCatching {
    Json.parseToJsonElement(json).jsonObject["results"]?.jsonArray.orEmpty().mapNotNull { element ->
        val o = element.jsonObject
        val isrc = o.text("isrc") ?: o.text("id") ?: return@mapNotNull null
        BiniHit(
            isrc = isrc,
            lyricsUrl = o.text("lyricsUrl"),
            trackName = o.text("track_name"),
            artistName = o.text("artist_name"),
            albumName = o.text("album_name"),
            durationSec = o.number("duration"),
            timingType = o.text("timing_type"),
        )
    }
}.getOrDefault(emptyList())

internal fun chooseBiniHit(
    hits: List<BiniHit>,
    track: String,
    artist: String,
    durationMs: Long,
    isrc: String? = null,
): BiniHit? {
    val targetSec = durationMs / 1000.0
    val exactIsrc = !isrc.isNullOrBlank()
    return hits.asSequence()
        .filter { !exactIsrc || it.isrc.equals(isrc, ignoreCase = true) }
        .filter { exactIsrc || it.trackName?.let { name -> namesMatch(name, track) } == true }
        .filter { exactIsrc || artist.isBlank() || it.artistName?.let { name -> namesMatch(name, artist) } == true }
        .filter { hit ->
            targetSec <= 0.0 || hit.durationSec == null || abs(hit.durationSec - targetSec) <= MAX_BINI_DRIFT_SEC
        }
        .minWithOrNull(
            compareBy<BiniHit> {
                if (targetSec > 0.0 && it.durationSec != null) abs(it.durationSec - targetSec) else Double.MAX_VALUE
            }.thenBy { if (it.timingType.equals("word", ignoreCase = true)) 0 else 1 },
        )
}

internal fun biniTimingVerified(durationMs: Long, matchedDurationSec: Double?): Boolean {
    val targetSec = durationMs / 1000.0
    return targetSec > 0.0 && matchedDurationSec != null &&
        abs(matchedDurationSec - targetSec) <= TIMING_TOLERANCE_SEC
}

private const val MAX_BINI_DRIFT_SEC = 15.0

private fun JsonObject.text(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.number(name: String): Double? =
    get(name)?.jsonPrimitive?.doubleOrNull ?: text(name)?.toDoubleOrNull()
