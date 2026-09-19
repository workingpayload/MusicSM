package com.example.musicsm.ui.jam

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The runtime permission Jam cannot work without on Android 17.
 *
 * Android 17 makes Local Network Protection mandatory for apps targeting SDK 37 or higher: every
 * local-network operation — making an outgoing TCP connection, accepting an incoming one, and
 * sending *or receiving* UDP unicast, multicast and broadcast — is blocked until the user grants
 * this. The restriction lives deep in the networking stack, so it applies to raw sockets and makes
 * no distinction between discovery and the session itself.
 *
 * This is what made Jam fail on every network it was tried on, including a phone hotspot: the
 * packets were never reaching the wire, so no amount of changing how they were addressed helped.
 *
 * Apps targeting below SDK 37 get an implicit grant, so nothing is requested on older releases.
 */
private const val ANDROID_17 = 37

/**
 * The literal rather than `Manifest.permission.ACCESS_LOCAL_NETWORK`, so the app still compiles
 * against an SDK that predates the constant. The name is part of the platform's public API.
 */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Null when the running platform grants local-network access implicitly. */
private val requiredPermission: String?
    get() = ACCESS_LOCAL_NETWORK.takeIf { Build.VERSION.SDK_INT >= ANDROID_17 }

/**
 * Whether Jam may touch the network, and how to ask if it may not.
 *
 * [runWhenGranted] defers the action until the user has answered, so a button can be wired
 * straight to it without the caller knowing whether a prompt will appear.
 */
@Immutable
internal class LocalNetworkPermissionState(
    /** False only where the platform needs a grant and hasn't been given one. */
    val granted: Boolean,
    /** True once the user has actively refused, which is worth explaining rather than retrying. */
    val denied: Boolean,
    val runWhenGranted: (action: () -> Unit) -> Unit,
    val request: () -> Unit,
)

@Composable
internal fun rememberLocalNetworkPermission(): LocalNetworkPermissionState {
    val context = LocalContext.current
    val permission = requiredPermission

    var granted by remember { mutableStateOf(permission == null || context.hasPermission(permission)) }
    var denied by remember { mutableStateOf(false) }
    // Held across the prompt so a tap that triggered it still happens once the user says yes.
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        denied = !isGranted
        if (isGranted) pending.value?.invoke()
        pending.value = null
    }

    return remember(granted, denied, permission) {
        LocalNetworkPermissionState(
            granted = granted,
            denied = denied,
            runWhenGranted = { action ->
                if (permission == null || granted) {
                    action()
                } else {
                    pending.value = action
                    launcher.launch(permission)
                }
            },
            request = {
                if (permission != null && !granted) {
                    pending.value = null
                    launcher.launch(permission)
                }
            },
        )
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
