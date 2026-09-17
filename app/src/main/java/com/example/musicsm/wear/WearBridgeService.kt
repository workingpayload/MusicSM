package com.example.musicsm.wear

import android.view.KeyEvent
import com.example.musicsm.playback.MediaButtons
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives transport commands from the Wear OS companion app and forwards them to the media
 * session as media-button intents.
 *
 * Commands only take effect while a session exists (i.e. something is playing or paused) —
 * the watch's "Open on phone" action covers the cold-start case.
 */
class WearBridgeService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearContract.PATH_COMMAND) return
        val keyCode = when (String(messageEvent.data)) {
            WearContract.CMD_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            WearContract.CMD_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            WearContract.CMD_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        MediaButtons.send(this, keyCode)
    }
}
