package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Syllable-timed lyrics from the YouLy+ LyricsPlus mirrors. */
@Singleton
class LyricsPlusProvider @Inject constructor(
    private val client: OkHttpClient,
) : IsrcLyricsProvider {

    override val source = LyricsSource.LYRICS_PLUS

    override suspend fun fetch(song: Song, track: String, artist: String, isrc: String?): Lyrics? =
        withContext(Dispatchers.IO) {
            for (mirror in MIRRORS) {
                val url = "$mirror/v2/lyrics/get".toHttpUrl().newBuilder()
                    .addQueryParameter("title", track)
                    .addQueryParameter("artist", artist)
                    .apply {
                        val seconds = song.durationMs / 1000
                        if (seconds > 0) addQueryParameter("duration", seconds.toString())
                        song.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album", it) }
                        isrc?.takeIf { it.isNotBlank() }?.let { addQueryParameter("isrc", it) }
                    }
                    .build()
                val body = client.getText(url.toString(), mapOf("Accept" to "application/json")) ?: continue
                val doc = parseLyricsPlusJson(body) ?: continue
                if (!lyricsPlusMetadataMatches(doc, track, artist, song.durationMs, isrc)) continue
                return@withContext Lyrics(
                    synced = true,
                    lines = doc.lines,
                    timingVerified = lyricsPlusTimingVerified(song.durationMs, doc.durationSec),
                    source = source,
                )
            }
            null
        }

    private companion object {
        val MIRRORS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.prjktla.workers.dev",
        )
    }
}

internal data class LyricsPlusDoc(
    val lines: List<LyricLine>,
    val durationSec: Double?,
    val title: String?,
    val artist: String?,
    val isrc: String?,
)

internal fun parseLyricsPlusJson(json: String): LyricsPlusDoc? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val lyrics = root["lyrics"]?.jsonArray ?: return@runCatching null
    val lines = lyrics.mapNotNull { element ->
        val line = element.jsonObject
        if (line.isLyricsPlusBackground()) return@mapNotNull null
        val start = line.long("time") ?: return@mapNotNull null
        val duration = line.long("duration")
        val syllables = line["syllabus"] as? JsonArray
        val (text, words) = syllables?.let(::parseLyricsPlusSyllables) ?: ("" to emptyList())
        when {
            words.isNotEmpty() -> LyricLine(
                timeMs = minOf(start, words.first().startMs),
                text = text,
                words = finishLyricsPlusWords(words, duration?.let { start + it }),
            )
            line.text("text")?.isNotBlank() == true -> LyricLine(timeMs = start, text = line.text("text")!!.trim())
            else -> null
        }
    }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs ?: 0L }
    if (lines.isEmpty()) return@runCatching null

    val metadata = root["metadata"]?.jsonObject
    LyricsPlusDoc(
        lines = lines,
        durationSec = metadata?.text("totalDuration")?.let { parseClock(it)?.div(1000.0) }
            ?: lyricsPlusInferredDurationSec(lines),
        title = metadata?.text("title"),
        artist = metadata?.text("artist"),
        isrc = metadata?.text("isrc"),
    )
}.getOrNull()

internal fun parseLyricsPlusSyllables(syllables: JsonArray): Pair<String, List<LyricWord>> {
    val text = StringBuilder()
    val words = ArrayList<LyricWord>()

    fun append(raw: String, startMs: Long, endMs: Long) {
        var piece = raw.replace(Regex("""\s+"""), " ")
        if (text.isEmpty() || text.last() == ' ') piece = piece.trimStart()
        val before = text.length
        text.append(piece)
        var start = before
        while (start < text.length && text[start] == ' ') start++
        var end = text.length
        while (end > start && text[end - 1] == ' ') end--
        if (end > start) {
            words += LyricWord(startMs = startMs, endMs = endMs, charStart = start, charEnd = end)
        }
    }

    syllables.forEach { element ->
        val o = element.jsonObject
        val raw = o.text("text") ?: return@forEach
        val start = o.long("time") ?: return@forEach
        val duration = o.long("duration") ?: 0L
        append(raw, start, start + duration)
    }
    return text.toString().trimEnd() to words
}

internal fun lyricsPlusTimingVerified(durationMs: Long, matchedDurationSec: Double?): Boolean {
    val targetSec = durationMs / 1000.0
    return targetSec > 0.0 && matchedDurationSec != null &&
        abs(matchedDurationSec - targetSec) <= TIMING_TOLERANCE_SEC
}

internal fun lyricsPlusMetadataMatches(
    doc: LyricsPlusDoc,
    track: String,
    artist: String,
    durationMs: Long,
    isrc: String?,
): Boolean {
    if (!isrc.isNullOrBlank() && doc.isrc != null && !doc.isrc.equals(isrc, ignoreCase = true)) return false
    if (doc.title != null && !namesMatch(doc.title, track)) return false
    if (isrc.isNullOrBlank() && artist.isNotBlank() && doc.artist != null && !namesMatch(doc.artist, artist)) return false
    val targetSec = durationMs / 1000.0
    return targetSec <= 0.0 || doc.durationSec == null || abs(doc.durationSec - targetSec) <= MAX_LYRICS_PLUS_DRIFT_SEC
}

private fun finishLyricsPlusWords(words: List<LyricWord>, lineEnd: Long?): List<LyricWord> =
    words.mapIndexed { i, word ->
        if (word.endMs > word.startMs) {
            word
        } else {
            val next = words.getOrNull(i + 1)?.startMs ?: lineEnd ?: (word.startMs + 500)
            word.copy(endMs = maxOf(next, word.startMs + 1))
        }
    }

private fun lyricsPlusInferredDurationSec(lines: List<LyricLine>): Double? =
    lines.flatMap { it.words }.maxOfOrNull { it.endMs }?.div(1000.0)

private fun JsonObject.isLyricsPlusBackground(): Boolean {
    val tags = elementTags(this["element"]).map { it.lowercase() }
    return tags.any { it in DROPPED_LYRICS_PLUS_TAGS }
}

private fun elementTags(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonObject -> buildList {
        listOf("role", "ttm:role", "tag", "type", "kind", "part").forEach { key ->
            element.text(key)?.let(::add)
        }
        (element["roles"] as? JsonArray)?.forEach { role ->
            (role as? JsonPrimitive)?.contentOrNull?.let(::add)
        }
    }
    is JsonPrimitive -> listOfNotNull(element.contentOrNull)
    else -> emptyList()
}

private val DROPPED_LYRICS_PLUS_TAGS = setOf(
    "background",
    "backing",
    "bg",
    "x-bg",
    "translation",
    "x-translation",
    "roman",
    "romanization",
    "x-roman",
)

private const val MAX_LYRICS_PLUS_DRIFT_SEC = 15.0

private fun JsonObject.text(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.long(name: String): Long? =
    get(name)?.jsonPrimitive?.longOrNull ?: text(name)?.toLongOrNull()
