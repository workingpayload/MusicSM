package com.example.musicsm.domain.model

/**
 * How a track relates to its release's own audio: the recording that lyrics databases time their
 * lyrics against. A music video often runs longer (intro, skits, outro), which shifts every line.
 */
sealed interface AlbumAudio {
    /** The track is the release's audio itself (or plays it in place of a restricted video). */
    data object Same : AlbumAudio

    /** The track is another cut of the song, such as a music video; [song] is the release's audio. */
    data class Other(val song: Song) : AlbumAudio

    /** No release audio could be identified for the track. */
    data object Unknown : AlbumAudio
}
