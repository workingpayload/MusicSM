package com.example.musicsm.playback

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.musicsm.domain.repository.MusicRepository
import kotlinx.coroutines.runBlocking

/**
 * Rewrites a placeholder `ytstream:<videoId>` [DataSpec] into a fresh, directly-playable audio
 * URL just before ExoPlayer loads it. Runs on the player's loader thread (never main), so the
 * blocking [runBlocking] extraction is acceptable here.
 */
@UnstableApi
class StreamUrlResolver(
    private val repository: MusicRepository,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != MediaItemMapper.SCHEME) return dataSpec
        val videoId = dataSpec.uri.schemeSpecificPart
        val stream = runBlocking { repository.resolveStream(videoId) }
        return dataSpec.withUri(stream.url.toUri())
    }
}
