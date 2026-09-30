package com.example.musicsm.data.source.youtube

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioVersionTest {

    private fun song(id: String, title: String, artist: String) = Song(id = id, title = title, artist = artist)

    @Test
    fun `prefers the same song by the same artist over a top-ranked cover`() {
        val results = listOf(
            song("cover", "Blurred Lines", "TMC Pop Starz"),
            song("audio", "Blurred Lines (feat. T.I. & Pharrell)", "Robin Thicke"),
        )
        assertEquals("audio", pickAudioVersion("Blurred Lines (Unrated Version)", "Robin Thicke", results)?.id)
    }

    @Test
    fun `reads the artist out of VEVO channels and Artist - Title uploads`() {
        val results = listOf(song("audio", "Sugar", "Maroon 5"))
        assertEquals("audio", pickAudioVersion("Maroon 5 - Sugar (Official Music Video)", "Maroon5VEVO", results)?.id)
    }

    @Test
    fun `trusts only the top result when the channel isn't named after the artist`() {
        val results = listOf(
            song("audio", "Animals", "Maroon 5"),
            song("other", "Animals", "Martin Garrix"),
        )
        assertEquals("audio", pickAudioVersion("Animals", "Kara's Flowers", results)?.id)
        // Top result is a different song: a same-titled one further down isn't taken on trust.
        assertNull(pickAudioVersion("Animals", "Kara's Flowers", listOf(song("x", "Payphone", "Maroon 5")) + results.drop(1)))
    }

    @Test
    fun `never swaps in another version of the song`() {
        val results = listOf(
            song("remix", "Animals (Remix) (feat. J. Cole)", "Maroon 5"),
            song("live", "Animals (Live)", "Maroon 5"),
        )
        assertNull(pickAudioVersion("Animals", "Maroon 5", results))
        assertEquals("live", pickAudioVersion("Animals (Live at Wembley)", "Maroon 5", results)?.id)
    }

    @Test
    fun `nothing to match on is no match`() {
        assertNull(pickAudioVersion("(Official Video)", "Maroon 5", listOf(song("a", "Animals", "Maroon 5"))))
        assertNull(pickAudioVersion("Animals", "Maroon 5", emptyList()))
    }

    @Test
    fun `keeps the title when featured artists come before the dash`() {
        val results = listOf(
            song("cover", "I'm On One", "Cover Crew"),
            song("audio", "I'm On One (feat. Drake, Rick Ross & Lil Wayne)", "DJ Khaled"),
        )
        assertEquals(
            "audio",
            pickAudioVersion("DJ Khaled ft. Drake, Rick Ross, Lil Wayne - I'm On One (Explicit Version)", "DJKhaledVEVO", results)?.id,
        )
    }

    @Test
    fun `reads the artist from the title when a label or another channel uploaded it`() {
        val results = listOf(
            song("cover", "Lucid Dreams", "Piano Covers"),
            song("audio", "Lucid Dreams", "Juice WRLD"),
        )
        assertEquals("audio", pickAudioVersion("Juice WRLD - Lucid Dreams (Official Music Video)", "Lyrical Lemonade", results)?.id)
        // "Title - Film | cast" as film labels post songs: YouTube Music's top result is the song.
        val film = listOf(song("song", "Tum Hi Ho", "Arijit Singh"))
        assertEquals("song", pickAudioVersion("Tum Hi Ho - Aashiqui 2 | Aditya Roy Kapur | Mithoon", "T-Series", film)?.id)
    }
}
