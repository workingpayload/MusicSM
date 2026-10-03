package com.example.musicsm.ui.account

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.example.innertube.YtCookieAuth
import com.example.musicsm.R
import com.example.musicsm.data.auth.SignInPrompt
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant

/** "YouTube wants you to sign in" popup, raised when YouTube blocks anonymous playback. */
@Composable
fun YouTubeSignInPromptDialog(
    reason: SignInPrompt,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    when (reason) {
                        SignInPrompt.BLOCKED -> R.string.youtube_signin_blocked_title
                        SignInPrompt.EXPIRED -> R.string.youtube_signin_expired_title
                    },
                ),
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        when (reason) {
                            SignInPrompt.BLOCKED -> R.string.youtube_signin_blocked_body
                            SignInPrompt.EXPIRED -> R.string.youtube_signin_expired_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.youtube_signin_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.youtube_signin_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.youtube_signin_not_now)) }
        },
    )
}

/**
 * Google's own sign-in page, full screen. Once it lands back on YouTube Music signed in, the
 * youtube.com session cookies are handed to [onSignedIn]; returning false keeps the page open.
 * The password is typed into Google's page and never seen by the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleSignInDialog(
    onSignedIn: (cookie: String) -> Boolean,
    onClose: () -> Unit,
) {
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = {
            // Back steps through the sign-in pages before it closes the dialog.
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(AppBackground)
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = OnDark)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        stringResource(R.string.youtube_login_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDark,
                    )
                    Text(
                        stringResource(R.string.youtube_login_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkVariant,
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            private var done = false

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                captureSession(url)
                            }

                            // YouTube Music navigates in-page after the first load.
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                captureSession(url)
                            }

                            private fun captureSession(url: String?) {
                                if (done || url == null || url.toUri().host != YOUTUBE_MUSIC_HOST) return
                                val cookie = CookieManager.getInstance().getCookie(YOUTUBE_MUSIC_URL) ?: return
                                if (!YtCookieAuth.isSignedIn(cookie)) return
                                CookieManager.getInstance().flush()
                                done = currentOnSignedIn(cookie)
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                        webView = this
                    }
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }
}

private const val YOUTUBE_MUSIC_HOST = "music.youtube.com"
private const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"

/** Google's sign-in for YouTube Music, returning to YouTube Music when done. */
private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin" +
    "?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"
