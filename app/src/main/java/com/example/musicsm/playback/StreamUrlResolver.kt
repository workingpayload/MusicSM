package com.example.musicsm.playback

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Rewrites a placeholder `ytstream:<videoId>` [DataSpec] just before ExoPlayer loads it: if the
 * track is downloaded, points at the local file; otherwise resolves a fresh network audio URL.
 * Runs on the player's loader thread (never main), so the blocking [runBlocking] is acceptable.
 */
@UnstableApi
class StreamUrlResolver(
    private val repository: MusicRepository,
    private val downloadRepository: DownloadRepository,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != MediaItemMapper.SCHEME) return dataSpec
        val videoId = dataSpec.uri.schemeSpecificPart
        return runBlocking {
            val local = downloadRepository.localPath(videoId)
            if (local != null) {
                dataSpec.withUri(File(local).toUri())
            } else {
                dataSpec.withUri(repository.resolveStream(videoId).url.toUri())
            }
        }
    }
}
