package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/** One lyrics database. [LyricsRepositoryImpl] asks several of these and picks the best answer. */
interface LyricsProvider {
    val source: LyricsSource

    /**
     * Lyrics for [song], or null when this source has none. [track] and [artist] are already
     * stripped of YouTube noise; [Song.durationMs] is the real length of the audio being played.
     */
    suspend fun fetch(song: Song, track: String, artist: String): Lyrics?
}

/**
 * A source that can also look a song up by its ISRC, the code that names one exact recording (a
 * single and its album cut share a title, artist and nearly a length, and can differ in the words).
 */
interface IsrcLyricsProvider : LyricsProvider {
    /** As [LyricsProvider.fetch], matching on [isrc] when it is known. */
    suspend fun fetch(song: Song, track: String, artist: String, isrc: String?): Lyrics?

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? = fetch(song, track, artist, null)
}

internal const val LYRICS_USER_AGENT = "MusicSM/1.0 (Android)"

/**
 * GETs [url] as text, or null on any HTTP or network failure. Cancelling the caller cancels the
 * call itself, so a slow source is dropped the moment a better one has answered.
 */
internal suspend fun OkHttpClient.getText(url: String, headers: Map<String, String> = emptyMap()): String? {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", LYRICS_USER_AGENT)
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()
    return suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.use { if (it.isSuccessful) runCatching { it.body?.string() }.getOrNull() else null }
                if (cont.isActive) cont.resume(body)
            }
        })
    }
}

/** Splits plain lyrics into untimed lines. */
internal fun plainLyricLines(text: String): List<LyricLine> =
    text.replace("\r\n", "\n").trim().split("\n").map { LyricLine(timeMs = null, text = it.trim()) }
