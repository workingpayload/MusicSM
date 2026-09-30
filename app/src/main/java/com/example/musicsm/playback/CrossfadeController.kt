package com.example.musicsm.playback

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicsm.playback.mix.Audibility
import com.example.musicsm.playback.mix.Automation
import com.example.musicsm.playback.mix.BeatAnalyzer
import com.example.musicsm.playback.mix.BeatGrid
import com.example.musicsm.playback.mix.DjFilterProcessor
import com.example.musicsm.playback.mix.FadeShape
import com.example.musicsm.playback.mix.FilterAutomation
import com.example.musicsm.playback.mix.MixCurves
import com.example.musicsm.playback.mix.MixIn
import com.example.musicsm.playback.mix.MixMediaSourceFactory
import com.example.musicsm.playback.mix.MixPlan
import com.example.musicsm.playback.mix.MixStartSource
import com.example.musicsm.playback.mix.PcmSnippet
import com.example.musicsm.playback.mix.PcmSnippetDecoder
import com.example.musicsm.playback.mix.TransitionPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * True overlapping crossfade, and DJ-style "Mix" transitions, done inside the main player's own
 * audio stream. When [crossfadeMs] is 0 and Mix is off the controller is inert and ExoPlayer's
 * native gapless transition applies.
 *
 * A while before the end of a track the opening of the next one is decoded ([PcmSnippetDecoder]).
 * The main player's [DjFilterProcessor] then fades the current track out and mixes that opening
 * in, both by media time, so the blend lands to the sample however far ahead audio is buffered.
 * When the current track ends the player moves on by itself (gaplessly - no seek) and the next
 * track starts exactly where the mixed-in opening got to ([MixStartSource]); the processor patches
 * any few-ms difference. Nothing is played twice or cut, nothing depends on measuring positions,
 * and pausing, seeking or changing speed during a blend just works. There is one audio output, so
 * volume, equalizer and output device apply as usual.
 *
 * In Mix mode both tracks are analysed first: silent run-outs and lead-ins are skipped
 * ([Audibility]), beats are found ([BeatAnalyzer]) and, when the tempos already agree, the blend
 * is placed so downbeats land together ([TransitionPlanner]). The bass is swapped from one track
 * to the other part-way through. Otherwise it's a shorter timed blend with the same bass handling.
 */
@UnstableApi
class CrossfadeController(
    private val mainPlayer: ExoPlayer,
    private val sources: MixMediaSourceFactory,
    dataSourceFactory: DataSource.Factory,
    private val mainFilter: DjFilterProcessor,
    private val scope: CoroutineScope,
) {
    var crossfadeMs: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            updateMonitoring()
        }

    /**
     * Seamless "mix" transitions, regardless of [crossfadeMs]: a DJ-style blend of [MIX_WINDOW_MS]
     * (or the user's crossfade length if they set an even longer one), beat-matched when possible.
     */
    var mixMode: Boolean = false
        set(value) {
            field = value
            updateMonitoring()
        }

    /** Crossfade runs if the user set a fade length, or Mix mode is on. */
    private val enabled: Boolean get() = crossfadeMs > 0 || mixMode

    /** The overlap length actually used, widening to a full mix when Mix mode is on. */
    private fun activeWindowMs(): Int = if (mixMode) maxOf(crossfadeMs, MIX_WINDOW_MS) else crossfadeMs

    private val decoder = PcmSnippetDecoder(dataSourceFactory)
    private val playbackHandler by lazy { Handler(mainPlayer.playbackLooper) }
    private var monitorJob: Job? = null
    private var listenerAttached = false
    private var pending: Pending? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && enabled) startMonitor() else stopMonitor()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            pending?.onItemChanged(mediaItem, reason)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) pending?.checkNext()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            pending?.checkNext()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            pending?.checkNext()
        }
    }

    private fun updateMonitoring() {
        if (released) return
        if (enabled) {
            if (!listenerAttached) {
                mainPlayer.addListener(listener)
                listenerAttached = true
            }
            if (mainPlayer.isPlaying) startMonitor() else stopMonitor()
        } else {
            stopMonitor()
            pending?.cancel("turned off")
            if (listenerAttached) {
                mainPlayer.removeListener(listener)
                listenerAttached = false
            }
        }
    }

    /** Polls only while audio is actually playing, not for the whole life of the service. */
    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                maybeTransition()
                delay(POLL_MS)
            }
        }
    }

    private fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun maybeTransition() {
        val cf = activeWindowMs()
        if (cf <= 0 || !mainPlayer.isPlaying) return
        val current = mainPlayer.currentMediaItem ?: return
        val duration = mainPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val position = mainPlayer.currentPosition
        pending?.let { p ->
            if (p.outgoing.mediaId == current.mediaId) {
                p.tick(position)
                return
            }
            p.cancel("track changed")
        }
        val next = nextItem() ?: return
        // Repeat-one (next == current) shouldn't blend a track into itself.
        if (next.mediaId == current.mediaId) return
        val window = cf.toLong().coerceAtMost(duration / 2)
        if (window < MIN_FADE_MS) return
        val speed = mainPlayer.playbackParameters.speed
        val lead = window + if (mixMode) ANALYSIS_LEAD_MS else PREPARE_LEAD_MS
        if (duration - position > lead * speed) return
        pending = Pending(current, next, duration, window, mixMode).also { it.start(position, speed) }
    }

    private fun nextItem(): MediaItem? {
        val timeline = mainPlayer.currentTimeline
        if (timeline.isEmpty) return null
        // Use timeline auto-advance, not skip-next navigation, so repeat-one returns this item.
        val idx = timeline.getNextWindowIndex(
            mainPlayer.currentMediaItemIndex,
            mainPlayer.repeatMode,
            mainPlayer.shuffleModeEnabled,
        )
        if (idx == C.INDEX_UNSET || idx !in 0 until mainPlayer.mediaItemCount) return null
        return mainPlayer.getMediaItemAt(idx)
    }

    /** Where the player should start [mediaId] when it moves on to it by itself (0 = the start). */
    private fun setStart(mediaId: String, ms: Double) {
        val targets = sources.sourcesFor(mediaId)
        val us = (ms * 1000).roundToLong().coerceAtLeast(0L)
        playbackHandler.post { targets.forEach { it.setStartUs(us) } }
    }

    /** One transition from [outgoing] into [next], from preparing it to the join. Main thread. */
    private inner class Pending(
        val outgoing: MediaItem,
        val next: MediaItem,
        private val duration: Long,
        private val window: Long,
        private val mix: Boolean,
    ) {
        private var job: Job? = null
        private var armed = false
        private var startSet = false
        private var closed = false
        private var nextStartMs = 0.0

        fun start(position: Long, speed: Float) {
            job = scope.launch {
                val ready = try {
                    withContext(Dispatchers.Default) { prepare(position, speed) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "preparing $this failed", e)
                    null
                }
                if (closed) return@launch
                if (ready == null) Log.d(TAG, "no blend for $this") else arm(ready)
            }
        }

        /** Plans the blend and decodes the part of the next track it needs. Off the main thread. */
        private suspend fun prepare(position: Long, speed: Float): Ready? {
            val started = SystemClock.elapsedRealtime()
            val plan = if (mix) {
                val a = analyse(outgoing, next, duration, window)
                TransitionPlanner.plan(
                    durationMs = duration,
                    windowMs = window,
                    earliestHandoffMs = position + (PLAN_GRACE_MS * speed).roundToLong(),
                    outgoing = a.outGrid,
                    incoming = a.inGrid,
                    outgoingEndMs = a.outgoingEndMs ?: duration,
                    incomingStartMs = a.incomingStartMs,
                    maxStretch = MATCH_TOLERANCE,
                ).also {
                    Log.d(
                        TAG,
                        "plan $this: beatMatched=${it.beatMatched} handoff=${it.handoffAtMs}/$duration " +
                            "fade=${it.fadeMs} swap=${it.bassSwapMs} audibleEnd=${a.outgoingEndMs} " +
                            "incomingStart=${it.incomingStartMs} out=${a.outGrid} in=${a.inGrid}",
                    )
                }
            } else {
                MixPlan(
                    handoffAtMs = duration - window,
                    fadeMs = window,
                    outgoingRate = 1f,
                    beatMatched = false,
                    bassSwapMs = window / 2,
                    swapRampMs = 0,
                )
            }
            // The mixed-in opening must last until this track ends, and a little beyond.
            val from = plan.incomingStartMs
            val head = decoder.decode(next, from, from + (duration - plan.handoffAtMs) + HEAD_MARGIN_MS, keepChannels = true)
                ?: return null
            Log.d(TAG, "prepared $this in ${SystemClock.elapsedRealtime() - started}ms")
            return Ready(plan, head)
        }

        /** Hands the blend to the fade stage. It only acts on this track's audio, by media time. */
        private fun arm(ready: Ready) {
            if (mainPlayer.currentMediaItem?.mediaId != outgoing.mediaId) return
            if (mainFilter.streamMediaId != outgoing.mediaId) {
                Log.d(TAG, "no blend for $this: the audio stream doesn't say which track it is")
                return
            }
            val speed = mainPlayer.playbackParameters.speed
            val plan = ready.plan
            val head = ready.head
            var handoff = plan.handoffAtMs.toDouble()
            var fade = plan.fadeMs.toDouble()
            var matched = plan.beatMatched
            // The fade stage works ahead of what's heard; it has to have the blend before reaching it.
            val earliest = mainPlayer.currentPosition + ARM_MARGIN_MS * speed.toDouble()
            if (handoff < earliest) {
                handoff = earliest
                fade = min(fade, duration - handoff)
                matched = false
            }
            nextStartMs = head.startMs + (duration - handoff)
            val headEndMs = head.startMs + head.samples.size / head.channels * 1000.0 / head.sampleRate
            if (fade < MIN_FADE_MS || headEndMs < nextStartMs + JOIN_MARGIN_MS) {
                Log.d(TAG, "no blend for $this: fade ${fade.fmt()}ms, opening ends ${headEndMs.fmt()} < ${nextStartMs.fmt()}")
                return
            }
            mainFilter.takeJoin()
            mainFilter.automate(
                Automation(
                    gain = MixCurves.fadeOut(handoff, fade, if (mix) FadeShape.MIX else FadeShape.EQUAL_POWER),
                    cutoff = if (mix) outgoingCutoff(handoff, fade, plan, matched) else null,
                    mediaId = outgoing.mediaId,
                    mixIn = MixIn(
                        pcm = head.samples,
                        channels = head.channels,
                        sampleRate = head.sampleRate,
                        startMs = handoff,
                        nextMediaId = next.mediaId,
                        nextStartMs = head.startMs,
                        outgoingEndMs = duration.toDouble(),
                        gain = MixCurves.fadeIn(handoff, fade),
                        cutoff = if (mix) incomingCutoff(handoff, fade, plan, matched) else null,
                    ),
                ),
            )
            armed = true
            Log.d(
                TAG,
                "armed $this: blend ${handoff.fmt()}..${(handoff + fade).fmt()}/$duration matched=$matched, " +
                    "next continues at ${nextStartMs.fmt()}ms",
            )
        }

        /** Called on every poll while this track plays. */
        fun tick(position: Long) {
            if (!armed || startSet || closed) return
            val speed = mainPlayer.playbackParameters.speed
            val left = duration - position
            if (left > START_SET_AHEAD_MS * speed) return
            if (nextItem()?.mediaId != next.mediaId) {
                cancel("queue changed")
                return
            }
            // Once the player may already be reading the next track, moving its start would make
            // it re-seek this one. The fade stage then drops the repeat itself (see finish()).
            if (left < MIN_START_SET_MS * speed) return
            setStart(next.mediaId, nextStartMs)
            startSet = true
        }

        fun onItemChanged(item: MediaItem?, reason: Int) {
            if (closed) return
            if (item?.mediaId == outgoing.mediaId) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) cancel("repeated")
                return
            }
            if (armed && item?.mediaId == next.mediaId && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                finish()
            } else {
                cancel("moved on to ${item?.mediaId}")
            }
        }

        fun checkNext() {
            if (closed || mainPlayer.currentMediaItem?.mediaId != outgoing.mediaId) return
            if (nextItem()?.mediaId != next.mediaId) cancel("queue changed")
        }

        /** The player moved on to [next] by itself: the blend is done. */
        private fun finish() {
            close()
            val join = mainFilter.takeJoin()
            mainFilter.clear()
            if (startSet) setStart(next.mediaId, 0.0)
            Log.d(TAG, "joined $this: gap ${join?.gapMs?.fmt()}ms, fixed=${join?.fixed}")
            // Started from the top after all: the fade stage dropped the part the mix already
            // played, so the audio is right but the reported position is behind by that much.
            if (join != null && join.fixed && join.gapMs < -POSITION_FIX_MS) {
                mainPlayer.seekTo(mainPlayer.currentPosition + (-join.gapMs).roundToLong())
            }
        }

        fun cancel(reason: String) {
            if (closed) return
            close()
            job?.cancel()
            if (armed) mainFilter.clear()
            if (startSet) setStart(next.mediaId, 0.0)
            Log.d(TAG, "cancel $this: $reason")
        }

        private fun close() {
            closed = true
            if (pending === this) pending = null
        }

        /** Mix: the outgoing track keeps its bass until the swap, then loses it. */
        private fun outgoingCutoff(h: Double, fade: Double, plan: MixPlan, matched: Boolean): FilterAutomation =
            if (matched) {
                val swap = h + plan.bassSwapMs
                FilterAutomation.of(h to OFF, swap to OFF, swap + plan.swapRampMs to BASS_CUT_HZ)
            } else {
                FilterAutomation.of(h to OFF, h + UNMATCHED_OUT_CUT * fade to BASS_CUT_HZ)
            }

        /** Mix: the next track comes in without its bass and gets it at the swap. */
        private fun incomingCutoff(h: Double, fade: Double, plan: MixPlan, matched: Boolean): FilterAutomation =
            if (matched) {
                val swap = h + plan.bassSwapMs
                FilterAutomation.of(h to BASS_CUT_HZ, swap to BASS_CUT_HZ, swap + plan.swapRampMs to OFF)
            } else {
                FilterAutomation.of(
                    h to BASS_CUT_HZ,
                    h + UNMATCHED_IN_HOLD * fade to BASS_CUT_HZ,
                    h + UNMATCHED_IN_OPEN * fade to OFF,
                )
            }

        override fun toString() = "${outgoing.mediaId}>${next.mediaId}"
    }

    /** Decodes the end of [outgoing] and the start of [next] and finds their beats and silences. */
    private suspend fun analyse(outgoing: MediaItem, next: MediaItem, duration: Long, window: Long): Analysis =
        coroutineScope {
            val started = SystemClock.elapsedRealtime()
            // Both reads are mostly network/decoder bound, so run them side by side.
            val tailJob = async(Dispatchers.IO) {
                decodeOrNull("tail") {
                    decoder.decode(outgoing, (duration - window - TAIL_EXTRA_MS).coerceAtLeast(0L), duration - 500)
                }
            }
            val headJob = async(Dispatchers.IO) {
                decodeOrNull("head") { decoder.decode(next, 0, HEAD_MS) }
            }
            val tail = tailJob.await()
            val head = headJob.await()
            val decoded = SystemClock.elapsedRealtime()
            val outGrid = tail?.let { BeatAnalyzer.analyze(it.samples, it.sampleRate, it.startMs) }
            val inGrid = head?.let { BeatAnalyzer.analyze(it.samples, it.sampleRate, it.startMs) }
            // A silent run-out isn't worth blending over, nor a silent lead-in worth waiting for.
            val outgoingEnd = tail?.let { snippet ->
                Audibility.endMs(snippet)
                    ?.takeIf { it < Audibility.snippetEndMs(snippet) - RUN_OUT_MIN_MS }
                    ?.let { (it + AUDIBLE_PAD_MS).roundToLong().coerceAtMost(duration) }
            }
            val incomingStart = head?.let { Audibility.startMs(it) }
                ?.let { (it - AUDIBLE_PAD_MS).roundToLong().coerceIn(0L, MAX_INCOMING_START_MS) }
                ?: 0L
            Log.d(TAG, "analysed ${outgoing.mediaId}>${next.mediaId}: decode ${decoded - started}ms, beats ${SystemClock.elapsedRealtime() - decoded}ms")
            Analysis(outGrid, inGrid, outgoingEnd, incomingStart)
        }

    private suspend fun decodeOrNull(what: String, block: suspend () -> PcmSnippet?): PcmSnippet? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what decode failed", e)
            null
        }

    fun release() {
        released = true
        stopMonitor()
        pending?.cancel("released")
        if (listenerAttached) {
            mainPlayer.removeListener(listener)
            listenerAttached = false
        }
        mainFilter.clear()
    }

    private class Ready(val plan: MixPlan, val head: PcmSnippet)

    private class Analysis(
        val outGrid: BeatGrid?,
        val inGrid: BeatGrid?,
        val outgoingEndMs: Long?,
        val incomingStartMs: Long,
    )

    private companion object {
        const val TAG = "Mix"
        const val POLL_MS = 200L
        const val MIX_WINDOW_MS = 8_000 // blend length in Mix mode
        const val MIN_FADE_MS = 1_000L

        // Scheduling (media ms; scaled by the user's speed where it matters).
        const val PREPARE_LEAD_MS = 20_000L // crossfade: decode the next opening this far ahead
        const val ANALYSIS_LEAD_MS = 60_000L // Mix: analyse this far ahead of the blend
        const val PLAN_GRACE_MS = 5_000L // earliest beat-matched blend after the analysis starts
        const val ARM_MARGIN_MS = 2_000L // the fade stage runs up to ~1 s ahead of what's heard
        const val START_SET_AHEAD_MS = 4_000L // move the next track's start this long before the end
        const val MIN_START_SET_MS = 1_500L // ...unless the player may already be reading it
        const val HEAD_MARGIN_MS = 1_500L
        const val JOIN_MARGIN_MS = 300.0
        const val POSITION_FIX_MS = 300.0

        // Nothing is time-stretched, so beats are only matched when the tempos already agree.
        const val MATCH_TOLERANCE = 0.004

        // Mix analysis.
        const val TAIL_EXTRA_MS = 12_000L // analyse this much of the outgoing track before the blend
        const val HEAD_MS = 20_000L // analyse the first 20 s of the incoming track
        const val RUN_OUT_MIN_MS = 1_000.0 // shorter silent run-outs are left alone
        const val AUDIBLE_PAD_MS = 50.0
        const val MAX_INCOMING_START_MS = 8_000L

        // Mix bass swap.
        const val OFF = DjFilterProcessor.OFF_HZ
        const val BASS_CUT_HZ = 150.0
        const val UNMATCHED_OUT_CUT = 0.3
        const val UNMATCHED_IN_HOLD = 0.35
        const val UNMATCHED_IN_OPEN = 0.55
    }
}

private fun Double.fmt(): String = String.format(Locale.US, "%.1f", this)
