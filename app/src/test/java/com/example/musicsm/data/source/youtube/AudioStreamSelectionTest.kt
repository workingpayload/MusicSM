package com.example.musicsm.data.source.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioStreamSelectionTest {

    private data class Candidate(
        val name: String,
        val aac: Boolean,
        val progressive: Boolean,
        val kbps: Int,
    )

    private fun pick(vararg streams: Candidate): Candidate? = pickAudioStream(
        streams = streams.toList(),
        isAac = Candidate::aac,
        isProgressive = Candidate::progressive,
        bitrate = Candidate::kbps,
    )

    private val opus160 = Candidate("opus-251", aac = false, progressive = true, kbps = 160)
    private val opus70 = Candidate("opus-250", aac = false, progressive = true, kbps = 70)
    private val aac128 = Candidate("aac-140", aac = true, progressive = true, kbps = 128)
    private val aac48 = Candidate("aac-139", aac = true, progressive = true, kbps = 48)

    @Test
    fun `AAC wins over a higher-bitrate Opus stream`() {
        assertEquals(aac128, pick(opus160, aac128, opus70))
    }

    @Test
    fun `highest-bitrate AAC is chosen among AAC streams`() {
        assertEquals(aac128, pick(aac48, opus160, aac128))
    }

    @Test
    fun `progressive AAC beats a higher-bitrate manifest AAC`() {
        val hlsAac = Candidate("aac-hls", aac = true, progressive = false, kbps = 256)
        assertEquals(aac128, pick(hlsAac, aac128))
    }

    @Test
    fun `manifest-only AAC is skipped for progressive Opus`() {
        val hlsAac = Candidate("aac-hls", aac = true, progressive = false, kbps = 128)
        assertEquals(opus160, pick(opus160, hlsAac))
    }

    @Test
    fun `returns null when only manifest streams are offered`() {
        val hlsAac = Candidate("aac-hls", aac = true, progressive = false, kbps = 128)
        assertNull(pick(hlsAac))
    }

    @Test
    fun `falls back to the best Opus when no AAC is offered`() {
        assertEquals(opus160, pick(opus70, opus160))
    }

    @Test
    fun `returns null when nothing is offered`() {
        assertNull(pick())
    }

    private data class Muxed(
        val name: String,
        val aac: Boolean,
        val progressive: Boolean,
        val height: Int,
    )

    private fun pickMuxed(vararg streams: Muxed): Muxed? = pickMuxedStream(
        streams = streams.toList(),
        isAac = Muxed::aac,
        isProgressive = Muxed::progressive,
        height = Muxed::height,
    )

    @Test
    fun `smallest progressive MP4 is chosen as the muxed fallback`() {
        val mp4At360 = Muxed("itag-18", aac = true, progressive = true, height = 360)
        val mp4At720 = Muxed("itag-22", aac = true, progressive = true, height = 720)
        val webmAt144 = Muxed("webm", aac = false, progressive = true, height = 144)
        assertEquals(mp4At360, pickMuxed(mp4At720, webmAt144, mp4At360))
    }

    @Test
    fun `muxed fallback ignores manifest streams`() {
        val hls = Muxed("hls", aac = true, progressive = false, height = 144)
        val webm = Muxed("webm", aac = false, progressive = true, height = 360)
        assertEquals(webm, pickMuxed(hls, webm))
        assertNull(pickMuxed(hls))
    }
}
