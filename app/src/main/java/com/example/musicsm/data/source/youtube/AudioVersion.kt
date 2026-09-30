package com.example.musicsm.data.source.youtube

import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.model.Song

/**
 * The audio-only version of a song, for a video titled [title] from channel [artist], among
 * [candidates] (YouTube Music song search results, best first); null if none is safe to use.
 *
 * It has to be the same song and the same version: a remix or a live take doesn't stand in for
 * the original. Video titles often carry the artist ("Artist ft. X - Title", uploaded by the
 * artist, their label or anyone else) or the film ("Title - Film"), so the title is read as a
 * whole and on each side of its " - ". A result credited to the same artist (the channel's, or
 * the one named in the title) wins. Failing that, only YouTube Music's own top result is trusted,
 * and only if it is the same song.
 */
internal fun pickAudioVersion(title: String, artist: String, candidates: List<Song>): Song? {
    val video = VideoTitle.of(title, artist) ?: return null
    val same = candidates.filter { video.isSameSongAs(it) }
    return same.firstOrNull { song -> ArtistMatching.creditKeys(song.artist).any { it in video.artistKeys } }
        ?: candidates.firstOrNull()?.takeIf { it in same }
}

/** What a video's title says: the names the song may have, its version, and who it credits. */
private class VideoTitle(
    private val full: String,
    private val names: Set<String>,
    private val markers: Set<String>,
    val artistKeys: Set<String>,
) {
    fun isSameSongAs(song: Song): Boolean {
        if (markers(song.title) != markers) return false
        val name = songName(song.title, song.artist) ?: return false
        if (name in names) return true
        // "Artist Title" with no separator, the artist being the song's own credit.
        return ArtistMatching.creditedNames(song.artist).any { stripPrefix(full, it) == name }
    }

    companion object {
        fun of(title: String, channel: String): VideoTitle? {
            val raw = rawParts(title)
            val parts = parts(raw)
            val full = ArtistMatching.normalize(parts.joinToString(" "))
            if (full.isEmpty()) return null
            val names = buildSet {
                add(full)
                stripPrefix(full, ArtistMatching.normalize(channel))?.let(::add)
                if (parts.size >= 2) {
                    // "Artist - Title" and "Title - Film": either side can be the song.
                    add(ArtistMatching.normalize(parts.drop(1).joinToString(" ")))
                    add(ArtistMatching.normalize(parts.first()))
                }
                remove("")
            }
            val credits = if (raw.size >= 2) ArtistMatching.creditKeys(raw.first()) else emptySet()
            return VideoTitle(full, names, markers(title), ArtistMatching.creditKeys(channel) + credits)
        }
    }
}

/** A song's name as a comparison key, without its own artist in front ("Artist - Title"). */
private fun songName(title: String, artist: String): String? {
    val full = ArtistMatching.normalize(parts(rawParts(title)).joinToString(" "))
    if (full.isEmpty()) return null
    return stripPrefix(full, ArtistMatching.normalize(artist)) ?: full
}

/** A title's " - " separated parts, without brackets or a " | …" tail. */
private fun rawParts(title: String): List<String> =
    PIPE_SUFFIX.replace(BRACKETED.replace(title, " "), "").split(DASH)

/** [raw] parts without featured credits; per part, so "A ft. B - Title" keeps its title. */
private fun parts(raw: List<String>): List<String> =
    raw.map { FEATURING.replace(it, "").trim() }.filter { it.isNotEmpty() }

private fun stripPrefix(key: String, prefix: String): String? =
    if (prefix.isNotEmpty() && key.length > prefix.length && key.startsWith(prefix)) key.removePrefix(prefix) else null

private fun markers(title: String): Set<String> =
    VERSION_MARKERS.findAll(title).map { it.value.lowercase().replace(SPACES, "") }.toSet()

private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
private val PIPE_SUFFIX = Regex("""\s[|｜].*$""")
private val DASH = Regex("""\s[-–—]\s""")
private val FEATURING = Regex("""(?i)\s(?:feat\.?|ft\.?|featuring)\s.*$""")
private val SPACES = Regex("""\s+""")
private val VERSION_MARKERS = Regex(
    """(?i)\b(?:remix|live|acoustic|unplugged|instrumental|karaoke|cover|sped\s*up|slowed|reverb|lo-?fi|8d|mashup|a\s*cappella|acapella)\b""",
)
