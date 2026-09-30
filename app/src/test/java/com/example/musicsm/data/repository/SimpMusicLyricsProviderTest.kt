package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpMusicLyricsProviderTest {

    @Test
    fun `duration match chooses rich sync over line sync and plain`() {
        val lyrics = parseSimpMusicLyricsResponse(
            """
            {
              "success": true,
              "data": [
                {
                  "duration": 190,
                  "richSyncLyrics": "[00:01.00]<00:01.00>Wrong <00:01.50>cut",
                  "syncedLyrics": "[00:01.00]Wrong cut",
                  "plainLyrics": "Wrong cut"
                },
                {
                  "duration": 201,
                  "richSyncLyrics": "[00:10.00]<00:10.00>I&#x27;m <00:10.40>blinded <00:11.20>by <00:11.50>the lights\n[00:13.00]next",
                  "syncedLyrics": "[00:10.00]I'm blinded by the lights",
                  "plainLyrics": "I'm blinded by the lights"
                }
              ]
            }
            """.trimIndent(),
            targetMs = 200_000,
        )

        requireNotNull(lyrics)
        assertEquals(LyricsSource.SIMPMUSIC, lyrics.source)
        assertTrue(lyrics.synced)
        assertTrue(lyrics.timingVerified)
        assertEquals("I'm blinded by the lights", lyrics.lines.first().text)
        assertEquals(
            listOf(
                LyricWord(10_000, 10_400, 0, 3),
                LyricWord(10_400, 11_200, 4, 11),
                LyricWord(11_200, 11_500, 12, 14),
                LyricWord(11_500, 13_000, 15, 25),
            ),
            lyrics.lines.first().words,
        )
    }

    @Test
    fun `line sync is used when rich sync is absent`() {
        val lyrics = parseSimpMusicLyricsResponse(
            """
            {"success":true,"data":[{"duration":200,"syncedLyrics":"[00:01.00]One\n[00:02.50]Two","plainLyrics":"One\nTwo"}]}
            """.trimIndent(),
            targetMs = 200_000,
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.synced)
        assertEquals(listOf(1_000L, 2_500L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("One", "Two"), lyrics.lines.map { it.text })
    }

    @Test
    fun `plain lyrics are returned when no synced text is present`() {
        val lyrics = parseSimpMusicLyricsResponse(
            """
            {"success":true,"data":[{"duration":200,"plainLyrics":"One\nTwo"}]}
            """.trimIndent(),
            targetMs = 200_000,
        )

        requireNotNull(lyrics)
        assertFalse(lyrics.synced)
        assertFalse(lyrics.timingVerified)
        assertEquals(listOf("One", "Two"), lyrics.lines.map { it.text })
    }

    @Test
    fun `duration outside SimpMusic tolerance is no match`() {
        val lyrics = parseSimpMusicLyricsResponse(
            """
            {"success":true,"data":[{"duration":180,"syncedLyrics":"[00:01.00]Wrong cut"}]}
            """.trimIndent(),
            targetMs = 200_000,
        )

        assertNull(lyrics)
    }
}
