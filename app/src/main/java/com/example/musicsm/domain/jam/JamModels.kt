package com.example.musicsm.domain.jam

import com.example.musicsm.domain.model.Song

/** Bumped only on an incompatible change; both sides refuse to talk across a mismatch. */
const val JAM_PROTOCOL_VERSION = 1

/** Who someone is in a session. The host owns the queue and is the only device playing audio. */
enum class JamRole { HOST, GUEST }

/** A participant, as seen by everyone in the session. */
data class JamMember(
    val id: String,
    val name: String,
    val role: JamRole,
)

/**
 * A snapshot of the host's player, as broadcast to guests.
 *
 * [positionMs] and [hostClockMs] are not used by the remote-control MVP — a guest plays no audio,
 * so it has nothing to seek. They are carried anyway because synced playback is the obvious next
 * step, and adding them later would be a breaking protocol change. With both values a guest can
 * estimate the host's clock offset and work out where playback actually is *now*, including the
 * time the message spent in flight.
 */
data class JamSnapshot(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val hostClockMs: Long = 0L,
    val members: List<JamMember> = emptyList(),
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
}

/** Anything a guest is allowed to ask the host to do. The host stays authoritative. */
sealed interface JamCommand {
    data object PlayPause : JamCommand
    data object Next : JamCommand
    data object Previous : JamCommand
    data class AddSong(val song: Song) : JamCommand
    data class RemoveAt(val index: Int) : JamCommand
}

/** One line on the wire, in either direction. */
sealed interface JamMessage {
    /** Guest → host, always first. Carries the session token so strangers on the Wi-Fi bounce. */
    data class Hello(val version: Int, val token: String, val displayName: String) : JamMessage

    /** Host → guest, the reply to a valid [Hello]. */
    data class Welcome(val version: Int, val sessionName: String, val memberId: String) : JamMessage

    /** Host → guest, whenever the player changes and as a periodic heartbeat. */
    data class Snapshot(val snapshot: JamSnapshot) : JamMessage

    /** Guest → host. */
    data class Command(val command: JamCommand) : JamMessage

    /** Either direction: the session is over, or the join was refused. */
    data class Bye(val reason: String) : JamMessage
}
