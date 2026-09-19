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
