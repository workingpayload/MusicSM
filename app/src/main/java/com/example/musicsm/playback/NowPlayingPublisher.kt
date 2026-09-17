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

    fun publish(song: Song?, isPlaying: Boolean) {
        val signature = "${song?.id}|${song?.title}|$isPlaying"
        if (signature == lastSignature) return
        lastSignature = signature

        preferences.saveNowPlaying(song, isPlaying)
        NowPlayingWidget.updateAll(context, preferences.nowPlaying())
        requestTileUpdate()
        publishToWear(song, isPlaying)
    }

    /** Mirror the state to a paired Wear OS watch (no-op without Play Services / a watch). */
    private fun publishToWear(song: Song?, isPlaying: Boolean) {
        runCatching {
            val request = PutDataMapRequest.create(WearContract.PATH_STATE).apply {
                dataMap.putString(WearContract.KEY_TITLE, song?.title.orEmpty())
                dataMap.putString(WearContract.KEY_ARTIST, song?.artist.orEmpty())
                dataMap.putBoolean(WearContract.KEY_PLAYING, isPlaying)
                dataMap.putBoolean(WearContract.KEY_HAS_TRACK, song != null)
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
