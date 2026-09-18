package com.example.musicsm.data.source.youtube

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.source.MusicSource
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * YouTube-backed [MusicSource] using NewPipeExtractor. All calls are blocking; the repository
 * layer moves them to the IO dispatcher. NewPipe must be initialized (see MusicSmApp).
 */
@Singleton
class NewPipeMusicSource @Inject constructor() : MusicSource {

    private val youtube get() = ServiceList.YouTube

    override suspend fun homeFeed(): HomeFeed {
        val sections = HOME_SHELVES.mapNotNull { (title, query) ->
            runCatching { searchSongs(query, limit = 10) }
                .getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { songs -> HomeSection(title, songs.map { HomeItem.SongItem(it) }) }
        }
        return HomeFeed(sections)
    }

    override suspend fun search(query: String): SearchResults {
        if (query.isBlank()) return SearchResults()
        val songs = runCatching { searchSongs(query, limit = 20) }.getOrDefault(emptyList())
        val albums = runCatching { searchAlbums(query, limit = 12) }.getOrDefault(emptyList())
        val artists = runCatching { searchArtists(query, limit = 12) }.getOrDefault(emptyList())
        return SearchResults(songs = songs, albums = albums, artists = artists)
    }

    override suspend fun album(id: String): Album {
        // [id] is the YouTube playlist URL for the album.
        val info = PlaylistInfo.getInfo(youtube, id)
        val songs = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
        return Album(
            id = id,
            title = info.name.orEmpty(),
            artist = info.uploaderName.orEmpty(),
            artworkUrl = bestThumbnail(info.thumbnails),
            songs = songs,
        )
    }

    override suspend fun artist(id: String): Artist {
        // [id] is the artist name; channel tabs are unreliable, so the page is assembled from
        // music searches. A keyword search matches the *title* as well as the uploader, which is
        // how covers and "best of" compilations used to leak in — everything is therefore filtered
        // down to uploads actually credited to this artist.
        val candidates = runCatching {
            searchItems(id, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, ARTIST_SCAN_LIMIT, ARTIST_MAX_PAGES)
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toSongOrNull() }
        }.getOrDefault(emptyList())

        val (credited, rest) = candidates.partition { ArtistMatching.matches(it.artist, id) }
        // Fall back to the unfiltered results rather than showing an empty page: some artists only
        // ever appear under a label channel, which no uploader match can recognise.
        val songs = credited.ifEmpty { rest }
            .distinctBy { it.id }
            // The same track is often up as both "Artist" and "Artist - Topic".
            .distinctBy { ArtistMatching.normalize(it.title) }
            .take(ARTIST_SONG_LIMIT)

        val albums = runCatching {
            searchItems(id, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, ARTIST_ALBUM_SCAN, pages = 1)
                .filterIsInstance<PlaylistInfoItem>()
                .filter { ArtistMatching.matches(it.uploaderName, id) }
                .map { it.toAlbum() }
                .distinctBy { it.id }
                .take(ARTIST_ALBUM_LIMIT)
        }.getOrDefault(emptyList())

        // The artist's own channel has a real avatar and a subscriber count; a video thumbnail is
        // only a fallback.
        val channel = runCatching {
            searchItems(id, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS, limit = 3, pages = 1)
                .filterIsInstance<ChannelInfoItem>()
                .firstOrNull { ArtistMatching.matches(it.name, id) }
        }.getOrNull()

        return Artist(
            id = id,
            name = channel?.name?.takeIf { it.isNotBlank() } ?: id,
            artworkUrl = channel?.let { bestThumbnail(it.thumbnails) }
                ?: songs.firstOrNull()?.artworkUrl,
            subscribers = channel?.subscriberCount?.takeIf { it >= 0 }?.let(::formatSubscribers),
            topSongs = songs,
            albums = albums,
        )
    }

    override suspend fun playlist(id: String): Playlist {
        val info = PlaylistInfo.getInfo(youtube, id)
        val songs = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
        return Playlist(
            id = id,
            name = info.name.orEmpty(),
            artworkUrl = bestThumbnail(info.thumbnails),
            songs = songs,
        )
    }

    override suspend fun trending(limit: Int): List<Song> {
        // Prefer YouTube's music-specific trending kiosk; fall back to the default trending kiosk.
        val extractor = runCatching { youtube.kioskList.getExtractorById("trending_music", null) }
            .getOrElse { youtube.kioskList.defaultKioskExtractor }
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
            .take(limit)
    }

    override suspend fun relatedTo(songId: String): List<Song> {
        val info = StreamInfo.getInfo(youtube, watchUrl(songId))
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
    }

    override suspend fun song(songId: String): Song {
        val info = StreamInfo.getInfo(youtube, watchUrl(songId))
        return Song(
            id = songId,
            title = info.name.orEmpty(),
            artist = info.uploaderName.orEmpty(),
            artworkUrl = bestThumbnail(info.thumbnails),
            durationMs = if (info.duration > 0) info.duration * 1000 else 0L,
        )
    }

    override suspend fun resolveStream(songId: String): PlayableStream {
        val info = StreamInfo.getInfo(youtube, watchUrl(songId))
        // Highest-bitrate audio: prefer a directly-playable progressive stream, else any with a URL.
        val playable = info.audioStreams.filter { !it.content.isNullOrEmpty() }
        val audio = playable
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull(AudioStream::getAverageBitrate)
            ?: playable.maxByOrNull(AudioStream::getAverageBitrate)
            ?: error("No audio stream for $songId")
        return PlayableStream(
            url = audio.content,
            mimeType = audio.format?.mimeType,
            bitrate = audio.averageBitrate,
            expiresAtMs = System.currentTimeMillis() + STREAM_TTL_MS,
        )
    }

    // --- internals ---------------------------------------------------------

    private fun searchSongs(query: String, limit: Int): List<Song> =
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }

    private fun searchAlbums(query: String, limit: Int): List<Album> =
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, limit)
            .filterIsInstance<PlaylistInfoItem>()
            .map { it.toAlbum() }

    private fun PlaylistInfoItem.toAlbum() = Album(
        id = url,
        title = name.orEmpty(),
        artist = uploaderName.orEmpty(),
        artworkUrl = bestThumbnail(thumbnails),
    )

    private fun searchArtists(query: String, limit: Int): List<Artist> =
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS, limit)
            .filterIsInstance<ChannelInfoItem>()
            .map { item ->
                Artist(
                    id = item.name.orEmpty().ifBlank { item.url },
                    name = item.name.orEmpty(),
                    artworkUrl = bestThumbnail(item.thumbnails),
                )
            }

    /**
     * Runs a search and collects up to [limit] items, following at most [pages] result pages.
     *
     * A single page is ~20 items, which is not a discography. Paging matters most for the artist
     * screen, where the uploader filter discards a large share of what comes back.
     */
    private fun searchItems(
        query: String,
        contentFilter: String,
        limit: Int,
        pages: Int = 1,
    ): List<InfoItem> {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(contentFilter), "")
        val extractor = youtube.getSearchExtractor(handler)
        extractor.fetchPage()

        var page = extractor.initialPage
        val items = ArrayList<InfoItem>(page.items)
        var fetched = 1
        while (items.size < limit && fetched < pages && page.hasNextPage()) {
            // A failed continuation is not fatal — keep whatever has already been collected.
            page = runCatching { extractor.getPage(page.nextPage) }.getOrNull() ?: break
            items += page.items
            fetched++
        }
        return items.take(limit)
    }

    private fun StreamInfoItem.toSongOrNull(): Song? {
        val videoId = runCatching { youtube.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return Song(
            id = videoId,
            title = name.orEmpty(),
            artist = uploaderName.orEmpty(),
            artworkUrl = bestThumbnail(thumbnails),
            durationMs = if (duration > 0) duration * 1000 else 0L,
        )
    }

    private fun bestThumbnail(images: List<Image>?): String? {
        val url = images?.maxByOrNull { it.height.takeIf { h -> h > 0 } ?: it.width }?.url
            ?: images?.lastOrNull()?.url
        return url?.let(::upscaleThumbnail)
    }

    /**
     * YouTube thumbnails are often served small (blurry when shown large). The Google image CDN
     * URLs are resizable, so request a larger square; i.ytimg URLs get bumped to hqdefault.
     */
    private fun upscaleThumbnail(url: String): String = when {
        "googleusercontent.com" in url || "ggpht.com" in url ->
            "${url.substringBefore("=")}=w1080-h1080-l90-rj"
        "i.ytimg.com/vi/" in url ->
            url.replace(Regex("/[^/]+\\.jpg"), "/hqdefault.jpg")
        else -> url
    }

    private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    /** "1234567" reads as nothing across a room; "1.2M" does. */
    private fun formatSubscribers(count: Long): String {
        fun scaled(value: Double, suffix: String): String {
            val rounded = if (value >= 100) value.roundToInt().toString()
            else String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")
            return rounded + suffix
        }
        return when {
            count >= 1_000_000_000L -> scaled(count / 1e9, "B")
            count >= 1_000_000L -> scaled(count / 1e6, "M")
            count >= 1_000L -> scaled(count / 1e3, "K")
            else -> count.toString()
        }
    }

    companion object {
        private const val STREAM_TTL_MS = 5 * 60 * 60 * 1000L // ~5h; googlevideo URLs expire ~6h

        // Artist page: scan wide because the uploader filter throws a lot away, then trim.
        private const val ARTIST_SCAN_LIMIT = 80
        private const val ARTIST_MAX_PAGES = 4
        private const val ARTIST_SONG_LIMIT = 50
        private const val ARTIST_ALBUM_SCAN = 20
        private const val ARTIST_ALBUM_LIMIT = 12

        // Curated genre/mood shelves. "Trending now" is served separately from the real
        // trending_music kiosk (see [trending]).
        private val HOME_SHELVES = listOf(
            "Chill vibes" to "chill lofi beats to relax",
            "Workout energy" to "workout motivation hype songs",
            "Focus flow" to "focus instrumental study music",
            "Party starters" to "party dance hits",
            "Throwbacks" to "2000s throwback hits",
        )
    }
}
