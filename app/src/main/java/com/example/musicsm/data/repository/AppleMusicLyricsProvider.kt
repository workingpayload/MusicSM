package com.example.musicsm.data.repository

import android.util.Xml
import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Apple Music's lyrics through the keyless BetterLyrics proxy, which matches on title, artist and
 * length and answers with Apple's TTML. Apple's timings are hand-made per recording, so when the
 * lengths agree they are the most accurate here. Uncached tracks need an API key, which this does
 * not have, so coverage is limited to songs someone has already looked up.
 */
@Singleton
class AppleMusicLyricsProvider @Inject constructor(
    private val client: OkHttpClient,
) : LyricsProvider {

    override val source = LyricsSource.APPLE_MUSIC

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? {
        if (artist.isBlank()) return null
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("s", track)
            .addQueryParameter("a", artist)
            .apply {
                val seconds = song.durationMs / 1000
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                song.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("al", it) }
            }
            .build()
        val body = client.getText(url.toString(), mapOf("Accept" to "application/json")) ?: return null
        val ttml = runCatching { JSONObject(body).optString("ttml") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        val doc = runCatching { parseTtml(ttml) }.getOrNull() ?: return null
        if (doc.lines.none { it.text.isNotBlank() }) return null

        val synced = doc.lines.any { it.timeMs != null }
        val targetSec = song.durationMs / 1000.0
        val verified = synced && doc.durationSec != null && targetSec > 0 &&
            abs(doc.durationSec - targetSec) <= TIMING_TOLERANCE_SEC
        return Lyrics(
            synced = synced,
            lines = if (synced) doc.lines.filter { it.timeMs != null } else doc.lines,
            timingVerified = verified,
            source = source,
        )
    }

    private data class TtmlDoc(val lines: List<LyricLine>, val durationSec: Double?)

    private companion object {
        const val ENDPOINT = "https://lyrics-api.boidu.dev/getLyrics"
    }
}

internal data class TtmlDoc(val lines: List<LyricLine>, val durationSec: Double?)

/**
 * Flattens Apple's TTML to one entry per `<p>`. Timed word/syllable `<span>`s are joined back into
 * the line and kept as [LyricWord]s for karaoke; background-vocal spans (`ttm:role="x-bg"`) are
 * dropped so the line reads as sung. Whitespace is collapsed as the text is built so the word
 * ranges stay valid.
 */
internal fun parseTtml(ttml: String): TtmlDoc {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(StringReader(ttml))

    class OpenSpan(val begin: Long?, val end: Long?, val charStart: Int)

    val lines = ArrayList<LyricLine>()
    var durationSec: Double? = null
    var lineStart: Long? = null
    var lineEnd: Long? = null
    var inLine = false
    var skipDepth = 0
    val text = StringBuilder()
    val words = ArrayList<LyricWord>()
    val spans = ArrayList<OpenSpan>()

    fun appendCollapsed(s: String) {
        for (ch in s) {
            if (ch.isWhitespace()) {
                if (text.isNotEmpty() && text.last() != ' ') text.append(' ')
            } else {
                text.append(ch)
            }
        }
    }

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "body" -> durationSec = parser.getAttributeValue(null, "dur")?.let(::parseClock)?.div(1000.0)
                "p" -> {
                    inLine = true
                    text.clear()
                    words.clear()
                    spans.clear()
                    lineStart = parser.getAttributeValue(null, "begin")?.let(::parseClock)
                    lineEnd = parser.getAttributeValue(null, "end")?.let(::parseClock)
                }
                "span" -> if (skipDepth > 0 || parser.getAttributeValue(null, "ttm:role") == "x-bg") {
                    skipDepth++
                } else if (inLine) {
                    spans += OpenSpan(
                        begin = parser.getAttributeValue(null, "begin")?.let(::parseClock),
                        end = parser.getAttributeValue(null, "end")?.let(::parseClock),
                        charStart = text.length,
                    )
                }
                "br" -> if (inLine && skipDepth == 0) appendCollapsed(" ")
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "span" -> if (skipDepth > 0) {
                    skipDepth--
                } else if (spans.isNotEmpty()) {
                    val span = spans.removeAt(spans.lastIndex)
                    var start = span.charStart
                    while (start < text.length && text[start] == ' ') start++
                    var end = text.length
                    while (end > start && text[end - 1] == ' ') end--
                    if (span.begin != null && end > start) {
                        words += LyricWord(
                            startMs = span.begin,
                            endMs = span.end ?: span.begin,
                            charStart = start,
                            charEnd = end,
                        )
                    }
                }
                "p" -> {
                    if (inLine) {
                        val lineText = text.toString().trimEnd()
                        lines += LyricLine(
                            timeMs = lineStart,
                            text = lineText,
                            words = if (lineStart != null) finishWords(words, lineEnd) else emptyList(),
                        )
                    }
                    inLine = false
                }
            }
            XmlPullParser.TEXT -> if (inLine && skipDepth == 0) appendCollapsed(parser.text)
        }
        event = parser.next()
    }
    return TtmlDoc(lines, durationSec)
}

/** Sorts words and fills a missing/zero end from the next word (or the line end). */
private fun finishWords(words: List<LyricWord>, lineEnd: Long?): List<LyricWord> {
    if (words.isEmpty()) return emptyList()
    val sorted = words.sortedBy { it.charStart }
    return sorted.mapIndexed { i, w ->
        if (w.endMs > w.startMs) {
            w
        } else {
            val next = sorted.getOrNull(i + 1)?.startMs ?: lineEnd ?: (w.startMs + 500)
            w.copy(endMs = maxOf(next, w.startMs + 1))
        }
    }
}

/** TTML clock values: `12.345`, `1:02.345`, `01:02:03.345`, or `12.3s`. Milliseconds, or null. */
internal fun parseClock(value: String): Long? {
    val v = value.trim().removeSuffix("s")
    val parts = v.split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    var seconds = 0.0
    for (part in parts) {
        val n = part.toDoubleOrNull() ?: return null
        seconds = seconds * 60 + n
    }
    return (seconds * 1000).toLong()
}
