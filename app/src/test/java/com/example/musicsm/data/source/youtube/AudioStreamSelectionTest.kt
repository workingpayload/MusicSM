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
    fun `manifest-only AAC still beats progressive Opus`() {
        val hlsAac = Candidate("aac-hls", aac = true, progressive = false, kbps = 128)
        assertEquals(hlsAac, pick(opus160, hlsAac))
    }

    @Test
    fun `falls back to the best Opus when no AAC is offered`() {
        assertEquals(opus160, pick(opus70, opus160))
    }

    @Test
    fun `returns null when nothing is offered`() {
        assertNull(pick())
    }
}
