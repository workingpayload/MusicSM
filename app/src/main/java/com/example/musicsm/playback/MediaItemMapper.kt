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
