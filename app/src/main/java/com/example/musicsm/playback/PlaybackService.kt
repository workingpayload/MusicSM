package com.example.musicsm.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
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
import com.example.musicsm.domain.model.isSignInRequired
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.playback.mix.DjFilterProcessor
import com.example.musicsm.playback.mix.DjRenderersFactory
import com.example.musicsm.playback.mix.MixMediaSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
    @Inject lateinit var audioOutput: AudioOutputManager
    @Inject lateinit var mixTransitions: MixTransitionBus

    private var mediaSession: MediaLibrarySession? = null
    private var crossfadeController: CrossfadeController? = null
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val audioSessionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == this@PlaybackService.audioSessionId) {
                return
            }
            if (this@PlaybackService.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                audioEffects.notifySessionClosed(this@PlaybackService.audioSessionId)
            }
            this@PlaybackService.audioSessionId = audioSessionId
            audioEffects.attach(audioSessionId)
            audioEffects.notifySessionOpen(audioSessionId)
        }
    }

    /** Keeps the home-screen widget and Quick Settings tile in sync with the player. */
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                return
            }
            val song = player.currentMediaItem?.let(MediaItemMapper::toSong)
            val resync = events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
            nowPlayingPublisher.publish(song, player.isPlaying, player.currentPosition, resync)
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
            // YouTube blocked anonymous playback: every track would fail alike, so don't burn
            // through the queue. The UI asks the listener to sign in, then prepares again.
            if (error.isSignInRequired()) {
                errorItemId = null
                errorRetries = 0
                return
            }
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

        // "Cache songs" is a user setting; read live so toggling it off stops new reads/writes to
        // disk immediately (already-cached tracks are untouched — remove them from Library ▸ Cached).
        val dataSourceFactory = DataSource.Factory {
            if (preferences.cacheSongsNow) cacheDataSourceFactory.createDataSource() else resolvingFactory.createDataSource()
        }

        // Every item is wrapped so Mix can set where the next track starts when the player reaches it.
        val mediaSourceFactory = MixMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))

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

        // Volume + high-pass + next-track mix stage at the head of the audio chain; pass-through
        // outside crossfades and Mix transitions.
        val mixFilter = DjFilterProcessor()

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(DjRenderersFactory(this, mixFilter))
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(MusicAudioAttributes, /* handleAudioFocus = */ true)
            // The Now Playing volume slider controls Android's STREAM_MUSIC volume.
            .setDeviceVolumeControlEnabled(true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // A track can be a muxed video file when YouTube offers no audio-only stream (see
        // pickMuxedStream); only its audio is wanted, so video is never decoded.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        // The audio session is deliberately NOT pinned here. Forcing a pre-generated id keeps one
        // session - and the effect chain hanging off it - alive across output changes, and a chain
        // built for the phone speaker does not survive the move to Bluetooth A2DP on many devices:
        // the route switches, the player keeps reporting progress, and nothing comes out of the
        // headphones. Letting ExoPlayer allocate a session per AudioTrack means a new id arrives
        // through onAudioSessionIdChanged on a route change, so effects are rebuilt against the
        // output that is actually playing.
        player.addListener(audioSessionListener)

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
        crossfadeController = CrossfadeController(
            mainPlayer = player,
            sources = mediaSourceFactory,
            dataSourceFactory = cacheDataSourceFactory,
            mainFilter = mixFilter,
            scope = serviceScope,
            transitions = mixTransitions,
        )

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

        preferences.mixMode
            .onEach { crossfadeController?.mixMode = it }
            .launchIn(serviceScope)

        // Output picked in the in-app switcher; null restores Android's normal routing.
        audioOutput.preferredDevice
            .onEach { device -> player.setPreferredAudioDevice(device) }
            .launchIn(serviceScope)

        serviceScope.launch {
            audioOutput.state
                .map { it.current?.id }
                .distinctUntilChanged()
                .collectLatest {
                    resyncDeviceVolume(player)
                    delay(DEVICE_VOLUME_ROUTE_RESYNC_DELAY_MS)
                    resyncDeviceVolume(player)
                }
        }

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

    private fun resyncDeviceVolume(player: ExoPlayer) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) return
        val actualVolume = runCatching {
            val audioManager = getSystemService(AudioManager::class.java) ?: return
            if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) return
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrNull() ?: return

        if (actualVolume != player.deviceVolume) {
            // Route changes can swap STREAM_MUSIC to another device's saved level without the
            // volume broadcast Media3 uses to refresh its cache; writing the same level re-syncs it.
            player.setDeviceVolume(actualVolume, 0)
        }
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
            player.removeListener(audioSessionListener)
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
        const val DEVICE_VOLUME_ROUTE_RESYNC_DELAY_MS = 1_000L
    }
}

internal val MusicAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()
