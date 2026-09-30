package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.Lyrics
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSelectionTest {

    @Test
    fun `prefer word ranks verified line above unverified word`() {
        val verifiedLine = lyrics(synced = true, timingVerified = true)
        val unverifiedWord = lyrics(synced = true, hasWords = true)

        assertSame(
            verifiedLine,
            pickBest(listOf(0 to unverifiedWord, 1 to verifiedLine), preferWord = true)?.second,
        )
    }

    @Test
    fun `prefer word ranks word above line at same verification level`() {
        val verifiedLine = lyrics(synced = true, timingVerified = true)
        val verifiedWord = lyrics(synced = true, timingVerified = true, hasWords = true)
        val unverifiedLine = lyrics(synced = true)
        val unverifiedWord = lyrics(synced = true, hasWords = true)

        assertTrue(lyricsRank(verifiedWord, preferWord = true) > lyricsRank(verifiedLine, preferWord = true))
        assertTrue(lyricsRank(unverifiedWord, preferWord = true) > lyricsRank(unverifiedLine, preferWord = true))
    }

    @Test
    fun `source priority breaks ties within a rank`() {
        val lowerPriority = lyrics(synced = true, timingVerified = true, hasWords = true)
        val higherPriority = lyrics(synced = true, timingVerified = true, hasWords = true)

        assertSame(
            higherPriority,
            pickBest(listOf(3 to lowerPriority, 1 to higherPriority), preferWord = true)?.second,
        )
    }

    @Test
    fun `plain lyrics rank last`() {
        val plain = lyrics(synced = false)
        val unverifiedLine = lyrics(synced = true)

        assertSame(
            unverifiedLine,
            pickBest(listOf(0 to plain, 1 to unverifiedLine), preferWord = true)?.second,
        )
    }

    @Test
    fun `preference off keeps legacy verified synced then synced then plain ranking`() {
        val verifiedLine = lyrics(synced = true, timingVerified = true)
        val unverifiedWord = lyrics(synced = true, hasWords = true)
        val highPriorityLine = lyrics(synced = true)
        val lowPriorityWord = lyrics(synced = true, hasWords = true)

        assertSame(
            verifiedLine,
            pickBest(listOf(0 to unverifiedWord, 1 to verifiedLine), preferWord = false)?.second,
        )
        assertSame(
            highPriorityLine,
            pickBest(listOf(0 to highPriorityLine, 1 to lowPriorityWord), preferWord = false)?.second,
        )
    }

    private fun lyrics(
        synced: Boolean,
        timingVerified: Boolean = false,
        hasWords: Boolean = false,
    ): Lyrics {
        val words = if (hasWords) {
            listOf(LyricWord(startMs = 0L, endMs = 500L, charStart = 0, charEnd = 5))
        } else {
            emptyList()
        }
        return Lyrics(
            synced = synced,
            timingVerified = timingVerified,
            lines = listOf(LyricLine(timeMs = if (synced) 0L else null, text = "hello", words = words)),
        )
    }
}
