package com.example.musicsm.data.source.youtube

/**
 * Rewrites YouTube artwork URLs to ask for a particular size.
 *
 * YouTube serves artwork at whatever size the surface that first requested it asked for, which is
 * routinely far smaller than a full-bleed header — and, just as often, far larger than a 48 dp list
 * row needs. Both directions cost: an undersized bitmap stretched across a header looks soft, and an
 * oversized one decoded for a row burns memory and decode time for pixels that are thrown away.
 * The URLs carry their own size, so the request is rewritten rather than the bitmap rescaled.
 *
 * Each host encodes size differently, so there is a branch per host rather than one regex.
 */
internal object YouTubeArtwork {

    /**
     * The size artwork URLs are stored at.
     *
     * A stored URL is not a decode instruction — every surface rewrites it to what it actually
     * needs — so this only has to be a sane thing to fall back to if some caller forgets. It is
     * chosen small enough that the video-still branch lands on the rung that always exists rather
     * than the one that is often missing, so a stored URL is always a URL that loads.
     */
    const val CANONICAL = 544

    /**
     * Video stills are not resizable: YouTube publishes a fixed ladder of named files. So this is a
     * choice between rungs, not a resize.
     *
     * The threshold is deliberately high. `maxresdefault.jpg` is generated from the source upload
     * and is simply absent for a large share of videos, answering 404, whereas `hqdefault.jpg` is
     * always present. Anything small enough to be satisfied by 480x360 therefore takes the rung that
     * never fails, and only a genuinely large surface gambles on the big one — with
     * [nextFallback] to catch it when the gamble does not pay off.
     */
    private const val MAXRES_THRESHOLD = 1200

    private val YTIMG_NAME = Regex("/(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg")

    /** Everything after `=` on a Google-hosted image URL is a size spec we are free to replace. */
    private val GOOGLE_SIZE_SUFFIX = Regex("=.*$")

    /** Channel avatars carry their size as either `=s512` or the older `-s512-c-k...` form. */
    private val GGPHT_SIZE_SUFFIX = Regex("(=|-s\\d+).*$")

    /**
     * The rungs of the video-still ladder, largest first.
     *
     * Ordered, and stepped one at a time by [nextFallback], because a missing `maxresdefault` says
     * nothing about `sddefault`. Jumping straight to the bottom rung on the first failure is the
     * obvious implementation and it is wrong: the common case is only the top rung missing, so it
     * lands on a needlessly small image for every video that has a perfectly good one in between.
     */
    private val LADDER = listOf(
        "maxresdefault.jpg",
        "sddefault.jpg",
        "hqdefault.jpg",
        "mqdefault.jpg",
    )

    /**
     * Returns [url] rewritten to request roughly [width] x [height] pixels.
     *
     * Returns the input untouched for hosts that do not encode a size, so this is safe to call on
     * any artwork URL, including local files and other providers' CDNs.
     */
    fun resize(url: String, width: Int, height: Int = width): String = when {
        "i.ytimg.com/vi/" in url -> {
            val rung = if (width >= MAXRES_THRESHOLD) "maxresdefault.jpg" else "hqdefault.jpg"
            YTIMG_NAME.replace(url, "/$rung")
        }
        // Channel and artist avatars. These take a single dimension and are always square, so the
        // wider of the two requested sides wins rather than silently cropping to the narrower one.
        "ggpht.com" in url -> GGPHT_SIZE_SUFFIX.replace(url, "") + "=s${maxOf(width, height)}"
        "googleusercontent.com" in url ->
            GOOGLE_SIZE_SUFFIX.replace(url, "") + "=w$width-h$height-p-l90-rj"
        else -> url
    }

    /** Null-tolerant [resize], for the many artwork fields that are optional. */
    fun resizeOrNull(url: String?, width: Int, height: Int = width): String? =
        url?.let { resize(it, width, height) }

    /**
     * The largest form of [url] that is certain to load, up to [width].
     *
     * For callers that have no way to retry. The media session is the case that matters: Android
     * hands its artwork URI to the notification, the lock screen and cast targets, all of which
     * fetch it themselves, so a 404 there is simply a track with no artwork rather than a load that
     * can fall down the ladder. Every host but the video stills honours any size asked of it, so
     * only they are held back.
     */
    fun guaranteedOrNull(url: String?, width: Int, height: Int = width): String? = url?.let {
        val safeWidth = if ("i.ytimg.com/vi/" in it) minOf(width, CANONICAL) else width
        resize(it, safeWidth, if (safeWidth == width) height else minOf(height, CANONICAL))
    }

    /**
     * The next URL to try after [url] failed to load, or null when nothing is left.
     *
     * Only video stills have a fallback: their ladder is a set of separately generated files, any of
     * which may be missing. A resized `googleusercontent` URL that fails has failed for some other
     * reason — the size spec is honoured server-side for any value — so retrying at a different
     * size would just be a second request for the same missing image.
     */
    fun nextFallback(url: String): String? {
        if ("i.ytimg.com/vi/" !in url) return null
        val current = LADDER.indexOfFirst { url.endsWith("/$it") }
        if (current < 0 || current == LADDER.lastIndex) return null
        return url.removeSuffix(LADDER[current]) + LADDER[current + 1]
    }
}
