package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song

/** An artist the listener keeps coming back to, with a normalized 0..1 [score]. */
data class ArtistAffinity(
    val name: String,
    val key: String,
    val score: Double,
)

/**
 * What we know about a listener's taste, derived from what they actually played.
 *
 * Home used to seed itself with `likedSongs().shuffled()`, which had two problems: a like is a
 * rare, deliberate act so most people have very few, and shuffling meant the feed was rebuilt from
 * a different random sample on every refresh. The shelves genuinely were arbitrary.
 *
 * This profile is [deterministic]: the same history always yields the same profile, so the feed
 * only changes when listening habits change. Plays are weighted by recency, because what someone
 * had on repeat last week predicts the next track far better than what they liked two years ago.
 */
data class TasteProfile(
    val seeds: List<Song> = emptyList(),
    val artists: List<ArtistAffinity> = emptyList(),
    val heavyRotation: List<Song> = emptyList(),
    val knownSongIds: Set<String> = emptySet(),
    val hasHistory: Boolean = false,
) {
    val topSeed: Song? get() = seeds.firstOrNull()
    val topArtist: ArtistAffinity? get() = artists.firstOrNull()

    private val scoreByKey: Map<String, Double> = artists.associate { it.key to it.score }

    /**
     * How strongly the listener is drawn to whoever is credited on [credit], as 0..1.
     *
     * A collaboration counts for its strongest member, so a track credited to
     * "Unknown Newcomer, Taylor Swift" still scores as Taylor Swift for a Taylor Swift fan.
     */
    fun affinity(credit: String?): Double =
        ArtistMatching.creditKeys(credit).maxOfOrNull { scoreByKey[it] ?: 0.0 } ?: 0.0

    val isEmpty: Boolean get() = seeds.isEmpty() && artists.isEmpty()
}

/**
 * Builds a [TasteProfile] from listening history, likes and follows.
 *
 * Every signal is folded into one score per song and per artist so the shelves can be ordered by
 * something meaningful. The weights are deliberately crude — the aim is a sensible ranking, not a
 * calibrated model, and anything more elaborate would be untestable guesswork.
 */
object TasteProfiles {

    /** A play in the last few weeks counts for this much. */
    const val RECENT_PLAY_WEIGHT = 2.0

    /** A play at any point in the listener's history counts for this much. */
    const val LIFETIME_PLAY_WEIGHT = 1.0

    /** Liking a song is deliberate, so it is worth a few plays. */
    const val LIKED_SONG_WEIGHT = 3.0

    /** Following an artist is the strongest explicit signal there is. */
    const val FOLLOWED_ARTIST_WEIGHT = 8.0

    /** A single recent play, used only to break ties among otherwise unranked songs. */
    const val RECENTLY_PLAYED_WEIGHT = 0.5

    /** Below this many lifetime plays there isn't enough history to personalize from. */
    const val MIN_PLAYS_FOR_HISTORY = 5

    /** Seeds are capped to one per artist so recommendations don't collapse onto one act. */
    private const val MAX_SEEDS_PER_ARTIST = 1

    /** "Listen again" tolerates a few repeats per artist, but not a wall of one of them. */
    private const val MAX_ROTATION_PER_ARTIST = 3

    fun build(
        recent: ListeningStats = ListeningStats(),
        lifetime: ListeningStats = ListeningStats(),
        liked: List<Song> = emptyList(),
        followedArtists: List<Artist> = emptyList(),
        recentlyPlayed: List<Song> = emptyList(),
        seedCount: Int = 4,
        rotationSize: Int = 12,
    ): TasteProfile {
        val likedIds = liked.map { it.id }.toSet()
        val playedIds = buildSet {
            recent.topSongs.forEach { add(it.song.id) }
            lifetime.topSongs.forEach { add(it.song.id) }
            recentlyPlayed.forEach { add(it.id) }
        }
        val songScores = LinkedHashMap<String, Double>()
        val songs = LinkedHashMap<String, Song>()

        // Insertion order doubles as the tie-break, so the most trustworthy signal goes in first.
        fun contribute(song: Song, weight: Double) {
            if (song.id.isBlank()) return
            songs.putIfAbsent(song.id, song)
            songScores[song.id] = (songScores[song.id] ?: 0.0) + weight
        }

        recent.topSongs.forEach { contribute(it.song, it.playCount * RECENT_PLAY_WEIGHT) }
        lifetime.topSongs.forEach { contribute(it.song, it.playCount * LIFETIME_PLAY_WEIGHT) }
        recentlyPlayed.forEach { contribute(it, RECENTLY_PLAYED_WEIGHT) }
        liked.forEach { contribute(it, LIKED_SONG_WEIGHT) }

        val artistScores = LinkedHashMap<String, Double>()
        val artistNames = LinkedHashMap<String, String>()

        fun creditArtist(rawName: String, weight: Double) {
            val wholeKey = ArtistMatching.normalize(rawName)
            if (wholeKey.isNotEmpty()) {
                artistScores[wholeKey] = (artistScores[wholeKey] ?: 0.0) + weight
                artistNames.putIfAbsent(wholeKey, ArtistMatching.displayName(rawName))
            }
            // Credit every act on the line too, so a duo's work counts for both halves.
            ArtistMatching.creditLabels(rawName).forEach { label ->
                val key = ArtistMatching.normalize(label)
                if (key.isEmpty() || key == wholeKey) return@forEach
                artistScores[key] = (artistScores[key] ?: 0.0) + weight
                artistNames.putIfAbsent(key, label)
            }
        }

        recent.topArtists.forEach { creditArtist(it.name, it.playCount * RECENT_PLAY_WEIGHT) }
        lifetime.topArtists.forEach { creditArtist(it.name, it.playCount * LIFETIME_PLAY_WEIGHT) }
        liked.forEach { creditArtist(it.artist, LIKED_SONG_WEIGHT) }
        // Follows are applied last so their name wins as the display label where it differs.
        followedArtists.forEach { artist ->
            val key = ArtistMatching.normalize(artist.name)
            if (key.isEmpty()) return@forEach
            artistScores[key] = (artistScores[key] ?: 0.0) + FOLLOWED_ARTIST_WEIGHT
            artistNames[key] = artist.name.trim()
        }

        val maxArtistScore = artistScores.values.maxOrNull() ?: 0.0
        val affinities = artistScores.entries
            .map { (key, score) ->
                ArtistAffinity(
                    name = artistNames[key] ?: key,
                    key = key,
                    score = if (maxArtistScore > 0.0) score / maxArtistScore else 0.0,
                )
            }
            .sortedByDescending { it.score }

        val ranked = songScores.entries
            .sortedByDescending { it.value }
            .mapNotNull { songs[it.key] }

        val seeds = capPerArtist(ranked, MAX_SEEDS_PER_ARTIST).take(seedCount)
        // "Listen again" must only offer things actually heard before, never untouched likes.
        val rotation = capPerArtist(ranked.filter { it.id in playedIds }, MAX_ROTATION_PER_ARTIST)
            .take(rotationSize)

        val known = buildSet {
            addAll(songs.keys)
            addAll(likedIds)
        }

        return TasteProfile(
            seeds = seeds,
            artists = affinities,
            heavyRotation = rotation,
            knownSongIds = known,
            hasHistory = lifetime.totalPlays >= MIN_PLAYS_FOR_HISTORY,
        )
    }

    /** Keep list order but allow each artist through at most [maxPerArtist] times. */
    internal fun capPerArtist(songs: List<Song>, maxPerArtist: Int): List<Song> {
        if (maxPerArtist <= 0) return emptyList()
        val used = mutableMapOf<String, Int>()
        return songs.filter { song ->
            val key = ArtistMatching.normalize(song.artist).ifEmpty { song.id }
            val count = used.getOrDefault(key, 0)
            if (count >= maxPerArtist) {
                false
            } else {
                used[key] = count + 1
                true
            }
        }
    }
}
