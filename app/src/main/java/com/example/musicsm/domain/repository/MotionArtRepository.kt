package com.example.musicsm.domain.repository

import com.example.motionart.MotionArt
import com.example.musicsm.domain.model.Song

/**
 * Supplies the looping cover video some releases ship, when one exists.
 *
 * Coverage is patchy by nature, so callers should treat null as ordinary rather than exceptional
 * and keep showing the still cover.
 */
interface MotionArtRepository {

    /** The motion cover for [song], or null if the release has none or lookup failed. */
    suspend fun forSong(song: Song): MotionArt?
}
