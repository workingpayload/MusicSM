package com.example.musicsm.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.ambient.AmbientLifecycleObserver

private enum class WearScreen { PLAYER, SEARCH, LYRICS }

class MainActivity : ComponentActivity() {

    // Ambient (always-on) state, driven by the platform ambient callbacks below.
    private val ambient = mutableStateOf(false)
    private val ambientTick = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ambientObserver = AmbientLifecycleObserver(
            this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    ambient.value = true
                    ambientTick.longValue = SystemClock.elapsedRealtime()
                }

                override fun onExitAmbient() {
                    ambient.value = false
                }

                override fun onUpdateAmbient() {
                    ambientTick.longValue = SystemClock.elapsedRealtime()
                }
            },
        )
        lifecycle.addObserver(ambientObserver)

        setContent {
            MusicSmWearTheme {
                val viewModel: WearPlayerViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val results by viewModel.searchResults.collectAsStateWithLifecycle()
                val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
                val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
                val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsStateWithLifecycle()
                val accent by viewModel.accent.collectAsStateWithLifecycle()

                val isAmbient by ambient
                val tick by ambientTick

                var screen by remember { mutableStateOf(WearScreen.PLAYER) }

                DisposableEffect(viewModel) {
                    viewModel.start()
                    onDispose { viewModel.stop() }
                }

                val voiceSearch = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val spoken = if (result.resultCode == Activity.RESULT_OK) {
                        result.data
                            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            ?.firstOrNull()
                            .orEmpty()
                    } else {
                        ""
                    }
                    if (spoken.isNotBlank()) {
                        viewModel.search(spoken)
                        screen = WearScreen.SEARCH
                    }
                }

                val startVoiceSearch = {
                    runCatching {
                        voiceSearch.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                            },
                        )
                    }
                    Unit
                }

                BackHandler(enabled = screen != WearScreen.PLAYER) {
                    when (screen) {
                        WearScreen.SEARCH -> viewModel.clearSearch()
                        WearScreen.LYRICS -> viewModel.clearLyrics()
                        WearScreen.PLAYER -> Unit
                    }
                    screen = WearScreen.PLAYER
                }

                if (isAmbient) {
                    WearAmbientScreen(
                        showLyrics = screen == WearScreen.LYRICS,
                        state = state,
                        lyrics = lyrics,
                        tick = tick,
                    )
                } else {
                    when (screen) {
                        WearScreen.PLAYER -> WearPlayerScreen(
                            state = state,
                            accent = accent,
                            onPlayPause = viewModel::playPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            onOpenOnPhone = viewModel::openOnPhone,
                            onSearch = startVoiceSearch,
                            onLyrics = {
                                viewModel.requestLyrics()
                                screen = WearScreen.LYRICS
                            },
                        )

                        WearScreen.SEARCH -> WearSearchScreen(
                            results = results,
                            isSearching = isSearching,
                            onPlay = { song ->
                                viewModel.play(song)
                                viewModel.clearSearch()
                                screen = WearScreen.PLAYER
                            },
                            onSearchAgain = startVoiceSearch,
                        )

                        WearScreen.LYRICS -> WearLyricsScreen(
                            lyrics = lyrics,
                            isLoading = isLoadingLyrics,
                            playbackState = state,
                            accent = accent,
                        )
                    }
                }
            }
        }
    }
}
