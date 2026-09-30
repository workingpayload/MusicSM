package com.example.musicsm.data.source.youtube

import android.util.Log
import com.example.innertube.InnerTube
import com.example.innertube.model.YtAlbum
import com.example.innertube.model.YtArtist
import com.example.innertube.model.YtItem
import com.example.innertube.model.YtPlaylist
import com.example.innertube.model.YtSearchFilter
import com.example.innertube.model.YtShelf
import com.example.innertube.model.YtSong
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.AlbumAudio
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.source.MusicSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's [MusicSource]: YouTube Music for metadata, NewPipeExtractor for playable audio.
 *
 * The two providers are not interchangeable and are not treated as such. YouTube Music knows what
 * a *release* is — real albums with real track orders, artist pages, and above all the related-track
 * graph that makes recommendations feel chosen rather than searched for. NewPipeExtractor knows
 * none of that, but it is the piece that turns a video id into a URL that actually plays, and it
 * has a genuinely music-specific trending chart. So each is asked only for what it is good at.
 *
 * Every metadata call falls back to NewPipe when YouTube Music fails, because a degraded screen is
 * worth more than an error one; the fallback is silent by design. Stream resolution has no
 * fallback because it has no alternative.
 *
 * Ids are passed through the app unchanged, so this class dispatches on their *shape* — a
 * `MPREb_…` album or a `UC…` channel came from YouTube Music, whereas a URL or a bare artist name
 * came from NewPipe, and each must go back to the provider that can resolve it.
 */
@Singleton
class YouTubeMusicSource @Inject constructor(
    private val innerTube: InnerTube,
    private val newPipe: NewPipeMusicSource,
) : MusicSource {

    /** Age-restricted videos, and the audio-only version of the same song that plays instead. */
    private val audioVersions = ConcurrentHashMap<String, String>()

    /**
     * YouTube Music's own home feed, which is editorial rather than personalised while signed out.
     * That is the right input here: the app ranks and blends it against local listening history,
     * so what it needs from the network is a broad, fresh pool rather than someone else's guess.
     */
    override suspend fun homeFeed(): HomeFeed {
        val page = tryRemote { innerTube.home() } ?: return newPipe.homeFeed()
        val sections = page.shelves.mapNotNull { it.toSectionOrNull() }
        return if (sections.isEmpty()) newPipe.homeFeed() else HomeFeed(sections, page.continuation)
    }

    /**
     * The next batch of home shelves.
     *
     * There is no NewPipe fallback here: a failure part-way down an already-populated page should
     * simply stop the feed growing, not replace what the listener is looking at.
     */
    override suspend fun moreHomeShelves(continuation: String): HomeFeed {
        val page = tryRemote { innerTube.homeContinuation(continuation) } ?: return HomeFeed()
        return HomeFeed(page.shelves.mapNotNull { it.toSectionOrNull() }, page.continuation)
    }

    /**
     * Searches songs, albums, artists and (when [includeVideos]) videos concurrently.
     *
     * Each filter is a separate request, so they are issued together rather than in sequence; a
     * filtered search also returns one clean shelf of a known type, which is both cheaper to parse
     * and far more predictable than the mixed "top result" page. Songs are only released tracks;
     * what exists only as a YouTube upload (unreleased tracks, leaks, covers) is under videos.
     */
    override suspend fun search(query: String, includeVideos: Boolean): SearchResults {
        if (query.isBlank()) return SearchResults()

        val results = coroutineScope {
            val songs = async { tryRemote { innerTube.searchSongs(query) }.orEmpty() }
            val videos = if (includeVideos) {
                async { tryRemote { innerTube.search(query, YtSearchFilter.VIDEOS) }.orEmpty() }
            } else {
                null
            }
            val albums = async { tryRemote { innerTube.search(query, YtSearchFilter.ALBUMS) }.orEmpty() }
            val artists = async { tryRemote { innerTube.search(query, YtSearchFilter.ARTISTS) }.orEmpty() }
            SearchResults(
                songs = songs.await().map { it.toSong() },
                albums = albums.await().filterIsInstance<YtItem.Album>().map { it.album.toAlbum() },
                artists = artists.await().filterIsInstance<YtItem.Artist>().map { it.artist.toArtist() },
                videos = videos?.await().orEmpty().filterIsInstance<YtItem.Song>().map { it.song.toSong() },
            )
        }
        return if (results.isEmpty) newPipe.search(query, includeVideos) else results
    }

    override suspend fun searchSongs(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val songs = tryRemote { innerTube.searchSongs(query) }.orEmpty()
        return if (songs.isEmpty()) newPipe.searchSongs(query) else songs.map { it.toSong() }
    }

    /**
     * An album, or a playlist presented as one.
     *
     * The detail screen for a release is a cover, a credit line and a track list, which is also
     * exactly what a playlist is; the app therefore has one destination for both and the id
     * decides which provider call resolves it.
     */
    override suspend fun album(id: String): Album {
        if (id.isInnerTubePlaylistId()) return playlist(id).toAlbum()
        if (!id.isInnerTubeAlbumId()) return newPipe.album(id)
        val album = tryRemote { innerTube.album(id) } ?: return newPipe.album(id)
        return album.toAlbum()
    }

    override suspend fun artist(id: String): Artist {
        if (!id.isChannelId()) return newPipe.artist(id)
        val artist = tryRemote { innerTube.artist(id) } ?: return newPipe.artist(id)
        // An artist page with no music on it is a parse failure in all but name, and NewPipe's
        // name-based search can still produce something useful from the display name.
        if (artist.topSongs.isEmpty() && artist.albums.isEmpty()) {
            val fallbackKey = artist.name.ifBlank { id }
            return runCatching { newPipe.artist(fallbackKey) }.getOrNull() ?: artist.toArtist()
        }
        return artist.toArtist()
    }

    override suspend fun playlist(id: String): Playlist {
        if (!id.isInnerTubePlaylistId()) return newPipe.playlist(id)
        val playlist = tryRemote { innerTube.playlist(id) } ?: return newPipe.playlist(id)
        return playlist.toPlaylist()
    }

    override suspend fun fullPlaylist(id: String, maxTracks: Int): Playlist {
        if (id.isInnerTubePlaylistId()) {
            tryRemote { innerTube.playlist(id, maxTracks) }
                ?.takeIf { it.songs.isNotEmpty() }
                ?.let { list ->
                    return Playlist(
                        id = list.id,
                        name = list.title,
                        artworkUrl = YouTubeArtwork.resizeOrNull(list.thumbnailUrl, YouTubeArtwork.CANONICAL),
                        songs = list.songs.map { it.toSong() },
                    )
                }
        }
        // Lists YouTube Music can't browse (channel uploads, radio mixes) still read through NewPipe.
        return newPipe.fullPlaylist(id, maxTracks)
    }

    /** NewPipe's music trending kiosk is a real chart; YouTube Music has no browse id that matches it. */
    override suspend fun trending(limit: Int): List<Song> = newPipe.trending(limit)

    /**
     * Songs YouTube Music associates with [songId].
     *
     * This is the single biggest reason the module exists. NewPipe's related items come from the
     * video recommendation graph and drift into interviews, reaction videos and whatever else the
     * algorithm is pushing; YouTube Music's related shelf is music, by artists that actually sit
     * near this track.
     */
    override suspend fun relatedTo(songId: String): List<Song> {
        val related = tryRemote { innerTube.relatedSongs(songId) }
        return if (related.isNullOrEmpty()) newPipe.relatedTo(songId) else related.map { it.toSong() }
    }

    /** Metadata for a track; for an age-restricted video, from YouTube Music's player endpoint. */
    override suspend fun song(songId: String): Song = try {
        newPipe.song(songId)
    } catch (restricted: AgeRestrictedContentException) {
        tryRemote { innerTube.videoDetails(songId) }?.toSong() ?: throw restricted
    }

    /**
     * A stream for [songId]. YouTube won't play an age-restricted video without signing in (no
     * client gets around that any more), but the same song's audio-only version on YouTube Music
     * normally isn't restricted, so that one plays in its place.
     */
    override suspend fun resolveStream(songId: String): PlayableStream {
        audioVersions[songId]?.let { return newPipe.resolveStream(it) }
        return try {
            newPipe.resolveStream(songId)
        } catch (restricted: AgeRestrictedContentException) {
            val audio = audioVersionOf(songId) ?: throw restricted
            Log.i(TAG, "$songId is age-restricted; playing its audio version $audio")
            audioVersions[songId] = audio
            newPipe.resolveStream(audio)
        }
    }

    private suspend fun audioVersionOf(videoId: String): String? {
        val video = tryRemote { innerTube.videoDetails(videoId) } ?: return null
        return audioVersionFor(video)?.id
    }

    /**
     * Album audio is the recording lyrics are timed to. A restricted video already plays its
     * audio version in its place, so it counts as the album audio too.
     */
    override suspend fun albumAudio(songId: String): AlbumAudio {
        if (audioVersions.containsKey(songId)) return AlbumAudio.Same
        val video = tryRemote { innerTube.videoDetails(songId) } ?: return AlbumAudio.Unknown
        if (video.isAlbumAudio == true) return AlbumAudio.Same
        return audioVersionFor(video)?.let { AlbumAudio.Other(it) } ?: AlbumAudio.Unknown
    }

    /** The same song's album audio on YouTube Music, for [video] (see [pickAudioVersion]). */
    private suspend fun audioVersionFor(video: YtSong): Song? {
        val results = tryRemote { innerTube.searchSongs("${video.title} ${video.artistLine}") }.orEmpty()
        val candidates = results.filter { it.id != video.id }.map { it.toSong() }
        return pickAudioVersion(video.title, video.artistLine, candidates)
    }

    // --- fallback ----------------------------------------------------------

    /**
     * Runs a YouTube Music call, reporting failure as null so the caller can fall back.
     *
     * Cancellation is rethrown: a cancelled coroutine is not a provider failure, and swallowing it
     * here would keep work running after the screen that wanted it is gone.
     */
    private inline fun <T> tryRemote(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        null
    }

    // --- id shapes ---------------------------------------------------------

    private fun String.isInnerTubeAlbumId() = startsWith("MPREb")

    /** YouTube channel ids; NewPipe's artist "id" is a display name, which never matches this. */
    private fun String.isChannelId() = startsWith("UC") && length > 10 && "/" !in this

    private fun String.isInnerTubePlaylistId(): Boolean {
        if ("://" in this) return false
        val bare = removePrefix("VL")
        return PLAYLIST_ID_PREFIXES.any { bare.startsWith(it) }
    }

    // --- mapping -----------------------------------------------------------

    private fun YtShelf.toSectionOrNull(): HomeSection? {
        val cards = items.mapNotNull { it.toHomeItemOrNull() }
        return if (cards.isEmpty()) null else HomeSection(title, cards)
    }

    private fun YtItem.toHomeItemOrNull(): HomeItem? = when (this) {
        is YtItem.Song -> HomeItem.SongItem(song.toSong())
        is YtItem.Album -> HomeItem.AlbumItem(album.toAlbum())
        is YtItem.Artist -> HomeItem.ArtistItem(artist.toArtist())
        is YtItem.Playlist -> HomeItem.PlaylistItem(playlist.toPlaylist())
    }

    private fun YtSong.toSong(
        artistFallback: String? = null,
        albumFallback: String? = null,
    ): Song {
        val albumTitle = album?.title.nonBlankOrNull() ?: albumFallback.nonBlankOrNull()
        val artistName = artistLine.nonBlankOrNull()
            ?: artistFallback.nonBlankOrNull()
            ?: albumTitle.orEmpty()
        return Song(
            id = id,
            title = title,
            artist = artistName,
            album = albumTitle,
            artworkUrl = YouTubeArtwork.resizeOrNull(thumbnailUrl, YouTubeArtwork.CANONICAL),
            durationMs = durationMs,
        )
    }

    private fun YtAlbum.toAlbum(): Album {
        val albumArtist = artistLine.nonBlankOrNull()
            ?: songs.firstNotNullOfOrNull { it.artistLine.nonBlankOrNull() }
            ?: title
        return Album(
            id = id,
            title = title,
            artist = albumArtist,
            artworkUrl = YouTubeArtwork.resizeOrNull(thumbnailUrl, YouTubeArtwork.CANONICAL),
            year = year,
            songs = songs.map { it.toSong(artistFallback = albumArtist, albumFallback = title) },
        )
    }

    private fun YtArtist.toArtist(): Artist {
        val displayName = name.nonBlankOrNull().orEmpty()
        return Artist(
            id = id,
            name = displayName,
            artworkUrl = YouTubeArtwork.resizeOrNull(thumbnailUrl, YouTubeArtwork.CANONICAL),
            subscribers = subscribers?.substringBefore(' ')?.takeIf { it.isNotBlank() },
            topSongs = topSongs.map { it.toSong(artistFallback = displayName) }
                // The same song can be up more than once (on the single, the album, a compilation).
                .distinctBy { ArtistMatching.normalize(it.title).ifEmpty { it.id } }
                .take(ARTIST_SONG_LIMIT),
            albums = albums.map { it.toAlbum() },
        )
    }

    private fun YtPlaylist.toPlaylist() = Playlist(
        id = id,
        name = title,
        artworkUrl = YouTubeArtwork.resizeOrNull(thumbnailUrl, YouTubeArtwork.CANONICAL),
        songs = songs.map { it.toSong(albumFallback = title) },
    )

    /** A playlist has no release artist, so the credit line falls back to its first track's. */
    private fun Playlist.toAlbum(): Album {
        val albumArtist = songs.firstNotNullOfOrNull { it.artist.nonBlankOrNull() }
            ?: name.nonBlankOrNull().orEmpty()
        return Album(
            id = id,
            title = name,
            artist = albumArtist,
            artworkUrl = artworkUrl,
            songs = songs.map { it.withAlbumFallback(name, albumArtist) },
        )
    }

    private fun Song.withAlbumFallback(albumTitle: String, artistFallback: String): Song {
        val resolvedAlbum = album.nonBlankOrNull() ?: albumTitle.nonBlankOrNull()
        val resolvedArtist = artist.nonBlankOrNull()
            ?: artistFallback.nonBlankOrNull()
            ?: resolvedAlbum.orEmpty()
        return if (artist == resolvedArtist && album == resolvedAlbum) {
            this
        } else {
            copy(artist = resolvedArtist, album = resolvedAlbum)
        }
    }

    private fun String?.nonBlankOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "YouTubeMusicSource"

        /** How YouTube prefixes playlist ids: user, radio/mix, auto-generated, and uploads. */
        val PLAYLIST_ID_PREFIXES = listOf("PL", "RD", "OLAK5", "LM", "UU")

        /** Songs on an artist page; the same as the NewPipe-built page. */
        const val ARTIST_SONG_LIMIT = 50
    }
}
