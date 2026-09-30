package com.example.musicsm.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

/** Results for a watch search. Tapping a row asks the phone to play it. */
@Composable
fun WearSearchScreen(
    results: List<WearSong>,
    isSearching: Boolean,
    onPlay: (WearSong) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        when {
            isSearching -> CenterMessage { CircularProgressIndicator() }

            results.isEmpty() -> CenterMessage {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                )
            }

            else -> ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { ListHeader { Text(stringResource(R.string.results)) } }
                items(results, key = { it.id }) { song ->
                    Chip(
                        onClick = { onPlay(song) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            WearArtwork(
                                url = song.artworkUrl,
                                modifier = Modifier.size(ChipDefaults.LargeIconSize),
                            )
                        },
                        label = {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        secondaryLabel = if (song.artist.isNotBlank()) {
                            {
                                Text(
                                    text = song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                item {
                    Chip(
                        onClick = onSearchAgain,
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search_again)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}
