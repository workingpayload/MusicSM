package com.example.musicsm.domain.share

import com.example.musicsm.domain.model.Song
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/** A playlist packed for sharing: just the name and enough per-track data to rebuild it. */
data class SharedPlaylist(
    val name: String,
    val songs: List<Song>,
)

/**
 * Packs a playlist into a self-contained string so it can travel inside a link or a QR code with
 * no server, account or backend of any kind — everything the recipient needs is in the payload.
 *
 * Layout, before compression:
 * ```
 * MSM1
 * <playlist name>
 * <videoId>US<title>US<artist>US<durationSeconds>
 * ...
 * ```
 * (`US` is the ASCII unit separator, 0x1F.) That text is deflated and then Base64url-encoded,
 * which matters because a QR code tops out at roughly 2,950 bytes: the line format carries far
 * less overhead than JSON, and deflate collapses the repetition between rows.
 *
 * Artwork URLs are deliberately *not* transmitted — they are long, compress poorly, and are
 * reconstructible from the video id, so [artworkFor] rebuilds them on import instead.
 *
 * Pure Kotlin on purpose: no Android types, so the round trip is unit-testable.
 */
object PlaylistShareCodec {

    /** Magic + format version. A future format bumps this and old clients reject it cleanly. */
    const val VERSION = "MSM1"

    /** Links look like `musicsm://shared/playlist?d=<payload>`. */
    const val LINK_PREFIX = "musicsm://shared/playlist?d="

    /** Upper bound on tracks, applied in both directions. */
    const val MAX_SONGS = 1_000

    /** A shared link is untrusted input, so cap how far a hostile payload can inflate. */
    private const val MAX_INFLATED_BYTES = 1 shl 20

    private const val UNIT = '\u001F'

    fun encode(playlist: SharedPlaylist): String {
        val text = buildString {
            append(VERSION).append('\n')
            append(playlist.name.singleLine()).append('\n')
            playlist.songs.take(MAX_SONGS).forEach { song ->
                append(song.id.singleLine()).append(UNIT)
                append(song.title.singleLine()).append(UNIT)
                append(song.artist.singleLine()).append(UNIT)
                append(song.durationMs / 1000)
                append('\n')
            }
        }
        return base64Url(deflate(text.toByteArray(Charsets.UTF_8)))
    }

    /** @return the playlist, or null if [payload] is not a well-formed share payload. */
    fun decode(payload: String): SharedPlaylist? = runCatching {
        val raw = inflate(Base64.getUrlDecoder().decode(payload.trim()))
        val lines = String(raw, Charsets.UTF_8).split('\n')
        if (lines.size < 2 || lines[0] != VERSION) return null

        val songs = lines.asSequence()
            .drop(2)
            .filter { it.isNotBlank() }
            .take(MAX_SONGS)
            .mapNotNull(::parseSong)
            .toList()
        if (songs.isEmpty()) return null

        SharedPlaylist(name = lines[1].trim(), songs = songs)
    }.getOrNull()

    fun shareUrl(playlist: SharedPlaylist): String = LINK_PREFIX + encode(playlist)

    /** Pulls the payload out of a share link, tolerating surrounding text from a share sheet. */
    fun payloadFromUrl(raw: String): String? {
        val start = raw.indexOf(LINK_PREFIX)
        if (start < 0) return null
        return raw.substring(start + LINK_PREFIX.length)
            .takeWhile { !it.isWhitespace() && it != '&' && it != '#' }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Every YouTube video exposes a thumbnail at this path and [Song.id] *is* the video id, so
     * imported tracks get real artwork without shipping a single URL in the payload.
     */
    fun artworkFor(songId: String): String = "https://i.ytimg.com/vi/$songId/hqdefault.jpg"

    private fun parseSong(line: String): Song? {
        val parts = line.split(UNIT)
        if (parts.size < 3) return null
        val id = parts[0].trim()
        if (id.isEmpty()) return null
        return Song(
            id = id,
            title = parts[1].ifBlank { id },
            artist = parts[2],
            artworkUrl = artworkFor(id),
            durationMs = (parts.getOrNull(3)?.trim()?.toLongOrNull() ?: 0L).coerceAtLeast(0L) * 1000,
        )
    }

    /** Separators are structural, so they can never survive inside a field. */
    private fun String.singleLine(): String =
        replace('\n', ' ').replace('\r', ' ').replace(UNIT, ' ').trim()

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(input)
            val buffer = ByteArray(8 * 1024)
            val out = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                // Finished-but-empty means truncated input; bail rather than spin forever.
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
                require(out.size() <= MAX_INFLATED_BYTES) { "shared playlist payload too large" }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
