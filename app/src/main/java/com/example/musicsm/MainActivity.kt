package com.example.musicsm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.navigation.AppIntent
import com.example.musicsm.navigation.MusicSmRoot
import com.example.musicsm.navigation.toAppIntent
import com.example.musicsm.playback.MediaControllerManager
import com.example.musicsm.ui.theme.AppThemeViewModel
import com.example.musicsm.ui.theme.MusicSMTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var controllerManager: MediaControllerManager

    /**
     * What the app was launched to do (shortcut, deep link, shared link, widget, voice command).
     * The UI clears it once handled so a configuration change doesn't replay it.
     */
    private val appIntents = MutableStateFlow<AppIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) appIntents.value = intent.toAppIntent()
        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val themeSettings by themeViewModel.settings.collectAsStateWithLifecycle()
            MusicSMTheme(settings = themeSettings) {
                MusicSmRoot(
                    appIntents = appIntents,
                    onIntentHandled = { appIntents.value = null },
                )
            }
        }
    }

    // launchMode="singleTask": later links arrive here instead of starting a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appIntents.value = intent.toAppIntent()
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
