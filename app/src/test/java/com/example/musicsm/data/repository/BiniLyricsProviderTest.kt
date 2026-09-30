package com.example.musicsm.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiniLyricsProviderTest {

    @Test
    fun `search parser chooses the closest matching recording and exposes its ISRC`() {
        val json = """
            {
              "results": [
                {
                  "album_name": "After Hours",
                  "artist_name": "The Weeknd",
                  "duration": 200,
                  "id": "USUG11904206",
                  "isrc": "USUG11904206",
                  "lyricsUrl": "https://lrc.red/s/USUG11904206.ttml",
                  "timing_type": "word",
                  "track_name": "Blinding Lights"
                },
                {
                  "album_name": "Blinding Lights - Single",
                  "artist_name": "All Hail The Queen",
                  "duration": 199,
                  "id": "QZK6M2043581",
                  "isrc": "QZK6M2043581",
                  "lyricsUrl": "https://lrc.red/s/QZK6M2043581.ttml",
                  "timing_type": "none",
                  "track_name": "Blinding Lights"
                },
                {
                  "album_name": "Blinding Lights (Remix) - Single",
                  "artist_name": "The Weeknd, ROSALÍA",
                  "duration": 216,
                  "id": "USUG12004507",
                  "isrc": "USUG12004507",
                  "lyricsUrl": "https://lrc.red/s/USUG12004507.ttml",
                  "timing_type": "line",
                  "track_name": "Blinding Lights (Remix)"
                }
              ],
              "source": "HIT-LRC-RED",
              "total": 3
            }
        """.trimIndent()

        val hit = chooseBiniHit(parseBiniHits(json), "Blinding Lights", "The Weeknd", 200_000)!!

        assertEquals("USUG11904206", hit.isrc)
        assertEquals("https://lrc.red/s/USUG11904206.ttml", hit.lyricsUrl)
        assertEquals(200.0, hit.durationSec!!, 0.001)
    }

    @Test
    fun `ISRC selection ignores other results`() {
        val json = """
            {"results":[
              {"artist_name":"The Weeknd","duration":200,"isrc":"USUG11904206","lyricsUrl":"https://lrc.red/s/USUG11904206.ttml","track_name":"Blinding Lights"},
              {"artist_name":"The Weeknd","duration":201,"isrc":"GBUM71905957","lyricsUrl":"https://lrc.red/s/GBUM71905957.ttml","track_name":"Blinding Lights"}
            ]}
        """.trimIndent()

        val hit = chooseBiniHit(parseBiniHits(json), "Blinding Lights", "The Weeknd", 200_000, "GBUM71905957")!!

        assertEquals("GBUM71905957", hit.isrc)
    }

    @Test
    fun `no search results are a normal miss`() {
        assertNull(chooseBiniHit(parseBiniHits("""{"results":[],"source":"MISS-LRC-RED","total":0}"""), "Nope", "Nobody", 200_000))
    }

    @Test
    fun `timing is verified only when matched duration agrees`() {
        assertTrue(biniTimingVerified(200_000, 201.57))
        assertFalse(biniTimingVerified(200_000, 216.0))
        assertFalse(biniTimingVerified(0, 200.0))
    }
}
