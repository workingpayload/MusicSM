package com.example.musicsm.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtistCircle
import com.example.musicsm.ui.components.BrowseTileCard
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.SpotifyGreen

@Composable
fun SearchScreen(
    onPlaySong: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                text = "Search",
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
                    placeholder = { Text("Songs, artists, albums") },
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
                        Text(
                            "Browse all",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    items(s.tiles) { tile ->
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

                is SearchUiState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchUiState.Results -> ResultsList(s.results, onPlaySong, onOpenAlbum, onOpenArtist)
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
) {
    if (results.isEmpty) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "No results",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
        if (results.artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(results.artists) { artist ->
                        ArtistCircle(artist = artist, onClick = { onOpenArtist(artist.id) })
                    }
                }
            }
        }
        if (results.albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(results.albums) { album ->
                        AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                    }
                }
            }
        }
        if (results.songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            items(results.songs) { song ->
                SongRow(song = song, onClick = { onPlaySong(song) })
            }
        }
    }
}
