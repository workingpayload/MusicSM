package com.example.musicsm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SongSortTest {

    private val songs = listOf(
        Song(id = "3", title = "Clocks", artist = "Coldplay", album = "Rush", durationMs = 300_000),
        Song(id = "1", title = "Alive", artist = "Zedd", album = "Beta", durationMs = 100_000),
        Song(id = "2", title = "banana", artist = "aespa", album = null, durationMs = 200_000),
    )

    @Test
    fun `default keeps source order`() {
        assertEquals(listOf("3", "1", "2"), songs.sortedFor(SongSort.DEFAULT).map { it.id })
    }

    @Test
    fun `title sort is case-insensitive`() {
        // "banana" must land between "Alive" and "Clocks", not after both.
        assertEquals(listOf("1", "2", "3"), songs.sortedFor(SongSort.TITLE).map { it.id })
    }

    @Test
    fun `artist sort is case-insensitive`() {
        assertEquals(listOf("2", "3", "1"), songs.sortedFor(SongSort.ARTIST).map { it.id })
    }

    @Test
    fun `songs with no album sort last`() {
        assertEquals(listOf("1", "3", "2"), songs.sortedFor(SongSort.ALBUM).map { it.id })
    }

    @Test
    fun `duration sorts both ways`() {
        assertEquals(listOf("1", "2", "3"), songs.sortedFor(SongSort.DURATION_SHORT).map { it.id })
        assertEquals(listOf("3", "2", "1"), songs.sortedFor(SongSort.DURATION_LONG).map { it.id })
    }

    @Test
    fun `sorting never drops or duplicates songs`() {
        for (sort in SongSort.entries) {
            assertEquals(songs.size, songs.sortedFor(sort).size)
            assertEquals(songs.map { it.id }.toSet(), songs.sortedFor(sort).map { it.id }.toSet())
        }
    }

    @Test
    fun `fromName round-trips every entry`() {
        for (sort in SongSort.entries) {
            assertEquals(sort, SongSort.fromName(sort.name))
        }
    }

    @Test
    fun `fromName falls back to default for unknown or missing values`() {
        // Persisted preferences can hold a value from an older build, so this must not throw.
        assertEquals(SongSort.DEFAULT, SongSort.fromName(null))
        assertEquals(SongSort.DEFAULT, SongSort.fromName(""))
        assertEquals(SongSort.DEFAULT, SongSort.fromName("BY_VIBES"))
    }
}
