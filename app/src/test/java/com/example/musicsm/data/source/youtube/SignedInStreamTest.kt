package com.example.musicsm.data.source.youtube

import com.example.innertube.model.YtPlayerStreams
import com.example.innertube.model.YtStreamFormat
import com.example.musicsm.domain.model.SignInRequiredException
import com.example.musicsm.domain.model.isSignInRequired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SignedInStreamTest {

    private fun format(
        itag: Int,
        mime: String,
        bitrate: Int,
        url: String? = "https://x/$itag",
        cipher: String? = null,
        default: Boolean? = null,
        height: Int? = null,
    ) = YtStreamFormat(
        itag = itag,
        mimeType = mime,
        bitrate = bitrate,
        url = url,
        signatureCipher = cipher,
        isDefaultAudio = default,
        height = height,
    )

    @Test
    fun `prefers AAC audio over higher-bitrate Opus`() {
        val streams = YtPlayerStreams(
            status = "OK",
            adaptive = listOf(
                format(251, "audio/webm; codecs=\"opus\"", 160_000),
                format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000, url = null, cipher = "s=1&url=u"),
                format(137, "video/mp4; codecs=\"avc1\"", 4_000_000),
            ),
        )
        assertEquals(140, pickSignedInFormat(streams)?.itag)
    }

    @Test
    fun `skips dubbed audio tracks for the original`() {
        val streams = YtPlayerStreams(
            status = "OK",
            adaptive = listOf(
                format(140, "audio/mp4", 130_000, default = false),
                format(139, "audio/mp4", 48_000, default = true),
            ),
        )
        assertEquals(139, pickSignedInFormat(streams)?.itag)
    }

    @Test
    fun `ignores formats without a source and falls back to muxed`() {
        val streams = YtPlayerStreams(
            status = "OK",
            adaptive = listOf(format(140, "audio/mp4", 130_000, url = null)),
            muxed = listOf(format(18, "video/mp4; codecs=\"avc1, mp4a\"", 500_000, height = 360)),
        )
        assertEquals(18, pickSignedInFormat(streams)?.itag)
    }

    @Test
    fun `nothing playable gives null`() {
        assertNull(pickSignedInFormat(YtPlayerStreams(status = "OK")))
    }

    @Test
    fun `finds a sign-in failure anywhere in the cause chain`() {
        val wrapped = IOException("load failed", RuntimeException(SignInRequiredException("blocked")))
        assertTrue(wrapped.isSignInRequired())
        assertFalse(IOException("403").isSignInRequired())
    }
}
