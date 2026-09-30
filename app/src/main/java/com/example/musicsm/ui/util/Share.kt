package com.example.musicsm.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import java.io.File
import java.io.FileOutputStream

/**
 * Public link for a song. [Song.id] is the YouTube video id, so a youtu.be link opens in the
 * browser / YouTube / YouTube Music on any device.
 */
fun songShareUrl(song: Song): String = "https://youtu.be/${song.id}"

/** Fire the system share sheet for [song]. */
fun shareSong(context: Context, song: Song) {
    val subject = "${song.title} — ${song.artist}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "$subject\n${songShareUrl(song)}")
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_song_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Writes [bitmap] to the shared cache and fires the system share sheet as an image. Used for the
 * lyric card. The file goes under `cacheDir/shared`, which the app's FileProvider exposes.
 */
fun shareImage(context: Context, bitmap: Bitmap, subject: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "lyric_card.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.lyrics_share_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
