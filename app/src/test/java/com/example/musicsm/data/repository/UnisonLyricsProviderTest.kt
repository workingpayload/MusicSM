package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnisonLyricsProviderTest {

    @Test
    fun `line-synced LRC response becomes verified synced lyrics`() {
        val lyrics = parseUnisonLyricsResponse(
            """
            {
              "success": true,
              "data": {
                "song": "Test Song",
                "artist": "Test Artist",
                "lyrics": "[00:01.00] Test line\n[00:05.00] Test line 2",
                "format": "lrc",
                "syncType": "linesync",
                "duration": 200
              }
            }
            """.trimIndent(),
            targetMs = 200_000,
        )

        requireNotNull(lyrics)
        assertEquals(LyricsSource.UNISON, lyrics.source)
        assertTrue(lyrics.synced)
        assertTrue(lyrics.timingVerified)
        assertEquals(listOf(1_000L, 5_000L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Test line", "Test line 2"), lyrics.lines.map { it.text })
    }

    @Test
    fun `word-synced enhanced LRC keeps word character ranges`() {
        val lyrics = parseUnisonLyricsResponse(
            """
            {
              "success": true,
              "data": {
                "lyrics": "[00:10.00]<00:10.00>Never <00:10.50>gonna <00:11.00>give <00:11.50>you up\n[00:13.00]next",
                "format": "lrc",
                "syncType": "wordsync",
                "duration": 213
              }
            }
            """.trimIndent(),
            targetMs = 213_000,
        )

        requireNotNull(lyrics)
        assertTrue(lyrics.synced)
        assertEquals("Never gonna give you up", lyrics.lines.first().text)
        assertEquals(
            listOf(
                LyricWord(10_000, 10_500, 0, 5),
                LyricWord(10_500, 11_000, 6, 11),
                LyricWord(11_000, 11_500, 12, 16),
                LyricWord(11_500, 13_000, 17, 23),
            ),
            lyrics.lines.first().words,
        )
    }

    @Test
    fun `plain syncType returns untimed lines`() {
        val lyrics = parseUnisonLyricsResponse(
            """
            {"success":true,"data":{"lyrics":"First line\nSecond line","format":"lrc","syncType":"plain","duration":200}}
            """.trimIndent(),
            targetMs = 200_000,
        )

        requireNotNull(lyrics)
        assertFalse(lyrics.synced)
        assertFalse(lyrics.timingVerified)
        assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
        assertTrue(lyrics.lines.all { it.timeMs == null })
    }

    @Test
    fun `unsuccessful or empty Unison response is no match`() {
        assertNull(parseUnisonLyricsResponse("{\"success\":false}", targetMs = 200_000))
        assertNull(parseUnisonLyricsResponse("{\"success\":true,\"data\":{\"format\":\"lrc\"}}", targetMs = 200_000))
    }
}
