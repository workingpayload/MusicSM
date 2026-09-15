package com.example.musicsm.data.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A single track's search-friendly fields. */
data class SpotifyTrack(
    val title: String,
    val artist: String,
) {
    val searchQuery: String get() = "$artist $title".trim()
}

/** A public Spotify playlist read from its share link (name + cover + tracks). */
data class PublicPlaylist(
    val name: String,
    val coverUrl: String?,
    val tracks: List<SpotifyTrack>,
)

/**
 * Reads a **public** Spotify playlist from its share link without the Web API (which 403s for
 * Development-Mode apps). Fetches the embed page and parses the `__NEXT_DATA__` JSON that carries
 * the track list. Only works for public / "anyone with the link" playlists.
 */
@Singleton
class SpotifyPublicClient @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetchPlaylist(link: String): PublicPlaylist = withContext(Dispatchers.IO) {
        val id = extractPlaylistId(link) ?: error("That doesn't look like a Spotify playlist link")
        val html = fetchEmbed(id)
        val nextData = extractNextData(html) ?: error("Couldn't read the playlist (is it public?)")
        parse(nextData)
    }

    private fun fetchEmbed(id: String): String {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/playlist/$id")
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Spotify returned ${resp.code} for that link")
            return body
        }
    }

    private fun extractNextData(html: String): JSONObject? {
        val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
        val end = html.indexOf("</script>", start).takeIf { it >= 0 } ?: return null
        return runCatching { JSONObject(html.substring(start, end)) }.getOrNull()
    }

    private fun parse(nextData: JSONObject): PublicPlaylist {
        val entity = nextData
            .optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?: error("Couldn't parse the playlist")

        val name = entity.optString("name").ifBlank { entity.optString("title").ifBlank { "Imported playlist" } }
        val cover = entity.optJSONObject("coverArt")?.optJSONArray("sources")?.let { srcs ->
            // Pick the largest source.
            (0 until srcs.length()).mapNotNull { srcs.optJSONObject(it) }
                .maxByOrNull { it.optInt("width") }
                ?.optString("url")
        }?.ifBlank { null } ?: entity.optJSONArray("images")?.optJSONObject(0)?.optString("url")?.ifBlank { null }
        val list = entity.optJSONArray("trackList") ?: error("No tracks found in that playlist")
        val tracks = buildList {
            for (i in 0 until list.length()) {
                val t = list.optJSONObject(i) ?: continue
                val title = t.optString("title").ifBlank { continue }
                val artist = t.optString("subtitle") // comma-separated artist names
                add(SpotifyTrack(title = title, artist = artist))
            }
        }
        return PublicPlaylist(name, cover, tracks)
    }

    private fun extractPlaylistId(link: String): String? {
        // Handles open.spotify.com/playlist/<id>?..., spotify:playlist:<id>, and bare ids.
        Regex("playlist[/:]([A-Za-z0-9]+)").find(link)?.let { return it.groupValues[1] }
        return link.trim().takeIf { it.matches(Regex("[A-Za-z0-9]{16,}")) }
    }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
