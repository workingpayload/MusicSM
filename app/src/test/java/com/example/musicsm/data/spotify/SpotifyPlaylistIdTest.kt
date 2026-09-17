package com.example.musicsm.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistIdTest {

    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun `parses a share link with tracking parameters`() {
        assertEquals(id, extractSpotifyPlaylistId("https://open.spotify.com/playlist/$id?si=abc123"))
    }

    @Test
    fun `parses a plain web link`() {
        assertEquals(id, extractSpotifyPlaylistId("https://open.spotify.com/playlist/$id"))
    }

    @Test
    fun `parses a localised web link`() {
        assertEquals(id, extractSpotifyPlaylistId("https://open.spotify.com/intl-de/playlist/$id"))
    }

    @Test
    fun `parses a spotify URI`() {
        assertEquals(id, extractSpotifyPlaylistId("spotify:playlist:$id"))
    }

    @Test
    fun `accepts a bare id`() {
        assertEquals(id, extractSpotifyPlaylistId(id))
    }

    @Test
    fun `trims surrounding whitespace from a pasted id`() {
        assertEquals(id, extractSpotifyPlaylistId("  $id  "))
    }

    @Test
    fun `rejects junk and short strings`() {
        assertNull(extractSpotifyPlaylistId(""))
        assertNull(extractSpotifyPlaylistId("hello world"))
        assertNull(extractSpotifyPlaylistId("abc123"))
    }
}
