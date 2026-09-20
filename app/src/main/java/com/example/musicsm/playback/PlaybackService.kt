package com.example.musicsm.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.musicsm.MainActivity
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground media playback service. Owns the [ExoPlayer] whose data source resolves YouTube
 * stream URLs on demand via [StreamUrlResolver]. Media3 renders the notification/lockscreen
 * controls automatically from the session.
 *
 * It's a [MediaLibraryService] rather than a plain `MediaSessionService` so Android Auto, Wear
 * and Assistant can browse the library (see [MusicLibraryCallback]).
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var repository: MusicRepository
    @Inject lateinit var downloadRepository: DownloadRepository
    @Inject lateinit var mediaCache: SimpleCache
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var nowPlayingPublisher: NowPlayingPublisher
    @Inject lateinit var audioEffects: AudioEffectsManager

    private var mediaSession: MediaLibrarySession? = null
    private var crossfadeController: CrossfadeController? = null
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Keeps the home-screen widget and Quick Settings tile in sync with the player. */
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                return
            }
            val song = player.currentMediaItem?.let(MediaItemMapper::toSong)
            nowPlayingPublisher.publish(song, player.isPlaying)
        }
    }

    // Recovery state for the error listener below.
    private var errorItemId: String? = null
    private var errorRetries = 0

    /**
     * Recovers from load errors instead of dead-stopping on "loads and stops". A YouTube stream URL
     * can expire or be rejected before its advertised lifetime (they're IP-bound and throttled),
     * which surfaces as a source error. We drop the cached URL and re-prepare so a fresh one is
     * resolved; after a couple of failures we skip the track so the queue never gets stuck.
     */
    private val errorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val player = mediaSession?.player ?: return
            val currentId = player.currentMediaItem?.mediaId
            if (currentId != errorItemId) {
                errorItemId = currentId
                errorRetries = 0
            }
            when {
                currentId != null && errorRetries < MAX_STREAM_RETRIES -> {
                    errorRetries++
                    // The cached URL may be stale/rejected — drop it so prepare() re-resolves fresh.
                    repository.invalidateStream(currentId)
                    player.prepare()
                }
                player.hasNextMediaItem() -> {
                    errorItemId = null
                    errorRetries = 0
                    player.seekToNext()
                    player.prepare()
                }
                // Nothing else to try: leave the error surfaced rather than looping.
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // A clean READY means recovery worked (or a new track loaded): reset the retry budget.
            if (playbackState == Player.STATE_READY) {
                errorItemId = null
                errorRetries = 0
            }
        }
    }

    /** Warms the next track's stream URL ahead of the transition so there's no network wait. */
    private val preloadListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = mediaSession?.player ?: return
            if (!player.hasNextMediaItem()) return
            val idx = player.nextMediaItemIndex
            if (idx == C.INDEX_UNSET || idx >= player.mediaItemCount) return
            val nextId = player.getMediaItemAt(idx).mediaId
            serviceScope.launch(Dispatchers.IO) { runCatching { repository.resolveStream(nextId) } }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloaderImpl.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        // DefaultDataSource routes file:// (downloaded tracks) to a FileDataSource and http(s)://
        // (streamed tracks) to the configured HTTP source.
        val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(
            upstreamFactory,
            StreamUrlResolver(repository, downloadRepository),
        )

        // Read-through disk cache: streamed bytes are cached (keyed by videoId via the MediaItem's
        // customCacheKey), so replays — including offline — serve from disk without re-resolving.
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // Buffer well ahead so ExoPlayer preloads the next track while the current one plays,
        // eliminating the buffering gap (and dead air during a crossfade) at transitions.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 50_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Pin the audio session up front so the equalizer exists (and the settings screen can
        // read the device's band layout) before the first track is ever loaded.
        audioSessionId = Util.generateAudioSessionIdV21(this)
        player.audioSessionId = audioSessionId
        audioEffects.attach(audioSessionId)
        audioEffects.notifySessionOpen(audioSessionId)

        // Tapping the media notification / lockscreen controls reopens the app (Now Playing).
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            MusicLibraryCallback(
                context = this,
                scope = serviceScope,
                library = libraryRepository,
                downloads = downloadRepository,
                music = repository,
                preferences = preferences,
            ),
        )
            .setSessionActivity(sessionActivity)
            .build()

        player.addListener(widgetListener)
        player.addListener(preloadListener)
        player.addListener(errorListener)
        crossfadeController = CrossfadeController(this, player, mediaSourceFactory, serviceScope)

        // "Skip silence" is a user setting; apply it live whenever it changes.
        preferences.skipSilence
            .onEach { player.skipSilenceEnabled = it }
            .launchIn(serviceScope)

        preferences.playbackSpeed
            .onEach { player.playbackParameters = PlaybackParameters(it) }
            .launchIn(serviceScope)

        preferences.crossfadeMs
            .onEach { crossfadeController?.crossfadeMs = it }
            .launchIn(serviceScope)

        // Any equalizer/bass/virtualizer/loudness change re-applies to the live session.
        combine(
            preferences.effectsEnabled,
            preferences.equalizerPreset,
            preferences.equalizerBands,
            preferences.bassBoost,
            preferences.virtualizer,
        ) { _, _, _, _, _ -> Unit }
            .onEach { audioEffects.apply() }
            .launchIn(serviceScope)

        preferences.loudnessGainMb
            .onEach { audioEffects.apply() }
            .launchIn(serviceScope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Leave the widget/tile showing the last track, but without live transport controls.
        nowPlayingPublisher.publish(null, false)
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            audioEffects.notifySessionClosed(audioSessionId)
            audioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
        audioEffects.release()
        mediaSession?.run {
            crossfadeController?.release()
            player.removeListener(widgetListener)
            player.removeListener(preloadListener)
            player.removeListener(errorListener)
            player.release()
            release()
        }
        crossfadeController = null
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        /** How many times to re-resolve a failing track before skipping past it. */
        const val MAX_STREAM_RETRIES = 2
    }
}
