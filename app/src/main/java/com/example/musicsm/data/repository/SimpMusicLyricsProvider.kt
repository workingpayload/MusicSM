package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * SimpMusic's lyrics database, looked up by the exact YouTube video id. A video may still have
 * several submitted lyric entries, so the recording length is used only to choose between those.
 */
@Singleton
class SimpMusicLyricsProvider @Inject constructor(
    private val client: OkHttpClient,
) : LyricsProvider {

    override val source = LyricsSource.SIMPMUSIC

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? {
        val videoId = song.id.takeIf { it.isNotBlank() } ?: return null
        val body = client.getText(BASE + videoId, mapOf("Accept" to "application/json")) ?: return null
        return parseSimpMusicLyricsResponse(body, song.durationMs)
    }

    private companion object {
        const val BASE = "https://api-lyrics.simpmusic.org/v1/"
    }
}

internal data class SimpMusicLyricsEntry(
    val durationSec: Double?,
    val richSyncLyrics: String?,
    val syncedLyrics: String?,
    val plainLyrics: String?,
)

internal fun parseSimpMusicLyricsResponse(json: String, targetMs: Long): Lyrics? {
    val root = runCatching { LYRICS_JSON.parseToJsonElement(json) }.getOrNull()?.smObj() ?: return null
    if (root.smBool("success") != true) return null
    val entries = (root["data"] as? JsonArray).orEmpty().mapNotNull { it.smObj()?.toSimpMusicEntry() }
    return simpmusicEntryToLyrics(chooseSimpMusicEntry(entries, targetMs), targetMs)
}

internal fun chooseSimpMusicEntry(entries: List<SimpMusicLyricsEntry>, targetMs: Long): SimpMusicLyricsEntry? {
    val withLyrics = entries.filter { it.richSyncLyrics != null || it.syncedLyrics != null || it.plainLyrics != null }
    if (withLyrics.isEmpty()) return null
    val targetSec = targetMs / 1000.0
    if (targetSec <= 0.0) return withLyrics.first()
    return withLyrics
        .filter { it.durationSec != null && abs(it.durationSec - targetSec) <= SIMPMUSIC_DURATION_TOLERANCE_SEC }
        .minByOrNull { abs((it.durationSec ?: 0.0) - targetSec) }
}

internal fun simpmusicEntryToLyrics(entry: SimpMusicLyricsEntry?, targetMs: Long): Lyrics? {
    entry ?: return null
    val timingVerified = targetMs > 0 && entry.durationSec != null &&
        abs(entry.durationSec - targetMs / 1000.0) <= TIMING_TOLERANCE_SEC

    entry.richSyncLyrics?.let { rich ->
        val lines = parseLrcLines(decodeSimpMusicRichSync(rich))
        if (lines.any { it.words.isNotEmpty() } && lines.any { it.text.isNotBlank() }) {
            return Lyrics(synced = true, lines = lines, timingVerified = timingVerified, source = LyricsSource.SIMPMUSIC)
        }
    }

    entry.syncedLyrics?.let { lrc ->
        val lines = parseLrcLines(lrc)
        if (lines.any { it.timeMs != null && it.text.isNotBlank() }) {
            return Lyrics(synced = true, lines = lines, timingVerified = timingVerified, source = LyricsSource.SIMPMUSIC)
        }
    }

    entry.plainLyrics?.let { plain ->
        val lines = plainLyricLines(plain)
        if (lines.any { it.text.isNotBlank() }) {
            return Lyrics(synced = false, lines = lines, timingVerified = false, source = LyricsSource.SIMPMUSIC)
        }
    }
    return null
}

private fun JsonObject.toSimpMusicEntry(): SimpMusicLyricsEntry = SimpMusicLyricsEntry(
    durationSec = smNumber("duration"),
    richSyncLyrics = smString("richSyncLyrics")?.takeIf { it.isNotBlank() },
    syncedLyrics = smString("syncedLyrics")?.takeIf { it.isNotBlank() },
    plainLyrics = smString("plainLyrics")?.takeIf { it.isNotBlank() },
)

/** SimpMusic rich sync has been seen with HTML-escaped apostrophes and spaces. */
internal fun decodeSimpMusicRichSync(text: String): String {
    if ('&' !in text) return text
    return text
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}

private const val SIMPMUSIC_DURATION_TOLERANCE_SEC = 10.0

internal val LYRICS_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun JsonElement.smObj(): JsonObject? = this as? JsonObject

internal fun JsonObject.smString(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.smNumber(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.smBool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
