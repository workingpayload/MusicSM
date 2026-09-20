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

    /** Playback speed multiplier, 0.5x - 2.0x. */
    val playbackSpeed: Flow<Float> get() = watch(KEY_SPEED) { playbackSpeedNow }
    val playbackSpeedNow: Float get() = prefs.getFloat(KEY_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)
    fun setPlaybackSpeed(value: Float) =
        prefs.edit().putFloat(KEY_SPEED, value.coerceIn(MIN_SPEED, MAX_SPEED)).apply()

    /** Length of the volume fade applied at track boundaries, in ms. 0 disables fading. */
    val crossfadeMs: Flow<Int> get() = watch(KEY_CROSSFADE) { crossfadeMsNow }
    val crossfadeMsNow: Int get() = prefs.getInt(KEY_CROSSFADE, 0).coerceIn(0, MAX_CROSSFADE_MS)
    fun setCrossfadeMs(value: Int) =
        prefs.edit().putInt(KEY_CROSSFADE, value.coerceIn(0, MAX_CROSSFADE_MS)).apply()

    // --- appearance --------------------------------------------------------

    /** One of [com.example.musicsm.ui.theme.ThemeMode]'s names; defaults to the dark palette. */
    val themeMode: Flow<String> get() = watch(KEY_THEME_MODE) { themeModeNow }
    val themeModeNow: String get() = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
    fun setThemeMode(value: String) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    val amoled: Flow<Boolean> get() = watch(KEY_AMOLED) { amoledNow }
    val amoledNow: Boolean get() = prefs.getBoolean(KEY_AMOLED, false)
    fun setAmoled(value: Boolean) = prefs.edit().putBoolean(KEY_AMOLED, value).apply()

    val materialYou: Flow<Boolean> get() = watch(KEY_MATERIAL_YOU) { materialYouNow }
    val materialYouNow: Boolean get() = prefs.getBoolean(KEY_MATERIAL_YOU, false)
    fun setMaterialYou(value: Boolean) = prefs.edit().putBoolean(KEY_MATERIAL_YOU, value).apply()

    /** Packed ARGB accent override, or [NO_ACCENT] to keep the palette's own accent. */
    val accentColor: Flow<Int> get() = watch(KEY_ACCENT) { accentColorNow }
    val accentColorNow: Int get() = prefs.getInt(KEY_ACCENT, NO_ACCENT)
    fun setAccentColor(argb: Int) = prefs.edit().putInt(KEY_ACCENT, argb).apply()

    /** Re-tint the whole app from the current track's artwork. */
    val themeFromArtwork: Flow<Boolean> get() = watch(KEY_ARTWORK_THEME) { themeFromArtworkNow }
    val themeFromArtworkNow: Boolean get() = prefs.getBoolean(KEY_ARTWORK_THEME, false)
    fun setThemeFromArtwork(value: Boolean) = prefs.edit().putBoolean(KEY_ARTWORK_THEME, value).apply()

    // --- audio effects -----------------------------------------------------

    val effectsEnabled: Flow<Boolean> get() = watch(KEY_FX_ENABLED) { effectsEnabledNow }
    val effectsEnabledNow: Boolean get() = prefs.getBoolean(KEY_FX_ENABLED, false)
    fun setEffectsEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_FX_ENABLED, value).apply()

    /** Index into the device equalizer's built-in presets, or [CUSTOM_PRESET] for manual bands. */
    val equalizerPreset: Flow<Int> get() = watch(KEY_FX_PRESET) { equalizerPresetNow }
    val equalizerPresetNow: Int get() = prefs.getInt(KEY_FX_PRESET, CUSTOM_PRESET)
    fun setEqualizerPreset(value: Int) = prefs.edit().putInt(KEY_FX_PRESET, value).apply()

    /** Per-band gains in millibels, one entry per band the device reports. */
    val equalizerBands: Flow<List<Int>> get() = watch(KEY_FX_BANDS) { equalizerBandsNow }
    val equalizerBandsNow: List<Int>
        get() = prefs.getString(KEY_FX_BANDS, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()

    fun setEqualizerBands(levels: List<Int>) {
        prefs.edit()
            .putString(KEY_FX_BANDS, levels.joinToString(","))
            .putInt(KEY_FX_PRESET, CUSTOM_PRESET)
            .apply()
    }

    /** Bass boost strength, 0-1000 as defined by [android.media.audiofx.BassBoost]. */
    val bassBoost: Flow<Int> get() = watch(KEY_FX_BASS) { bassBoostNow }
    val bassBoostNow: Int get() = prefs.getInt(KEY_FX_BASS, 0).coerceIn(0, 1000)
    fun setBassBoost(value: Int) = prefs.edit().putInt(KEY_FX_BASS, value.coerceIn(0, 1000)).apply()

    /** Virtualizer (stereo widening) strength, 0-1000. */
    val virtualizer: Flow<Int> get() = watch(KEY_FX_VIRTUALIZER) { virtualizerNow }
    val virtualizerNow: Int get() = prefs.getInt(KEY_FX_VIRTUALIZER, 0).coerceIn(0, 1000)
    fun setVirtualizer(value: Int) =
        prefs.edit().putInt(KEY_FX_VIRTUALIZER, value.coerceIn(0, 1000)).apply()

    /** Extra gain in millibels applied by LoudnessEnhancer; levels up quiet tracks. */
    val loudnessGainMb: Flow<Int> get() = watch(KEY_FX_LOUDNESS) { loudnessGainMbNow }
    val loudnessGainMbNow: Int get() = prefs.getInt(KEY_FX_LOUDNESS, 0).coerceIn(0, MAX_LOUDNESS_MB)
    fun setLoudnessGainMb(value: Int) =
        prefs.edit().putInt(KEY_FX_LOUDNESS, value.coerceIn(0, MAX_LOUDNESS_MB)).apply()

    /** Resets the equalizer, bass boost, virtualizer and loudness to their defaults. */
    fun resetAudioEffects() {
        prefs.edit()
            .remove(KEY_FX_PRESET)
            .remove(KEY_FX_BANDS)
            .remove(KEY_FX_BASS)
            .remove(KEY_FX_VIRTUALIZER)
            .remove(KEY_FX_LOUDNESS)
            .apply()
    }

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

    // --- in-app updates ----------------------------------------------------

    /** The update version the user tapped "Later" on, so the launch popup doesn't nag every time. */
    var dismissedUpdateVersion: String?
        get() = prefs.getString(KEY_DISMISSED_UPDATE, null)
        set(value) = prefs.edit().putString(KEY_DISMISSED_UPDATE, value).apply()

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

    // --- settings backup / restore -----------------------------------------

    /**
     * Serializes user-facing settings for a backup file. Deliberately excludes volatile session
     * state (the saved queue, now-playing snapshot and pending sleep timer) — those are tied to a
     * specific run and would be meaningless, or actively wrong, restored onto a fresh install.
     */
    fun exportSettings(): JSONObject {
        val obj = JSONObject()
        val all = prefs.all
        BACKUP_KEYS.forEach { key -> all[key]?.let { obj.put(key, it) } }
        return obj
    }

    /** Restores settings written by [exportSettings]. Unknown keys are ignored, so an older or
     *  newer backup never crashes the restore. */
    fun importSettings(obj: JSONObject) {
        val editor = prefs.edit()
        obj.keys().forEach { key ->
            if (key !in BACKUP_KEYS) return@forEach
            when (val value = obj.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                // JSON collapses all numbers to Int/Long/Double after a file round-trip; only the
                // playback speed is a float, everything else is an int.
                is Number -> if (key == KEY_SPEED) editor.putFloat(key, value.toFloat())
                    else editor.putInt(key, value.toInt())
            }
        }
        editor.apply()
    }

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

    companion object {
        /** [accentColor] value meaning "keep the palette's own accent". */
        const val NO_ACCENT = 0
        const val DEFAULT_THEME_MODE = "DARK"

        /** [equalizerPreset] value meaning "use the per-band levels instead of a device preset". */
        const val CUSTOM_PRESET = -1
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
        const val MAX_CROSSFADE_MS = 12_000
        const val MAX_LOUDNESS_MB = 2_000

        private const val SEPARATOR = "\u001F"
        private const val MAX_RECENT_SEARCHES = 12

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "theme_amoled"
        private const val KEY_MATERIAL_YOU = "theme_material_you"
        private const val KEY_ACCENT = "theme_accent"
        private const val KEY_ARTWORK_THEME = "theme_from_artwork"
        private const val KEY_RESTORE_QUEUE = "restore_queue"
        private const val KEY_SKIP_SILENCE = "skip_silence"
        private const val KEY_AUTOPLAY = "autoplay_radio"
        private const val KEY_SLEEP_FADE = "sleep_timer_fade"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_CROSSFADE = "crossfade_ms"
        private const val KEY_FX_ENABLED = "fx_enabled"
        private const val KEY_FX_PRESET = "fx_preset"
        private const val KEY_FX_BANDS = "fx_bands"
        private const val KEY_FX_BASS = "fx_bass_boost"
        private const val KEY_FX_VIRTUALIZER = "fx_virtualizer"
        private const val KEY_FX_LOUDNESS = "fx_loudness_mb"
        private const val KEY_WIFI_ONLY = "wifi_only_downloads"
        private const val KEY_SORT_PLAYLIST = "sort_playlist"
        private const val KEY_SORT_DOWNLOADS = "sort_downloads"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_SLEEP_ENDS_AT = "sleep_timer_ends_at"
        private const val KEY_SLEEP_END_OF_TRACK = "sleep_timer_end_of_track"
        private const val KEY_QUEUE = "queue_songs"
        private const val KEY_QUEUE_INDEX = "queue_index"
        private const val KEY_QUEUE_POSITION = "queue_position"
        private const val KEY_NOW_PLAYING = "now_playing"
        private const val KEY_NOW_PLAYING_IS_PLAYING = "now_playing_is_playing"
        private const val KEY_DISMISSED_UPDATE = "dismissed_update_version"

        /**
         * The settings included in a backup. Session/volatile keys (queue, now-playing, sleep
         * timer deadlines) are intentionally left out.
         */
        private val BACKUP_KEYS = setOf(
            KEY_THEME_MODE, KEY_AMOLED, KEY_MATERIAL_YOU, KEY_ACCENT, KEY_ARTWORK_THEME,
            KEY_RESTORE_QUEUE, KEY_SKIP_SILENCE, KEY_AUTOPLAY, KEY_SLEEP_FADE, KEY_SPEED,
            KEY_CROSSFADE, KEY_FX_ENABLED, KEY_FX_PRESET, KEY_FX_BANDS, KEY_FX_BASS,
            KEY_FX_VIRTUALIZER, KEY_FX_LOUDNESS, KEY_WIFI_ONLY, KEY_SORT_PLAYLIST,
            KEY_SORT_DOWNLOADS, KEY_RECENT_SEARCHES,
        )
    }
}
