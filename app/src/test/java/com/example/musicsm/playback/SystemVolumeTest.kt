package com.example.musicsm.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemVolumeTest {
    @Test
    fun fractionToIndexRoundsToNearestStep() {
        assertEquals(8, systemVolumeIndex(0.5f, 0, 15))
        assertEquals(7, systemVolumeIndex(0.49f, 0, 15))
    }

    @Test
    fun fractionToIndexClampsBelowZero() {
        assertEquals(0, systemVolumeIndex(-1f, 0, 15))
    }

    @Test
    fun fractionToIndexClampsAboveOne() {
        assertEquals(15, systemVolumeIndex(2f, 0, 15))
    }

    @Test
    fun fractionToIndexHandlesNonZeroMinimum() {
        assertEquals(7, systemVolumeIndex(0.5f, 2, 12))
    }

    @Test
    fun maxEqualToMinMapsToMinimumAndZeroFraction() {
        assertEquals(4, systemVolumeIndex(0.75f, 4, 4))
        assertEquals(0f, systemVolumeFraction(4, 4, 4, muted = false), 0f)
    }

    @Test
    fun mutedAlwaysMapsToZeroFraction() {
        assertEquals(0f, systemVolumeFraction(10, 0, 15, muted = true), 0f)
    }

    @Test
    fun everyIndexRoundTripsForZeroToFifteenRange() {
        for (index in 0..15) {
            val fraction = systemVolumeFraction(index, 0, 15, muted = false)
            assertEquals(index, systemVolumeIndex(fraction, 0, 15))
        }
    }
}
