package com.example.musicsm.data.source.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeArtworkTest {

    private val still = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
    private val cover = "https://lh3.googleusercontent.com/AbCdEf=w544-h544-l90-rj"
    private val avatar = "https://yt3.ggpht.com/ytc/AbCdEf=s176-c-k-c0x00ffffff-no-rj"

    @Test
    fun `small request keeps video stills on the rung that always exists`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            YouTubeArtwork.resize(still, 544),
        )
    }

    @Test
    fun `a header sized request upgrades a video still to maxres`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
            YouTubeArtwork.resize(still, 1200),
        )
    }

    @Test
    fun `a request already at maxres is downgraded when a small size is asked for`() {
        val big = "https://i.ytimg.com/vi/abc123/maxresdefault.jpg"
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", YouTubeArtwork.resize(big, 96))
    }

    @Test
    fun `cover size suffix is replaced rather than appended`() {
        assertEquals(
            "https://lh3.googleusercontent.com/AbCdEf=w1200-h1200-p-l90-rj",
            YouTubeArtwork.resize(cover, 1200),
        )
    }

    @Test
    fun `a cover url with no suffix gains one`() {
        assertEquals(
            "https://lh3.googleusercontent.com/AbCdEf=w96-h96-p-l90-rj",
            YouTubeArtwork.resize("https://lh3.googleusercontent.com/AbCdEf", 96),
        )
    }

    @Test
    fun `non square requests are honoured for covers`() {
        assertEquals(
            "https://lh3.googleusercontent.com/AbCdEf=w400-h225-p-l90-rj",
            YouTubeArtwork.resize(cover, 400, 225),
        )
    }

    @Test
    fun `avatars take a single dimension and drop the old one`() {
        assertEquals("https://yt3.ggpht.com/ytc/AbCdEf=s512", YouTubeArtwork.resize(avatar, 512))
    }

    @Test
    fun `an avatar asked for a non square size takes the larger side`() {
        assertEquals("https://yt3.ggpht.com/ytc/AbCdEf=s400", YouTubeArtwork.resize(avatar, 200, 400))
    }

    @Test
    fun `unknown hosts are left alone`() {
        val other = "https://cdn.example.com/cover.png"
        assertEquals(other, YouTubeArtwork.resize(other, 1200))
    }

    @Test
    fun `a local file uri is left alone`() {
        val local = "content://media/external/audio/albumart/42"
        assertEquals(local, YouTubeArtwork.resize(local, 96))
    }

    @Test
    fun `resizeOrNull passes null through`() {
        assertNull(YouTubeArtwork.resizeOrNull(null, 544))
    }

    @Test
    fun `a guaranteed request will not gamble on a video still that may not exist`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            YouTubeArtwork.guaranteedOrNull(still, 1200),
        )
    }

    @Test
    fun `a guaranteed request is free to ask a resizing host for the full size`() {
        assertEquals(
            "https://lh3.googleusercontent.com/AbCdEf=w1200-h1200-p-l90-rj",
            YouTubeArtwork.guaranteedOrNull(cover, 1200),
        )
    }

    @Test
    fun `a guaranteed request below the safe size is not inflated`() {
        assertEquals(
            "https://lh3.googleusercontent.com/AbCdEf=w96-h96-p-l90-rj",
            YouTubeArtwork.guaranteedOrNull(cover, 96),
        )
    }

    @Test
    fun `guaranteedOrNull passes null through`() {
        assertNull(YouTubeArtwork.guaranteedOrNull(null, 1200))
    }

    @Test
    fun `the fallback ladder steps down one rung at a time`() {
        var url: String? = "https://i.ytimg.com/vi/abc123/maxresdefault.jpg"
        val walked = buildList {
            while (url != null) {
                url = YouTubeArtwork.nextFallback(url!!)
                url?.let { add(it.substringAfterLast('/')) }
            }
        }
        assertEquals(listOf("sddefault.jpg", "hqdefault.jpg", "mqdefault.jpg"), walked)
    }

    @Test
    fun `the bottom rung has nowhere left to fall`() {
        assertNull(YouTubeArtwork.nextFallback("https://i.ytimg.com/vi/abc123/mqdefault.jpg"))
    }

    @Test
    fun `covers have no ladder because a resize is always honoured`() {
        assertNull(YouTubeArtwork.nextFallback(cover))
    }
}
