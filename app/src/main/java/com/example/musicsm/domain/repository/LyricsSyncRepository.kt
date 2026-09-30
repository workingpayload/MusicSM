package com.example.musicsm.domain.repository

import com.example.musicsm.domain.model.LyricsSyncResult
import com.example.musicsm.domain.model.Song

/**
 * Measures how far a track runs from its album audio, the recording lyrics are timed to, by
 * listening to both. A music video's intro shifts every lyric line by its length; this finds it.
 */
interface LyricsSyncRepository {
    /** Measures [song] (length [durationMs]) near [positionMs] against its album audio. */
    suspend fun measure(song: Song, positionMs: Long, durationMs: Long): LyricsSyncResult
}
