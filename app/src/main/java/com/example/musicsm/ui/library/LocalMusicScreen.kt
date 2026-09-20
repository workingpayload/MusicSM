package com.example.musicsm.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.musicsm.R
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant

/** The audio-read permission this feature needs, correct for the running OS version. */
private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun LocalMusicScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.load()
    }

    // Ask on first entry if we don't already have access; otherwise scan straight away.
    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.load() else requestPermission.launch(audioPermission)
    }

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.action_back),
                    tint = OnDark,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.local_music_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnDark,
                )
                if (songs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.local_music_count, songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkVariant,
                    )
                }
            }
            if (songs.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { playerViewModel.shufflePlay(songs) }
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.local_music_shuffle), tint = Coral)
                }
            }
        }

        when {
            !hasPermission -> PermissionPrompt(onGrant = { requestPermission.launch(audioPermission) })
            loading && songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }
            songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.LibraryMusic,
                    title = stringResource(R.string.local_music_title),
                    subtitle = stringResource(R.string.local_music_empty),
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
            ) {
                itemsIndexed(songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(song = song, onClick = { playerViewModel.play(songs, index) })
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = OnDarkVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.local_music_permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Coral)
                .clickable(onClick = onGrant)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.local_music_permission_grant),
                style = MaterialTheme.typography.labelLarge,
                color = OnAccent,
            )
        }
    }
}
