package com.example.musicsm.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions

/**
 * Scanning for shared-playlist QR codes.
 *
 * A playlist code is a `musicsm://` link, and phone camera apps only ever offer to open `http(s)`
 * codes — they silently ignore custom schemes, so scanning one with the system camera appears to
 * do nothing at all. The code has to be read by MusicSM itself.
 *
 * Google Play services does that without the app touching the camera: [GmsBarcodeScanning] shows
 * its *own* scanning UI in a separate process and hands back only the decoded string. That is why
 * MusicSM still declares no `CAMERA` permission. The cost is a dependency on Play services, so
 * every caller has to handle [onUnavailable].
 *
 * @return a callback that opens the scanner. Cancelling is silent; it isn't an error.
 */
@Composable
fun rememberQrScanner(
    onScanned: (String) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scanned by rememberUpdatedState(onScanned)
    val unavailable by rememberUpdatedState(onUnavailable)

    val client = remember(context) {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                // Playlist codes are dense, so they are often held further away than a URL code.
                .enableAutoZoom()
                .build(),
        )
    }

    return remember(client) {
        {
            client.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.takeIf { it.isNotBlank() }?.let { scanned(it) }
                }
                // A deliberate back-press is not a failure and must stay silent.
                .addOnCanceledListener { }
                // Play services missing, or the scanner module could not be downloaded.
                .addOnFailureListener { unavailable() }
        }
    }
}
