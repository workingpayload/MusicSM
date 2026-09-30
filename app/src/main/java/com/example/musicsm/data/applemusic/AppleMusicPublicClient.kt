package com.example.musicsm.data.applemusic

import com.example.musicsm.data.importer.ImportedPlaylist
import com.example.musicsm.data.importer.ImportedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a **public** Apple Music playlist from its share link, without the Apple Music API (which
 * needs a developer token). The web page ships the playlist as JSON for its own first render;
 * that carries the name, cover and tracks (title, artist, length). Apple lets a shared playlist
 * show at most 300 songs, and the page carries exactly those.
 */
@Singleton
class AppleMusicPublicClient @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetchPlaylist(storefront: String, playlistId: String): ImportedPlaylist = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://music.apple.com/$storefront/playlist/$playlistId")
            .header("User-Agent", BROWSER_UA)
            .header("Accept-Language", "en-US,en;q=0.8")
            .get()
            .build()
        val html = client.newCall(request).execute().use { resp ->
            if (resp.code == 404) error("Apple Music couldn't find that playlist (is it public?)")
            if (!resp.isSuccessful) error("Apple Music returned ${resp.code} for that link")
            resp.body?.string().orEmpty()
        }
        parseAppleMusicPlaylist(html) ?: error("Couldn't read that playlist (is it public?)")
    }

    private companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

/**
 * The playlist in an Apple Music playlist page's `serialized-server-data` JSON, or null if the
 * page has none (not a playlist, or not public). Internal so it can be tested on a saved page.
 */
internal fun parseAppleMusicPlaylist(html: String): ImportedPlaylist? {
    val open = SERVER_DATA.find(html) ?: return null
    val start = open.range.last + 1
    val end = html.indexOf("</script>", start).takeIf { it >= 0 } ?: return null
    val root = runCatching { Json.parseToJsonElement(html.substring(start, end)) }.getOrNull() ?: return null
    // {"data": [{"intent": …, "data": {"sections": […]}}]}; older pages were the bare array.
    val pages = (root as? JsonObject)?.get("data") as? JsonArray ?: root as? JsonArray ?: return null
    val sections = pages.firstNotNullOfOrNull { it.obj("data")?.get("sections") as? JsonArray } ?: return null
    fun items(kind: String): List<JsonObject> = sections
        .firstOrNull { it.obj()?.string("itemKind") == kind }
        ?.obj()?.objects("items")
        .orEmpty()

    val tracks = items("trackLockup").mapNotNull { item ->
        val title = item.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val artist = item.string("artistName")
            ?: (item["subtitleLinks"] as? JsonArray)?.mapNotNull { it.obj()?.string("title") }?.joinToString(", ")
            ?: ""
        ImportedTrack(title, artist, (item["duration"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }
    if (tracks.isEmpty()) return null
    val header = items("containerDetailHeaderLockup").firstOrNull()
    // Artwork is a URL template: {w}x{h} size, {c} crop, {f} format.
    val cover = header?.obj("artwork")?.obj("dictionary")?.string("url")
        ?.replace("{w}", COVER_SIZE)?.replace("{h}", COVER_SIZE)?.replace("{c}", "bb")?.replace("{f}", "jpg")
    return ImportedPlaylist(
        name = header?.string("title")?.takeIf { it.isNotBlank() } ?: "Imported playlist",
        coverUrl = cover,
        tracks = tracks,
    )
}

private fun JsonElement.obj(): JsonObject? = this as? JsonObject

private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private val SERVER_DATA = Regex("""<script[^>]*\bid="serialized-server-data"[^>]*>""")
private const val COVER_SIZE = "600"
