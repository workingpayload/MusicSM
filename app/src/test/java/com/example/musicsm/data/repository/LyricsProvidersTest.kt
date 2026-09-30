package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsProvidersTest {

    @Test
    fun `parseClock reads every TTML clock form`() {
        assertEquals(33_522L, parseClock("33.522"))
        assertEquals(65_123L, parseClock("1:05.123"))
        assertEquals(3_723_456L, parseClock("01:02:03.456"))
        assertEquals(12_300L, parseClock("12.3s"))
        assertNull(parseClock("soon"))
    }

    @Test
    fun `KuGou title card and credits are dropped, lyrics kept`() {
        val lines = listOf(
            LyricLine(0, "Yellow - Coldplay"),
            LyricLine(1_000, "Lyrics by: Chris Martin"),
            LyricLine(2_000, "作曲：Coldplay"),
            LyricLine(33_000, "Look at the stars"),
            LyricLine(36_000, "Time: a line that happens to have a colon"),
        )
        val kept = stripKuGouCredits(lines, "Yellow", "Coldplay")
        assertEquals(listOf(33_000L, 36_000L), kept.map { it.timeMs })
    }

    @Test
    fun `plain lyrics split into untimed lines`() {
        val lines = plainLyricLines("One\r\nTwo \n\nThree\n")
        assertEquals(listOf("One", "Two", "", "Three"), lines.map { it.text })
        assertEquals(true, lines.all { it.timeMs == null })
    }
}
