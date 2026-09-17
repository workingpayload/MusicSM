package com.example.musicsm.wear

/**
 * Wearable Data Layer protocol shared by the phone and watch apps. Both modules ship an
 * identical copy — keep them in sync.
 *
 * - The phone publishes a `DataItem` at [PATH_STATE] whenever the current track changes.
 * - The watch sends transport commands as messages on [PATH_COMMAND].
 */
object WearContract {
    const val PATH_STATE = "/musicsm/now_playing"
    const val PATH_COMMAND = "/musicsm/command"

    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_PLAYING = "playing"
    const val KEY_HAS_TRACK = "has_track"
    const val KEY_UPDATED_AT = "updated_at"

    const val CMD_PLAY_PAUSE = "play_pause"
    const val CMD_NEXT = "next"
    const val CMD_PREVIOUS = "previous"
}
