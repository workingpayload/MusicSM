package com.example.musicsm.domain.match

import java.text.Normalizer

/**
 * Decides whether a credit line actually *belongs* to an artist.
 *
 * This started life as a fix for the artist page, which is built from a music search for the
 * artist's name: a plain keyword search happily returns anything with that name in the **title** —
 * covers, reaction videos, "best of" compilations, and unrelated tracks that merely namecheck
 * them. The fix is to ignore the title entirely and match on the uploader instead.
 *
 * That is harder than string equality because YouTube credits the same artist in several shapes:
 * auto-generated `"Artist - Topic"` channels, legacy `"ArtistVEVO"` channels, and multi-artist
 * credits like `"Ed Sheeran, Justin Bieber"`.
 *
 * It lives in `domain` rather than next to the YouTube source because the recommendation engine
 * needs the same name-folding to tell whether a candidate track is by an artist the listener
 * already loves. Duplicating these rules would let the two copies drift apart.
 */
object ArtistMatching {

    /** Collaboration separators. Deliberately excludes a bare "x" — it appears inside names. */
    private val CREDIT_SEPARATORS = Regex(
        """\s*(?:,|;|·|&|/|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\bvs\.?\b)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Channel-type suffixes that are packaging, not part of the artist's name. */
    private val CHANNEL_NOISE = Regex(
        """\s*-\s*topic$|vevo$|\s*-\s*official.*$|\s*\(official.*\)$|official\s+artist\s+channel$""",
        RegexOption.IGNORE_CASE,
    )

    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Reduce a name to a comparison key: strip channel packaging, fold accents and drop everything
     * that isn't a letter or digit, so "Beyoncé", "BEYONCE" and "Beyoncé - Topic" all agree.
     */
    fun normalize(raw: String): String {
        val folded = Normalizer.normalize(displayName(raw), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        return NON_ALPHANUMERIC.replace(folded, "").lowercase()
    }

    /**
     * The name with channel packaging removed but its original spelling intact, for showing to
     * people: "Arijit Singh - Topic" becomes "Arijit Singh".
     */
    fun displayName(raw: String): String = CHANNEL_NOISE.replace(raw.trim(), "").trim()

    /** Split a credit line such as "Ed Sheeran, Justin Bieber" into its individual artists. */
    fun creditedNames(uploader: String): List<String> =
        uploader.split(CREDIT_SEPARATORS)
            .map(::normalize)
            .filter { it.isNotEmpty() }

    /**
     * Like [creditedNames] but keeping each act's original spelling, so a shelf can be titled
     * "More like Justin Bieber" rather than "More like justinbieber".
     */
    fun creditLabels(uploader: String?): List<String> =
        uploader.orEmpty()
            .split(CREDIT_SEPARATORS)
            .map(::displayName)
            .filter { normalize(it).isNotEmpty() }

    /**
     * Every comparison key [uploader] could reasonably be filed under: the whole credit line plus
     * each individual act within it. Lets the recommender score a track against a listener's
     * favourite artists with one set lookup instead of a [matches] pass per artist.
     */
    fun creditKeys(uploader: String?): Set<String> {
        val credit = uploader.orEmpty()
        if (credit.isBlank()) return emptySet()
        return buildSet {
            normalize(credit).takeIf { it.isNotEmpty() }?.let(::add)
            addAll(creditedNames(credit))
        }
    }

    /**
     * True when [uploader] is credited to [artist].
     *
     * The whole string is checked before splitting on separators, so names that legitimately
     * contain one — "Simon & Garfunkel", "Earth, Wind & Fire" — still match as a single act.
     * Comparison is by whole name, never substring, so "Drake" does not match "Drake Bell".
     */
    fun matches(uploader: String?, artist: String?): Boolean {
        val target = normalize(artist.orEmpty())
        if (target.isEmpty()) return false
        val credit = uploader.orEmpty()
        if (normalize(credit) == target) return true
        return creditedNames(credit).any { it == target }
    }
}
