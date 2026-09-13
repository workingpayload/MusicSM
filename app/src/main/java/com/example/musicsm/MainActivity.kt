package com.example.musicsm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.musicsm.navigation.MusicSmRoot
import com.example.musicsm.playback.MediaControllerManager
import com.example.musicsm.ui.theme.MusicSMTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var controllerManager: MediaControllerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicSMTheme {
                MusicSmRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controllerManager.initialize()
    }

    override fun onStop() {
        controllerManager.release()
        super.onStop()
    }
}
