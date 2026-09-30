package com.example.musicsm.data.repository

import android.util.Base64
import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * KuGou's lyrics database. Two keyless calls: a search that names the candidate recordings with
 * their lengths, then a download of the chosen one's LRC (base64-wrapped).
 */
@Singleton
class KuGouLyricsProvider @Inject constructor(
    private val client: OkHttpClient,
) : LyricsProvider {

    override val source = LyricsSource.KUGOU

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? {
        val targetMs = song.durationMs
        val keyword = if (artist.isBlank()) track else "$track - $artist"

        // The song catalogue is far better at resolving a name than the lyrics search, and a
        // recording's hash leads straight to the lyrics filed against it. The keyword search of
        // the lyrics database itself is the fallback.
        val byHash = songHashes(keyword, track, targetMs).take(MAX_HASHES)
            .firstNotNullOfOrNull { hash -> pick(lyricsCandidates("hash" to hash), track, targetMs) }
        val choice = byHash
            ?: pick(
                lyricsCandidates(
                    *buildList {
                        add("keyword" to keyword)
                        if (targetMs > 0) add("duration" to targetMs.toString())
                    }.toTypedArray(),
                ),
                track,
                targetMs,
            )
            ?: return null

        val downloadUrl = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "pc")
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8")
            .addQueryParameter("id", choice.id)
            .addQueryParameter("accesskey", choice.key)
            .build()
        val download = client.getText(downloadUrl.toString()) ?: return null
        val content = runCatching { JSONObject(download).optString("content") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        val lrc = runCatching { String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
            ?: return null
        val lines = stripKuGouCredits(parseLrcLines(lrc), track, artist)
        if (lines.none { it.text.isNotBlank() }) return null

        val verified = targetMs > 0 && choice.durationMs > 0 &&
            abs(choice.durationMs - targetMs) <= TIMING_TOLERANCE_SEC * 1000
        return Lyrics(synced = true, lines = lines, timingVerified = verified, source = source)
    }

    private companion object {
        const val MAX_DRIFT_MS = 8_000L
        const val MAX_HASHES = 3
    }

    private data class Candidate(val id: String, val key: String, val durationMs: Long, val title: String)

    /** Hashes of catalogue recordings with this title, closest in length first. */
    private suspend fun songHashes(keyword: String, track: String, targetMs: Long): List<String> {
        val url = "https://mobileservice.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
            .addQueryParameter("version", "9108")
            .addQueryParameter("plat", "0")
            .addQueryParameter("pagesize", "8")
            .addQueryParameter("showtype", "0")
            .addQueryParameter("keyword", keyword)
            .build()
        val body = client.getText(url.toString()) ?: return emptyList()
        val info = runCatching { JSONObject(body).optJSONObject("data")?.optJSONArray("info") }.getOrNull()
            ?: return emptyList()
        return (0 until info.length()).mapNotNull { i ->
            val o = info.optJSONObject(i) ?: return@mapNotNull null
            val hash = o.optString("hash").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!namesMatch(o.optString("songname"), track)) return@mapNotNull null
            val deltaMs = if (targetMs > 0) abs(o.optLong("duration", 0L) * 1000 - targetMs) else 0L
            if (deltaMs > MAX_DRIFT_MS) return@mapNotNull null
            hash to deltaMs
        }.sortedBy { it.second }.map { it.first }
    }

    private suspend fun lyricsCandidates(vararg query: Pair<String, String>): List<Candidate> {
        val url = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "pc")
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val body = client.getText(url.toString()) ?: return emptyList()
        val candidates = runCatching { JSONObject(body).optJSONArray("candidates") }.getOrNull() ?: return emptyList()
        return (0 until candidates.length()).mapNotNull { i ->
            val o = candidates.optJSONObject(i) ?: return@mapNotNull null
            Candidate(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                key = o.optString("accesskey").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                durationMs = o.optLong("duration", 0L),
                title = o.optString("song"),
            )
        }
    }

    /**
     * KuGou answers loose searches with anything nearby, so the title has to agree and, when the
     * length is known, so must the length (within a different-cut margin). Closest length wins.
     */
    private fun pick(pool: List<Candidate>, track: String, targetMs: Long): Candidate? = pool
        .filter { it.title.isBlank() || namesMatch(it.title, track) }
        .filter { targetMs <= 0 || it.durationMs <= 0 || abs(it.durationMs - targetMs) <= MAX_DRIFT_MS }
        .minByOrNull { if (targetMs > 0 && it.durationMs > 0) abs(it.durationMs - targetMs) else Long.MAX_VALUE }
}

/**
 * KuGou prefixes its LRC with a title card and credits ("Yellow - Coldplay", "Lyrics: …",
 * "作词：…"), timestamped like lyrics. Drops those leading lines.
 */
internal fun stripKuGouCredits(lines: List<LyricLine>, track: String, artist: String): List<LyricLine> {
    val credit = Regex("""^[^:：]{1,24}\s*[:：].+""")
    return lines.dropWhile { line ->
        val t = line.text
        t.isBlank() || credit.matches(t) || (namesMatch(t, track) && (artist.isBlank() || namesMatch(t, artist)))
    }
}
