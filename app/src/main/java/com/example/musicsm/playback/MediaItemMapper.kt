package com.example.musicsm.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicsm.data.source.youtube.YouTubeArtwork
import com.example.musicsm.domain.model.Song

/**
 * Song <-> MediaItem. The URI is a placeholder (`ytstream:<videoId>`) — the real audio URL
 * is resolved lazily by [StreamUrlResolver] on the player's loader thread, since URLs expire.
 */
object MediaItemMapper {

    const val SCHEME = "ytstream"

    /**
     * Namespaces media-cache entries by the stream format policy. The cache key has to be fixed
     * before the stream is resolved, so it cannot name the format itself. Bump this whenever
     * the preferred format changes (see `pickAudioStream`); otherwise bytes cached in the old
     * format would be spliced onto the new one. Old entries are never read again and age out
     * of the LRU cache.
     */
    internal const val CACHE_KEY_PREFIX = "aac:"

    fun cacheKey(songId: String): String = CACHE_KEY_PREFIX + songId

    /**
     * Marks a [Song.id] as a device-local file rather than a YouTube videoId. The rest of the id is
     * the file's real `content://` (or `file://`) URI, which [StreamUrlResolver] plays directly with
     * no network round-trip. Format: `local:content://media/external/audio/media/123`.
     */
    const val LOCAL_PREFIX = "local:"

    /** True when [id] refers to a device-local track scanned from MediaStore. */
    fun isLocal(id: String): Boolean = id.startsWith(LOCAL_PREFIX)

    fun toMediaItem(song: Song): MediaItem {
        val artist = resolvedSongArtist(song.artist, song.album)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(artist)
            .setAlbumTitle(song.album)
            // Asked for at header size because this art is not only the notification thumbnail —
            // the system hands it to the lock screen, Android Auto and cast targets, any of which
            // may draw it far larger than the notification ever does.
            .apply {
                YouTubeArtwork.guaranteedOrNull(song.artworkUrl, ArtworkSize.MEDIA_SESSION)
                    ?.let { setArtworkUri(it.toUri()) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri("$SCHEME:${song.id}")
            // Stable cache key so the media cache keys by videoId, not the volatile resolved URL.
            .setCustomCacheKey(cacheKey(song.id))
            .setMediaMetadata(metadata)
            .build()
    }

    fun toSong(item: MediaItem): Song {
        val metadata = item.mediaMetadata
        val album = metadata.albumTitle?.toString()
        return Song(
            id = item.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = resolvedSongArtist(metadata.artist?.toString(), album),
            album = album,
            // Wound back to the size every other source stores, so a song that has been through the
            // player is byte-identical to the same song straight from the library. Anything else and
            // the two disagree on cache keys and the UI re-downloads art it already has.
            artworkUrl = YouTubeArtwork.resizeOrNull(
                metadata.artworkUri?.toString(),
                YouTubeArtwork.CANONICAL,
            ),
        )
    }
}

private fun resolvedSongArtist(artist: String?, album: String?): String =
    artist?.trim()?.takeIf { it.isNotEmpty() }
        ?: album?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""

/** Sizes the media session asks for, kept out of the UI layer's constants. */
private object ArtworkSize {
    const val MEDIA_SESSION = 1200
}
