package com.example.musicsm.data.auth

import android.content.Context
import androidx.core.content.edit
import com.example.innertube.YtCookieAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Why the app is asking the listener to sign in. */
enum class SignInPrompt {
    /** YouTube put up its "confirm you're not a bot" wall for this network. */
    BLOCKED,

    /** The saved session stopped working (signed out elsewhere, or expired). */
    EXPIRED,
}

/**
 * The optional Google / YouTube session used only when YouTube blocks anonymous playback.
 *
 * Only the youtube.com cookies captured after signing in are kept (never the password or any
 * google.com cookie), in a private preferences file excluded from backups and device transfer.
 * The prompt state lives here too because the player service raises it and the UI shows it.
 */
@Singleton
class YouTubeAccount @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _cookie = MutableStateFlow(
        prefs.getString(KEY_COOKIE, null)?.takeIf(YtCookieAuth::isSignedIn),
    )

    /** The signed-in session's `Cookie` header for youtube.com, or null when signed out. */
    val cookie: String? get() = _cookie.value

    val isSignedIn: Boolean get() = _cookie.value != null

    val signedIn: StateFlow<Boolean> get() = _signedIn.asStateFlow()
    private val _signedIn = MutableStateFlow(_cookie.value != null)

    private val _prompt = MutableStateFlow<SignInPrompt?>(null)

    /** A pending request to sign in, for the UI to show; null when there's nothing to ask. */
    val prompt: StateFlow<SignInPrompt?> = _prompt.asStateFlow()

    /** "Not now" holds until the app restarts or the listener signs in. */
    @Volatile
    private var promptDismissed = false

    fun requestSignIn(reason: SignInPrompt) {
        if (!promptDismissed && _prompt.value == null) _prompt.value = reason
    }

    fun dismissPrompt() {
        promptDismissed = true
        _prompt.value = null
    }

    /** Saves a session captured from the sign-in page. False when [cookie] isn't signed in. */
    fun save(cookie: String): Boolean {
        if (!YtCookieAuth.isSignedIn(cookie)) return false
        prefs.edit { putString(KEY_COOKIE, cookie) }
        _cookie.value = cookie
        _signedIn.value = true
        promptDismissed = false
        _prompt.value = null
        return true
    }

    fun signOut() {
        prefs.edit { remove(KEY_COOKIE) }
        _cookie.value = null
        _signedIn.value = false
    }

    /** YouTube no longer accepts the saved session: forget it and ask again. */
    fun onSessionRejected() {
        signOut()
        promptDismissed = false
        _prompt.value = SignInPrompt.EXPIRED
    }

    private companion object {
        /** Named in backup_rules.xml and data_extraction_rules.xml; keep them in sync. */
        const val PREFS_NAME = "youtube_account"
        const val KEY_COOKIE = "cookie"
    }
}
