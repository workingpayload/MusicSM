package com.example.musicsm.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.playback.MediaControllerManager
import com.example.musicsm.playback.PlayerState
import com.example.musicsm.playback.SleepTimerManager
import com.example.musicsm.playback.SleepTimerState
import com.example.musicsm.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** State of the lyrics fetch for the current track. */
sealed interface LyricsState {
    data object Empty : LyricsState
    data object Loading : LyricsState
    data object None : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
}

/**
 * Shared player VM (obtained once at the nav root). Exposes [state] and forwards transport
 * commands to [MediaControllerManager].
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: MediaControllerManager,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val libraryRepository: LibraryRepository,
    private val sleepTimerManager: SleepTimerManager,
    private val preferences: AppPreferences,
) : ViewModel() {

    val state: StateFlow<PlayerState> = controller.state

    /** Countdown state of the sleep timer, or an inactive snapshot. */
    val sleepTimer: StateFlow<SleepTimerState> = sleepTimerManager.state

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Empty)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    /** Guards against re-triggering autoplay for the same track. */
    private var autoplayedAfter: String? = null

    init {
        // Fetch lyrics whenever the current track changes.
        viewModelScope.launch {
            controller.state
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { song ->
                    if (song == null) {
                        _lyrics.value = LyricsState.Empty
                        return@collectLatest
                    }
                    _lyrics.value = LyricsState.Loading
                    val result = runCatching { lyricsRepository.forSong(song) }.getOrNull()
                    _lyrics.value = if (result == null) LyricsState.None else LyricsState.Loaded(result)
                }
        }
        // Record play history when the current track changes (capped in the repository).
        viewModelScope.launch {
            controller.state
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { song ->
                    if (song != null) runCatching { libraryRepository.recordPlay(song) }
                }
        }
        // Endless playback: when the queue runs dry, extend it with related tracks.
        viewModelScope.launch {
            controller.state
                .map { Pair(it.isEnded && !it.hasNext, it.currentSong) }
                .distinctUntilChanged()
                .collectLatest { (ended, song) ->
                    if (!ended || song == null) return@collectLatest
                    if (!preferences.autoplayRadioNow) return@collectLatest
                    if (autoplayedAfter == song.id) return@collectLatest
                    autoplayedAfter = song.id
                    val related = runCatching { musicRepository.relatedTo(song.id) }
                        .getOrDefault(emptyList())
                        .filterNot { it.id == song.id }
                    if (related.isEmpty()) return@collectLatest
                    related.forEach { controller.addToQueue(it) }
                    controller.next()
                }
        }
    }

    /** Play a list of songs starting at [startIndex] (e.g. an album, playlist, or search list). */
    fun play(songs: List<Song>, startIndex: Int = 0) = controller.playSongs(songs, startIndex)

    /** Play a single song, then extend the queue with related tracks (radio). */
    fun playWithRadio(song: Song) {
        controller.playSongs(listOf(song))
        viewModelScope.launch {
            runCatching { musicRepository.relatedTo(song.id) }
                .getOrDefault(emptyList())
                .forEach { controller.addToQueue(it) }
        }
    }

    /**
     * Play a track id that arrived from a deep link, share or widget. Returns false when the id
     * couldn't be resolved (bad link / offline).
     */
    suspend fun playSongId(songId: String): Boolean {
        val song = runCatching { musicRepository.song(songId) }.getOrNull() ?: return false
        playWithRadio(song)
        return true
    }

    /** Voice search ("play X on MusicSM"): play the best match for [query]. */
    suspend fun searchAndPlay(query: String): Boolean {
        val song = runCatching { musicRepository.search(query).songs.firstOrNull() }.getOrNull()
            ?: return false
        playWithRadio(song)
        return true
    }

    /** Resume the restored/paused queue. Returns false if there is nothing to resume. */
    suspend fun resumePlayback(): Boolean {
        val ready = withTimeoutOrNull(RESUME_TIMEOUT_MS) {
            state.first { it.isConnected && it.currentSong != null }
        } ?: return false
        if (!ready.isPlaying) controller.togglePlayPause()
        return true
    }

    /** Load an album by id and play its tracks. */
    fun playAlbum(albumId: String) {
        viewModelScope.launch {
            runCatching { musicRepository.album(albumId).songs }
                .getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { controller.playSongs(it) }
        }
    }

    /** Load an artist by id and play their top tracks. */
    fun playArtist(artistId: String) {
        viewModelScope.launch {
            runCatching { musicRepository.artist(artistId).topSongs }
                .getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { controller.playSongs(it) }
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekToFraction(fraction: Float) {
        val duration = state.value.durationMs
        if (duration > 0) controller.seekTo((fraction * duration).toLong())
    }
    fun seekToMs(ms: Long) = controller.seekTo(ms)
    fun seekToIndex(index: Int) = controller.seekToIndex(index)
    fun setVolume(volume: Float) = controller.setVolume(volume)
    fun toggleShuffle() = controller.toggleShuffle()

    /** Shuffle-play a list. Turns shuffle *on* (never off) and starts on a random track. */
    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        controller.setShuffle(true)
        controller.playSongs(songs, songs.indices.random())
    }
    fun setShuffle(enabled: Boolean) = controller.setShuffle(enabled)
    fun cycleRepeat() = controller.cycleRepeat()
    fun addToQueue(song: Song) = controller.addToQueue(song)
    fun playNext(song: Song) = controller.playNext(song)
    fun moveQueueItem(from: Int, to: Int) = controller.moveItem(from, to)
    fun removeQueueItem(index: Int) = controller.removeItem(index)

    // --- sleep timer -------------------------------------------------------

    fun startSleepTimer(minutes: Int) = sleepTimerManager.start(minutes)
    fun startSleepTimerAtEndOfTrack() = sleepTimerManager.startAtEndOfTrack()
    fun cancelSleepTimer() = sleepTimerManager.cancel()

    private companion object {
        const val RESUME_TIMEOUT_MS = 5_000L
    }
}
