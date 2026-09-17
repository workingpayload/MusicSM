package com.example.musicsm.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var nowPlayingPublisher: NowPlayingPublisher

    private var mediaSession: MediaLibrarySession? = null
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

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

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

        // "Skip silence" is a user setting; apply it live whenever it changes.
        preferences.skipSilence
            .onEach { player.skipSilenceEnabled = it }
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
        mediaSession?.run {
            player.removeListener(widgetListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
