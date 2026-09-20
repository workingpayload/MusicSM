package com.example.musicsm.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicsm.domain.model.Song

/**
 * Song <-> MediaItem. The URI is a placeholder (`ytstream:<videoId>`) — the real audio URL
 * is resolved lazily by [StreamUrlResolver] on the player's loader thread, since URLs expire.
 */
object MediaItemMapper {

    const val SCHEME = "ytstream"

    /**
     * Marks a [Song.id] as a device-local file rather than a YouTube videoId. The rest of the id is
     * the file's real `content://` (or `file://`) URI, which [StreamUrlResolver] plays directly with
     * no network round-trip. Format: `local:content://media/external/audio/media/123`.
     */
    const val LOCAL_PREFIX = "local:"

    /** True when [id] refers to a device-local track scanned from MediaStore. */
    fun isLocal(id: String): Boolean = id.startsWith(LOCAL_PREFIX)

    fun toMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .apply { song.artworkUrl?.let { setArtworkUri(it.toUri()) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri("$SCHEME:${song.id}")
            // Stable cache key so the media cache keys by videoId, not the volatile resolved URL.
            .setCustomCacheKey(song.id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun toSong(item: MediaItem): Song = Song(
        id = item.mediaId,
        title = item.mediaMetadata.title?.toString().orEmpty(),
        artist = item.mediaMetadata.artist?.toString().orEmpty(),
        album = item.mediaMetadata.albumTitle?.toString(),
        artworkUrl = item.mediaMetadata.artworkUri?.toString(),
    )
}
