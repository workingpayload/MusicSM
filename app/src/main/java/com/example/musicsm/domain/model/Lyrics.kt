package com.example.musicsm.domain.model

/**
 * One line of lyrics. [timeMs] is the start time for synced lyrics, or null for plain text.
 * [words] carries per-word timing for karaoke-style highlighting when the source provides it.
 */
data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

/**
 * A timed piece of a [LyricLine]: the characters `text[charStart until charEnd]` are sung from
 * [startMs] to [endMs]. Syllables of one word are separate entries with adjacent ranges.
 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val charStart: Int,
    val charEnd: Int,
)

/**
 * Lyrics for a track. [synced] is true when [lines] carry timestamps (karaoke-style highlight),
 * false when only plain text is available.
 *
 * [timingVerified] is true when the synced lyrics were matched to a recording whose length agrees
 * with the audio actually playing, so the timestamps can be trusted without any manual offset.
 * When false, the lyrics come from a different cut of the song (music video, radio edit, live)
 * and may drift.
 */
data class Lyrics(
    val synced: Boolean,
    val lines: List<LyricLine>,
    val timingVerified: Boolean = false,
    /** Which database the lyrics came from; null when not known. */
    val source: LyricsSource? = null,
)

/**
 * The lyrics databases that can be asked for a track. Declaration order is the default priority;
 * the user can reorder or switch them off in Settings (stored by [name], so this list may be
 * rearranged without disturbing a saved order).
 */
enum class LyricsSource(val label: String) {
    /** Apple Music's line and word timings, via the keyless BetterLyrics proxy. Popular songs only. */
    APPLE_MUSIC("Apple Music"),

    /** Apple Music's word timings from a second host, which also names the exact recording (ISRC). */
    BINI_LYRICS("BiniLyrics"),

    /** The YouLy+ lyrics backend: word and syllable timings, matched on the ISRC when it is known. */
    LYRICS_PLUS("LyricsPlus"),

    /** SimpMusic's database, looked up by the YouTube video itself, so it can't pick another cut. */
    SIMPMUSIC("SimpMusic"),

    /** Open community database; the widest coverage of time-synced lyrics. */
    LRCLIB("LRCLIB"),

    /** KuGou's catalogue: strong on Asian releases and many international hits. */
    KUGOU("KuGou"),

    /** Community-uploaded lyrics (Unison): thin coverage, but sometimes the only source with a song. */
    UNISON("Unison"),

    /** YouTube Music's own lyrics tab. Plain text only, but matched to the exact video. */
    YOUTUBE_MUSIC("YouTube Music"),
    ;

    companion object {
        fun fromName(name: String): LyricsSource? = entries.firstOrNull { it.name == name }
    }
}
