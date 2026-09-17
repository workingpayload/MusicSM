package com.example.musicsm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.widget.RemoteViews
import com.example.musicsm.MainActivity
import com.example.musicsm.R
import com.example.musicsm.data.prefs.NowPlayingSnapshot
import com.example.musicsm.playback.MediaButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.musicsm.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import okhttp3.Request
import java.io.IOException
import java.net.URL

/**
 * Renders the home-screen widget from a [NowPlayingSnapshot]. Deliberately independent of the
 * media session: the widget must draw something sensible even when the app process is dead.
 */
object NowPlayingWidget {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var artworkCache: Pair<String, Bitmap>? = null

    /** Push [snapshot] to every placed widget. Artwork is fetched in the background. */
    fun updateAll(context: Context, snapshot: NowPlayingSnapshot) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, NowPlayingWidgetProvider::class.java))
        }.getOrNull()
        if (ids == null || ids.isEmpty()) return

        val url = snapshot.song?.artworkUrl
        val cached = artworkCache?.takeIf { it.first == url }?.second
        manager.updateAppWidget(ids, buildViews(context, snapshot, cached))

        if (url != null && cached == null) {
            scope.launch {
                val bitmap = loadArtwork(context, url) ?: return@launch
                artworkCache = url to bitmap
                runCatching { manager.updateAppWidget(ids, buildViews(context, snapshot, bitmap)) }
            }
        }
    }

    private fun buildViews(
        context: Context,
        snapshot: NowPlayingSnapshot,
        artwork: Bitmap?,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
        val song = snapshot.song
        setTextViewText(R.id.widget_title, song?.title?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_empty_title))
        setTextViewText(R.id.widget_artist, song?.artist?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_empty_subtitle))

        if (artwork != null) {
            setImageViewBitmap(R.id.widget_artwork, artwork)
        } else {
            setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_placeholder)
        }

        setImageViewResource(
            R.id.widget_play_pause,
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        // Tapping the artwork/title always opens the app on Now Playing.
        setOnClickPendingIntent(R.id.widget_root, openApp(context, "resume"))

        if (snapshot.hasQueue) {
            setOnClickPendingIntent(R.id.widget_play_pause, MediaButtons.playPause(context))
            setOnClickPendingIntent(R.id.widget_next, MediaButtons.next(context))
            setOnClickPendingIntent(R.id.widget_previous, MediaButtons.previous(context))
        } else {
            // No live session: every control just brings the app up and resumes.
            val resume = openApp(context, "resume")
            setOnClickPendingIntent(R.id.widget_play_pause, resume)
            setOnClickPendingIntent(R.id.widget_next, resume)
            setOnClickPendingIntent(R.id.widget_previous, resume)
        }
    }

    private fun openApp(context: Context, host: String): PendingIntent = PendingIntent.getActivity(
        context,
        host.hashCode(),
        Intent(Intent.ACTION_VIEW, Uri.parse("musicsm://$host")).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Downloads and downsamples the artwork, then rounds it to match the app's corners. */
    private fun loadArtwork(context: Context, url: String): Bitmap? = runCatching {
        val http = EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .okHttpClient()
        val request = Request.Builder().url(URL(url)).build()
        val bytes = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes()
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, ARTWORK_PX)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::roundCorners)
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= target) {
            largest /= 2
            sample *= 2
        }
        return sample
    }

    private fun roundCorners(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height).coerceAtLeast(1)
        // Centre-crop to a square first so the rounded frame isn't stretched.
        val square = Bitmap.createBitmap(
            source,
            (source.width - size) / 2,
            (source.height - size) / 2,
            size,
            size,
        )
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size * CORNER_FRACTION
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        if (square !== source) square.recycle()
        return output
    }

    private const val ARTWORK_PX = 256
    private const val CORNER_FRACTION = 0.22f
}
