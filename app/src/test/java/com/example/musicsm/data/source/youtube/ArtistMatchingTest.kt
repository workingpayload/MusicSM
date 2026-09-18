package com.example.musicsm.data.source.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The artist screen lives or dies on these rules: too loose and covers leak back in, too strict
 * and a real discography disappears.
 */
class ArtistMatchingTest {

    @Test
    fun `exact name matches`() {
        assertTrue(ArtistMatching.matches("Taylor Swift", "Taylor Swift"))
    }

    @Test
    fun `case and spacing are ignored`() {
        assertTrue(ArtistMatching.matches("TAYLOR  SWIFT", "taylor swift"))
    }

    @Test
    fun `auto-generated topic channels match`() {
        assertTrue(ArtistMatching.matches("Arijit Singh - Topic", "Arijit Singh"))
    }

    @Test
    fun `vevo channels match`() {
        assertTrue(ArtistMatching.matches("TaylorSwiftVEVO", "Taylor Swift"))
    }

    @Test
    fun `accents fold`() {
        assertTrue(ArtistMatching.matches("Beyoncé", "Beyonce"))
    }

    @Test
    fun `either side of a collaboration matches`() {
        assertTrue(ArtistMatching.matches("Ed Sheeran, Justin Bieber", "Justin Bieber"))
        assertTrue(ArtistMatching.matches("Ed Sheeran, Justin Bieber", "Ed Sheeran"))
    }

    @Test
    fun `featured credits match`() {
        assertTrue(ArtistMatching.matches("Eminem feat. Rihanna", "Rihanna"))
        assertTrue(ArtistMatching.matches("Calvin Harris ft Dua Lipa", "Dua Lipa"))
    }

    @Test
    fun `an ampersand in a single act still matches as a whole`() {
        assertTrue(ArtistMatching.matches("Simon & Garfunkel", "Simon & Garfunkel"))
        assertTrue(ArtistMatching.matches("Earth, Wind & Fire", "Earth, Wind & Fire"))
    }

    @Test
    fun `an x inside a name is not a separator`() {
        assertTrue(ArtistMatching.matches("Lil Nas X", "Lil Nas X"))
    }

    /** The actual bug: a cover or compilation naming the artist in its title. */
    @Test
    fun `unrelated uploader does not match`() {
        assertFalse(ArtistMatching.matches("Music Hub", "Taylor Swift"))
        assertFalse(ArtistMatching.matches("Karaoke Version", "Arijit Singh"))
    }

    /** Whole-name comparison, never substring — otherwise Drake swallows Drake Bell. */
    @Test
    fun `a longer name containing the artist does not match`() {
        assertFalse(ArtistMatching.matches("Drake Bell", "Drake"))
        assertFalse(ArtistMatching.matches("Drake", "Drake Bell"))
    }

    @Test
    fun `blank input never matches`() {
        assertFalse(ArtistMatching.matches("Taylor Swift", ""))
        assertFalse(ArtistMatching.matches(null, "Taylor Swift"))
        assertFalse(ArtistMatching.matches("Taylor Swift", null))
    }

    @Test
    fun `normalize strips punctuation for dedupe keys`() {
        assertTrue(ArtistMatching.normalize("Blinding Lights") == ArtistMatching.normalize("blinding-lights"))
    }

    @Test
    fun `credited names split into individual artists`() {
        val names = ArtistMatching.creditedNames("Ed Sheeran, Justin Bieber & Khalid")
        assertTrue(names.containsAll(listOf("edsheeran", "justinbieber", "khalid")))
    }
}
