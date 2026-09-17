package com.example.musicsm.domain.model

/** Sort orders offered for song lists (liked songs, playlists, downloads). */
enum class SongSort {
    DEFAULT,
    TITLE,
    ARTIST,
    ALBUM,
    DURATION_SHORT,
    DURATION_LONG,
    ;

    companion object {
        fun fromName(value: String?): SongSort =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Applies [sort] to this list. [DEFAULT][SongSort.DEFAULT] keeps the source order, which is
 * already meaningful (recently added / playlist position).
 */
fun List<Song>.sortedFor(sort: SongSort): List<Song> = when (sort) {
    SongSort.DEFAULT -> this
    SongSort.TITLE -> sortedBy { it.title.lowercase() }
    SongSort.ARTIST -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    SongSort.ALBUM -> sortedWith(
        compareBy({ it.album?.lowercase() ?: "\uFFFF" }, { it.title.lowercase() }),
    )
    SongSort.DURATION_SHORT -> sortedBy { it.durationMs }
    SongSort.DURATION_LONG -> sortedByDescending { it.durationMs }
}
