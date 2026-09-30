package com.example.musicsm.data.repository

import android.util.Log
import com.example.musicsm.data.applemusic.AppleMusicPublicClient
import com.example.musicsm.data.importer.ImportLink
import com.example.musicsm.data.importer.ImportedPlaylist
import com.example.musicsm.data.importer.ImportedTrack
import com.example.musicsm.data.importer.isRadioMix
import com.example.musicsm.data.importer.parseImportLink
import com.example.musicsm.data.spotify.SpotifyPublicClient
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.ImportResult
import com.example.musicsm.domain.repository.ImportState
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.PlaylistImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Imports a public Spotify, Apple Music or YouTube / YouTube Music playlist into a new local
 * playlist. YouTube tracks are YouTube videos already and are saved as they are; the others are
 * looked up on YouTube by artist and title, a few at a time.
 */
@Singleton
class PlaylistImportRepositoryImpl @Inject constructor(
    private val spotify: SpotifyPublicClient,
    private val appleMusic: AppleMusicPublicClient,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
) : PlaylistImportRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    override val state: StateFlow<ImportState> = _state.asStateFlow()

    override fun start(link: String): Boolean {
        if (!running.compareAndSet(false, true)) return false
        _state.value = ImportState.Running(0, 0)
        scope.launch {
            try {
                val result = importFromLink(link) { done, total ->
                    _state.value = ImportState.Running(done, total)
                }
                _state.value = ImportState.Finished(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "import failed for $link", failure)
                _state.value = ImportState.Failed(failure.message)
            } finally {
                running.set(false)
            }
        }
        return true
    }

    override fun acknowledge() {
        if (_state.value !is ImportState.Running) {
            _state.value = ImportState.Idle
        }
    }

    private suspend fun importFromLink(
        link: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportResult =
        when (val source = parseImportLink(link) ?: error(NOT_A_PLAYLIST)) {
            is ImportLink.YouTube -> importYouTube(source.playlistId, onProgress)
            is ImportLink.Spotify -> importMatched(spotify.fetchPlaylist(source.playlistId), onProgress)
            is ImportLink.AppleMusic ->
                importMatched(appleMusic.fetchPlaylist(source.storefront, source.playlistId), onProgress)
        }

    private suspend fun importYouTube(playlistId: String, onProgress: (Int, Int) -> Unit): ImportResult {
        val playlist = try {
            val maxTracks = if (isRadioMix(playlistId)) MAX_MIX_TRACKS else MAX_YOUTUBE_TRACKS
            musicRepository.fullPlaylist(playlistId, maxTracks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IllegalStateException("Couldn't read that YouTube playlist (is it public?)", failure)
        }
        val songs = playlist.songs.distinctBy { it.id }
        if (songs.isEmpty()) error("That YouTube playlist is empty or private")
        val name = playlist.name.ifBlank { DEFAULT_NAME }
        Log.d(TAG, "YouTube '$name': ${songs.size} tracks")
        val id = libraryRepository.createPlaylist(name)
        playlist.artworkUrl?.let { libraryRepository.setPlaylistArtwork(id, it) }
        songs.forEachIndexed { index, song ->
            libraryRepository.addToPlaylist(id, song)
            onProgress(index + 1, songs.size)
        }
        return ImportResult(id, name, songs.size, songs.size)
    }

    private suspend fun importMatched(playlist: ImportedPlaylist, onProgress: (Int, Int) -> Unit): ImportResult {
        val tracks = playlist.tracks
        if (tracks.isEmpty()) error("No tracks found in that playlist")
        Log.d(TAG, "'${playlist.name}': ${tracks.size} tracks")
        val id = libraryRepository.createPlaylist(playlist.name)
        playlist.coverUrl?.let { libraryRepository.setPlaylistArtwork(id, it) }
        var matched = 0
        var done = 0
        val addedSongIds = HashSet<String>()
        // A few searches at once, each batch saved in the playlist's own order.
        for (batch in tracks.chunked(PARALLEL_SEARCHES)) {
            val songs = coroutineScope { batch.map { async { find(it) } }.awaitAll() }
            for (song in songs) {
                if (song != null && addedSongIds.add(song.id)) {
                    libraryRepository.addToPlaylist(id, song)
                    matched++
                }
                onProgress(++done, tracks.size)
            }
        }
        return ImportResult(id, playlist.name, matched, tracks.size)
    }

    private suspend fun find(track: ImportedTrack): Song? = try {
        pickMatch(musicRepository.searchSongs(track.searchQuery), track.durationMs)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(TAG, "search failed for '${track.searchQuery}'", failure)
        null
    }

    private companion object {
        const val TAG = "PlaylistImport"
        const val NOT_A_PLAYLIST = "Paste a Spotify, Apple Music or YouTube playlist link"
        const val DEFAULT_NAME = "Imported playlist"

        /** YouTube's own limit for a playlist. */
        const val MAX_YOUTUBE_TRACKS = 5_000
        const val MAX_MIX_TRACKS = 50
        const val PARALLEL_SEARCHES = 4
    }
}

/**
 * Which search result to save for a track: among the top few, the first about as long as the
 * original (so the song wins over a live cut, an extended mix or a video edit), else the top one.
 */
internal fun pickMatch(results: List<Song>, durationMs: Long): Song? {
    if (durationMs <= 0L) return results.firstOrNull()
    return results.take(MATCH_CANDIDATES)
        .firstOrNull { it.durationMs > 0L && abs(it.durationMs - durationMs) <= MATCH_TOLERANCE_MS }
        ?: results.firstOrNull()
}

private const val MATCH_CANDIDATES = 5
private const val MATCH_TOLERANCE_MS = 10_000L
