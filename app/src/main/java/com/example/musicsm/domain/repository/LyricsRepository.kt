package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.Song

/** Fetches lyrics for a track. Returns null when none are found. */
interface LyricsRepository {
    suspend fun forSong(song: Song): Lyrics?
}
