package com.example.musicsm.ui.jam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
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
import com.example.musicsm.domain.jam.DiscoveredJam
import com.example.musicsm.domain.jam.JamJoinCode
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
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()

    // Scan as soon as the screen is idle: a list that is already populated is the whole point.
    LaunchedEffect(jam is JamState.Off) {
        if (jam is JamState.Off) viewModel.refreshNearby()
    }

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
                        nearby = nearby,
                        onRefresh = viewModel::refreshNearby,
                        onJoin = viewModel::joinDiscovered,
                        onJoinByCode = viewModel::joinByCode,
                        onJoinByAddress = viewModel::joinByAddress,
                        isCodeComplete = viewModel::isCodeComplete,
                    )

                    is JamState.Connecting -> JamConnectingPanel(current.invite.sessionName)

                    is JamState.Hosting -> JamHostPanel(
                        state = current,
                        onDiscoverableChange = viewModel::setDiscoverable,
                    )

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
private fun JamIdlePanel(
    onStart: () -> Unit,
    nearby: JamNearbyState,
    onRefresh: () -> Unit,
    onJoin: (DiscoveredJam) -> Unit,
    onJoinByCode: (String) -> Unit,
    onJoinByAddress: (String, String) -> Unit,
    isCodeComplete: (String) -> Boolean,
) {
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

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = OnDarkVariant.copy(alpha = 0.2f))
        Spacer(Modifier.height(20.dp))

        JamNearbySection(nearby = nearby, onRefresh = onRefresh, onJoin = onJoin)

        Spacer(Modifier.height(24.dp))
        JamCodeEntry(
            nearby = nearby,
            onJoinByCode = onJoinByCode,
            onJoinByAddress = onJoinByAddress,
            isCodeComplete = isCodeComplete,
        )
    }
}

/**
 * The list of Jams found on this Wi-Fi.
 *
 * This is the primary way in. The QR code it replaced looked fine but did not work on real
 * hardware: OEM camera apps only offer to open `http(s)` codes, so scanning a `musicsm://` link
 * produced no prompt at all on the phones this was tested against.
 */
@Composable
private fun JamNearbySection(
    nearby: JamNearbyState,
    onRefresh: () -> Unit,
    onJoin: (DiscoveredJam) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.jam_nearby_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = OnDark,
            modifier = Modifier.weight(1f),
        )
        if (nearby.scanning) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Coral,
                modifier = Modifier.size(18.dp),
            )
        } else {
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.jam_nearby_refresh),
                    tint = OnDarkVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    when {
        nearby.jams.isNotEmpty() -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nearby.jams.forEach { jam ->
                    JamNearbyRow(jam = jam, onClick = { onJoin(jam) })
                }
            }
        }
        // "Looking" and "found nothing" must not look the same, or the user just waits.
        nearby.scanning -> Text(
            stringResource(R.string.jam_nearby_scanning),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )

        nearby.scanned -> Text(
            stringResource(R.string.jam_nearby_empty),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )

        else -> Unit
    }
}

@Composable
private fun JamNearbyRow(jam: DiscoveredJam, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Wifi, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                jam.invite.sessionName.ifBlank { stringResource(R.string.jam_title) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (jam.hostName.isNotBlank()) {
                Text(
                    jam.hostName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            stringResource(R.string.jam_nearby_join),
            style = MaterialTheme.typography.labelLarge,
            color = Coral,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Typing a code always works, even where broadcast discovery is filtered or a host is hidden. */
@Composable
private fun JamCodeEntry(
    nearby: JamNearbyState,
    onJoinByCode: (String) -> Unit,
    onJoinByAddress: (String, String) -> Unit,
    isCodeComplete: (String) -> Boolean,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val complete = isCodeComplete(code)

    val submit = {
        if (complete && !nearby.codeBusy) {
            keyboard?.hide()
            onJoinByCode(code)
        }
    }

    Text(
        stringResource(R.string.jam_code_header),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = OnDark,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { typed -> code = typed.take(CODE_INPUT_LIMIT) },
            singleLine = true,
            isError = nearby.codeFailed,
            placeholder = { Text(stringResource(R.string.jam_code_placeholder), color = OnDarkVariant) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = submit,
            enabled = complete && !nearby.codeBusy,
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = OnAccent),
            shape = RoundedCornerShape(50),
        ) {
            if (nearby.codeBusy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = OnAccent,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(stringResource(R.string.jam_code_join))
            }
        }
    }
    if (nearby.codeFailed) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.jam_code_not_found),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // Shown only once the automatic paths have visibly failed, so the common case stays simple.
    if (nearby.codeFailed || (nearby.scanned && nearby.jams.isEmpty())) {
        Spacer(Modifier.height(20.dp))
        JamManualAddressEntry(
            code = code,
            addressFailed = nearby.addressFailed,
            onConnect = { address -> keyboard?.hide(); onJoinByAddress(address, code) },
        )
    }
}

/**
 * The last-resort way in: type the host's address directly.
 *
 * Discovery needs a UDP broadcast to survive the network, and plenty of them don't deliver one —
 * guest isolation, some phone hotspots, most corporate Wi-Fi. A TCP connection to an address the
 * user can read off the host's screen has none of that fragility, so this always works wherever
 * the two devices can reach each other at all.
 */
@Composable
private fun JamManualAddressEntry(
    code: String,
    addressFailed: Boolean,
    onConnect: (String) -> Unit,
) {
    var address by rememberSaveable { mutableStateOf("") }

    Text(
        stringResource(R.string.jam_manual_header),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = OnDark,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.jam_manual_hint),
        style = MaterialTheme.typography.bodySmall,
        color = OnDarkVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = { typed -> address = typed.take(ADDRESS_INPUT_LIMIT) },
            singleLine = true,
            isError = addressFailed,
            placeholder = {
                Text(stringResource(R.string.jam_manual_placeholder), color = OnDarkVariant)
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onConnect(address) }),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onConnect(address) },
            enabled = address.isNotBlank() && code.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = OnAccent),
            shape = RoundedCornerShape(50),
        ) { Text(stringResource(R.string.jam_manual_join)) }
    }
    if (addressFailed) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.jam_manual_bad_address),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun JamHostPanel(state: JamState.Hosting, onDiscoverableChange: (Boolean) -> Unit) {
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

        // The code leads, not the QR: this is the path that actually works on every phone.
        Text(
            stringResource(R.string.jam_code_label),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            JamJoinCode.format(state.joinCode),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Coral,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.jam_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )

        // The address is shown too, because discovery is the part most likely to be blocked by a
        // network and this is what a stuck guest needs in order to connect anyway.
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.jam_host_address_label),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (state.invite.port == JamServer.PREFERRED_PORT) {
                state.invite.address
            } else {
                "${state.invite.address}:${state.invite.port}"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnDark,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.jam_host_address_hint),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLow)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.jam_discoverable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDark,
                )
                Text(
                    stringResource(R.string.jam_discoverable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkVariant,
                )
            }
            Switch(
                checked = state.discoverable,
                onCheckedChange = onDiscoverableChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Coral, checkedThumbColor = OnAccent),
            )
        }

        Spacer(Modifier.height(20.dp))

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

/** Generous enough for hyphens and spaces, tight enough to stop a paste filling the field. */
private const val CODE_INPUT_LIMIT = 12

/** Room for a dotted quad and an optional `:port`, and nothing more. */
private const val ADDRESS_INPUT_LIMIT = 21
