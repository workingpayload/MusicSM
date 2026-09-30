package com.example.musicsm.data.importer

import com.example.musicsm.data.spotify.extractSpotifyPlaylistId

/** Where a pasted playlist link points. */
internal sealed interface ImportLink {
    data class Spotify(val playlistId: String) : ImportLink

    /** [storefront] is the country the link is for ("us", "in"), which decides the catalog. */
    data class AppleMusic(val storefront: String, val playlistId: String) : ImportLink

    /** A YouTube or YouTube Music playlist, by its list id. */
    data class YouTube(val playlistId: String) : ImportLink
}

/**
 * Works out which service a pasted playlist link is from. Takes a bare link, text with a link in
 * it (as share sheets send), or a bare playlist id. Null when it isn't a playlist we can read.
 */
internal fun parseImportLink(raw: String): ImportLink? {
    val text = raw.trim()
    val link = URL_IN_TEXT.find(text)?.value ?: text
    APPLE_PLAYLIST.find(link)?.let { match ->
        val storefront = match.groupValues[1].ifEmpty { DEFAULT_STOREFRONT }.lowercase()
        return ImportLink.AppleMusic(storefront, match.groupValues[2])
    }
    if (YOUTUBE_HOST.containsMatchIn(link)) {
        // A watch link opened from inside a playlist carries the playlist too.
        val id = YOUTUBE_LIST_PARAM.find(link)?.groupValues?.get(1)
            ?: YOUTUBE_BROWSE.find(link)?.groupValues?.get(1)
        return id?.let(ImportLink::YouTube)
    }
    if (SPOTIFY.containsMatchIn(link)) return extractSpotifyPlaylistId(link)?.let(ImportLink::Spotify)
    if ("://" in link) return null
    return when {
        APPLE_ID.matches(link) -> ImportLink.AppleMusic(DEFAULT_STOREFRONT, link)
        SPOTIFY_ID.matches(link) -> ImportLink.Spotify(link)
        YOUTUBE_LIST_ID.matches(link) -> ImportLink.YouTube(link.removePrefix("VL"))
        else -> extractSpotifyPlaylistId(link)?.let(ImportLink::Spotify)
    }
}

internal fun isRadioMix(playlistId: String): Boolean =
    playlistId.startsWith("RD") && !playlistId.startsWith("RDCLAK5uy_")

private const val DEFAULT_STOREFRONT = "us"
private val URL_IN_TEXT = Regex("""(?:https?://|spotify:)\S+""")
private val APPLE_PLAYLIST =
    Regex("""(?i)apple\.com/(?:([a-z]{2})/)?playlist/(?:[^/?#\s]+/)?(pl\.[A-Za-z0-9-]+)""")
private val APPLE_ID = Regex("""pl\.[A-Za-z0-9-]{6,}""")
private val YOUTUBE_HOST = Regex("""(?i)(?:^|[/.])(?:youtube\.com|youtu\.be)(?:[/?#:]|$)""")
private val YOUTUBE_LIST_PARAM = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
private val YOUTUBE_BROWSE = Regex("""/browse/VL([A-Za-z0-9_-]+)""")
private val YOUTUBE_LIST_ID = Regex("""(?:VL)?(?:PL|OLAK5uy_|RDCLAK5uy_|RD|UU|FL)[A-Za-z0-9_-]{10,}""")
private val SPOTIFY = Regex("""(?i)spotify\.com|spotify:""")
private val SPOTIFY_ID = Regex("""[A-Za-z0-9]{22}""")
