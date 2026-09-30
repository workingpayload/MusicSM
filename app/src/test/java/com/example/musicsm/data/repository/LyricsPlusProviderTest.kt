package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricWord
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPlusProviderTest {

    @Test
    fun `word-timed JSON becomes line text with valid character ranges`() {
        val doc = parseLyricsPlusJson(BLINDING_LIGHTS_FIXTURE)!!

        assertEquals("Blinding Lights", doc.title)
        assertEquals("The Weeknd", doc.artist)
        assertEquals("USUG11904206", doc.isrc)
        assertEquals(201.57, doc.durationSec!!, 0.001)

        val first = doc.lines.first()
        assertEquals(27_395L, first.timeMs)
        assertEquals("I been tryna call", first.text)
        assertEquals(
            listOf(
                LyricWord(27_395, 27_549, 0, 1),
                LyricWord(27_549, 27_740, 2, 6),
                LyricWord(27_740, 28_077, 7, 12),
                LyricWord(28_077, 28_960, 13, 17),
            ),
            first.words,
        )
    }

    @Test
    fun `adjacent syllables share one written word without invented spaces`() {
        val (text, words) = parseLyricsPlusSyllables(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """[
                  {"time":1000,"duration":100,"text":"e"},
                  {"time":1100,"duration":250,"text":"nough "},
                  {"time":1400,"duration":100,"text":"now"}
                ]""",
            ).jsonArray,
        )

        assertEquals("enough now", text)
        assertEquals(
            listOf(
                LyricWord(1_000, 1_100, 0, 1),
                LyricWord(1_100, 1_350, 1, 6),
                LyricWord(1_400, 1_500, 7, 10),
            ),
            words,
        )
    }

    @Test
    fun `background and alternate lines are dropped from the main lyrics`() {
        val json = """
            {
              "type": "Word",
              "metadata": {"title":"Blinding Lights","artist":"The Weeknd","totalDuration":"3:21.570","isrc":"USUG11904206"},
              "lyrics": [
                {"time":1000,"duration":500,"text":"lead","syllabus":[{"time":1000,"duration":500,"text":"lead"}],"element":{"singer":"v1"}},
                {"time":1100,"duration":500,"text":"answer","syllabus":[{"time":1100,"duration":500,"text":"answer"}],"element":["background"]},
                {"time":1200,"duration":500,"text":"translation","syllabus":[{"time":1200,"duration":500,"text":"translation"}],"element":{"role":"x-translation"}}
              ]
            }
        """.trimIndent()

        val doc = parseLyricsPlusJson(json)!!

        assertEquals(listOf("lead"), doc.lines.map { it.text })
    }

    @Test
    fun `duration verification and metadata matching reject clear misses`() {
        val doc = parseLyricsPlusJson(BLINDING_LIGHTS_FIXTURE)!!

        assertTrue(lyricsPlusTimingVerified(200_000, doc.durationSec))
        assertFalse(lyricsPlusTimingVerified(216_000, doc.durationSec))
        assertTrue(lyricsPlusMetadataMatches(doc, "Blinding Lights", "The Weeknd", 200_000, "USUG11904206"))
        assertFalse(lyricsPlusMetadataMatches(doc, "Blinding Lights", "The Weeknd", 200_000, "GBUM71905957"))
        assertFalse(lyricsPlusMetadataMatches(doc, "Save Your Tears", "The Weeknd", 200_000, null))
    }

    @Test
    fun `empty lyrics payload is a normal miss`() {
        assertNull(parseLyricsPlusJson("""{"type":"Word","lyrics":[]}"""))
    }

    private companion object {
        val BLINDING_LIGHTS_FIXTURE = """
            {
              "KpoeTools": "1.7-1-ConvertTTMLtoJSON-DOMParser",
              "type": "Word",
              "metadata": {
                "source": "qApple",
                "title": "Blinding Lights",
                "artist": "The Weeknd",
                "album": "After Hours",
                "isrc": "USUG11904206",
                "totalDuration": "3:21.570",
                "agents": {"v1": {"type": "person", "name": "Vocal 1", "alias": "v1"}}
              },
              "lyrics": [
                {
                  "time": 27395,
                  "duration": 1565,
                  "text": "I been tryna call",
                  "syllabus": [
                    {"time": 27395, "duration": 154, "text": "I "},
                    {"time": 27549, "duration": 191, "text": "been "},
                    {"time": 27740, "duration": 337, "text": "tryna "},
                    {"time": 28077, "duration": 883, "text": "call"}
                  ],
                  "element": {"key": "L1", "singer": "v1", "songPartIndex": 0}
                },
                {
                  "time": 30189,
                  "duration": 2340,
                  "text": "I've been on my own for long enough",
                  "syllabus": [
                    {"time": 30189, "duration": 207, "text": "I've "},
                    {"time": 30396, "duration": 246, "text": "been "},
                    {"time": 30642, "duration": 157, "text": "on "},
                    {"time": 30799, "duration": 185, "text": "my "},
                    {"time": 30984, "duration": 261, "text": "own "},
                    {"time": 31245, "duration": 233, "text": "for "},
                    {"time": 31478, "duration": 246, "text": "long "},
                    {"time": 31724, "duration": 805, "text": "enough"}
                  ],
                  "element": {"key": "L2", "singer": "v1", "songPartIndex": 0}
                }
              ]
            }
        """.trimIndent()
    }
}
