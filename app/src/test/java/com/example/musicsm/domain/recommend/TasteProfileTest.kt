package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.ArtistPlayCount
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongPlayCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home feed used to seed itself from `likedSongs().shuffled()`, so it was rebuilt from a
 * different random sample every refresh. These tests pin down the two properties that fixes that:
 * the ordering comes from real play history, and it is deterministic.
 */
class TasteProfileTest {

    private fun song(id: String, artist: String = "Artist $id") =
        Song(id = id, title = "Title $id", artist = artist)

    private fun plays(vararg pairs: Pair<Song, Int>) =
        pairs.map { (song, count) -> SongPlayCount(song, count) }

    private fun artistPlays(vararg pairs: Pair<String, Int>) =
        pairs.map { (name, count) ->
            ArtistPlayCount(name = name, artworkUrl = null, playCount = count, songCount = 1, totalMs = 0L)
        }

    @Test
    fun `seeds are ordered by play count, not by library order`() {
        val rare = song("rare")
        val favourite = song("favourite")
        val profile = TasteProfiles.build(
            lifetime = ListeningStats(totalPlays = 30, topSongs = plays(rare to 1, favourite to 20)),
            liked = listOf(rare, favourite),
        )

        assertEquals("favourite", profile.topSeed?.id)
    }

    @Test
    fun `recent listening outweighs a larger lifetime count`() {
        val nowPlaying = song("now")
        val oldFavourite = song("old")
        val profile = TasteProfiles.build(
            recent = ListeningStats(totalPlays = 3, topSongs = plays(nowPlaying to 3)),
            lifetime = ListeningStats(
                totalPlays = 11,
                topSongs = plays(nowPlaying to 3, oldFavourite to 8),
            ),
        )

        assertEquals(listOf("now", "old"), profile.seeds.map { it.id })
    }

    @Test
    fun `seeds take at most one song per artist so one act cannot dominate`() {
        val a1 = song("a1", artist = "Artist A")
        val a2 = song("a2", artist = "Artist A")
        val b1 = song("b1", artist = "Artist B")
        val profile = TasteProfiles.build(
            lifetime = ListeningStats(
                totalPlays = 60,
                topSongs = plays(a1 to 30, a2 to 20, b1 to 10),
            ),
        )

        assertEquals(listOf("a1", "b1"), profile.seeds.map { it.id })
    }

    @Test
    fun `the same history always produces the same profile`() {
        val liked = listOf(song("x", artist = "One"), song("y", artist = "Two"))
        val stats = ListeningStats(totalPlays = 9, topSongs = plays(liked[0] to 4, liked[1] to 5))

        val first = TasteProfiles.build(lifetime = stats, liked = liked)
        val second = TasteProfiles.build(lifetime = stats, liked = liked)

        assertEquals(first, second)
    }

    @Test
    fun `history is only trusted once there are enough plays`() {
        val thin = TasteProfiles.build(lifetime = ListeningStats(totalPlays = 4))
        val enough = TasteProfiles.build(
            lifetime = ListeningStats(totalPlays = TasteProfiles.MIN_PLAYS_FOR_HISTORY),
        )

        assertFalse(thin.hasHistory)
        assertTrue(enough.hasHistory)
    }

    @Test
    fun `a brand new listener still gets seeds from their likes`() {
        val liked = listOf(song("first", artist = "One"), song("second", artist = "Two"))
        val profile = TasteProfiles.build(liked = liked)

        assertEquals(listOf("first", "second"), profile.seeds.map { it.id })
        assertFalse(profile.hasHistory)
    }

    @Test
    fun `following an artist is the strongest signal`() {
        val profile = TasteProfiles.build(
            lifetime = ListeningStats(totalPlays = 6, topArtists = artistPlays("Background Act" to 6)),
            followedArtists = listOf(Artist(id = "1", name = "Dua Lipa")),
        )

        assertEquals("Dua Lipa", profile.topArtist?.name)
        assertEquals(1.0, profile.affinity("Dua Lipa"), 0.0001)
    }

    @Test
    fun `a collaboration counts for every act on the credit line`() {
        val profile = TasteProfiles.build(
            liked = listOf(song("duet", artist = "Ed Sheeran, Justin Bieber")),
        )

        assertTrue(profile.affinity("Justin Bieber") > 0.0)
        assertTrue(profile.affinity("Ed Sheeran") > 0.0)
        assertEquals(0.0, profile.affinity("Unrelated Band"), 0.0001)
    }

    @Test
    fun `artist names drop channel packaging before being shown`() {
        val profile = TasteProfiles.build(
            lifetime = ListeningStats(totalPlays = 5, topArtists = artistPlays("Arijit Singh - Topic" to 5)),
        )

        assertEquals("Arijit Singh", profile.topArtist?.name)
    }

    @Test
    fun `listen again only offers songs that were actually played`() {
        val neverPlayed = song("liked-only")
        val played = song("played")
        val profile = TasteProfiles.build(
            lifetime = ListeningStats(totalPlays = 5, topSongs = plays(played to 5)),
            liked = listOf(neverPlayed),
        )

        assertEquals(listOf("played"), profile.heavyRotation.map { it.id })
    }

    @Test
    fun `everything already heard or liked is remembered so discovery can skip it`() {
        val profile = TasteProfiles.build(
            lifetime = ListeningStats(totalPlays = 5, topSongs = plays(song("played") to 5)),
            liked = listOf(song("liked")),
            recentlyPlayed = listOf(song("recent")),
        )

        assertEquals(setOf("played", "liked", "recent"), profile.knownSongIds)
    }

    @Test
    fun `an empty library produces an empty profile rather than failing`() {
        val profile = TasteProfiles.build()

        assertTrue(profile.isEmpty)
        assertFalse(profile.hasHistory)
        assertEquals(0.0, profile.affinity("Anyone"), 0.0001)
        assertEquals(0.0, profile.affinity(null), 0.0001)
    }
}
