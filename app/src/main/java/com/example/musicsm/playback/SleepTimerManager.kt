package com.example.musicsm.playback

import com.example.musicsm.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the sleep timer for the UI. */
data class SleepTimerState(
    val isActive: Boolean = false,
    val endOfTrack: Boolean = false,
    val remainingMs: Long = 0L,
)

/**
 * Pauses playback after a countdown or at the end of the current track. The pending deadline is
 * persisted so the timer survives process death, and the last few seconds are faded out.
 */
@Singleton
class SleepTimerManager @Inject constructor(
    private val controller: MediaControllerManager,
    private val preferences: AppPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var volumeBeforeFade: Float? = null

    init {
        // Restore a timer that was still pending when the process died.
        val endsAt = preferences.sleepTimerEndsAt
        when {
            preferences.sleepTimerEndOfTrack -> startAtEndOfTrack()
            endsAt > System.currentTimeMillis() -> startCountdown(endsAt)
            else -> clearPersisted()
        }
    }

    /** Stop playback after [minutes] from now. */
    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        startCountdown(System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Stop playback once the currently playing track finishes. */
    fun startAtEndOfTrack() {
        job?.cancel()
        restoreVolume()
        preferences.sleepTimerEndsAt = 0L
        preferences.sleepTimerEndOfTrack = true
        _state.value = SleepTimerState(isActive = true, endOfTrack = true)
        job = scope.launch {
            // Wait for a track to actually be loaded before latching onto it.
            val start = controller.state.first { it.currentSong != null }
            val startId = start.currentSong?.id
            val startIndex = start.currentIndex
            controller.state.first { snapshot ->
                snapshot.isEnded ||
                    snapshot.currentIndex != startIndex ||
                    (snapshot.currentSong != null && snapshot.currentSong.id != startId)
            }
            fire()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        restoreVolume()
        clearPersisted()
        _state.value = SleepTimerState()
    }

    private fun startCountdown(endsAtMs: Long) {
        job?.cancel()
        restoreVolume()
        preferences.sleepTimerEndOfTrack = false
        preferences.sleepTimerEndsAt = endsAtMs
        job = scope.launch {
            while (isActive) {
                val remaining = endsAtMs - System.currentTimeMillis()
                if (remaining <= 0L) break
                _state.value = SleepTimerState(isActive = true, remainingMs = remaining)
                if (preferences.sleepTimerFadeOutNow && remaining <= FADE_MS) {
                    val current = controller.state.value.volume
                    if (volumeBeforeFade == null) volumeBeforeFade = current
                    val target = (volumeBeforeFade ?: current) * (remaining.toFloat() / FADE_MS)
                    controller.setVolume(target.coerceIn(0f, 1f))
                    delay(200)
                } else {
                    delay(minOf(500L, remaining))
                }
            }
            fire()
        }
    }

    private fun fire() {
        controller.pause()
        restoreVolume()
        clearPersisted()
        job = null
        _state.value = SleepTimerState()
    }

    private fun restoreVolume() {
        volumeBeforeFade?.let { controller.setVolume(it) }
        volumeBeforeFade = null
    }

    private fun clearPersisted() {
        preferences.sleepTimerEndsAt = 0L
        preferences.sleepTimerEndOfTrack = false
    }

    private companion object {
        const val FADE_MS = 8_000L
    }
}
