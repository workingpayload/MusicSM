package com.example.musicsm.playback

import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One armed crossfade/Mix blend: while [outgoingId] plays, its audio fades into [next] between
 * [startMs] and [endMs] (media time of the outgoing track, the same clock as its position).
 */
data class MixBlend(
    val outgoingId: String,
    val next: Song,
    val startMs: Double,
    val endMs: Double,
) {
    /** How far the blend has got at [positionMs]: 0 before it, 1 once the next track has it all. */
    fun progressAt(positionMs: Long): Float {
        val length = endMs - startMs
        if (length <= 0.0) return if (positionMs >= endMs) 1f else 0f
        return ((positionMs - startMs) / length).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Lets the UI follow the blend [CrossfadeController] schedules, so Now Playing can cross-fade the
 * artwork in step with the audio. The service and the UI share the app process.
 */
@Singleton
class MixTransitionBus @Inject constructor() {
    private val _blend = MutableStateFlow<MixBlend?>(null)
    val blend: StateFlow<MixBlend?> = _blend.asStateFlow()

    fun publish(blend: MixBlend) {
        _blend.value = blend
    }

    /** Clears [blend] if it's still the current one (a newer blend is left alone). */
    fun clear(blend: MixBlend? = null) {
        if (blend == null) _blend.value = null else _blend.compareAndSet(blend, null)
    }
}
