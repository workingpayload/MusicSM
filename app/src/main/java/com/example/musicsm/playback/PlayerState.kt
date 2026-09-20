package com.example.musicsm.playback

import androidx.media3.common.Player
import com.example.musicsm.domain.model.Song

/**
 * Immutable snapshot of the player, observed by the UI.
 *
 * The fast-changing playback position deliberately does NOT live here — it is exposed separately as
 * [MediaControllerManager.position]. Keeping it out means this snapshot only changes on real player
 * events (play/pause, track change, queue edits), so collectors high in the tree (the nav root, the
 * mini player) no longer recompose twice a second just because the clock ticked.
 */
data class PlayerState(
    val isConnected: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val shuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val volume: Float = 1f,
)
