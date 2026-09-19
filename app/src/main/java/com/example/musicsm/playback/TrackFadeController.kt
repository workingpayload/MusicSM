package com.example.musicsm.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fades the player volume out at the end of a track and back in at the start of the next one.
 *
 * ExoPlayer renders a single output, so this is a fade-out/fade-in rather than an overlapping
 * crossfade — there is no second decoder to blend against. The audible effect for the common
 * case (a shuffled queue of unrelated tracks) is the same, and unlike a true overlap it cannot
 * break gapless album playback: when the fade duration is 0 the player is left completely alone
 * and ExoPlayer's native gapless transition applies.
 *
 * Volume has other writers — the in-app slider and [SleepTimerManager]. Rather than fight them,
 * the fader treats any volume change it did not make as the new *base* level and applies its
 * envelope on top, so a sleep-timer fade and a track fade multiply instead of cancelling.
 */
@UnstableApi
class TrackFadeController(
    private val player: Player,
    private val scope: CoroutineScope,
) : Player.Listener {

    /** Fade length in ms; 0 disables the fader entirely. */
    var fadeMs: Int = 0
        set(value) {
            val clamped = value.coerceAtLeast(0)
            if (field == clamped) return
            field = clamped
            if (clamped == 0) {
                stop()
                applyEnvelope(1f)
            } else {
                start()
            }
        }

    private var job: Job? = null
    private var base = 1f
    private var lastApplied = Float.NaN
    private var envelope = 1f

    init {
        base = player.volume
        player.addListener(this)
    }

    override fun onVolumeChanged(volume: Float) {
        // Ignore the echo of our own write; anything else is a new base level.
        if (!lastApplied.isNaN() && abs(volume - lastApplied) < EPSILON) return
        base = volume
        lastApplied = volume
    }

    fun release() {
        stop()
        player.removeListener(this)
        applyEnvelope(1f)
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                applyEnvelope(computeEnvelope())
                delay(TICK_MS)
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
    }

    /** 1.0 in the body of a track, ramping to 0 across [fadeMs] at either edge. */
    private fun computeEnvelope(): Float {
        val fade = fadeMs
        if (fade <= 0 || !player.isPlaying) return 1f

        val duration = player.duration
        val position = player.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0L) return 1f
        // A fade longer than half the track would never reach full volume.
        val window = fade.toLong().coerceAtMost(duration / 2)
        if (window <= 0L) return 1f

        val fadeIn = if (position < window) position.toFloat() / window else 1f

        val remaining = duration - position
        // Don't fade out the final track of the queue into silence; let it end naturally.
        val fadeOut = if (remaining < window && hasFollowingItem()) {
            remaining.toFloat() / window
        } else {
            1f
        }

        return minOf(fadeIn, fadeOut).coerceIn(0f, 1f)
    }

    private fun hasFollowingItem(): Boolean =
        player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF

    private fun applyEnvelope(value: Float) {
        envelope = value
        val target = (base * value).coerceIn(0f, 1f)
        if (!lastApplied.isNaN() && abs(target - lastApplied) < EPSILON) return
        lastApplied = target
        player.volume = target
    }

    private companion object {
        const val TICK_MS = 100L
        const val EPSILON = 0.001f
    }
}
