package com.example.musicsm.ui.jam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.data.jam.JamManager
import com.example.musicsm.data.jam.JamServer
import com.example.musicsm.data.jam.JamState
import com.example.musicsm.data.jam.JamClient
import com.example.musicsm.domain.jam.JamMember
import com.example.musicsm.domain.jam.JamRole
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.share.rememberQrMatrix
import com.example.musicsm.ui.share.QrCode
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.SurfaceLow

/**
 * Jam: one device hosts the queue, everyone else in the room controls it.
 *
 * Audio only ever comes out of the host — guests are remote controls. That is both what a Jam is
 * actually used for and what makes it reliable: with a single player there is no clock to
 * synchronise and nothing to drift.
 *
 * Joining is by QR code. The app has no camera permission and never scans anything; the guest uses
 * their own camera app, which opens the `musicsm://jam` link and hands it back to us.
 */
@Composable
fun JamScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JamViewModel = hiltViewModel(),
) {
    val jam by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = OnDark,
                )
            }
            Text(
                stringResource(R.string.jam_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnDark,
            )
            Spacer(Modifier.weight(1f))
            if (jam !is JamState.Off) {
                IconButton(onClick = viewModel::leave) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.jam_leave),
                        tint = OnDarkVariant,
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 24.dp),
        ) {
            item {
                when (val current = jam) {
                    JamState.Off -> JamIdlePanel(
                        onStart = { viewModel.startHosting(viewModel.defaultSessionName()) },
                    )

                    is JamState.Connecting -> JamConnectingPanel(current.invite.sessionName)

                    is JamState.Hosting -> JamHostPanel(state = current)

                    is JamState.Guest -> JamGuestPanel(sessionName = current.sessionName)

                    is JamState.Failed -> JamFailedPanel(
                        reason = current.reason,
                        onDismiss = viewModel::leave,
                    )
                }
            }

            if (jam is JamState.Hosting || jam is JamState.Guest) {
                item {
                    JamTransportBar(
                        isPlaying = ui.isPlaying,
                        onPrevious = viewModel::previous,
                        onPlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                    )
                }
                item {
                    Text(
                        stringResource(R.string.jam_queue_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnDark,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                if (ui.queue.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Filled.Groups,
                                title = stringResource(R.string.jam_queue_empty_title),
                                subtitle = stringResource(R.string.jam_queue_empty_subtitle),
                            )
                        }
                    }
                } else {
                    // Keyed by position: a queue may legitimately hold the same song twice, and
                    // duplicate keys crash Compose.
                    itemsIndexed(ui.queue, key = { index, _ -> index }) { index, song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongRow(
                                song = song,
                                onClick = {},
                                isCurrent = index == ui.currentIndex,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            IconButton(onClick = { viewModel.removeAt(index) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.jam_remove_track),
                                    tint = OnDarkVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JamIdlePanel(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = null,
            tint = Coral,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.jam_idle_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnDark,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.jam_idle_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = OnAccent),
            shape = RoundedCornerShape(50),
        ) { Text(stringResource(R.string.jam_start)) }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.jam_join_hint),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JamHostPanel(state: JamState.Hosting) {
    val context = LocalContext.current
    val link = state.invite.toUri()
    val matrix = rememberQrMatrix(link)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.jam_hosting_title),
            style = MaterialTheme.typography.labelMedium,
            color = Coral,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.invite.sessionName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))

        if (matrix != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(8.dp),
            ) { QrCode(matrix = matrix, modifier = Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            stringResource(R.string.jam_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { shareJamLink(context, state.invite.sessionName, link) },
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = OnDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.jam_share_link), color = OnDark)
            }
            OutlinedButton(
                onClick = { copyJamLink(context, state.invite.sessionName, link) },
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = OnDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.jam_copy_link), color = OnDark)
            }
        }

        Spacer(Modifier.height(16.dp))
        JamMemberList(state.members)
    }
}

@Composable
private fun JamGuestPanel(sessionName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.jam_guest_title),
            style = MaterialTheme.typography.labelMedium,
            color = Coral,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            sessionName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.jam_guest_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JamConnectingPanel(sessionName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Coral, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.jam_connecting, sessionName),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JamFailedPanel(reason: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(jamFailureMessage(reason)),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = OnAccent),
            shape = RoundedCornerShape(50),
        ) { Text(stringResource(R.string.jam_try_again)) }
    }
}

@Composable
private fun JamMemberList(members: List<JamMember>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            pluralStringResource(R.plurals.jam_members, members.size, members.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = OnDark,
        )
        Spacer(Modifier.height(8.dp))
        members.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (member.role == JamRole.HOST) {
                    Text(
                        stringResource(R.string.jam_role_host),
                        style = MaterialTheme.typography.labelSmall,
                        color = Coral,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun JamTransportBar(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous),
                tint = OnDark,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.action_pause else R.string.action_play,
                ),
                tint = Coral,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onNext) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next),
                tint = OnDark,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Maps a protocol reason code onto something a person can act on. */
private fun jamFailureMessage(reason: String): Int = when (reason) {
    JamManager.FAILED_NO_NETWORK -> R.string.jam_error_no_network
    JamManager.FAILED_PORT -> R.string.jam_error_port
    JamServer.BYE_BAD_TOKEN -> R.string.jam_error_bad_token
    JamServer.BYE_VERSION_MISMATCH -> R.string.jam_error_version
    JamServer.BYE_SESSION_ENDED -> R.string.jam_error_ended
    JamClient.DISCONNECT_HOST_GONE -> R.string.jam_error_host_gone
    else -> R.string.jam_error_connect
}

/**
 * The link is the fallback for anyone who can't scan — but it only works on the same Wi-Fi, so
 * the share sheet is for a group chat in the room, not for sending across town.
 */
private fun shareJamLink(context: Context, sessionName: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, sessionName)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.jam_share_message, sessionName, link))
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.jam_share_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun copyJamLink(context: Context, label: String, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, link))
    // Android 13+ shows its own clipboard confirmation; a toast would duplicate it.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.jam_link_copied), Toast.LENGTH_SHORT).show()
    }
}
