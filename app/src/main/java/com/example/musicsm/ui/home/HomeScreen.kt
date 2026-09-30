package com.example.musicsm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtistCircle
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ArtworkSize
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SkeletonBlock
import com.example.musicsm.ui.components.rememberShimmerProgress
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.StitchBackground
import com.example.musicsm.ui.theme.SurfaceLow

@Composable
fun HomeScreen(
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenCached: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    // Ambient aurora backdrop (coral + lavender blooms), like the Stitch design.
    // Palette tokens are composable reads, so they are hoisted out of the draw lambda.
    val backdrop = StitchBackground
    val bloomA = Coral
    val bloomB = Lavender
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(backdrop)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(bloomA.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.04f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(bloomB.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.25f),
                        radius = size.width * 0.7f,
                    ),
                )
            },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val s = state) {
                is HomeUiState.Loading -> HomeSkeleton()

                is HomeUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                is HomeUiState.Content -> HomeContent(
                    sections = s.feed.sections,
                    offline = s.offline,
                    loadingMore = s.loadingMore,
                    canLoadMore = s.canLoadMore,
                    pages = s.pages,
                    onPlaySongs = onPlaySongs,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onOpenPlaylist = onOpenPlaylist,
                    onSaveShelf = viewModel::saveShelf,
                    onLoadMore = viewModel::loadMore,
                    onOpenCached = onOpenCached,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    offline: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    pages: Int,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSaveShelf: (String, List<Song>) -> Unit,
    onLoadMore: () -> Unit,
    onOpenCached: () -> Unit,
) {
    // The first shelf may now be artists or albums, so pick the first one that actually has
    // tracks — the hero card can only play songs.
    val featuredList = sections
        .firstNotNullOfOrNull { section ->
            section.items.mapNotNull { (it as? HomeItem.SongItem)?.song }.ifEmpty { null }
        }
        .orEmpty()
    val featured = featuredList.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
    ) {
        // Header
        item {
            Column(modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
                Text(
                    text = stringResource(if (offline) R.string.home_title_offline else R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (offline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OverlayTint.copy(alpha = 0.06f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                    Text(
                        "  " + stringResource(R.string.home_offline_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(CircleShape)
                        .background(Coral)
                        .clickable(onClick = onOpenCached)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.OfflineBolt, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.cached_browse),
                        color = OnAccent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (sections.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_offline_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // Hero glass billboard
        if (featured != null) {
            item {
                HeroCard(
                    song = featured,
                    onPlay = { onPlaySongs(featuredList, 0) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        // Shelves
        items(sections, key = { it.title }) { section ->
            ShelfRow(
                section = section,
                onPlaySongs = onPlaySongs,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenPlaylist = onOpenPlaylist,
                onSaveShelf = onSaveShelf,
            )
        }

        if (loadingMore) {
            item { ShelfSkeleton(count = 2) }
        }

        // Paging trigger. Composing this row means the bottom of the list is on screen, which is
        // the cue to fetch the next batch; it doubles as the placeholder while that arrives.
        if (canLoadMore) {
            item(key = LOAD_MORE_KEY) {
                LaunchedEffect(pages) { onLoadMore() }
                ShelfSkeleton(count = 1)
            }
        }
    }
}

private const val LOAD_MORE_KEY = "home:load-more"

/**
 * One horizontal shelf. A section is all one kind of card in practice, but the model allows a
 * mix, so each item is rendered by its own type rather than by a per-shelf mode.
 */
@Composable
private fun ShelfRow(
    section: HomeSection,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSaveShelf: (String, List<Song>) -> Unit,
) {
    if (section.items.isEmpty()) return

    // Playing a card has to enqueue the whole shelf, so indices are taken against the songs
    // alone — a mixed shelf would otherwise start playback at the wrong track.
    val songs = section.items.mapNotNull { (it as? HomeItem.SongItem)?.song }

    ShelfHeader(
        title = section.title,
        // Only a shelf of tracks can become a playlist.
        onSave = if (songs.isNotEmpty()) ({ onSaveShelf(section.title, songs) }) else null,
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
        items(section.items, key = { it.key() }) { item ->
            when (item) {
                is HomeItem.SongItem -> AlbumCard(
                    title = item.song.title,
                    subtitle = item.song.artist,
                    artworkUrl = item.song.artworkUrl,
                    onClick = {
                        val index = songs.indexOfFirst { it.id == item.song.id }
                        if (index >= 0) onPlaySongs(songs, index)
                    },
                )

                is HomeItem.AlbumItem -> AlbumCard(
                    album = item.album,
                    onClick = { onOpenAlbum(item.album.id) },
                )

                is HomeItem.ArtistItem -> ArtistCircle(
                    artist = item.artist,
                    onClick = { onOpenArtist(item.artist.id) },
                )

                is HomeItem.PlaylistItem -> AlbumCard(
                    title = item.playlist.name,
                    subtitle = "",
                    artworkUrl = item.playlist.artworkUrl,
                    // Shelf cards carry no track list, so the playlist has to be opened rather
                    // than played — tapping one otherwise does nothing at all.
                    onClick = { onOpenPlaylist(item.playlist.id) },
                )
            }
        }
    }
}

/** Stable, type-qualified list key — a song and an album can share an id across providers. */
private fun HomeItem.key(): String = when (this) {
    is HomeItem.SongItem -> "song:${song.id}"
    is HomeItem.AlbumItem -> "album:${album.id}"
    is HomeItem.ArtistItem -> "artist:${artist.id}"
    is HomeItem.PlaylistItem -> "playlist:${playlist.id}"
}

@Composable
private fun ShelfHeader(title: String, onSave: (() -> Unit)?) {
    var saved by remember(title) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSave != null) {
            IconButton(onClick = { if (!saved) { onSave(); saved = true } }) {
                Icon(
                    imageVector = if (saved) Icons.Filled.Check else Icons.Filled.BookmarkAdd,
                    contentDescription = stringResource(R.string.home_save_as_playlist),
                    tint = if (saved) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    val progress = rememberShimmerProgress()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
    ) {
        // Header
        item {
            Column(modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                SkeletonBlock(progress = progress, modifier = Modifier.width(200.dp).height(30.dp))
            }
        }
        // Hero billboard
        item {
            SkeletonBlock(
                progress = progress,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
            )
        }
        // Shelves
        item { ShelfSkeleton(count = 3) }
    }
}

/** Placeholder shelves, used both for a cold start and while the network shelves load. */
@Composable
private fun ShelfSkeleton(count: Int) {
    val progress = rememberShimmerProgress()
    Column {
        repeat(count) {
            SkeletonBlock(
                progress = progress,
                modifier = Modifier
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
                    .width(140.dp)
                    .height(22.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), userScrollEnabled = false) {
                items(4) {
                    Column(modifier = Modifier.width(150.dp).padding(8.dp)) {
                        SkeletonBlock(
                            progress = progress,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Spacer(Modifier.height(8.dp))
                        SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth().height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    song: Song,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceLow),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            ArtworkImage(
                url = song.artworkUrl,
                shape = RoundedCornerShape(24.dp),
                targetSizePx = ArtworkSize.TILE,
                modifier = Modifier.fillMaxSize(),
            )
            // Legibility scrim.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1.0f to SurfaceLow.copy(alpha = 0.95f),
                        ),
                    ),
            )
            // Title block
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .fillMaxWidth(0.75f),
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = OverlayTint.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Coral glow play FAB
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Coral,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play), tint = OnAccent, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
