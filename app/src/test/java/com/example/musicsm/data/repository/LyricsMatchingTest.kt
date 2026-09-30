package com.example.musicsm.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchingTest {

    private fun candidate(
        track: String = "Blinding Lights",
        artist: String = "The Weeknd",
        duration: Double,
        synced: Boolean = true,
        plain: Boolean = true,
        trusted: Boolean = false,
    ) = LyricsCandidate(
        trackName = track,
        artistName = artist,
        durationSec = duration,
        syncedLrc = if (synced) "[00:01.00]line" else null,
        plainLyrics = if (plain) "line" else null,
        trustedMatch = trusted,
    )

    private fun choose(vararg c: LyricsCandidate, target: Double) =
        chooseLyrics(c.toList(), "Blinding Lights", "The Weeknd", target)

    @Test
    fun `picks the recording whose length matches the playing audio`() {
        val album = candidate(duration = 200.0)
        val extended = candidate(duration = 261.0)
        val choice = choose(extended, album, target = 201.0)!!
        assertEquals(album, choice.candidate)
        assertTrue(choice.timingVerified)
    }

    @Test
    fun `the longer cut wins when that is what is playing`() {
        val album = candidate(duration = 200.0)
        val extended = candidate(duration = 261.0)
        val choice = choose(album, extended, target = 260.0)!!
        assertEquals(extended, choice.candidate)
        assertTrue(choice.timingVerified)
    }

    @Test
    fun `a different cut by the same artist is shown but not verified`() {
        val album = candidate(duration = 200.0)
        val choice = choose(album, target = 232.0)!!
        assertEquals(album, choice.candidate)
        assertFalse(choice.timingVerified)
    }

    @Test
    fun `a different song returned by fuzzy search is never chosen`() {
        // LRCLIB really does return this for an artist-qualified "Blinding Lights" search.
        val wrongSong = candidate(track = "Get Lucky", artist = "Daft Punk", duration = 248.0)
        assertNull(choose(wrongSong, target = 248.0))
    }

    @Test
    fun `same title by another artist is only accepted at the same length`() {
        val cover = candidate(artist = "Someone Else", duration = 185.0)
        assertNull(choose(cover, target = 200.0))

        val sameLength = candidate(artist = "Someone Else", duration = 200.0)
        assertTrue(choose(sameLength, target = 200.0)!!.timingVerified)
    }

    @Test
    fun `verified synced beats a closer plain-only entry`() {
        val plainExact = candidate(duration = 200.0, synced = false)
        val syncedNear = candidate(duration = 202.0)
        val choice = choose(plainExact, syncedNear, target = 200.0)!!
        assertEquals(syncedNear, choice.candidate)
        assertTrue(choice.timingVerified)
    }

    @Test
    fun `falls back to plain lyrics when nothing synced exists`() {
        val plain = candidate(duration = 200.0, synced = false)
        val choice = choose(plain, target = 200.0)!!
        assertEquals(plain, choice.candidate)
        assertFalse(choice.timingVerified)
    }

    @Test
    fun `exact-endpoint result is trusted even when names are written differently`() {
        val exact = candidate(track = "ब्लाइंडिंग लाइट्स", artist = "द वीकेंड", duration = 200.0, trusted = true)
        assertTrue(choose(exact, target = 200.0)!!.timingVerified)
    }

    @Test
    fun `unknown length never claims verified timing`() {
        val album = candidate(duration = 200.0)
        val choice = choose(album, target = 0.0)!!
        assertFalse(choice.timingVerified)
    }

    @Test
    fun `names match ignoring case, accents, punctuation and extra words`() {
        assertTrue(namesMatch("Beyoncé", "beyonce"))
        assertTrue(namesMatch("Blinding Lights", "The Weeknd - Blinding Lights"))
        assertTrue(namesMatch("The Weeknd", "The Weeknd, Daft Punk"))
        assertFalse(namesMatch("Get Lucky", "Blinding Lights"))
        assertFalse(namesMatch("", "Blinding Lights"))
    }
}
