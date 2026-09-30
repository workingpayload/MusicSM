package com.example.musicsm.playback

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.Song
import com.example.musicsm.tile.PlaybackTileService
import com.example.musicsm.wear.WearContract
import com.example.musicsm.widget.NowPlayingWidget
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the current track to out-of-process surfaces (home-screen widget, Quick Settings
 * tile) and persists it so they still render after the app process dies.
 */
@Singleton
class NowPlayingPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) {

    private var lastSignature: String? = null

    /**
     * @param resync the playhead jumped (seek, stall recovery) without the track or play state
     * changing. The widget/tile don't show position so they're skipped, but the watch needs a
     * fresh position anchor or its lyrics drift.
     */
    fun publish(song: Song?, isPlaying: Boolean, positionMs: Long = 0L, resync: Boolean = false) {
        val signature = "${song?.id}|${song?.title}|$isPlaying"
        if (signature == lastSignature) {
            if (resync && song != null) publishToWear(song, isPlaying, positionMs)
            return
        }
        lastSignature = signature

        preferences.saveNowPlaying(song, isPlaying)
        NowPlayingWidget.updateAll(context, preferences.nowPlaying())
        requestTileUpdate()
        publishToWear(song, isPlaying, positionMs)
    }

    /** Mirror the state to a paired Wear OS watch (no-op without Play Services / a watch). */
    private fun publishToWear(song: Song?, isPlaying: Boolean, positionMs: Long) {
        runCatching {
            val request = PutDataMapRequest.create(WearContract.PATH_STATE).apply {
                dataMap.putString(WearContract.KEY_TITLE, song?.title.orEmpty())
                dataMap.putString(WearContract.KEY_ARTIST, song?.artist.orEmpty())
                dataMap.putString(WearContract.KEY_ARTWORK, song?.artworkUrl.orEmpty())
                dataMap.putBoolean(WearContract.KEY_PLAYING, isPlaying)
                dataMap.putBoolean(WearContract.KEY_HAS_TRACK, song != null)
                dataMap.putLong(WearContract.KEY_DURATION, song?.durationMs ?: 0L)
                // Anchor for the watch's lyric-sync clock: the position captured at publish time.
                dataMap.putLong(WearContract.KEY_POSITION, positionMs.coerceAtLeast(0L))
                // Forces a change event even when the same track is re-published.
                dataMap.putLong(WearContract.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request)
        }
    }

    private fun requestTileUpdate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            TileService.requestListeningState(
                context,
                ComponentName(context, PlaybackTileService::class.java),
            )
        }
    }
}
