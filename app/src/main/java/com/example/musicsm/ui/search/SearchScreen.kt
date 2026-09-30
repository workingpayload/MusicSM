package com.example.musicsm.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.SpotifyGreen
import com.example.musicsm.ui.theme.SurfaceLow

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
    val focusManager = LocalFocusManager.current
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    // A still-focused field makes the IME pop back up whenever the window regains focus (e.g.
    // screen off → on), so focus is dropped whenever the user is done typing.
    val dismissKeyboard = {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { focusManager.clearFocus() }

    // Tint the header by the top result's artwork (falls back to the accent).
    val firstArtwork = (state as? SearchUiState.Results)?.results?.let { r ->
        r.songs.firstOrNull()?.artworkUrl
            ?: r.albums.firstOrNull()?.artworkUrl
            ?: r.artists.firstOrNull()?.artworkUrl
            ?: r.videos.firstOrNull()?.artworkUrl
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
                            dismissKeyboard()
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
                    onPlaySong = { dismissKeyboard(); onPlaySong(it) },
                    onOpenAlbum = { dismissKeyboard(); onOpenAlbum(it) },
                    onOpenArtist = { dismissKeyboard(); onOpenArtist(it) },
                    onMore = { dismissKeyboard(); optionsSong = it },
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
    // Back to "All" whenever a new search comes in.
    var filter by rememberSaveable(results) { mutableStateOf(ResultFilter.ALL) }
    // With both songs and videos, "All" previews each and the chips show one kind in full.
    val both = results.songs.isNotEmpty() && results.videos.isNotEmpty()
    // A restored filter isn't checked against the results it was saved for; without both kinds
    // there are no chips to get out of it, so only "All" makes sense.
    val shown = if (both) filter else ResultFilter.ALL
    key(shown) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
            if (both) item(key = "filters") { ResultFilters(shown, onSelect = { filter = it }) }
            when (shown) {
                ResultFilter.ALL -> {
                    if (results.artists.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.section_artists)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                // Index in the key guarantees uniqueness even if two artists share an id.
                                itemsIndexed(results.artists, key = { index, artist -> "$index-${artist.id}" }) { _, artist ->
                                    ArtistCircle(artist = artist, onClick = { onOpenArtist(artist.id) })
                                }
                            }
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.section_albums)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                // Index in the key guarantees uniqueness even if two albums share an id.
                                itemsIndexed(results.albums, key = { index, album -> "$index-${album.id}" }) { _, album ->
                                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                                }
                            }
                        }
                    }
                    songSection(
                        R.string.section_songs, "songs", results.songs, if (both) PREVIEW_COUNT else Int.MAX_VALUE,
                        onShowAll = { filter = ResultFilter.SONGS }, onPlaySong, onMore,
                    )
                    songSection(
                        R.string.section_videos, "videos", results.videos, if (both) PREVIEW_COUNT else Int.MAX_VALUE,
                        onShowAll = { filter = ResultFilter.VIDEOS }, onPlaySong, onMore,
                    )
                }
                ResultFilter.SONGS -> songSection(R.string.section_songs, "songs", results.songs, Int.MAX_VALUE, {}, onPlaySong, onMore)
                ResultFilter.VIDEOS -> songSection(R.string.section_videos, "videos", results.videos, Int.MAX_VALUE, {}, onPlaySong, onMore)
            }
        }
    }
}

/** Which results the list shows. */
private enum class ResultFilter(@param:StringRes val label: Int) {
    ALL(R.string.search_filter_all),
    SONGS(R.string.section_songs),
    VIDEOS(R.string.section_videos),
}

@Composable
private fun ResultFilters(selected: ResultFilter, onSelect: (ResultFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultFilter.entries.forEach { filter ->
            val active = filter == selected
            Text(
                text = stringResource(filter.label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) OnAccent else OnDarkVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Coral else SurfaceLow)
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** A titled list of songs (or videos): the first [limit], then "Show all" if there are more. */
private fun LazyListScope.songSection(
    @StringRes title: Int,
    keyPrefix: String,
    songs: List<Song>,
    limit: Int,
    onShowAll: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onMore: (Song) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "$keyPrefix-header") { SectionHeader(stringResource(title)) }
    // Index in the key guarantees uniqueness even if two songs share an id.
    itemsIndexed(songs.take(limit), key = { index, song -> "$keyPrefix-$index-${song.id}" }) { _, song ->
        SongRow(
            song = song,
            onClick = { onPlaySong(song) },
            onMore = { onMore(song) },
        )
    }
    if (songs.size > limit) {
        item(key = "$keyPrefix-all") {
            Text(
                text = stringResource(R.string.search_show_all),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnDarkVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private const val PREVIEW_COUNT = 5
