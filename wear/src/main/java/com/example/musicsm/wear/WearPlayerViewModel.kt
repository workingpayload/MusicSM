package com.example.musicsm.wear

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What the watch knows about the phone's playback. */
data class WearPlaybackState(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val hasTrack: Boolean = false,
    // Playback position at the moment this state was published, plus the watch's own clock reading
    // when it arrived. Together they let the lyrics screen extrapolate the current position without
    // the phone streaming it continuously.
    val positionMs: Long = 0L,
    val receivedElapsedRealtime: Long = 0L,
) {
    /** Best estimate of the phone's current playback position, right now, on the watch's clock. */
    fun estimatedPositionMs(): Long =
        if (isPlaying) positionMs + (SystemClock.elapsedRealtime() - receivedElapsedRealtime) else positionMs
}

/** A search hit the watch can tap to play on the phone. */
data class WearSong(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
)

/** One line of lyrics for the watch. [timeMs] is null for plain (unsynced) lyrics. */
data class WearLyricLine(val timeMs: Long?, val text: String)

/** Lyrics for the current track, as delivered by the phone. */
data class WearLyrics(val synced: Boolean, val lines: List<WearLyricLine>)

/**
 * Remote control for the phone. State arrives as a Data Layer item published by the phone's
 * `NowPlayingPublisher`; commands, search queries and play requests go back as messages handled
 * by `WearBridgeService`. Search results come back as a message on [WearContract.PATH_SEARCH_RESULTS].
 */
class WearPlayerViewModel(application: Application) : AndroidViewModel(application),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    private val _state = MutableStateFlow(WearPlaybackState())
    val state: StateFlow<WearPlaybackState> = _state.asStateFlow()

    private val _searchResults = MutableStateFlow<List<WearSong>>(emptyList())
    val searchResults: StateFlow<List<WearSong>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _lyrics = MutableStateFlow<WearLyrics?>(null)
    val lyrics: StateFlow<WearLyrics?> = _lyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    // Accent pulled from the current cover, so the watch tints itself by the music like the phone app.
    private val _accent = MutableStateFlow(DefaultCoral)
    val accent: StateFlow<Color> = _accent.asStateFlow()

    init {
        viewModelScope.launch {
            _state.map { it.artworkUrl }.distinctUntilChanged().collect { url ->
                _accent.value = dominantColor(url) ?: DefaultCoral
            }
        }
    }

    /** Start listening and pull whatever the phone last published. */
    fun start() {
        dataClient.addListener(this)
        messageClient.addListener(this)
        dataClient.dataItems.addOnSuccessListener { buffer ->
            buffer.firstOrNull { it.uri.path == WearContract.PATH_STATE }?.let(::apply)
            buffer.release()
        }
    }

    fun stop() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearContract.PATH_STATE
            ) {
                apply(event.dataItem)
            }
        }
        events.release()
    }

    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        when (event.path) {
            WearContract.PATH_SEARCH_RESULTS -> {
                _searchResults.value = parseResults(String(event.data))
                _isSearching.value = false
            }

            WearContract.PATH_LYRICS_RESULT -> {
                lyricsTimeout?.cancel()
                _lyrics.value = parseLyrics(String(event.data))
                _isLoadingLyrics.value = false
            }
        }
    }

    fun playPause() {
        // Flip immediately so the button feels responsive; the phone confirms a moment later.
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
        send(WearContract.PATH_COMMAND, WearContract.CMD_PLAY_PAUSE.toByteArray())
    }

    fun next() = send(WearContract.PATH_COMMAND, WearContract.CMD_NEXT.toByteArray())

    fun previous() = send(WearContract.PATH_COMMAND, WearContract.CMD_PREVIOUS.toByteArray())

    /** Ask the phone to search [query] and reply with results. */
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _isSearching.value = true
        _searchResults.value = emptyList()
        send(WearContract.PATH_SEARCH, q.toByteArray())
    }

    /** Ask the phone to play [song] now. */
    fun play(song: WearSong) {
        val payload = JSONObject().apply {
            put(WearContract.KEY_ID, song.id)
            put(WearContract.KEY_TITLE, song.title)
            put(WearContract.KEY_ARTIST, song.artist)
            put(WearContract.KEY_ARTWORK, song.artworkUrl)
            put(WearContract.KEY_DURATION, song.durationMs)
        }.toString()
        send(WearContract.PATH_PLAY, payload.toByteArray())
    }

    /** Drop the current results (e.g. when leaving the search screen). */
    fun clearSearch() {
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    private var lyricsTimeout: Job? = null

    /** Ask the phone for the current track's lyrics. */
    fun requestLyrics() {
        val current = _state.value
        if (!current.hasTrack) return
        _isLoadingLyrics.value = true
        _lyrics.value = null
        val payload = JSONObject().apply {
            put(WearContract.KEY_TITLE, current.title)
            put(WearContract.KEY_ARTIST, current.artist)
            put(WearContract.KEY_DURATION, current.durationMs)
        }.toString()
        send(WearContract.PATH_LYRICS, payload.toByteArray())
        // Fall back to the empty state if the phone never replies (offline / no session).
        lyricsTimeout?.cancel()
        lyricsTimeout = viewModelScope.launch {
            delay(LYRICS_TIMEOUT_MS)
            if (_isLoadingLyrics.value) _isLoadingLyrics.value = false
        }
    }

    fun clearLyrics() {
        lyricsTimeout?.cancel()
        _lyrics.value = null
        _isLoadingLyrics.value = false
    }

    /** Bring the phone app to the foreground on Now Playing. */
    fun openOnPhone() {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("musicsm://resume"))
        runCatching {
            RemoteActivityHelper(getApplication()).startRemoteActivity(intent)
        }
    }

    private fun send(path: String, data: ByteArray) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, data)
            }
        }
    }

    private fun parseResults(payload: String): List<WearSong> = runCatching {
        val array = JSONArray(payload)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            WearSong(
                id = obj.getString(WearContract.KEY_ID),
                title = obj.optString(WearContract.KEY_TITLE),
                artist = obj.optString(WearContract.KEY_ARTIST),
                artworkUrl = obj.optString(WearContract.KEY_ARTWORK),
                durationMs = obj.optLong(WearContract.KEY_DURATION, 0L),
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun dominantColor(url: String): Color? {
        if (url.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val ctx = getApplication<Application>()
                val request = ImageRequest.Builder(ctx)
                    .data(url)
                    .allowHardware(false) // Palette needs readable pixels.
                    .size(160)
                    .build()
                val result = SingletonImageLoader.get(ctx).execute(request)
                val bitmap = (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap
                val palette = bitmap?.let { Palette.from(it).clearFilters().generate() }
                val swatch = palette?.let {
                    it.vibrantSwatch ?: it.dominantSwatch ?: it.darkVibrantSwatch ?: it.mutedSwatch
                }
                swatch?.let { Color(it.rgb) }
            }.getOrNull()
        }
    }

    private fun parseLyrics(payload: String): WearLyrics? = runCatching {
        val obj = JSONObject(payload)
        val array = obj.getJSONArray(WearContract.KEY_LINES)
        val lines = (0 until array.length()).map { i ->
            val line = array.getJSONObject(i)
            val time = line.optLong(WearContract.KEY_LINE_TIME, -1L)
            WearLyricLine(
                timeMs = time.takeIf { it >= 0 },
                text = line.optString(WearContract.KEY_LINE_TEXT),
            )
        }
        if (lines.isEmpty()) null
        else WearLyrics(synced = obj.optBoolean(WearContract.KEY_SYNCED, false), lines = lines)
    }.getOrNull()

    private fun apply(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        _state.value = WearPlaybackState(
            title = map.getString(WearContract.KEY_TITLE).orEmpty(),
            artist = map.getString(WearContract.KEY_ARTIST).orEmpty(),
            artworkUrl = map.getString(WearContract.KEY_ARTWORK).orEmpty(),
            durationMs = map.getLong(WearContract.KEY_DURATION, 0L),
            isPlaying = map.getBoolean(WearContract.KEY_PLAYING, false),
            hasTrack = map.getBoolean(WearContract.KEY_HAS_TRACK, false),
            positionMs = map.getLong(WearContract.KEY_POSITION, 0L),
            receivedElapsedRealtime = SystemClock.elapsedRealtime(),
        )
    }

    private companion object {
        const val LYRICS_TIMEOUT_MS = 12_000L
        val DefaultCoral = Color(0xFFFF525E)
    }
}
