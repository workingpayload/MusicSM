package com.example.musicsm.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses minutes seconds and hundredths`() {
        val lines = parseLrcLines("[01:02.35]Hello")
        assertEquals(1, lines.size)
        assertEquals(62_350L, lines[0].timeMs)
        assertEquals("Hello", lines[0].text)
    }

    @Test
    fun `tenths and milliseconds both scale correctly`() {
        assertEquals(500L, parseLrcLines("[00:00.5]x")[0].timeMs)
        assertEquals(50L, parseLrcLines("[00:00.05]x")[0].timeMs)
        assertEquals(5L, parseLrcLines("[00:00.005]x")[0].timeMs)
    }

    @Test
    fun `colon is accepted as the fraction separator`() {
        assertEquals(62_350L, parseLrcLines("[01:02:35]Hello")[0].timeMs)
    }

    @Test
    fun `timestamp without a fraction works`() {
        assertEquals(62_000L, parseLrcLines("[01:02]Hello")[0].timeMs)
    }

    @Test
    fun `a repeated chorus line expands to one entry per timestamp`() {
        val lines = parseLrcLines("[00:10.00][01:30.00]Chorus")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.text == "Chorus" })
        assertEquals(listOf(10_000L, 90_000L), lines.map { it.timeMs })
    }

    @Test
    fun `output is sorted by time regardless of input order`() {
        val lines = parseLrcLines(
            """
            [00:30.00]third
            [00:05.00]first
            [00:10.00]second
            """.trimIndent(),
        )
        assertEquals(listOf("first", "second", "third"), lines.map { it.text })
    }

    @Test
    fun `lines without a timestamp are ignored`() {
        val lines = parseLrcLines(
            """
            [00:01.00]real line
            no timestamp here
            """.trimIndent(),
        )
        assertEquals(1, lines.size)
        assertEquals("real line", lines[0].text)
    }

    @Test
    fun `empty input yields no lines`() {
        assertTrue(parseLrcLines("").isEmpty())
    }

    @Test
    fun `windows line endings do not leak into the text`() {
        val lines = parseLrcLines("[00:01.00]one\r\n[00:02.00]two")
        assertEquals(listOf("one", "two"), lines.map { it.text })
    }
}

class CleanTrackMetadataTest {

    @Test
    fun `strips official video suffixes`() {
        assertEquals("Song Name", cleanTrackMetadata("Song Name (Official Video)"))
        assertEquals("Song Name", cleanTrackMetadata("Song Name (Official Music Video)"))
    }

    @Test
    fun `strips bracketed noise`() {
        assertEquals("Song Name", cleanTrackMetadata("Song Name [4K Remaster]"))
    }

    @Test
    fun `strips the auto-generated Topic artist suffix`() {
        assertEquals("Some Artist", cleanTrackMetadata("Some Artist - Topic"))
    }

    @Test
    fun `collapses whitespace and trims separators`() {
        assertEquals("Song Name", cleanTrackMetadata("  Song    Name  -  "))
    }

    @Test
    fun `leaves an already clean title untouched`() {
        assertEquals("Clocks", cleanTrackMetadata("Clocks"))
    }
}

class StreamCacheTtlTest {

    private val margin = 60_000L

    @Test
    fun `a URL expiring well in the future is reused`() {
        assertTrue(isStreamUsable(expiresAtMs = 1_000_000, nowMs = 0, marginMs = margin))
    }

    @Test
    fun `an expired URL is rejected`() {
        assertFalse(isStreamUsable(expiresAtMs = 500, nowMs = 1_000, marginMs = margin))
    }

    @Test
    fun `a URL inside the refresh margin is refreshed early`() {
        // Expires in 30s but the margin is 60s, so it must not be handed to the player.
        assertFalse(isStreamUsable(expiresAtMs = 30_000, nowMs = 0, marginMs = margin))
    }

    @Test
    fun `the margin boundary is exclusive`() {
        assertFalse(isStreamUsable(expiresAtMs = 60_000, nowMs = 0, marginMs = margin))
        assertTrue(isStreamUsable(expiresAtMs = 60_001, nowMs = 0, marginMs = margin))
    }
}
