package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickMatchTest {

    private fun song(id: String, durationMs: Long) = Song(id = id, title = id, artist = "Artist", durationMs = durationMs)

    @Test
    fun `prefers the result about as long as the original`() {
        val results = listOf(song("live", 300_000), song("studio", 215_000), song("other", 214_000))
        assertEquals("studio", pickMatch(results, 213_000)?.id)
    }

    @Test
    fun `falls back to the top result`() {
        val results = listOf(song("first", 300_000), song("second", 400_000))
        assertEquals("first", pickMatch(results, 200_000)?.id)
        assertEquals("first", pickMatch(results, durationMs = 0)?.id)
        assertNull(pickMatch(emptyList(), 200_000))
    }

    @Test
    fun `looks only at the top few results`() {
        val results = List(5) { song("far$it", 500_000) } + song("close", 200_000)
        assertEquals("far0", pickMatch(results, 200_000)?.id)
    }
}
