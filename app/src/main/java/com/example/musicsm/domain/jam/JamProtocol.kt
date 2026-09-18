package com.example.musicsm.domain.jam

import com.example.musicsm.domain.model.Song

/**
 * The Jam wire format: one message per line, so a plain socket `BufferedReader` can frame it.
 *
 * Hand-rolled rather than JSON because `:app` does not apply the kotlinx-serialization plugin, and
 * adding it for five message types is not worth a build-config change. The same separator trick as
 * [com.example.musicsm.domain.share.PlaylistShareCodec] is used, one nesting level deeper:
 *
 * - `\u001F` (unit separator) between the fields of a message
 * - `\u001E` (record separator) between songs in a queue
 * - `\u001D` (group separator) between the fields of one song
 *
 * All four of those characters — plus newline — are stripped from any text written to the wire, so
 * a song title can never forge a field boundary. Every message comes from another device, so
 * [decode] treats its input as hostile: it returns null instead of throwing, and never trusts a
 * length or an index it was given.
 */
object JamProtocol {

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'
    private const val GROUP = '\u001D'

    /** A single oversized line must not be able to exhaust the heap on the receiving device. */
    const val MAX_LINE_LENGTH = 512 * 1024

    /** Matches the codec cap for shared playlists; a jam queue is not a bulk transfer. */
    const val MAX_QUEUE_SIZE = 1000

    private const val TYPE_HELLO = "HELLO"
    private const val TYPE_WELCOME = "WELCOME"
    private const val TYPE_SNAPSHOT = "STATE"
    private const val TYPE_COMMAND = "CMD"
    private const val TYPE_BYE = "BYE"

    private const val CMD_PLAY_PAUSE = "PLAYPAUSE"
    private const val CMD_NEXT = "NEXT"
    private const val CMD_PREV = "PREV"
    private const val CMD_ADD = "ADD"
    private const val CMD_REMOVE = "REMOVE"

    fun encode(message: JamMessage): String = when (message) {
        is JamMessage.Hello -> join(
            TYPE_HELLO,
            message.version.toString(),
            clean(message.token),
            clean(message.displayName),
        )

        is JamMessage.Welcome -> join(
            TYPE_WELCOME,
            message.version.toString(),
            clean(message.sessionName),
            clean(message.memberId),
        )

        is JamMessage.Snapshot -> join(
            TYPE_SNAPSHOT,
            if (message.snapshot.isPlaying) "1" else "0",
            message.snapshot.currentIndex.toString(),
            message.snapshot.positionMs.toString(),
            message.snapshot.hostClockMs.toString(),
            message.snapshot.queue.take(MAX_QUEUE_SIZE).joinToString(RECORD.toString(), transform = ::encodeSong),
            message.snapshot.members.joinToString(RECORD.toString(), transform = ::encodeMember),
        )

        is JamMessage.Command -> encodeCommand(message.command)

        is JamMessage.Bye -> join(TYPE_BYE, clean(message.reason))
    }

    fun decode(line: String): JamMessage? {
        if (line.isEmpty() || line.length > MAX_LINE_LENGTH) return null
        val parts = line.split(FIELD)
        return runCatching {
            when (parts[0]) {
                TYPE_HELLO -> JamMessage.Hello(
                    version = parts[1].toInt(),
                    token = parts[2],
                    displayName = parts.getOrElse(3) { "" },
                )

                TYPE_WELCOME -> JamMessage.Welcome(
                    version = parts[1].toInt(),
                    sessionName = parts.getOrElse(2) { "" },
                    memberId = parts.getOrElse(3) { "" },
                )

                TYPE_SNAPSHOT -> JamMessage.Snapshot(
                    JamSnapshot(
                        isPlaying = parts[1] == "1",
                        currentIndex = parts[2].toInt(),
                        positionMs = parts[3].toLong(),
                        hostClockMs = parts[4].toLong(),
                        queue = parts.getOrElse(5) { "" }.splitRecords()
                            .take(MAX_QUEUE_SIZE)
                            .mapNotNull(::decodeSong),
                        members = parts.getOrElse(6) { "" }.splitRecords().mapNotNull(::decodeMember),
                    ),
                )

                TYPE_COMMAND -> decodeCommand(parts)?.let(JamMessage::Command)

                TYPE_BYE -> JamMessage.Bye(parts.getOrElse(1) { "" })

                else -> null
            }
        }.getOrNull()
    }

    // --- internals ---------------------------------------------------------

    private fun encodeCommand(command: JamCommand): String = when (command) {
        JamCommand.PlayPause -> join(TYPE_COMMAND, CMD_PLAY_PAUSE)
        JamCommand.Next -> join(TYPE_COMMAND, CMD_NEXT)
        JamCommand.Previous -> join(TYPE_COMMAND, CMD_PREV)
        is JamCommand.AddSong -> join(TYPE_COMMAND, CMD_ADD, encodeSong(command.song))
        is JamCommand.RemoveAt -> join(TYPE_COMMAND, CMD_REMOVE, command.index.toString())
    }

    private fun decodeCommand(parts: List<String>): JamCommand? = when (parts.getOrNull(1)) {
        CMD_PLAY_PAUSE -> JamCommand.PlayPause
        CMD_NEXT -> JamCommand.Next
        CMD_PREV -> JamCommand.Previous
        CMD_ADD -> parts.getOrNull(2)?.let(::decodeSong)?.let(JamCommand::AddSong)
        // A negative index would be rejected by the host anyway, but fail early and loudly here.
        CMD_REMOVE -> parts.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 }?.let(JamCommand::RemoveAt)
        else -> null
    }

    private fun encodeSong(song: Song): String = listOf(
        clean(song.id),
        clean(song.title),
        clean(song.artist),
        song.durationMs.toString(),
        clean(song.artworkUrl.orEmpty()),
    ).joinToString(GROUP.toString())

    private fun decodeSong(raw: String): Song? {
        val f = raw.split(GROUP)
        if (f.size < 4) return null
        val id = f[0].takeIf { it.isNotBlank() } ?: return null
        return Song(
            id = id,
            title = f[1],
            artist = f[2],
            durationMs = f[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            artworkUrl = f.getOrNull(4)?.takeIf { it.isNotBlank() },
        )
    }

    private fun encodeMember(member: JamMember): String = listOf(
        clean(member.id),
        clean(member.name),
        member.role.name,
    ).joinToString(GROUP.toString())

    private fun decodeMember(raw: String): JamMember? {
        val f = raw.split(GROUP)
        if (f.size < 3) return null
        val role = runCatching { JamRole.valueOf(f[2]) }.getOrNull() ?: return null
        return JamMember(id = f[0], name = f[1], role = role)
    }

    private fun String.splitRecords(): List<String> =
        if (isEmpty()) emptyList() else split(RECORD).filter { it.isNotEmpty() }

    private fun join(vararg fields: String) = fields.joinToString(FIELD.toString())

    /** Strip every framing character so untrusted text cannot forge a field boundary. */
    private fun clean(value: String) = value
        .replace("\n", " ")
        .replace("\r", " ")
        .replace(FIELD.toString(), "")
        .replace(RECORD.toString(), "")
        .replace(GROUP.toString(), "")
}
