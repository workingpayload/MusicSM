package com.example.musicsm.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent
import com.example.musicsm.R
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.playback.MediaButtons
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Quick Settings play/pause tile. Reads the persisted now-playing snapshot instead of binding a
 * `MediaController`, so it renders instantly even when the app process isn't running.
 */
@AndroidEntryPoint
class PlaybackTileService : TileService() {

    @Inject lateinit var preferences: AppPreferences

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        MediaButtons.send(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        // Optimistically flip the tile; the service pushes the real state right after.
        val snapshot = preferences.nowPlaying()
        preferences.saveNowPlaying(snapshot.song, !snapshot.isPlaying)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val snapshot = preferences.nowPlaying()
        val song = snapshot.song

        tile.state = if (song == null) Tile.STATE_UNAVAILABLE else Tile.STATE_ACTIVE
        tile.label = song?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(
            this,
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = song?.artist?.takeIf { it.isNotBlank() }
                ?: getString(R.string.widget_empty_title)
        }
        tile.updateTile()
    }
}
