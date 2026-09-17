package com.example.musicsm.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtistCircle
import com.example.musicsm.ui.components.BrowseTileCard
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.SpotifyGreen

@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    onPlaySong: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recentSearches.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var optionsSong by remember { mutableStateOf<Song?>(null) }

    // Tint the header by the top result's artwork (falls back to the accent).
    val firstArtwork = (state as? SearchUiState.Results)?.results?.let { r ->
        r.songs.firstOrNull()?.artworkUrl
            ?: r.albums.firstOrNull()?.artworkUrl
            ?: r.artists.firstOrNull()?.artworkUrl
    }
    val headerAccent = rememberDominantColorState(firstArtwork, fallback = SpotifyGreen)

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val c = headerAccent.value
                    drawRect(
                        Brush.verticalGradient(
                            listOf(c.copy(alpha = 0.45f), Color.Transparent),
                        ),
                    )
                },
        ) {
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.onSubmit()
                            keyboard?.hide()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is SearchUiState.Idle -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + LocalBottomBarPadding.current,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        RecentSearches(
                            recents = recents,
                            onPick = { viewModel.onRecentSearchClick(it) },
                            onRemove = { viewModel.removeRecentSearch(it) },
                            onClearAll = { viewModel.clearRecentSearches() },
                        )
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Text(
                            stringResource(R.string.search_browse_all),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    items(s.tiles, key = { it.id }) { tile ->
                        BrowseTileCard(
                            title = tile.title,
                            color = Color(tile.accentColor),
                            onClick = { viewModel.onTileClick(tile) },
                        )
                    }
                }

                is SearchUiState.Loading -> CircularProgressIndicator(
                    color = SpotifyGreen,
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchUiState.Results -> ResultsList(
                    results = s.results,
                    onPlaySong = onPlaySong,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onMore = { optionsSong = it },
                )
            }
        }
    }

    optionsSong?.let { song ->
        SongOptionsSheet(
            song = song,
            playerViewModel = playerViewModel,
            onDismiss = { optionsSong = null },
        )
    }
}

/** Chips for previous queries, shown above "Browse all" while the search box is empty. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recents.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.search_recent),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.action_clear),
                style = MaterialTheme.typography.labelLarge,
                color = SpotifyGreen,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClearAll).padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recents.forEach { value ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(value) }
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $value",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onRemove(value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsList(
    results: SearchResults,
    onPlaySong: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onMore: (Song) -> Unit,
) {
    if (results.isEmpty) {
        Box(Modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.search_empty_title),
                subtitle = stringResource(R.string.search_empty_subtitle),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
        if (results.artists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_artists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(results.artists, key = { it.id }) { artist ->
                        ArtistCircle(artist = artist, onClick = { onOpenArtist(artist.id) })
                    }
                }
            }
        }
        if (results.albums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_albums)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(results.albums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                    }
                }
            }
        }
        if (results.songs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_songs)) }
            items(results.songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { onPlaySong(song) },
                    onMore = { onMore(song) },
                )
            }
        }
    }
}
