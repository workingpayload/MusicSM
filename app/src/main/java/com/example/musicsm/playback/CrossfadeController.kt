package com.example.musicsm.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * True overlapping crossfade. A single [ExoPlayer] can't blend two of its own items, so near the
 * end of a track we advance the main player to the next item early (fading it in) while a
 * secondary player carries the outgoing track's tail (fading it out) — so both are briefly
 * audible. When [crossfadeMs] is 0 the controller is inert and ExoPlayer's native gapless
 * transition applies.
 *
 * The main player's volume is treated as the user's base level; the crossfade ramps within it and
 * restores it afterwards.
 */
@UnstableApi
class CrossfadeController(
    private val context: Context,
    private val mainPlayer: ExoPlayer,
    private val mediaSourceFactory: MediaSource.Factory,
    private val scope: CoroutineScope,
) {
    var crossfadeMs: Int = 0
        set(value) {
            val clamped = value.coerceAtLeast(0)
            field = clamped
            if (clamped > 0) {
                // Feature on: watch play state and poll only while actually playing.
                if (!listenerAttached) {
                    mainPlayer.addListener(playStateListener)
                    listenerAttached = true
                }
                if (mainPlayer.isPlaying) startMonitor() else stopMonitor()
            } else {
                // Feature off: stop polling and detach the play-state listener.
                stopMonitor()
                if (listenerAttached) {
                    mainPlayer.removeListener(playStateListener)
                    listenerAttached = false
                }
            }
        }

    private var monitorJob: Job? = null
    private var fadeJob: Job? = null
    private var secondary: ExoPlayer? = null

    /** videoId of the track we've already begun crossfading out of (guards double-trigger). */
    private var crossfadedFrom: String? = null

    /** Whether [playStateListener] is currently attached to [mainPlayer]. */
    private var listenerAttached = false

    /**
     * Gates the polling loop on real playback state: the monitor only wakes every [POLL_MS] while
     * audio is actually playing, not for the whole life of the (long-lived) service. Pausing tears
     * the loop down; resuming (with crossfade still enabled) brings it back.
     */
    private val playStateListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && crossfadeMs > 0) startMonitor() else stopMonitor()
        }
    }

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                maybeCrossfade()
                delay(POLL_MS)
            }
        }
    }

    private fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun maybeCrossfade() {
        val cf = crossfadeMs
        if (cf <= 0 || !mainPlayer.isPlaying) return
        if (fadeJob?.isActive == true) return
        if (!mainPlayer.hasNextMediaItem()) return

        val duration = mainPlayer.duration
        val position = mainPlayer.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0L) return

        val window = cf.toLong().coerceAtMost(duration / 2)
        if (window <= 0L) return
        // Kick off early enough to pre-buffer the secondary before the actual overlap window.
        if (duration - position > window + PREP_MS) return

        val outgoing = mainPlayer.currentMediaItem ?: return
        if (outgoing.mediaId == crossfadedFrom) return

        val nextIdx = mainPlayer.nextMediaItemIndex
        if (nextIdx == C.INDEX_UNSET || nextIdx >= mainPlayer.mediaItemCount) return
        // Repeat-one (next == current) shouldn't overlap the same track with itself.
        if (mainPlayer.getMediaItemAt(nextIdx).mediaId == outgoing.mediaId) return

        crossfadedFrom = outgoing.mediaId
        beginCrossfade(outgoing, window)
    }

    private fun beginCrossfade(outgoing: MediaItem, window: Long) {
        val base = mainPlayer.volume.takeIf { it > 0f } ?: 1f
        fadeJob = scope.launch {
            val sec = obtainSecondary()

            // 1) Pre-buffer the outgoing track on the secondary at the current position, muted, so
            //    it's ready to take over without a gap.
            sec.setMediaSource(mediaSourceFactory.createMediaSource(outgoing))
            sec.volume = base
            sec.playWhenReady = false
            sec.prepare()
            sec.seekTo(mainPlayer.currentPosition)
            val ready = awaitReady(sec)

            // Main finished (or was skipped) while we prepared — let it transition normally.
            if (mainPlayer.currentMediaItem?.mediaId != outgoing.mediaId) {
                cleanup(sec)
                return@launch
            }
            if (!ready) {
                cleanup(sec)
                return@launch
            }

            // 2) Hand off: secondary resumes the outgoing tail exactly where main is; main jumps
            //    to the next track. Both now play at once.
            sec.seekTo(mainPlayer.currentPosition)
            sec.playWhenReady = true
            mainPlayer.seekToNextMediaItem()
            mainPlayer.volume = 0f

            // 3) Cross-ramp the volumes over the window.
            val steps = (window / TICK_MS).coerceAtLeast(1)
            for (i in 1..steps) {
                if (!mainPlayer.playWhenReady) break
                val f = i.toFloat() / steps
                mainPlayer.volume = base * f
                sec.volume = base * (1f - f)
                delay(TICK_MS)
            }
            mainPlayer.volume = base
            cleanup(sec)
        }
    }

    private suspend fun awaitReady(player: ExoPlayer): Boolean {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (player.playbackState == Player.STATE_READY) return true
            delay(50)
        }
        return player.playbackState == Player.STATE_READY
    }

    private fun cleanup(sec: ExoPlayer) {
        sec.playWhenReady = false
        sec.stop()
        sec.clearMediaItems()
    }

    private fun obtainSecondary(): ExoPlayer =
        secondary ?: ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { secondary = it }

    fun release() {
        stopMonitor()
        if (listenerAttached) {
            mainPlayer.removeListener(playStateListener)
            listenerAttached = false
        }
        fadeJob?.cancel()
        fadeJob = null
        secondary?.release()
        secondary = null
    }

    private companion object {
        const val POLL_MS = 200L
        const val TICK_MS = 50L
        const val PREP_MS = 1_500L        // lead time to pre-buffer the secondary
        const val READY_TIMEOUT_MS = 4_000L
    }
}
