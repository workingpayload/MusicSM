package com.example.musicsm.data.repository

import com.example.innertube.InnerTube
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.model.Song
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music's own "Lyrics" tab. Never timed, but looked up by the exact video playing, so it
 * is the last resort that still shows *something* for tracks no synced database knows.
 */
@Singleton
class YouTubeMusicLyricsProvider @Inject constructor(
    private val innerTube: InnerTube,
) : LyricsProvider {

    override val source = LyricsSource.YOUTUBE_MUSIC

    override suspend fun fetch(song: Song, track: String, artist: String): Lyrics? {
        if (!VIDEO_ID.matches(song.id)) return null
        val text = innerTube.lyrics(song.id) ?: return null
        return Lyrics(synced = false, lines = plainLyricLines(text), source = source)
    }

    private companion object {
        val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")
    }
}
