package com.example.musicsm.data.repository

import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LyricsRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Lyrics from several databases, tried in the user's order (Settings > Lyrics sources).
 *
 * Every enabled source is asked at once. With "Prefer word-by-word lyrics" off, answers keep the
 * legacy priority-order rule: first verified synced result, else first synced, else first plain.
 *
 * With it on, completed answers are ranked as verified word-timed, verified line-timed,
 * unverified word-timed, unverified line-timed, then plain text; source order breaks ties. A
 * verified word-timed result returns as soon as no higher-priority source can still beat it. Once
 * any synced candidate exists, slower sources get only a short grace window to improve it.
 *
 * When BiniLyrics is enabled, it first gets the recording ISRC in parallel with non-ISRC sources.
 * Providers that understand ISRC wait for that short lookup, then query the exact recording.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val preferences: AppPreferences,
    appleMusic: AppleMusicLyricsProvider,
    private val biniLyrics: BiniLyricsProvider,
    lyricsPlus: LyricsPlusProvider,
    simpMusic: SimpMusicLyricsProvider,
    lrcLib: LrcLibProvider,
    kuGou: KuGouLyricsProvider,
    unison: UnisonLyricsProvider,
    youTubeMusic: YouTubeMusicLyricsProvider,
) : LyricsRepository {

    private val providers: Map<LyricsSource, LyricsProvider> =
        listOf(appleMusic, biniLyrics, lyricsPlus, simpMusic, lrcLib, kuGou, unison, youTubeMusic)
            .associateBy { it.source }

    private val isrcCache = object : LinkedHashMap<String, String?>(ISRC_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean =
            size > ISRC_CACHE_SIZE
    }

    override suspend fun forSong(song: Song): Lyrics? {
        val track = cleanTrackMetadata(song.title)
        val artist = cleanTrackMetadata(song.artist)
        if (track.isBlank()) return null

        val disabled = preferences.disabledLyricsSourcesNow
        val sources = preferences.lyricsSourceOrderNow.filter { it !in disabled }.mapNotNull { providers[it] }
        if (sources.isEmpty()) return null

        return coroutineScope {
            val identification: Deferred<String?>? =
                if (sources.any { it.source == LyricsSource.BINI_LYRICS }) {
                    async { identifyIsrc(song, track, artist) }
                } else {
                    null
                }
            val pending: List<Deferred<Lyrics?>> = sources.map { provider ->
                async {
                    val isrc = if (provider is IsrcLyricsProvider) identification?.await() else null
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching {
                            if (provider is IsrcLyricsProvider) {
                                provider.fetch(song, track, artist, isrc)
                            } else {
                                provider.fetch(song, track, artist)
                            }
                        }.getOrNull()
                    }?.takeIf { lyrics -> lyrics.lines.any { it.text.isNotBlank() } }
                }
            }
            try {
                if (preferences.preferWordSyncedLyricsNow) {
                    selectPreferredWordLyrics(pending)
                } else {
                    var synced: Lyrics? = null
                    var plain: Lyrics? = null
                    for (job in pending) {
                        val result = job.await() ?: continue
                        when {
                            result.synced && result.timingVerified -> return@coroutineScope result
                            result.synced -> if (synced == null) synced = result
                            else -> if (plain == null) plain = result
                        }
                    }
                    synced ?: plain
                }
            } finally {
                pending.forEach { it.cancel() }
                identification?.cancel()
            }
        }
    }

    private suspend fun identifyIsrc(song: Song, track: String, artist: String): String? {
        val key = song.id.takeIf { it.isNotBlank() } ?: return null
        synchronized(isrcCache) { isrcCache[key]?.let { return it } }
        val isrc = withTimeoutOrNull(ISRC_LOOKUP_TIMEOUT_MS) {
            runCatching { biniLyrics.identify(track, artist, song.durationMs, song.album)?.isrc }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        // Only a found recording is remembered: a miss may just have been a slow or failed lookup.
        if (isrc != null) synchronized(isrcCache) { isrcCache[key] = isrc }
        return isrc
    }

    private companion object {
        /** A source that has not answered by then is treated as having nothing. */
        const val SOURCE_TIMEOUT_MS = 10_000L
        const val ISRC_LOOKUP_TIMEOUT_MS = 2_500L
        const val ISRC_CACHE_SIZE = 100
    }
}

private const val WORD_SYNC_GRACE_MS = 2_500L

private suspend fun selectPreferredWordLyrics(pendingJobs: List<Deferred<Lyrics?>>): Lyrics? {
    val pending = pendingJobs.withIndex().associate { it.index to it.value }.toMutableMap()
    val candidates = ArrayList<Pair<Int, Lyrics>>()
    var best: Pair<Int, Lyrics>? = null
    var graceDeadlineMs: Long? = null

    while (pending.isNotEmpty()) {
        val remainingGraceMs = graceDeadlineMs?.let { it - System.currentTimeMillis() }
        if (remainingGraceMs != null && remainingGraceMs <= 0L) break

        val completed = if (remainingGraceMs == null) {
            select<Pair<Int, Lyrics?>> {
                pending.forEach { (priority, job) -> job.onAwait { priority to it } }
            }
        } else {
            withTimeoutOrNull(remainingGraceMs) {
                select<Pair<Int, Lyrics?>> {
                    pending.forEach { (priority, job) -> job.onAwait { priority to it } }
                }
            } ?: break
        }

        val (priority, result) = completed
        pending.remove(priority)
        if (result != null) {
            candidates += priority to result
            best = pickBest(candidates, preferWord = true)

            if (graceDeadlineMs == null && result.synced) {
                graceDeadlineMs = System.currentTimeMillis() + WORD_SYNC_GRACE_MS
            }
        }
        if (best != null && pending.keys.none { priorityCanBeat(it, best, preferWord = true) }) {
            break
        }
    }
    return best?.second
}

internal fun lyricsRank(lyrics: Lyrics, preferWord: Boolean): Int {
    val hasWords = lyrics.lines.any { it.words.isNotEmpty() }
    return if (preferWord) {
        when {
            lyrics.synced && lyrics.timingVerified && hasWords -> 4
            lyrics.synced && lyrics.timingVerified -> 3
            lyrics.synced && hasWords -> 2
            lyrics.synced -> 1
            else -> 0
        }
    } else {
        when {
            lyrics.synced && lyrics.timingVerified -> 2
            lyrics.synced -> 1
            else -> 0
        }
    }
}

internal fun pickBest(candidates: List<Pair<Int, Lyrics>>, preferWord: Boolean): Pair<Int, Lyrics>? =
    candidates.maxWithOrNull(
        compareBy<Pair<Int, Lyrics>> { lyricsRank(it.second, preferWord) }
            .thenBy { -it.first },
    )

private fun priorityCanBeat(priority: Int, best: Pair<Int, Lyrics>, preferWord: Boolean): Boolean {
    val bestRank = lyricsRank(best.second, preferWord)
    val maxRank = if (preferWord) 4 else 2
    return maxRank > bestRank || (maxRank == bestRank && priority < best.first)
}

/**
 * Parse an LRC blob into timestamped lines. A single line may carry several timestamps
 * (`[00:12.00][01:30.00]same words`), so each one becomes its own entry.
 *
 * Top-level and `internal` so it can be unit-tested without an HTTP client.
 */
internal fun parseLrcLines(lrc: String): List<LyricLine> {
    val out = ArrayList<LyricLine>()
    val tagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    for (raw in lrc.split("\n")) {
        val matches = tagRegex.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val (text, words) = parseEnhancedLrcWords(raw.substring(matches.last().range.last + 1).trim())
        for (m in matches) {
            val ms = lrcClockMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            // Word stamps are only meaningful on the line's first occurrence.
            out.add(LyricLine(timeMs = ms, text = text, words = if (m == matches.first()) words else emptyList()))
        }
    }
    val sorted = out.sortedBy { it.timeMs ?: 0L }
    // The last enhanced-LRC word has no closing stamp: let it run until the next line starts.
    return sorted.mapIndexed { i, line ->
        val last = line.words.lastOrNull()
        if (last == null || last.endMs > last.startMs) return@mapIndexed line
        val nextStart = sorted.getOrNull(i + 1)?.timeMs ?: (last.startMs + 1_000)
        line.copy(words = line.words.dropLast(1) + last.copy(endMs = maxOf(nextStart, last.startMs + 1)))
    }
}

private fun lrcClockMs(min: String, sec: String, fracStr: String): Long {
    // LRC fractions are hundredths by convention, but both 1 and 3 digits occur.
    val frac = when (fracStr.length) {
        0 -> 0L
        1 -> fracStr.toLong() * 100
        2 -> fracStr.toLong() * 10
        else -> fracStr.take(3).toLong()
    }
    return (min.toLong() * 60 + sec.toLong()) * 1000 + frac
}

private val ENHANCED_LRC_TAG = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

/**
 * Enhanced ("A2") LRC puts `<mm:ss.xx>` before each word. Returns the line with the stamps removed
 * and the words they time; a stamp with nothing after it only closes the word before it.
 */
internal fun parseEnhancedLrcWords(line: String): Pair<String, List<LyricWord>> {
    val stamps = ENHANCED_LRC_TAG.findAll(line).toList()
    if (stamps.isEmpty()) return line to emptyList()
    val text = StringBuilder()
    val pieces = ArrayList<Triple<Long, Int, Int>>()
    fun appendPiece(raw: String, startMs: Long?) {
        var piece = raw.replace(Regex("""\s+"""), " ")
        if (text.isEmpty() || text.last() == ' ') piece = piece.trimStart()
        val before = text.length
        text.append(piece)
        var start = before
        while (start < text.length && text[start] == ' ') start++
        var end = text.length
        while (end > start && text[end - 1] == ' ') end--
        if (startMs != null && end > start) pieces += Triple(startMs, start, end)
    }
    appendPiece(line.substring(0, stamps.first().range.first), null)
    stamps.forEachIndexed { i, m ->
        val until = stamps.getOrNull(i + 1)?.range?.first ?: line.length
        appendPiece(
            line.substring(m.range.last + 1, until),
            lrcClockMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]),
        )
    }
    val stampTimes = stamps.map { lrcClockMs(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
    val words = pieces.map { (startMs, start, end) ->
        val endMs = stampTimes.firstOrNull { it > startMs } ?: startMs
        LyricWord(startMs = startMs, endMs = endMs, charStart = start, charEnd = end)
    }
    return text.toString().trimEnd() to words
}

/** Strip common noise from YouTube titles/artists ("(Official Video)", "- Topic", ...). */
internal fun cleanTrackMetadata(s: String): String {
    var r = s
    r = r.replace(Regex("""\((?:official|lyric|audio|video|visualizer|hd|4k|mv)[^)]*\)""", RegexOption.IGNORE_CASE), "")
    r = r.replace(Regex("""\[[^]]*]"""), "")
    r = r.replace(Regex(""" - Topic$""", RegexOption.IGNORE_CASE), "")
    r = r.replace(Regex("""(?:official|lyric[s]?|audio|video|visualizer)""", RegexOption.IGNORE_CASE), "")
    return r.replace(Regex("""\s+"""), " ").trim(' ', '-', '|', '·')
}

/**
 * How far a lyrics recording's length may differ from the playing audio before its timestamps
 * stop being trustworthy. LRCLIB rounds durations and its own exact lookup allows ±2s, so this is
 * just above that.
 */
internal const val TIMING_TOLERANCE_SEC = 3.0

/** One lyrics entry offered by LRCLIB. */
internal data class LyricsCandidate(
    val trackName: String,
    val artistName: String,
    val durationSec: Double,
    val syncedLrc: String?,
    val plainLyrics: String?,
    /** True when LRCLIB itself resolved name + artist (the exact-match endpoint). */
    val trustedMatch: Boolean = false,
)

internal data class LyricsChoice(
    val candidate: LyricsCandidate,
    val timingVerified: Boolean,
)

/**
 * Picks the lyrics for a track, given the title/artist being played and the real length of its
 * audio ([targetSec], or 0 when unknown).
 *
 * LRCLIB's search is fuzzy enough to return *other songs* — an artist search for one title can
 * include a completely different track by a different artist — so a candidate must match the title
 * to be considered at all. When the length is known, preference then goes:
 * 1. Synced lyrics from a recording of the same length → timing verified.
 * 2. Synced lyrics from another cut by the same artist, closest in length → shown, but flagged.
 * 3. Plain lyrics from the same artist or a same-length recording.
 *
 * A candidate whose artist does not match is only accepted at the same length: a same-titled song
 * by someone else is far more likely to be a different song than a different cut.
 */
internal fun chooseLyrics(
    candidates: List<LyricsCandidate>,
    title: String,
    artist: String,
    targetSec: Double,
): LyricsChoice? {
    val pool = candidates.filter {
        (it.syncedLrc != null || it.plainLyrics != null) &&
            (it.trustedMatch || namesMatch(it.trackName, title))
    }
    if (pool.isEmpty()) return null

    fun LyricsCandidate.sameArtist() = trustedMatch || artist.isBlank() || namesMatch(artistName, artist)

    if (targetSec <= 0) {
        // No length to check against: trust only the text match, prefer synced, then same artist.
        val best = pool.filter { it.syncedLrc != null }.let { synced ->
            synced.firstOrNull { it.sameArtist() } ?: synced.firstOrNull()
        } ?: pool.firstOrNull { it.sameArtist() } ?: pool.first()
        return LyricsChoice(best, timingVerified = false)
    }

    fun LyricsCandidate.delta() = abs(durationSec - targetSec)
    fun LyricsCandidate.sameLength() = durationSec > 0 && delta() <= TIMING_TOLERANCE_SEC
    val eligible = pool.filter { it.sameArtist() || it.sameLength() }

    eligible.filter { it.syncedLrc != null && it.sameLength() }
        .minByOrNull { it.delta() }
        ?.let { return LyricsChoice(it, timingVerified = true) }

    eligible.filter { it.syncedLrc != null && it.durationSec > 0 }
        .minByOrNull { it.delta() }
        ?.let { return LyricsChoice(it, timingVerified = false) }

    return eligible.minByOrNull { if (it.durationSec > 0) it.delta() else Double.MAX_VALUE }
        ?.let { LyricsChoice(it, timingVerified = false) }
}

/**
 * Loose name equality for matching YouTube metadata against LRCLIB: case, accents, punctuation
 * and spacing are ignored, and either side may contain the other ("Song" vs "Artist - Song",
 * "Artist" vs "Artist, Featured").
 */
internal fun namesMatch(a: String, b: String): Boolean {
    val x = normalizeName(a)
    val y = normalizeName(b)
    if (x.isEmpty() || y.isEmpty()) return false
    return x == y || x.contains(y) || y.contains(x)
}

private fun normalizeName(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase()
        .filter { it.isLetterOrDigit() }
