package com.example.musicsm.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicSmWearTheme {
                val viewModel: WearPlayerViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                DisposableEffect(viewModel) {
                    viewModel.start()
                    onDispose { viewModel.stop() }
                }

                WearPlayerScreen(
                    state = state,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onOpenOnPhone = viewModel::openOnPhone,
                )
            }
        }
    }
}
