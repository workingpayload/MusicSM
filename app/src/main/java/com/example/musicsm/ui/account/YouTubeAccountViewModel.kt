package com.example.musicsm.ui.account

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.example.musicsm.data.auth.SignInPrompt
import com.example.musicsm.data.auth.YouTubeAccount
import com.example.musicsm.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Signing in to YouTube: the "please sign in" prompt, the sign-in page, and signing out. */
@HiltViewModel
class YouTubeAccountViewModel @Inject constructor(
    private val account: YouTubeAccount,
    private val player: MediaControllerManager,
) : ViewModel() {

    val prompt: StateFlow<SignInPrompt?> = account.prompt
    val signedIn: StateFlow<Boolean> = account.signedIn

    private val _showSignIn = MutableStateFlow(false)
    val showSignIn: StateFlow<Boolean> = _showSignIn.asStateFlow()

    fun openSignIn() {
        _showSignIn.value = true
    }

    fun dismissPrompt() = account.dismissPrompt()

    /** Closing the sign-in page without signing in also answers the prompt with "not now". */
    fun cancelSignIn() {
        _showSignIn.value = false
        if (account.prompt.value != null) account.dismissPrompt()
    }

    /**
     * Saves the session captured by the sign-in page and replays the track that was blocked.
     * False when [cookie] isn't a signed-in session (the page keeps waiting).
     */
    fun completeSignIn(cookie: String): Boolean {
        if (!account.save(cookie)) return false
        _showSignIn.value = false
        player.retryAfterError()
        return true
    }

    fun signOut() {
        account.signOut()
        // Also drop the sign-in page's own cookies, or the next sign-in would silently reuse them.
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }
}
