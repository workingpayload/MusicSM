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
 * Within each tier a directly-playable progressive stream beats a manifest-based one, then the
 * highest bitrate wins. Opus and other formats remain as a fallback so a video that offers no
 * AAC still plays.
 */
internal fun <T> pickAudioStream(
    streams: List<T>,
    isAac: (T) -> Boolean,
    isProgressive: (T) -> Boolean,
    bitrate: (T) -> Int,
): T? {
    fun List<T>.best(): T? =
        filter(isProgressive).maxByOrNull(bitrate) ?: maxByOrNull(bitrate)

    return streams.filter(isAac).best() ?: streams.best()
}
