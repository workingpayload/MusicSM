package com.example.musicsm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    private fun stats(
        byDay: List<DailyPlayCount> = emptyList(),
        byHour: List<HourlyPlayCount> = emptyList(),
        totalPlays: Int = 1,
    ) = ListeningStats(totalPlays = totalPlays, byDay = byDay, byHour = byHour)

    @Test
    fun `empty stats report no streak and no peak hour`() {
        val s = stats(totalPlays = 0)
        assertTrue(s.isEmpty)
        assertEquals(0, s.currentStreakDays)
        assertNull(s.peakHour)
    }

    @Test
    fun `streak counts consecutive days ending at the most recent one`() {
        val s = stats(
            byDay = listOf(
                DailyPlayCount(100, 2),
                DailyPlayCount(101, 1),
                DailyPlayCount(102, 5),
            ),
        )
        assertEquals(3, s.currentStreakDays)
    }

    @Test
    fun `streak stops at the first gap`() {
        // Day 98 is separated from 100-102 by an empty day 99.
        val s = stats(
            byDay = listOf(
                DailyPlayCount(98, 4),
                DailyPlayCount(100, 2),
                DailyPlayCount(101, 1),
                DailyPlayCount(102, 5),
            ),
        )
        assertEquals(3, s.currentStreakDays)
    }

    @Test
    fun `days with zero plays do not extend a streak`() {
        // A padded chart can contain zero-count days; they must not be counted.
        val s = stats(
            byDay = listOf(
                DailyPlayCount(100, 1),
                DailyPlayCount(101, 0),
                DailyPlayCount(102, 3),
            ),
        )
        assertEquals(1, s.currentStreakDays)
    }

    @Test
    fun `single day of listening is a one day streak`() {
        assertEquals(1, stats(byDay = listOf(DailyPlayCount(100, 7))).currentStreakDays)
    }

    @Test
    fun `peak hour is the hour with the most plays`() {
        val s = stats(
            byHour = listOf(
                HourlyPlayCount(8, 3),
                HourlyPlayCount(22, 9),
                HourlyPlayCount(23, 4),
            ),
        )
        assertEquals(22, s.peakHour)
    }
}
