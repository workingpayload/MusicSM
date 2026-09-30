package com.example.musicsm.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemMapperTest {

    @Test
    fun `uses album as artist fallback in media metadata`() {
        val song = Song(
            id = "vid00000001",
            title = "Midnight Signal",
            artist = " ",
            album = "Night Drive",
        )

        val item = MediaItemMapper.toMediaItem(song)

        assertEquals("Night Drive", item.mediaMetadata.artist.toString())
        assertEquals("Night Drive", MediaItemMapper.toSong(item).artist)
    }

    @Test
    fun `keeps explicit artist ahead of album fallback`() {
        val item = MediaItem.Builder()
            .setMediaId("vid00000002")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Afterglow")
                    .setArtist("Neon Fields")
                    .setAlbumTitle("Night Drive")
                    .build(),
            )
            .build()

        assertEquals("Neon Fields", MediaItemMapper.toSong(item).artist)
    }
}
