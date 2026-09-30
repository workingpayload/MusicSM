package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Song

/**
 * Turns a raw bag of candidate tracks into an ordered shelf.
 *
 * The provider already returns candidates in its own relevance order, which is real information,
 * so this never sorts from scratch. It applies a *stable* sort on artist affinity, which lifts acts
 * the listener actually plays while leaving the provider's ordering intact within each tier. The
 * old code did the opposite — it called `shuffled()` — which threw that information away and made
 * the feed different on every refresh for no reason.
 */
object ShelfRanker {

    /** Discovery shelves cap repeats harder than "listen again" does. */
    const val DEFAULT_MAX_PER_ARTIST = 2

    /**
     * Rank [candidates] for a shelf.
     *
     * @param exclude song ids already shown elsewhere on the page, so shelves don't repeat.
     * @param excludeKnown drop anything the listener has already played or liked. Discovery
     *   shelves want this; "listen again" style shelves do not.
     */
    fun rank(
        candidates: List<Song>,
        profile: TasteProfile,
        limit: Int,
        exclude: Set<String> = emptySet(),
        excludeKnown: Boolean = false,
        maxPerArtist: Int = DEFAULT_MAX_PER_ARTIST,
    ): List<Song> {
        val filtered = clean(candidates, limit, exclude, excludeKnown, profile) ?: return emptyList()
        // sortedByDescending is stable, so equal-affinity tracks keep the provider's own ordering.
        val ordered = filtered.sortedByDescending { profile.affinity(it.artist) }
        return TasteProfiles.capPerArtist(ordered, maxPerArtist).take(limit)
    }

    /**
     * De-duplicate and cap a shelf without reordering it.
     *
     * Used for charts such as "Trending now", where the position *is* the meaning — promoting the
     * listener's favourite act to the top would misreport what is actually popular.
     */
    fun dedupe(
        candidates: List<Song>,
        limit: Int,
        exclude: Set<String> = emptySet(),
        maxPerArtist: Int = DEFAULT_MAX_PER_ARTIST,
    ): List<Song> {
        val filtered = clean(candidates, limit, exclude, excludeKnown = false, profile = null)
            ?: return emptyList()
        return TasteProfiles.capPerArtist(filtered, maxPerArtist).take(limit)
    }

    /**
     * Merge per-seed candidate lists by taking one from each in turn.
     *
     * The alternative — concatenating them and shuffling — throws away two useful things. Each
     * provider list is already in relevance order, and the seeds themselves are ranked, so a flat
     * shuffle buries the best match behind noise and makes the result different on every call even
     * though nothing about the listener changed. Round-robin keeps both orderings *and* guarantees
     * that one prolific seed cannot fill the shelf before the others get a turn.
     *
     * Duplicates are resolved in favour of the earliest position, so a track that several seeds
     * agree on is promoted rather than repeated.
     */
    fun interleave(lists: List<List<Song>>, limit: Int): List<Song> {
        if (limit <= 0) return emptyList()
        val nonEmpty = lists.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, Song>()
        val longest = nonEmpty.maxOf { it.size }
        for (round in 0 until longest) {
            for (list in nonEmpty) {
                val song = list.getOrNull(round) ?: continue
                if (song.id.isBlank()) continue
                merged.putIfAbsent(song.id, song)
            }
            if (merged.size >= limit) break
        }
        return merged.values.take(limit)
    }

    private fun clean(
        candidates: List<Song>,
        limit: Int,
        exclude: Set<String>,
        excludeKnown: Boolean,
        profile: TasteProfile?,
    ): List<Song>? {
        if (limit <= 0) return null
        return candidates
            .asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .filter { it.id !in exclude }
            .filter { !excludeKnown || profile == null || it.id !in profile.knownSongIds }
            .toList()
    }
}
