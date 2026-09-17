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
        // [id] is the artist name; top songs come from a music search (channel tabs are unreliable).
        val songs = runCatching { searchSongs(id, limit = 20) }.getOrDefault(emptyList())
        return Artist(
            id = id,
            name = id,
            artworkUrl = songs.firstOrNull()?.artworkUrl,
            topSongs = songs,
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
            .map { item ->
                Album(
                    id = item.url,
                    title = item.name.orEmpty(),
                    artist = item.uploaderName.orEmpty(),
                    artworkUrl = bestThumbnail(item.thumbnails),
                )
            }

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

    private fun searchItems(query: String, contentFilter: String, limit: Int): List<InfoItem> {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(contentFilter), "")
        val extractor = youtube.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items.take(limit)
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

    companion object {
        private const val STREAM_TTL_MS = 5 * 60 * 60 * 1000L // ~5h; googlevideo URLs expire ~6h

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
