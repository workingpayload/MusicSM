package com.example.musicsm.domain.recommend

import java.time.LocalDate
import java.time.ZoneId

/**
 * Picks a slice of a candidate pool that changes once a day and never mid-day.
 *
 * A "daily mix" shelf has two requirements that pull against each other: it has to feel fresh
 * tomorrow, and it has to stay put today. Calling [List.shuffled] satisfies the first and breaks
 * the second — the shelf would reorder itself on every pull-to-refresh, every rotation, and every
 * return to the Home tab, so nothing the listener spotted a minute ago would still be there.
 *
 * Instead the pool is *rotated* by an offset derived from the calendar day. Within one day the
 * offset is constant, so the shelf is completely stable; at midnight it moves on. Because the
 * stride is coprime with most pool sizes, consecutive days land on genuinely different windows
 * rather than nudging along by one, and over time the whole pool gets a turn.
 */
object DailyRotation {

    /**
     * Steps between one day's window and the next. A largish prime so that for any pool size that
     * isn't a multiple of it, repeatedly adding it walks the entire pool before repeating.
     */
    private const val STRIDE = 31L

    /** The current local calendar day, as the offset seed for [pick]. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    /**
     * Take [count] items from [items], starting at the day's offset and wrapping around.
     *
     * Wrapping means a pool smaller than [count] still yields everything it has (each item once)
     * rather than a short list padded with repeats.
     */
    fun <T> pick(items: List<T>, count: Int, epochDay: Long = today()): List<T> {
        if (items.isEmpty() || count <= 0) return emptyList()
        val size = items.size
        val take = minOf(count, size)
        val start = offsetFor(epochDay, size)
        return List(take) { items[(start + it) % size] }
    }

    /** The rotation offset for [epochDay] into a pool of [size], always in `0 until size`. */
    internal fun offsetFor(epochDay: Long, size: Int): Int {
        if (size <= 0) return 0
        return Math.floorMod(epochDay * STRIDE, size.toLong()).toInt()
    }
}
