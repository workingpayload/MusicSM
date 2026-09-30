package com.example.musicsm.domain.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily discover has to feel new tomorrow without moving under the listener today. */
class DailyRotationTest {

    private val pool = ('a'..'j').map { it.toString() }

    @Test
    fun `the same day always produces the same picks`() {
        val monday = 20_000L

        assertEquals(
            DailyRotation.pick(pool, count = 3, epochDay = monday),
            DailyRotation.pick(pool, count = 3, epochDay = monday),
        )
    }

    @Test
    fun `a different day produces different picks`() {
        val monday = DailyRotation.pick(pool, count = 3, epochDay = 20_000L)
        val tuesday = DailyRotation.pick(pool, count = 3, epochDay = 20_001L)

        assertNotEquals(monday, tuesday)
    }

    @Test
    fun `picks are drawn from the pool without repeating`() {
        val picks = DailyRotation.pick(pool, count = 4, epochDay = 12_345L)

        assertEquals(4, picks.size)
        assertEquals(picks.size, picks.distinct().size)
        assertTrue(pool.containsAll(picks))
    }

    @Test
    fun `a pool smaller than the shelf yields each item once rather than repeats`() {
        val small = listOf("one", "two")

        val picks = DailyRotation.pick(small, count = 5, epochDay = 7L)

        assertEquals(2, picks.size)
        assertEquals(setOf("one", "two"), picks.toSet())
    }

    @Test
    fun `rotation eventually covers the whole pool`() {
        val seen = (0L until 100L)
            .flatMap { day -> DailyRotation.pick(pool, count = 1, epochDay = day) }
            .toSet()

        assertEquals(pool.toSet(), seen)
    }

    @Test
    fun `empty pools and empty shelves are handled`() {
        assertTrue(DailyRotation.pick(emptyList<String>(), count = 3, epochDay = 1L).isEmpty())
        assertTrue(DailyRotation.pick(pool, count = 0, epochDay = 1L).isEmpty())
    }

    @Test
    fun `the offset always lands inside the pool, including before the epoch`() {
        listOf(-5_000L, -1L, 0L, 1L, 19_999L).forEach { day ->
            val offset = DailyRotation.offsetFor(day, pool.size)
            assertTrue("offset $offset out of range for day $day", offset in pool.indices)
        }
    }
}
