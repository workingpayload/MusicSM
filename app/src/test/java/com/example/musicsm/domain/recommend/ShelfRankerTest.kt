package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shelf assembly: promote what the listener loves, but never scramble a chart. */
class ShelfRankerTest {

    private fun song(id: String, artist: String = "Artist $id") =
        Song(id = id, title = "Title $id", artist = artist)

    private val fanOfDuaLipa = TasteProfiles.build(
        followedArtists = listOf(Artist(id = "1", name = "Dua Lipa")),
    )

    @Test
    fun `tracks by a favourite artist are lifted to the top`() {
        val candidates = listOf(
            song("a", artist = "Unknown One"),
            song("b", artist = "Unknown Two"),
            song("c", artist = "Dua Lipa"),
        )

        val ranked = ShelfRanker.rank(candidates, fanOfDuaLipa, limit = 3)

        assertEquals("c", ranked.first().id)
    }

    @Test
    fun `equally relevant tracks keep the order the provider gave them`() {
        val candidates = listOf(song("first"), song("second"), song("third"))

        val ranked = ShelfRanker.rank(candidates, TasteProfile(), limit = 3)

        assertEquals(listOf("first", "second", "third"), ranked.map { it.id })
    }

    @Test
    fun `discovery shelves skip anything already heard`() {
        val heard = song("heard")
        val profile = TasteProfiles.build(recentlyPlayed = listOf(heard))

        val ranked = ShelfRanker.rank(
            listOf(heard, song("fresh")),
            profile,
            limit = 5,
            excludeKnown = true,
        )

        assertEquals(listOf("fresh"), ranked.map { it.id })
    }

    @Test
    fun `songs shown on an earlier shelf are not repeated`() {
        val ranked = ShelfRanker.rank(
            listOf(song("a"), song("b")),
            TasteProfile(),
            limit = 5,
            exclude = setOf("a"),
        )

        assertEquals(listOf("b"), ranked.map { it.id })
    }

    @Test
    fun `one artist cannot fill a whole shelf`() {
        val candidates = (1..5).map { song("s$it", artist = "Same Act") } + song("other", artist = "Other")

        val ranked = ShelfRanker.rank(candidates, TasteProfile(), limit = 10, maxPerArtist = 2)

        assertEquals(listOf("s1", "s2", "other"), ranked.map { it.id })
    }

    @Test
    fun `duplicates and malformed entries are dropped`() {
        val candidates = listOf(
            song("a"),
            song("a"),
            Song(id = "", title = "No id", artist = "X"),
            Song(id = "blank", title = "", artist = "X"),
        )

        val ranked = ShelfRanker.rank(candidates, TasteProfile(), limit = 10)

        assertEquals(listOf("a"), ranked.map { it.id })
    }

    @Test
    fun `a chart keeps its own order even for a devoted fan`() {
        val chart = listOf(
            song("number-one", artist = "Unknown One"),
            song("number-two", artist = "Dua Lipa"),
        )

        val deduped = ShelfRanker.dedupe(chart, limit = 10)

        assertEquals(listOf("number-one", "number-two"), deduped.map { it.id })
        // Ranking the same list would promote the favourite, which is why charts use dedupe.
        assertEquals("number-two", ShelfRanker.rank(chart, fanOfDuaLipa, limit = 10).first().id)
    }

    @Test
    fun `shelves respect their size limit`() {
        val ranked = ShelfRanker.rank(
            (1..20).map { song("s$it") },
            TasteProfile(),
            limit = 4,
        )

        assertEquals(4, ranked.size)
        assertTrue(ShelfRanker.rank(listOf(song("a")), TasteProfile(), limit = 0).isEmpty())
    }
}
