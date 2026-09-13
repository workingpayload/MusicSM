package com.example.musicsm.data.repository

import com.example.musicsm.data.local.dao.LikeDao
import com.example.musicsm.data.local.dao.PlaylistDao
import com.example.musicsm.data.local.dao.SongDao
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlaylistSongCrossRef
import com.example.musicsm.data.local.entity.toEntity
import com.example.musicsm.data.local.entity.toSong
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val likeDao: LikeDao,
    private val playlistDao: PlaylistDao,
) : LibraryRepository {

    override fun likedSongs(): Flow<List<Song>> =
        likeDao.likedSongs().map { list -> list.map { it.toSong() } }

    override fun playlists(): Flow<List<Playlist>> =
        playlistDao.playlists().map { list ->
            list.map { Playlist(id = it.playlistId.toString(), name = it.name, isLocal = true) }
        }

    override fun isLiked(songId: String): Flow<Boolean> = likeDao.isLiked(songId)

    override suspend fun toggleLike(song: Song) {
        songDao.upsert(song.toEntity())
        if (likeDao.isLikedNow(song.id)) {
            likeDao.unlike(song.id)
        } else {
            likeDao.like(LikedSongEntity(song.id, System.currentTimeMillis()))
        }
    }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(
            com.example.musicsm.data.local.entity.PlaylistEntity(
                name = name,
                createdAt = System.currentTimeMillis(),
            ),
        )

    override suspend fun deletePlaylist(playlistId: Long) = playlistDao.deletePlaylist(playlistId)

    override suspend fun renamePlaylist(playlistId: Long, name: String) =
        playlistDao.rename(playlistId, name)

    override fun playlist(playlistId: Long): Flow<Playlist?> =
        combine(playlistDao.playlist(playlistId), playlistDao.playlistSongs(playlistId)) { entity, songs ->
            entity?.let {
                Playlist(
                    id = it.playlistId.toString(),
                    name = it.name,
                    artworkUrl = songs.firstOrNull()?.artworkUrl,
                    songs = songs.map { s -> s.toSong() },
                    isLocal = true,
                )
            }
        }

    override suspend fun addToPlaylist(playlistId: Long, song: Song) {
        songDao.upsert(song.toEntity())
        val position = playlistDao.maxPosition(playlistId) + 1
        playlistDao.addCrossRef(PlaylistSongCrossRef(playlistId, song.id, position))
    }

    override suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        playlistDao.removeCrossRef(playlistId, songId)

    override suspend fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val refs = playlistDao.crossRefs(playlistId).toMutableList()
        if (fromIndex !in refs.indices || toIndex !in refs.indices) return
        val moved = refs.removeAt(fromIndex)
        refs.add(toIndex, moved)
        playlistDao.updateCrossRefs(refs.mapIndexed { index, ref -> ref.copy(position = index) })
    }
}
