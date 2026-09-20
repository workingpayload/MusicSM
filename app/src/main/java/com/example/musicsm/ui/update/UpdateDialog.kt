package com.example.musicsm.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.domain.model.UpdateInfo

/** "A new version is available" popup, shared by the launch check and the Settings check. */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_available_body, info.versionName, currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    // Release notes can be long, so cap the height and let them scroll.
                    Text(
                        text = info.notes.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_now)) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
        },
    )
}
