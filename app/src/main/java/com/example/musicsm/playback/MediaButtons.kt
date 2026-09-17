package com.example.musicsm.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * Transport controls for surfaces that live outside the app process (home-screen widget,
 * Quick Settings tile). Media3's `MediaSessionService` handles `ACTION_MEDIA_BUTTON`, so a
 * plain service intent is enough — no `MediaController` connection required.
 */
object MediaButtons {

    fun playPause(context: Context): PendingIntent = keyIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun next(context: Context): PendingIntent = keyIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(context: Context): PendingIntent = keyIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /** Fire a media button immediately. No-op if the service can't be started right now. */
    fun send(context: Context, keyCode: Int) {
        runCatching { context.startService(buttonIntent(context, keyCode)) }
    }

    private fun keyIntent(context: Context, keyCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            keyCode,
            buttonIntent(context, keyCode),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buttonIntent(context: Context, keyCode: Int) =
        Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(context, PlaybackService::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
}
