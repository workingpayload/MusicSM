package com.example.musicsm.domain.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The join code is read aloud across a room and typed by hand, so the forgiving-input behaviour
 * matters more than the generation does.
 */
class JamJoinCodeTest {

    @Test
    fun `generated codes are the advertised length and alphabet`() {
        repeat(200) {
            val code = JamJoinCode.random()
            assertEquals(JamJoinCode.LENGTH, code.length)
            assertTrue(
                "unexpected character in $code",
                code.all { it in '0'..'9' || it in 'A'..'Z' },
            )
            // The whole point of Crockford: none of the characters people confuse.
            assertTrue("$code contains a look-alike", code.none { it in "ILOU" })
        }
    }

    @Test
    fun `generation is not obviously degenerate`() {
        val codes = List(200) { JamJoinCode.random() }
        assertTrue("codes repeat far too often", codes.toSet().size > 190)
    }

    @Test
    fun `round trips a clean code`() {
        assertEquals("K7M2P9", JamJoinCode.normalize("K7M2P9"))
    }

    @Test
    fun `accepts lower case`() {
        assertEquals("K7M2P9", JamJoinCode.normalize("k7m2p9"))
    }

    @Test
    fun `ignores the separators people type`() {
        assertEquals("K7M2P9", JamJoinCode.normalize("K7M-2P9"))
        assertEquals("K7M2P9", JamJoinCode.normalize(" K7M 2P9 "))
        assertEquals("K7M2P9", JamJoinCode.normalize("K7M_2P9"))
    }

    @Test
    fun `folds look-alike characters rather than rejecting them`() {
        // Someone reading "0" aloud as "oh" must not be told their code is wrong.
        assertEquals("K7M209", JamJoinCode.normalize("K7M2O9"))
        assertEquals("K1M2P9", JamJoinCode.normalize("KIM2P9"))
        assertEquals("K1M2P9", JamJoinCode.normalize("KLM2P9"))
    }

    @Test
    fun `rejects U because there is no safe fold for it`() {
        assertNull(JamJoinCode.normalize("K7M2U9"))
    }

    @Test
    fun `rejects wrong lengths`() {
        assertNull(JamJoinCode.normalize("K7M2P"))
        assertNull(JamJoinCode.normalize("K7M2P99"))
        assertNull(JamJoinCode.normalize(""))
    }

    @Test
    fun `rejects punctuation that is not a separator`() {
        assertNull(JamJoinCode.normalize("K7M!P9"))
        assertNull(JamJoinCode.normalize("K7M/P9"))
    }

    @Test
    fun `a generated code always survives its own normalisation`() {
        repeat(200) {
            val code = JamJoinCode.random()
            assertEquals(code, JamJoinCode.normalize(code))
            // And it survives being shown to the user and typed back in.
            assertEquals(code, JamJoinCode.normalize(JamJoinCode.format(code)))
        }
    }

    @Test
    fun `formats as two readable halves`() {
        assertEquals("K7M-2P9", JamJoinCode.format("K7M2P9"))
    }

    @Test
    fun `formatting leaves an unexpected length alone`() {
        assertEquals("ABC", JamJoinCode.format("ABC"))
    }

    @Test
    fun `normalisation is idempotent`() {
        val once = JamJoinCode.normalize("k7m-2o9")
        assertNotNull(once)
        assertEquals(once, JamJoinCode.normalize(once!!))
    }
}
