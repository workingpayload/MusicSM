package com.example.musicsm.wear

/**
 * Wearable Data Layer protocol shared by the phone and watch apps. Both modules ship an
 * identical copy — keep them in sync.
 *
 * - The phone publishes a `DataItem` at [PATH_STATE] whenever the current track changes.
 * - The watch sends transport commands as messages on [PATH_COMMAND].
 * - The watch searches with a query on [PATH_SEARCH]; the phone replies with a JSON array of
 *   songs on [PATH_SEARCH_RESULTS], and the watch asks to play one (JSON song) on [PATH_PLAY].
 */
object WearContract {
    const val PATH_STATE = "/musicsm/now_playing"
    const val PATH_COMMAND = "/musicsm/command"
    const val PATH_SEARCH = "/musicsm/search"
    const val PATH_SEARCH_RESULTS = "/musicsm/results"
    const val PATH_PLAY = "/musicsm/play"
    const val PATH_LYRICS = "/musicsm/lyrics"
    const val PATH_LYRICS_RESULT = "/musicsm/lyrics_result"

    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_PLAYING = "playing"
    const val KEY_HAS_TRACK = "has_track"
    const val KEY_UPDATED_AT = "updated_at"
    // Playback position (ms) at publish time — the watch extrapolates from it to sync lyrics.
    const val KEY_POSITION = "position"

    // Song fields carried in the search-results and play payloads.
    const val KEY_ID = "id"
    const val KEY_ARTWORK = "artwork"
    const val KEY_DURATION = "duration"

    // Lyrics-result payload.
    const val KEY_SYNCED = "synced"
    const val KEY_LINES = "lines"
    const val KEY_LINE_TIME = "t"
    const val KEY_LINE_TEXT = "x"

    const val CMD_PLAY_PAUSE = "play_pause"
    const val CMD_NEXT = "next"
    const val CMD_PREVIOUS = "previous"

    /** Cap on songs returned to the watch — keeps the Data Layer message small and the list scannable. */
    const val SEARCH_RESULT_LIMIT = 20
}
