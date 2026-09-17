package com.example.musicsm.ui.util

import android.content.Context
import android.content.Intent
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song

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
