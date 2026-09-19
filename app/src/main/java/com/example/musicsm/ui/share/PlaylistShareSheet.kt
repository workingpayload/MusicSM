package com.example.musicsm.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.share.PlaylistShareCodec
import com.example.musicsm.domain.share.SharedPlaylist
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.SurfaceLow

/**
 * "Share this playlist" sheet: a QR code plus link actions.
 *
 * There is no backend and no account — the entire track list is packed into the link itself by
 * [PlaylistShareCodec], so the QR is genuinely self-contained.
 *
 * The code carries a `musicsm://` link, and phone camera apps only offer to open `http(s)` codes;
 * they ignore custom schemes without saying so, which makes a system-camera scan look like it
 * simply did nothing. The receiving phone therefore has to scan from inside MusicSM
 * (Library → Scan playlist code), which reads the code via Play services and still needs no
 * camera permission. Sending the link instead works anywhere.
 *
 * A long playlist can outgrow the ~2,950-byte QR ceiling. That isn't an error — the sheet simply
 * drops the code and offers the link, which has no length limit.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaylistShareSheet(
    playlistName: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copiedMessage = stringResource(R.string.share_playlist_copied)

    // Encoding walks every track, so keep it off the recomposition path.
    val link = remember(playlistName, songs) {
        PlaylistShareCodec.shareUrl(SharedPlaylist(playlistName, songs))
    }
    val matrix = rememberQrMatrix(link)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                playlistName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnDark,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.share_playlist_tracks, songs.size, songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkVariant,
            )

            Spacer(Modifier.height(20.dp))

            if (matrix != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp),
                ) {
                    QrCode(matrix = matrix, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.share_playlist_scan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = OnDarkVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.share_playlist_too_big),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { shareLink(context, playlistName, link) },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = OnAccent),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_playlist_send_link))
                }
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, playlistName, link)
                        // Android 13+ shows its own clipboard confirmation; a toast would duplicate it.
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = OnDark,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_playlist_copy_link), color = OnDark)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun shareLink(context: Context, playlistName: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, playlistName)
        putExtra(
            Intent.EXTRA_TEXT,
            context.getString(R.string.share_playlist_message, playlistName, link),
        )
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_playlist_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun copyToClipboard(context: Context, label: String, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, link))
}
