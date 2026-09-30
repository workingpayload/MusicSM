package com.example.musicsm.playback.mix

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.lang.ref.WeakReference

/**
 * The player's media source factory, wrapping every item in a [MixStartSource] so Mix can tell
 * the next item where to start when the player moves on to it by itself.
 */
@UnstableApi
class MixMediaSourceFactory(private val delegate: MediaSource.Factory) : MediaSource.Factory {

    private val live = mutableListOf<WeakReference<MixStartSource>>()

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val source = MixStartSource(delegate.createMediaSource(mediaItem), mediaItem.mediaId)
        synchronized(live) {
            live.removeAll { it.get() == null }
            live += WeakReference(source)
        }
        return source
    }

    /** Every live source for [mediaId] (a track can be queued more than once). */
    fun sourcesFor(mediaId: String): List<MixStartSource> = synchronized(live) {
        live.mapNotNull { it.get() }.filter { it.mediaId == mediaId }
    }
}

/**
 * A playlist item whose default start position can be moved. The player starts an item there when
 * it advances to it by itself, so a Mix can have the next track continue exactly where its
 * already-mixed-in opening ends - gaplessly, with no seek (which would leave a gap) and with the
 * track's own timeline (so position and lyrics stay right). Seeks to a position are unaffected.
 */
@UnstableApi
class MixStartSource(child: MediaSource, val mediaId: String) : WrappingMediaSource(child) {

    private var startUs = 0L
    private var childTimeline: Timeline? = null

    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        childTimeline = newTimeline
        publish()
    }

    /** Must be called on the player's playback thread. */
    fun setStartUs(us: Long) {
        if (us == startUs) return
        startUs = us
        publish()
    }

    private fun publish() {
        val timeline = childTimeline ?: return
        refreshSourceInfo(if (startUs > 0) StartTimeline(timeline, startUs) else timeline)
    }
}

private class StartTimeline(timeline: Timeline, private val startUs: Long) : ForwardingTimeline(timeline) {
    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        super.getWindow(windowIndex, window, defaultPositionProjectionUs)
        if (window.durationUs == C.TIME_UNSET || startUs < window.durationUs) window.defaultPositionUs = startUs
        return window
    }
}
