package com.example.musicsm.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.Song
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the [MediaController] connection to [PlaybackService] and exposes player state to Compose
 * as a [StateFlow]. All controller access happens on the main thread. App-scoped singleton.
 */
@Singleton
class MediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // The volume the user set. The crossfade/sleep-timer transiently write the player's actual
    // volume; the UI slider tracks this instead so it doesn't jump around during transitions.
    private var userVolume = 1f

    private var lastSavedSignature: String? = null
    private var lastSaveAtMs = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            if (player.isPlaying) startTicker() else stopTicker()
        }
    }

    fun initialize() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()?.also {
                it.addListener(listener)
                userVolume = it.volume
                restoreQueueIfNeeded(it)
            }
            pushState()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Repopulates an empty player with the queue saved on the previous run, paused at the
     * position the user left off. Never auto-plays.
     */
    private fun restoreQueueIfNeeded(c: MediaController) {
        if (c.mediaItemCount > 0) return
        if (!preferences.restoreQueueNow) return
        val saved = preferences.loadQueue() ?: return
        runCatching {
            c.setMediaItems(
                saved.songs.map(MediaItemMapper::toMediaItem),
                saved.index,
                saved.positionMs,
            )
            c.playWhenReady = false
            c.prepare()
        }
    }

    fun release() {
        persistQueue(force = true)
        stopTicker()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    // --- commands ----------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.setMediaItems(songs.map(MediaItemMapper::toMediaItem), startIndex, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    fun playSong(song: Song) = playSongs(listOf(song))

    fun togglePlayPause() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_IDLE || c.playbackState == Player.STATE_ENDED -> {
                c.prepare(); c.play()
            }
            else -> c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun next() = controller?.seekToNext().let {}

    fun previous() = controller?.seekToPrevious().let {}

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekToIndex(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun addToQueue(song: Song) {
        controller?.addMediaItem(MediaItemMapper.toMediaItem(song))
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        val index = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(index, MediaItemMapper.toMediaItem(song))
    }

    fun moveItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    fun removeItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        userVolume = v
        controller?.volume = v
        pushState()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Force shuffle on/off. Use this for "Shuffle" buttons so they never turn shuffle *off*. */
    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // --- state ------------------------------------------------------------

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                pushState()
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun pushState() {
        val c = controller
        if (c == null) {
            _state.value = PlayerState(isConnected = false)
            return
        }
        val count = c.mediaItemCount
        val queue = if (count == 0) {
            emptyList()
        } else {
            (0 until count).map { MediaItemMapper.toSong(c.getMediaItemAt(it)) }
        }
        val duration = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = PlayerState(
            isConnected = true,
            currentSong = c.currentMediaItem?.let(MediaItemMapper::toSong),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            isEnded = c.playbackState == Player.STATE_ENDED,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            queue = queue,
            currentIndex = c.currentMediaItemIndex.coerceAtLeast(0),
            shuffleOn = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            // Report the user's volume, not the live player volume (which the crossfade ramps).
            volume = userVolume,
        )
        persistQueue()
    }

    /**
     * Writes the queue snapshot to disk. Throttled to once every [SAVE_INTERVAL_MS] unless the
     * queue contents or the current track changed, in which case it is written immediately.
     */
    private fun persistQueue(force: Boolean = false) {
        val snapshot = _state.value
        if (!snapshot.isConnected) return
        if (snapshot.queue.isEmpty()) {
            if (lastSavedSignature != null) {
                lastSavedSignature = null
                preferences.clearQueue()
            }
            return
        }
        val signature = snapshot.queue.joinToString(",") { it.id } + "@" + snapshot.currentIndex
        val now = System.currentTimeMillis()
        val changed = signature != lastSavedSignature
        if (!force && !changed && now - lastSaveAtMs < SAVE_INTERVAL_MS) return
        lastSavedSignature = signature
        lastSaveAtMs = now
        preferences.saveQueue(snapshot.queue, snapshot.currentIndex, snapshot.positionMs)
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
