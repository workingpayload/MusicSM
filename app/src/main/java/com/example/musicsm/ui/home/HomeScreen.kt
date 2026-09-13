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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.SkeletonBlock
import com.example.musicsm.ui.components.rememberShimmerProgress
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.StitchBackground
import com.example.musicsm.ui.theme.SurfaceLow

@Composable
fun HomeScreen(
    onPlaySongs: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    // Ambient aurora backdrop (coral + lavender blooms), like the Stitch design.
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(StitchBackground)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Coral.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.04f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Lavender.copy(alpha = 0.16f), Color.Transparent),
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

                is HomeUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }

                is HomeUiState.Content -> HomeContent(s.feed.sections, onPlaySongs)
            }
        }
    }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val featuredList = sections.firstOrNull()
        ?.items?.mapNotNull { (it as? HomeItem.SongItem)?.song }
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
                    text = "Made For You",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
        items(sections) { section ->
            val songs = section.items.mapNotNull { (it as? HomeItem.SongItem)?.song }
            if (songs.isNotEmpty()) {
                SectionHeader(section.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    itemsIndexed(songs) { index, song ->
                        AlbumCard(
                            title = song.title,
                            subtitle = song.artist,
                            artworkUrl = song.artworkUrl,
                            onClick = { onPlaySongs(songs, index) },
                        )
                    }
                }
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
        items(3) {
            Column {
                SkeletonBlock(
                    progress = progress,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
                        .width(140.dp)
                        .height(22.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
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
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
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
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
