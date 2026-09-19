package com.example.musicsm.domain.share

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistShareCodecTest {

    private fun song(i: Int) = Song(
        id = "vid%07d".format(i),
        title = "Track number $i",
        artist = "Artist ${i % 12}",
        durationMs = (180 + i).toLong() * 1000,
    )

    @Test
    fun `round trips name and tracks`() {
        val original = SharedPlaylist("Late night drive", List(25) { song(it) })

        val decoded = PlaylistShareCodec.decode(PlaylistShareCodec.encode(original))

        assertNotNull(decoded)
        assertEquals("Late night drive", decoded!!.name)
        assertEquals(25, decoded.songs.size)
        assertEquals(original.songs[7].id, decoded.songs[7].id)
        assertEquals(original.songs[7].title, decoded.songs[7].title)
        assertEquals(original.songs[7].artist, decoded.songs[7].artist)
        assertEquals(original.songs[7].durationMs, decoded.songs[7].durationMs)
    }

    @Test
    fun `rebuilds artwork from the video id`() {
        val decoded = PlaylistShareCodec.decode(
            PlaylistShareCodec.encode(SharedPlaylist("x", listOf(song(1)))),
        )

        assertEquals(
            "https://i.ytimg.com/vi/vid0000001/hqdefault.jpg",
            decoded!!.songs.single().artworkUrl,
        )
    }

    @Test
    fun `separators inside fields cannot break the format`() {
        val nasty = Song(
            id = "abc",
            // A newline and a unit separator would both corrupt the row if passed through.
            title = "Weird\ntitle\u001Fwith separators",
            artist = "Some\r\nArtist",
            durationMs = 1_000,
        )

        val decoded = PlaylistShareCodec.decode(
            PlaylistShareCodec.encode(SharedPlaylist("p", listOf(nasty))),
        )

        assertEquals(1, decoded!!.songs.size)
        assertEquals("Weird title with separators", decoded.songs.single().title)
        assertEquals("Some  Artist", decoded.songs.single().artist)
    }

    @Test
    fun `unicode names survive the round trip`() {
        val decoded = PlaylistShareCodec.decode(
            PlaylistShareCodec.encode(SharedPlaylist("夜のドライブ 🎧", listOf(song(1)))),
        )

        assertEquals("夜のドライブ 🎧", decoded!!.name)
    }

    @Test
    fun `payload is url safe and compact enough for a qr code`() {
        val payload = PlaylistShareCodec.encode(SharedPlaylist("Road trip", List(60) { song(it) }))

        assertTrue(payload.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        // QR version 40-L holds ~2953 bytes; 60 tracks must fit comfortably inside that.
        assertTrue("payload was ${payload.length} chars", payload.length < 2_000)
    }

    @Test
    fun `extracts the payload from a share message`() {
        val url = PlaylistShareCodec.shareUrl(SharedPlaylist("Mix", listOf(song(3))))

        val extracted = PlaylistShareCodec.payloadFromUrl("Check this out!\n$url\nsent from MusicSM")

        assertNotNull(extracted)
        assertEquals("Mix", PlaylistShareCodec.decode(extracted!!)!!.name)
    }

    @Test
    fun `rejects junk instead of throwing`() {
        assertNull(PlaylistShareCodec.decode(""))
        assertNull(PlaylistShareCodec.decode("not-base64!!!"))
        assertNull(PlaylistShareCodec.decode("aGVsbG8gd29ybGQ"))
        assertNull(PlaylistShareCodec.payloadFromUrl("https://example.com/nope"))
    }

    @Test
    fun `rejects a payload with no usable tracks`() {
        assertNull(PlaylistShareCodec.decode(PlaylistShareCodec.encode(SharedPlaylist("Empty", emptyList()))))
    }
}
