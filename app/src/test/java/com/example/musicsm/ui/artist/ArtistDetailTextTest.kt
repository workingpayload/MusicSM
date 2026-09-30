package com.example.musicsm.ui.artist

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDetailTextTest {

    @Test
    fun `loading placeholder hides channel ids but keeps names`() {
        assertEquals("", displayableArtistSeed("UCfixtureArtist"))
        assertEquals("Neon Fields", displayableArtistSeed(" Neon Fields "))
    }

    @Test
    fun `artist detail name falls back to song credit without exposing ids`() {
        val songs = listOf(Song(id = "vid00000001", title = "Midnight Signal", artist = "Neon Fields"))

        assertEquals("Neon Fields", resolvedArtistDetailName("UCfixtureArtist", "UCfixtureArtist", songs))
        assertEquals("", resolvedArtistDetailName("MPREb_fixture01", "UCfixtureArtist", emptyList()))
    }
}
