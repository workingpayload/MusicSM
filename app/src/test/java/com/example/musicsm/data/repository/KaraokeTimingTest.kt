package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.ui.player.sungCharOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTimingTest {

    @Test
    fun `enhanced LRC stamps become timed words`() {
        val lines = parseLrcLines("[00:10.00]<00:10.00>Look <00:10.50>at the <00:11.20>stars\n[00:13.00]next")
        val first = lines.first()
        assertEquals("Look at the stars", first.text)
        assertEquals(
            listOf(
                LyricWord(10_000, 10_500, 0, 4),
                LyricWord(10_500, 11_200, 5, 11),
                LyricWord(11_200, 13_000, 12, 17),
            ),
            first.words,
        )
        assertTrue(lines[1].words.isEmpty())
    }

    @Test
    fun `sung offset moves through the word being sung`() {
        val words = listOf(LyricWord(1_000, 2_000, 0, 4), LyricWord(2_000, 3_000, 5, 9))
        assertEquals(0f, sungCharOffset(words, 500), 0.001f)
        assertEquals(2f, sungCharOffset(words, 1_500), 0.001f)
        assertEquals(7f, sungCharOffset(words, 2_500), 0.001f)
        assertEquals(Float.POSITIVE_INFINITY, sungCharOffset(words, 3_000), 0f)
    }
}
