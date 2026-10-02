package com.example.musicsm.data.source.youtube

/**
 * Picks the audio stream to play from YouTube's offered formats.
 *
 * AAC (M4A) is preferred over Opus even though Opus is usually offered at a higher bitrate.
 * Opus is decoded by the platform's `c2.android.opus.decoder`, and at least one OS update
 * (Samsung, Android 17) ships a build of it that returns digital silence: the track plays, the
 * position advances, AudioFlinger reports a healthy track at full volume, and nothing is
 * audible on any output. AAC is the most widely exercised decoder on Android, and 128 kbps AAC
 * is perceptually very close to YouTube's ~160 kbps Opus for music.
 *
 * Only progressive streams are considered: the player builds a progressive media source for every
 * track before the URL is resolved, so an HLS or DASH manifest would fail to load and the track
 * would be skipped. Within each tier the highest bitrate wins. Opus and other formats remain as a
 * fallback so a video that offers no AAC still plays.
 */
internal fun <T> pickAudioStream(
    streams: List<T>,
    isAac: (T) -> Boolean,
    isProgressive: (T) -> Boolean,
    bitrate: (T) -> Int,
): T? {
    val playable = streams.filter(isProgressive)
    return playable.filter(isAac).maxByOrNull(bitrate) ?: playable.maxByOrNull(bitrate)
}

/**
 * Picks a muxed (video + audio) stream to play when YouTube offers no audio-only stream.
 *
 * Made-for-kids videos, and every video under YouTube's SABR enforcement, often come with only
 * the 360p MP4 (itag 18). Its AAC audio track plays fine; the player has video tracks disabled.
 * Progressive AAC (MP4) is preferred, then the smallest resolution, to keep the download small.
 */
internal fun <T> pickMuxedStream(
    streams: List<T>,
    isAac: (T) -> Boolean,
    isProgressive: (T) -> Boolean,
    height: (T) -> Int,
): T? {
    fun List<T>.smallest(): T? = minByOrNull { height(it).takeIf { h -> h > 0 } ?: Int.MAX_VALUE }

    val playable = streams.filter(isProgressive)
    return playable.filter(isAac).smallest() ?: playable.smallest()
}
