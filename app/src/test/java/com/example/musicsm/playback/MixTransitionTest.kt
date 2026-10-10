package com.example.musicsm.playback

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MixTransitionTest {

    private val next = Song(id = "next", title = "Next", artist = "Artist", artworkUrl = "https://example.com/a.jpg")

    @Test
    fun `progress follows the blend window`() {
        val blend = MixBlend("current", next, startMs = 10_000.0, endMs = 18_000.0)

        assertEquals(0f, blend.progressAt(0L), 0f)
        assertEquals(0f, blend.progressAt(10_000L), 0f)
        assertEquals(0.5f, blend.progressAt(14_000L), 1e-4f)
        assertEquals(1f, blend.progressAt(18_000L), 0f)
        assertEquals(1f, blend.progressAt(25_000L), 0f)
    }

    @Test
    fun `empty window jumps at its end`() {
        val blend = MixBlend("current", next, startMs = 5_000.0, endMs = 5_000.0)

        assertEquals(0f, blend.progressAt(4_999L), 0f)
        assertEquals(1f, blend.progressAt(5_000L), 0f)
    }

    @Test
    fun `clearing an old blend leaves a newer one`() {
        val bus = MixTransitionBus()
        val old = MixBlend("a", next, 0.0, 1_000.0)
        val newer = MixBlend("b", next, 0.0, 1_000.0)

        bus.publish(old)
        bus.publish(newer)
        bus.clear(old)
        assertSame(newer, bus.blend.value)

        bus.clear(newer)
        assertNull(bus.blend.value)
    }
}
