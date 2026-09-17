package com.example.musicsm.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A persisted snapshot of the playback queue, restored on the next app launch. */
data class SavedQueue(
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
)

/**
 * Last known playback state, written by the service so out-of-process surfaces (home-screen
 * widget, Quick Settings tile) can render without connecting to the session.
 */
data class NowPlayingSnapshot(
    val song: Song?,
    val isPlaying: Boolean,
    val hasQueue: Boolean,
)

/**
 * All persisted user settings plus small pieces of session state (queue snapshot, search
 * history, pending sleep timer). Backed by [SharedPreferences]; every getter has a [Flow]
 * variant that re-emits when the underlying key changes.
 */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("musicsm_settings", Context.MODE_PRIVATE)

    private val changes = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) changes.tryEmit(key)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun <T> watch(key: String, read: () -> T): Flow<T> =
        changes.filter { it == key }.map { read() }.onStart { emit(read()) }.distinctUntilChanged()

    // --- playback settings -------------------------------------------------

    val restoreQueue: Flow<Boolean> get() = watch(KEY_RESTORE_QUEUE) { restoreQueueNow }
    val restoreQueueNow: Boolean get() = prefs.getBoolean(KEY_RESTORE_QUEUE, true)
    fun setRestoreQueue(value: Boolean) = prefs.edit().putBoolean(KEY_RESTORE_QUEUE, value).apply()

    val skipSilence: Flow<Boolean> get() = watch(KEY_SKIP_SILENCE) { prefs.getBoolean(KEY_SKIP_SILENCE, false) }
    fun setSkipSilence(value: Boolean) = prefs.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()

    val autoplayRadio: Flow<Boolean> get() = watch(KEY_AUTOPLAY) { autoplayRadioNow }
    val autoplayRadioNow: Boolean get() = prefs.getBoolean(KEY_AUTOPLAY, false)
    fun setAutoplayRadio(value: Boolean) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    /** Fade the volume down over the last few seconds before the sleep timer pauses playback. */
    val sleepTimerFadeOut: Flow<Boolean> get() = watch(KEY_SLEEP_FADE) { sleepTimerFadeOutNow }
    val sleepTimerFadeOutNow: Boolean get() = prefs.getBoolean(KEY_SLEEP_FADE, true)
    fun setSleepTimerFadeOut(value: Boolean) = prefs.edit().putBoolean(KEY_SLEEP_FADE, value).apply()

    // --- download settings -------------------------------------------------

    val wifiOnlyDownloads: Flow<Boolean> get() = watch(KEY_WIFI_ONLY) { wifiOnlyDownloadsNow }
    val wifiOnlyDownloadsNow: Boolean get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnlyDownloads(value: Boolean) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    // --- sort orders -------------------------------------------------------

    val playlistSort: Flow<SongSort> get() = watch(KEY_SORT_PLAYLIST) { SongSort.fromName(prefs.getString(KEY_SORT_PLAYLIST, null)) }
    fun setPlaylistSort(sort: SongSort) = prefs.edit().putString(KEY_SORT_PLAYLIST, sort.name).apply()

    val downloadsSort: Flow<SongSort> get() = watch(KEY_SORT_DOWNLOADS) { SongSort.fromName(prefs.getString(KEY_SORT_DOWNLOADS, null)) }
    fun setDownloadsSort(sort: SongSort) = prefs.edit().putString(KEY_SORT_DOWNLOADS, sort.name).apply()

    // --- search history ----------------------------------------------------

    val recentSearches: Flow<List<String>> get() = watch(KEY_RECENT_SEARCHES) { recentSearchesNow() }

    fun recentSearchesNow(): List<String> =
        prefs.getString(KEY_RECENT_SEARCHES, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        // Typing "a" -> "ab" -> "abc" should leave one entry, not three.
        val updated = (
            listOf(trimmed) + recentSearchesNow().filterNot {
                it.equals(trimmed, ignoreCase = true) || trimmed.startsWith(it, ignoreCase = true)
            }
            ).take(MAX_RECENT_SEARCHES)
        writeRecentSearches(updated)
    }

    fun removeRecentSearch(query: String) {
        writeRecentSearches(recentSearchesNow().filterNot { it == query })
    }

    fun clearRecentSearches() = writeRecentSearches(emptyList())

    private fun writeRecentSearches(values: List<String>) {
        prefs.edit().putString(KEY_RECENT_SEARCHES, values.joinToString(SEPARATOR)).apply()
    }

    // --- sleep timer -------------------------------------------------------

    /** Wall-clock time the sleep timer should fire, or 0 when no timer is pending. */
    var sleepTimerEndsAt: Long
        get() = prefs.getLong(KEY_SLEEP_ENDS_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_SLEEP_ENDS_AT, value).apply()

    /** True when the pending sleep timer should stop at the end of the current track instead. */
    var sleepTimerEndOfTrack: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_END_OF_TRACK, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_END_OF_TRACK, value).apply()

    // --- queue snapshot ----------------------------------------------------

    fun saveQueue(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) {
            clearQueue()
            return
        }
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album ?: JSONObject.NULL)
                    put("artworkUrl", song.artworkUrl ?: JSONObject.NULL)
                    put("durationMs", song.durationMs)
                },
            )
        }
        prefs.edit()
            .putString(KEY_QUEUE, array.toString())
            .putInt(KEY_QUEUE_INDEX, index.coerceAtLeast(0))
            .putLong(KEY_QUEUE_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun loadQueue(): SavedQueue? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val songs = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Song(
                    id = id,
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    album = obj.optStringOrNull("album"),
                    artworkUrl = obj.optStringOrNull("artworkUrl"),
                    durationMs = obj.optLong("durationMs"),
                )
            }
        }.getOrNull().orEmpty()
        if (songs.isEmpty()) return null
        return SavedQueue(
            songs = songs,
            index = prefs.getInt(KEY_QUEUE_INDEX, 0).coerceIn(0, songs.lastIndex),
            positionMs = prefs.getLong(KEY_QUEUE_POSITION, 0L),
        )
    }

    fun clearQueue() {
        prefs.edit()
            .remove(KEY_QUEUE)
            .remove(KEY_QUEUE_INDEX)
            .remove(KEY_QUEUE_POSITION)
            .apply()
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    // --- now-playing snapshot (widget + tile) ------------------------------

    /**
     * Mirror of the current track for surfaces that live outside the app process. Survives
     * process death so a widget added days ago still shows something meaningful.
     */
    fun saveNowPlaying(song: Song?, isPlaying: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_NOW_PLAYING_IS_PLAYING, isPlaying)
        if (song == null) {
            editor.remove(KEY_NOW_PLAYING).apply()
            return
        }
        val json = JSONObject().apply {
            put("id", song.id)
            put("title", song.title)
            put("artist", song.artist)
            put("artworkUrl", song.artworkUrl ?: JSONObject.NULL)
            put("durationMs", song.durationMs)
        }
        editor.putString(KEY_NOW_PLAYING, json.toString()).apply()
    }

    fun nowPlaying(): NowPlayingSnapshot {
        val raw = prefs.getString(KEY_NOW_PLAYING, null)
        val song = raw?.let {
            runCatching {
                val obj = JSONObject(it)
                Song(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    artworkUrl = obj.optStringOrNull("artworkUrl"),
                    durationMs = obj.optLong("durationMs"),
                )
            }.getOrNull()
        }?.takeIf { it.id.isNotEmpty() }
        // Fall back to the saved queue so a cold widget can still offer "resume".
        val fallback = if (song == null) loadQueue()?.let { it.songs.getOrNull(it.index) } else null
        return NowPlayingSnapshot(
            song = song ?: fallback,
            isPlaying = song != null && prefs.getBoolean(KEY_NOW_PLAYING_IS_PLAYING, false),
            hasQueue = song != null,
        )
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT_SEARCHES = 12

        const val KEY_RESTORE_QUEUE = "restore_queue"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_AUTOPLAY = "autoplay_radio"
        const val KEY_SLEEP_FADE = "sleep_timer_fade"
        const val KEY_WIFI_ONLY = "wifi_only_downloads"
        const val KEY_SORT_PLAYLIST = "sort_playlist"
        const val KEY_SORT_DOWNLOADS = "sort_downloads"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_SLEEP_ENDS_AT = "sleep_timer_ends_at"
        const val KEY_SLEEP_END_OF_TRACK = "sleep_timer_end_of_track"
        const val KEY_QUEUE = "queue_songs"
        const val KEY_QUEUE_INDEX = "queue_index"
        const val KEY_QUEUE_POSITION = "queue_position"
        const val KEY_NOW_PLAYING = "now_playing"
        const val KEY_NOW_PLAYING_IS_PLAYING = "now_playing_is_playing"
    }
}
