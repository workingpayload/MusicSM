package com.example.musicsm.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.domain.model.SongSort
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDark

/** Localised menu label for each [SongSort], kept in the UI layer so the domain stays Android-free. */
@get:StringRes
private val SongSort.labelRes: Int
    get() = when (this) {
        SongSort.DEFAULT -> R.string.sort_default
        SongSort.TITLE -> R.string.sort_title
        SongSort.ARTIST -> R.string.sort_artist
        SongSort.ALBUM -> R.string.sort_album
        SongSort.DURATION_SHORT -> R.string.sort_duration_short
        SongSort.DURATION_LONG -> R.string.sort_duration_long
    }

/** Overflow button that lets the user pick a [SongSort] for the surrounding list. */
@Composable
fun SortMenuButton(
    current: SongSort,
    onSelect: (SongSort) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = OnDark,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.action_sort),
                tint = if (current == SongSort.DEFAULT) tint else Coral,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = {
                        if (option == current) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
    }
}
