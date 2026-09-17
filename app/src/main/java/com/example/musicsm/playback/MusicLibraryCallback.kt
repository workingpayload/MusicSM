package com.example.musicsm.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.musicsm.R
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Browse tree + playback resumption for Android Auto, Wear and Assistant.
 *
 * Browsers only ever see media ids, so [onAddMediaItems] rebuilds every item with the
 * `ytstream:` URI that [StreamUrlResolver] understands before it reaches the player.
 */
@OptIn(UnstableApi::class)
class MusicLibraryCallback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val music: MusicRepository,
    private val preferences: AppPreferences,
) : MediaLibraryService.MediaLibrarySession.Callback {

    /** Search results are fetched in [onSearch] and handed over in [onGetSearchResult]. */
    private val searchResults = ConcurrentHashMap<String, List<Song>>()

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = immediate(
        LibraryResult.ofItem(
            folder(ROOT, context.getString(R.string.browse_root), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            params,
        ),
    )

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        val item = CATEGORY_TITLES[mediaId]?.let { folder(mediaId, context.getString(it)) }
            ?: findSong(mediaId)?.let(::playable)
        if (item == null) {
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        } else {
            LibraryResult.ofItem(item, null)
        }
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        val children = childrenOf(parentId)
        LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        val songs = runCatching { music.search(query).songs }.getOrDefault(emptyList())
        searchResults[query] = songs
        session.notifySearchResultChanged(browser, query, songs.size, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        val songs = searchResults[query]
            ?: runCatching { music.search(query).songs }.getOrDefault(emptyList())
        LibraryResult.ofItemList(ImmutableList.copyOf(songs.map(::playable)), params)
    }

    /**
     * Items handed back by a browser carry only a media id. Rebuild them into streamable items;
     * a browsable id (e.g. "Liked songs") expands to everything inside it.
     */
    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = future {
        val resolved = mutableListOf<MediaItem>()
        mediaItems.forEach { item ->
            when {
                item.localConfiguration != null -> resolved += item
                isCategory(item.mediaId) -> resolved += childrenOf(item.mediaId).filter {
                    it.mediaMetadata.isPlayable == true
                }
                item.mediaId.isNotEmpty() -> resolved += MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri("${MediaItemMapper.SCHEME}:${item.mediaId}")
                    .setMediaMetadata(item.mediaMetadata)
                    .build()
            }
        }
        resolved
    }

    /** Lets the system media notification restart the last queue after a reboot. */
    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val saved = preferences.loadQueue()
            ?: throw UnsupportedOperationException("Nothing to resume")
        MediaSession.MediaItemsWithStartPosition(
            saved.songs.map(MediaItemMapper::toMediaItem),
            saved.index,
            saved.positionMs,
        )
    }

    // --- tree ---------------------------------------------------------------

    private suspend fun childrenOf(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(
            folder(LIKED, context.getString(CATEGORY_TITLES.getValue(LIKED))),
            folder(DOWNLOADS, context.getString(CATEGORY_TITLES.getValue(DOWNLOADS))),
            folder(RECENT, context.getString(CATEGORY_TITLES.getValue(RECENT))),
            folder(PLAYLISTS, context.getString(CATEGORY_TITLES.getValue(PLAYLISTS))),
        )
        PLAYLISTS -> runCatching { library.playlists().first() }
            .getOrDefault(emptyList())
            .map { folder(PLAYLIST_PREFIX + it.id, it.name, artworkUrl = it.artworkUrl) }
        else -> songsFor(parentId).map(::playable)
    }

    private suspend fun songsFor(parentId: String): List<Song> = runCatching {
        when {
            parentId == LIKED -> library.likedSongs().first()
            parentId == DOWNLOADS -> downloads.downloads().first()
            parentId == RECENT -> library.recentlyPlayed().first()
            parentId.startsWith(PLAYLIST_PREFIX) ->
                parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()
                    ?.let { library.playlist(it).first()?.songs }
                    .orEmpty()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /** Look a track up across the browsable collections, then in the last search results. */
    private suspend fun findSong(songId: String): Song? =
        listOf(LIKED, DOWNLOADS, RECENT)
            .firstNotNullOfOrNull { parent -> songsFor(parent).firstOrNull { it.id == songId } }
            ?: searchResults.values.firstNotNullOfOrNull { list ->
                list.firstOrNull { it.id == songId }
            }

    private fun isCategory(mediaId: String) =
        mediaId in CATEGORY_TITLES || mediaId.startsWith(PLAYLIST_PREFIX)

    // --- item builders ------------------------------------------------------

    private fun folder(
        id: String,
        title: String,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        artworkUrl: String? = null,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .apply { artworkUrl?.let { setArtworkUri(it.toUri()) } }
                .build(),
        )
        .build()

    private fun playable(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id)
        .setUri("${MediaItemMapper.SCHEME}:${song.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { song.artworkUrl?.let { setArtworkUri(it.toUri()) } }
                .build(),
        )
        .build()

    // --- future plumbing ----------------------------------------------------

    private fun <T> immediate(value: T): ListenableFuture<T> =
        SettableFuture.create<T>().apply { set(value) }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val settable = SettableFuture.create<T>()
        scope.launch {
            runCatching { block() }
                .onSuccess { settable.set(it) }
                .onFailure { settable.setException(it) }
        }
        return settable
    }

    private companion object {
        const val ROOT = "root"
        const val LIKED = "liked"
        const val DOWNLOADS = "downloads"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST_PREFIX = "playlist/"

        val CATEGORY_TITLES = mapOf(
            ROOT to R.string.browse_root,
            LIKED to R.string.browse_liked,
            DOWNLOADS to R.string.browse_downloads,
            RECENT to R.string.browse_recent,
            PLAYLISTS to R.string.browse_playlists,
        )
    }
}
