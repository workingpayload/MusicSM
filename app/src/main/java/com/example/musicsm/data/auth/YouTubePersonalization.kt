package com.example.musicsm.data.auth

import com.example.musicsm.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether YouTube Music calls may use the signed-in account to personalise (Home, radio, related
 * songs, history, library): only when signed in *and* the listener turned it on in Settings.
 * Signing in by itself only unblocks playback.
 */
@Singleton
class YouTubePersonalization @Inject constructor(
    private val account: YouTubeAccount,
    private val preferences: AppPreferences,
) {
    /** The session to personalise with, or null to stay anonymous. */
    fun cookie(): String? = if (preferences.personalizeYouTubeNow) account.cookie else null

    val enabled: Flow<Boolean> =
        combine(account.signedIn, preferences.personalizeYouTube) { signedIn, on -> signedIn && on }
            .distinctUntilChanged()
}
