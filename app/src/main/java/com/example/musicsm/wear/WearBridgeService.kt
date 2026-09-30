package com.example.musicsm.wear

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicsm.di.AppEntryPoint
import com.example.musicsm.domain.model.Song
import com.example.musicsm.playback.MediaButtons
import com.example.musicsm.playback.MediaItemMapper
import com.example.musicsm.playback.PlaybackService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles everything the Wear OS companion sends over the Data Layer:
 *
 * - [WearContract.PATH_COMMAND] — transport (play/pause/next/prev), forwarded as media buttons.
 *   Only takes effect while a session exists; the watch's "Open on phone" covers cold start.
 * - [WearContract.PATH_SEARCH] — a query; runs the catalog search and replies with a JSON song
 *   array on [WearContract.PATH_SEARCH_RESULTS].
 * - [WearContract.PATH_PLAY] — a JSON song the watch picked; starts playback headlessly by
 *   connecting a [MediaController] to [PlaybackService] (works even if the phone UI isn't running).
 *
 * [onMessageReceived] runs on a background thread, so the search blocks here on purpose: it keeps
 * the (otherwise short-lived) service alive until the reply is sent. An earlier version launched
 * the search on a service-scoped coroutine that `onDestroy` cancelled the instant the callback
 * returned, so the reply never arrived and the watch spun forever.
 */
class WearBridgeService : WearableListenerService() {

    private val main = Handler(Looper.getMainLooper())

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, AppEntryPoint::class.java)
    }
    private val musicRepository by lazy { entryPoint.musicRepository() }
    private val lyricsRepository by lazy { entryPoint.lyricsRepository() }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearContract.PATH_COMMAND -> handleCommand(String(messageEvent.data))
            WearContract.PATH_SEARCH -> handleSearch(String(messageEvent.data), messageEvent.sourceNodeId)
            WearContract.PATH_PLAY -> handlePlay(String(messageEvent.data))
            WearContract.PATH_LYRICS -> handleLyrics(String(messageEvent.data), messageEvent.sourceNodeId)
        }
    }

    private fun handleCommand(command: String) {
        val keyCode = when (command) {
            WearContract.CMD_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            WearContract.CMD_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            WearContract.CMD_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        MediaButtons.send(this, keyCode)
    }

    private fun handleSearch(query: String, nodeId: String) {
        if (query.isBlank()) return
        val songs = runCatching {
            runBlocking { musicRepository.search(query).songs }
        }.getOrDefault(emptyList()).take(WearContract.SEARCH_RESULT_LIMIT)
        val payload = JSONArray().apply { songs.forEach { put(it.toJson()) } }.toString()
        runCatching {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, WearContract.PATH_SEARCH_RESULTS, payload.toByteArray())
        }
    }

    private fun handlePlay(payload: String) {
        val song = runCatching { JSONObject(payload).toSong() }.getOrNull() ?: return
        main.post { playHeadless(song) }
    }

    private fun handleLyrics(payload: String, nodeId: String) {
        // The lyrics request only carries what LRCLIB needs (title/artist/duration) — no videoId —
        // so build the Song straight from those fields rather than via toSong(), which requires an id.
        val song = runCatching {
            val obj = JSONObject(payload)
            Song(
                id = "",
                title = obj.optString(WearContract.KEY_TITLE),
                artist = obj.optString(WearContract.KEY_ARTIST),
                durationMs = obj.optLong(WearContract.KEY_DURATION, 0L),
            )
        }.getOrNull() ?: return
        if (song.title.isBlank()) return
        val lyrics = runCatching {
            runBlocking { lyricsRepository.forSong(song) }
        }.getOrNull()
        val result = JSONObject().apply {
            put(WearContract.KEY_SYNCED, lyrics?.synced == true)
            put(
                WearContract.KEY_LINES,
                JSONArray().apply {
                    lyrics?.lines.orEmpty().forEach { line ->
                        put(
                            JSONObject().apply {
                                put(WearContract.KEY_LINE_TIME, line.timeMs ?: -1L)
                                put(WearContract.KEY_LINE_TEXT, line.text)
                            },
                        )
                    }
                },
            )
        }.toString()
        runCatching {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, WearContract.PATH_LYRICS_RESULT, result.toByteArray())
        }
    }

    /**
     * Connects a transient [MediaController] just long enough to hand [song] to the session and
     * start it, then releases it — the session keeps playing on its own and [PlaybackService]'s
     * listener mirrors the new track back to the watch.
     *
     * The controller is built against the *application* context, not this service: the service is
     * torn down the moment [onMessageReceived] returns, and a controller bound to a dead context
     * throws `IllegalArgumentException: Service not registered` when it later tries to unbind.
     */
    private fun playHeadless(song: Song) {
        val appContext: Context = applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller == null) {
                runCatching { MediaController.releaseFuture(future) }
                return@addListener
            }
            runCatching {
                controller.setMediaItem(MediaItemMapper.toMediaItem(song))
                controller.prepare()
                controller.play()
            }
            // Release after a short grace so the async play command reaches the session first.
            main.postDelayed({ runCatching { MediaController.releaseFuture(future) } }, RELEASE_DELAY_MS)
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun Song.toJson(): JSONObject = JSONObject().apply {
        put(WearContract.KEY_ID, id)
        put(WearContract.KEY_TITLE, title)
        put(WearContract.KEY_ARTIST, artist)
        put(WearContract.KEY_ARTWORK, artworkUrl.orEmpty())
        put(WearContract.KEY_DURATION, durationMs)
    }

    private fun JSONObject.toSong(): Song = Song(
        id = getString(WearContract.KEY_ID),
        title = optString(WearContract.KEY_TITLE),
        artist = optString(WearContract.KEY_ARTIST),
        artworkUrl = optString(WearContract.KEY_ARTWORK).ifBlank { null },
        durationMs = optLong(WearContract.KEY_DURATION, 0L),
    )

    private companion object {
        // Give the async play() command time to reach the session before dropping the controller.
        const val RELEASE_DELAY_MS = 3_000L
    }
}
