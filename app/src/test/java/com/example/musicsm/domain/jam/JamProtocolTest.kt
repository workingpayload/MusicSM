package com.example.musicsm.domain.jam

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every one of these messages arrives from another device on the same Wi-Fi, so the decoder is as
 * much a trust boundary as a parser.
 */
class JamProtocolTest {

    private fun song(id: String, title: String = "Title", artist: String = "Artist") =
        Song(id = id, title = title, artist = artist, durationMs = 1000L, artworkUrl = "https://x/$id.jpg")

    private fun roundTrip(message: JamMessage): JamMessage? =
        JamProtocol.decode(JamProtocol.encode(message))

    @Test
    fun `hello round trips`() {
        val decoded = roundTrip(JamMessage.Hello(JAM_PROTOCOL_VERSION, "tok3n", "Ravi's phone"))
        assertEquals(JamMessage.Hello(JAM_PROTOCOL_VERSION, "tok3n", "Ravi's phone"), decoded)
    }

    @Test
    fun `welcome round trips`() {
        val decoded = roundTrip(JamMessage.Welcome(JAM_PROTOCOL_VERSION, "Kitchen", "m-1"))
        assertEquals(JamMessage.Welcome(JAM_PROTOCOL_VERSION, "Kitchen", "m-1"), decoded)
    }

    @Test
    fun `snapshot round trips with queue and members`() {
        val snapshot = JamSnapshot(
            queue = listOf(song("a"), song("b"), song("c")),
            currentIndex = 1,
            isPlaying = true,
            positionMs = 42_000L,
            hostClockMs = 1_700_000_000_000L,
            members = listOf(
                JamMember("m-0", "Host phone", JamRole.HOST),
                JamMember("m-1", "Guest phone", JamRole.GUEST),
            ),
        )
        val decoded = roundTrip(JamMessage.Snapshot(snapshot)) as JamMessage.Snapshot
        assertEquals(snapshot, decoded.snapshot)
        assertEquals("b", decoded.snapshot.currentSong?.id)
    }

    @Test
    fun `empty queue round trips`() {
        val decoded = roundTrip(JamMessage.Snapshot(JamSnapshot())) as JamMessage.Snapshot
        assertTrue(decoded.snapshot.queue.isEmpty())
        assertTrue(decoded.snapshot.members.isEmpty())
    }

    @Test
    fun `every command round trips`() {
        val commands = listOf(
            JamCommand.PlayPause,
            JamCommand.Next,
            JamCommand.Previous,
            JamCommand.RemoveAt(4),
            JamCommand.AddSong(song("z")),
        )
        commands.forEach { command ->
            val decoded = roundTrip(JamMessage.Command(command)) as? JamMessage.Command
            assertEquals(command, decoded?.command)
        }
    }

    @Test
    fun `bye round trips`() {
        assertEquals(JamMessage.Bye("host left"), roundTrip(JamMessage.Bye("host left")))
    }

    /** A crafted title must not be able to invent extra fields, songs or members. */
    @Test
    fun `separators in text cannot forge fields`() {
        val nasty = song(
            id = "x",
            title = "Hack\u001Fme\u001Eand\u001Dyou\nlose",
            artist = "Evil\u001FArtist",
        )
        val decoded = roundTrip(JamMessage.Snapshot(JamSnapshot(queue = listOf(nasty), currentIndex = 0)))
                as JamMessage.Snapshot
        assertEquals(1, decoded.snapshot.queue.size)
        val title = decoded.snapshot.queue[0].title
        assertFalse(title.contains('\u001F'))
        assertFalse(title.contains('\u001E'))
        assertFalse(title.contains('\u001D'))
        assertFalse(title.contains('\n'))
    }

    @Test
    fun `unicode survives`() {
        val decoded = roundTrip(
            JamMessage.Snapshot(JamSnapshot(queue = listOf(song("u", title = "夜に駆ける", artist = "YOASOBI")))),
        ) as JamMessage.Snapshot
        assertEquals("夜に駆ける", decoded.snapshot.queue[0].title)
        assertEquals("YOASOBI", decoded.snapshot.queue[0].artist)
    }

    @Test
    fun `a message never contains a newline so line framing holds`() {
        val encoded = JamProtocol.encode(
            JamMessage.Snapshot(
                JamSnapshot(queue = listOf(song("a", title = "line\nbreak\r\nhere"))),
            ),
        )
        assertFalse(encoded.contains('\n'))
        assertFalse(encoded.contains('\r'))
    }

    @Test
    fun `junk is rejected rather than thrown`() {
        listOf("", "nonsense", "HELLO", "STATE\u001Fx", "CMD", "CMD\u001FFLY", "\u001F\u001F\u001F")
            .forEach { assertNull("expected null for '$it'", JamProtocol.decode(it)) }
    }

    @Test
    fun `an oversized line is refused`() {
        assertNull(JamProtocol.decode("A".repeat(JamProtocol.MAX_LINE_LENGTH + 1)))
    }

    @Test
    fun `a negative remove index is refused`() {
        assertNull(JamProtocol.decode("CMD\u001FREMOVE\u001F-3"))
        assertNotNull(JamProtocol.decode("CMD\u001FREMOVE\u001F0"))
    }

    @Test
    fun `a song with no id is dropped from the queue`() {
        // Field order is id, title, artist, duration, artwork.
        val forged = "STATE\u001F1\u001F0\u001F0\u001F0\u001F\u001D\u001DGhost\u001D0\u001D"
        val decoded = JamProtocol.decode(forged) as? JamMessage.Snapshot
        assertTrue(decoded?.snapshot?.queue.isNullOrEmpty())
    }

    @Test
    fun `a version mismatch still decodes so the peer can be told why it was refused`() {
        val decoded = JamProtocol.decode("HELLO\u001F999\u001Ftok\u001FFuture phone")
        assertEquals(999, (decoded as? JamMessage.Hello)?.version)
    }
}
