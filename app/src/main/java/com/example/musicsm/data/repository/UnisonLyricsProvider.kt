package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Unison is a community lyrics database. It answers with one entry whose payload declares whether
 * the lyrics are TTML, LRC/enhanced LRC, or plain text.
 */
@Singleton
class UnisonLyricsProvider @Inject constructor(
    private val client: OkHttpClient,
) : LyricsProvider {

    override val source = LyricsSource.UNISON

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("song", track)
            .addQueryParameter("artist", artist)
            .apply {
                song.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album", it) }
                val seconds = song.durationMs / 1000
                if (seconds > 0) addQueryParameter("duration", seconds.toString())
            }
            .build()
        val body = client.getText(url.toString(), mapOf("Accept" to "application/json")) ?: return null
        return parseUnisonLyricsResponse(body, song.durationMs)
    }

    private companion object {
        const val ENDPOINT = "https://unison.boidu.dev/lyrics"
    }
}

internal data class UnisonLyricsEntry(
    val lyrics: String,
    val format: String?,
    val syncType: String?,
    val durationSec: Double?,
)

internal fun parseUnisonLyricsResponse(json: String, targetMs: Long): Lyrics? {
    val root = runCatching { LYRICS_JSON.parseToJsonElement(json) }.getOrNull()?.smObj() ?: return null
    if (root.smBool("success") != true) return null
    val data = root["data"]
    val entry = when (data) {
        is JsonObject -> data.toUnisonEntry()
        is JsonArray -> data.firstNotNullOfOrNull { it.smObj()?.toUnisonEntry() }
        else -> null
    } ?: return null
    return unisonEntryToLyrics(entry, targetMs)
}

internal fun unisonEntryToLyrics(entry: UnisonLyricsEntry, targetMs: Long): Lyrics? {
    val syncType = entry.syncType.orEmpty().lowercase()
    val format = entry.format.orEmpty().lowercase()
    val timingVerifiedByEntry = targetMs > 0 && entry.durationSec != null &&
        abs(entry.durationSec - targetMs / 1000.0) <= TIMING_TOLERANCE_SEC

    if (syncType == "plain") {
        val lines = plainLyricLines(entry.lyrics)
        return lines.takeIf { it.any { line -> line.text.isNotBlank() } }
            ?.let { Lyrics(synced = false, lines = it, timingVerified = false, source = LyricsSource.UNISON) }
    }

    if (format == "ttml") {
        val doc = runCatching { parseTtml(entry.lyrics) }.getOrNull() ?: return null
        val synced = doc.lines.any { it.timeMs != null }
        val durationSec = doc.durationSec ?: entry.durationSec
        val timingVerified = synced && targetMs > 0 && durationSec != null &&
            abs(durationSec - targetMs / 1000.0) <= TIMING_TOLERANCE_SEC
        val lines = if (synced) doc.lines.filter { it.timeMs != null } else doc.lines
        return lines.takeIf { it.any { line -> line.text.isNotBlank() } }
            ?.let { Lyrics(synced = synced, lines = it, timingVerified = timingVerified, source = LyricsSource.UNISON) }
    }

    if (format == "lrc" || syncType == "linesync" || syncType == "wordsync" || syncType == "richsync") {
        val lines = parseLrcLines(entry.lyrics)
        if (lines.any { it.timeMs != null && it.text.isNotBlank() }) {
            return Lyrics(synced = true, lines = lines, timingVerified = timingVerifiedByEntry, source = LyricsSource.UNISON)
        }
    }

    val plain = plainLyricLines(entry.lyrics)
    return plain.takeIf { it.any { line -> line.text.isNotBlank() } }
        ?.let { Lyrics(synced = false, lines = it, timingVerified = false, source = LyricsSource.UNISON) }
}

private fun JsonObject.toUnisonEntry(): UnisonLyricsEntry? {
    val lyrics = smString("lyrics")?.takeIf { it.isNotBlank() } ?: return null
    return UnisonLyricsEntry(
        lyrics = lyrics,
        format = smString("format"),
        syncType = smString("syncType"),
        durationSec = smNumber("duration"),
    )
}
