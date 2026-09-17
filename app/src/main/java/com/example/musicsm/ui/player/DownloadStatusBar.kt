package com.example.musicsm.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.ui.components.WavyProgressBar
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.Teal
import kotlinx.coroutines.delay

/**
 * A floating status pill that shows a wavy download progress bar while downloads run, then a
 * brief "Downloads complete" confirmation so you know when it finished.
 */
@Composable
fun DownloadStatusBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val active = progress.isNotEmpty()
    val overall = if (active) progress.values.sum() / progress.size else 0f
    val pct = (overall * 100).toInt()

    var showDone by remember { mutableStateOf(false) }
    var prev by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active && prev) {
            showDone = true
            delay(2500)
            showDone = false
        }
        prev = active
    }

    AnimatedVisibility(
        visible = active || showDone,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E1F25).copy(alpha = 0.96f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    Icon(Icons.Filled.Downloading, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        pluralStringResource(R.plurals.downloads_in_progress_count, progress.size, progress.size),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("$pct%", color = OnDarkVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.downloads_complete), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            if (active) {
                Spacer(Modifier.height(8.dp))
                WavyProgressBar(progress = overall)
            }
        }
    }
}
