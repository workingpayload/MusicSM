package com.example.musicsm.data.repository

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import com.example.musicsm.domain.model.AlbumAudio
import com.example.musicsm.domain.model.LyricsSyncResult
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LyricsSyncRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.playback.MediaItemMapper
import com.example.musicsm.playback.StreamUrlResolver
import com.example.musicsm.playback.mix.PcmSnippetDecoder
import com.example.musicsm.playback.sync.AudioAlignment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Decodes stretches of the playing track and of its album audio to 10 ms loudness frames and
 * lines them up with [AudioAlignment]. Reads through the song cache without writing to it, so a
 * track already cached is read from disk and the album audio doesn't take up cache space.
 */
@UnstableApi
@Singleton
class LyricsSyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: SimpleCache,
    private val music: MusicRepository,
    private val downloads: DownloadRepository,
) : LyricsSyncRepository {

    private val decoder by lazy { PcmSnippetDecoder(readOnlySource()) }

    override suspend fun measure(song: Song, positionMs: Long, durationMs: Long): LyricsSyncResult =
        withContext(Dispatchers.IO) {
            if (MediaItemMapper.isLocal(song.id)) return@withContext LyricsSyncResult.NoAlbumAudio
            val album = try {
                music.albumAudio(song.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return@withContext LyricsSyncResult.Failed
            }
            when (album) {
                AlbumAudio.Same -> LyricsSyncResult.AlreadyAlbumAudio
                AlbumAudio.Unknown -> LyricsSyncResult.NoAlbumAudio
                is AlbumAudio.Other -> try {
                    align(song, album.song, positionMs, durationMs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "Couldn't measure ${song.id} against ${album.song.id}", failure)
                    LyricsSyncResult.Failed
                }
            }
        }

    private suspend fun align(song: Song, albumSong: Song, positionMs: Long, durationMs: Long): LyricsSyncResult {
        val starts = AudioAlignment.windowStarts(positionMs, durationMs)
        if (starts.isEmpty()) return LyricsSyncResult.NoMatch
        // Album time = playing time - offset, so each window's match lies within
        // [start - MAX_OFFSET, start + WINDOW - MIN_OFFSET] of the album audio.
        val referenceStart = (starts.first() - AudioAlignment.MAX_OFFSET_MS)
            .coerceAtLeast(0L)
            .let { it - it % AudioAlignment.HOP_MS }
        val referenceEnd = starts.last() + AudioAlignment.WINDOW_MS - AudioAlignment.MIN_OFFSET_MS

        val (targets, reference) = coroutineScope {
            val item = MediaItemMapper.toMediaItem(song)
            val targets = starts.map { start -> async { energy(item, start, start + AudioAlignment.WINDOW_MS) } }
            val reference = async { energy(MediaItemMapper.toMediaItem(albumSong), referenceStart, referenceEnd) }
            targets.map { it.await() } to reference.await()
        }
        if (reference == null || targets.any { it == null }) return LyricsSyncResult.Failed

        val referenceOnsets = AudioAlignment.onsets(reference)
        val matches = starts.zip(targets.filterNotNull()) { start, energy ->
            AudioAlignment.align(
                target = AudioAlignment.onsets(energy),
                targetStartMs = start,
                reference = referenceOnsets,
                referenceStartMs = referenceStart,
                minOffsetMs = AudioAlignment.MIN_OFFSET_MS,
                maxOffsetMs = AudioAlignment.MAX_OFFSET_MS,
            )
        }
        val offset = AudioAlignment.decide(matches)
        Log.i(TAG, "${song.id} vs album ${albumSong.id}: $matches -> $offset")
        return offset?.let { LyricsSyncResult.Measured(it) } ?: LyricsSyncResult.NoMatch
    }

    /**
     * Mean-square loudness per [AudioAlignment.HOP_MS] frame of [item] from [startMs] to [endMs],
     * decoded in chunks the decoder accepts. Null if too little of it could be decoded.
     */
    private suspend fun energy(item: MediaItem, startMs: Long, endMs: Long): FloatArray? {
        val hopMs = AudioAlignment.HOP_MS
        val frames = FloatArray(((endMs - startMs) / hopMs).toInt())
        var written = 0
        var chunkStart = startMs
        while (chunkStart < endMs) {
            val chunkEnd = minOf(chunkStart + CHUNK_MS, endMs)
            val snippet = decoder.decode(item, chunkStart, chunkEnd) ?: break
            val hop = snippet.sampleRate * hopMs / 1000
            if (hop <= 0) break
            val first = ((snippet.startMs - startMs) / hopMs).roundToInt()
            val count = snippet.samples.size / hop
            for (f in 0 until count) {
                val index = first + f
                if (index !in frames.indices) continue
                var sum = 0f
                val base = f * hop
                for (i in base until base + hop) sum += snippet.samples[i] * snippet.samples[i]
                frames[index] = sum / hop
                written++
            }
            // The track ended inside this chunk.
            if (snippet.startMs + count * hopMs < chunkEnd - END_SLACK_MS) break
            chunkStart = chunkEnd
        }
        return frames.takeIf { written >= frames.size / 2 }
    }

    private fun readOnlySource(): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloaderImpl.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val resolving = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, http),
            StreamUrlResolver(music, downloads),
        )
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolving)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private companion object {
        const val TAG = "LyricsSync"

        /** Within the decoder's own 45 s limit. */
        const val CHUNK_MS = 30_000L

        /** A chunk this much short of its end means the track itself ended there. */
        const val END_SLACK_MS = 1_000L
    }
}
