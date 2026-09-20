package com.example.musicsm.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LocalMusicRepository
import com.example.musicsm.playback.MediaItemMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalMusicRepository {

    override suspend fun localSongs(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        // IS_MUSIC filters out ringtones/notifications/alarms; duration guards against 0-length stubs.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = ArrayList<Song>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, mediaId)
                    val albumId = cursor.getLong(albumIdCol)
                    val artUri = if (albumId > 0) {
                        ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString()
                    } else {
                        null
                    }
                    val artist = cursor.getString(artistCol)
                        ?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                        .orEmpty()
                    songs += Song(
                        id = MediaItemMapper.LOCAL_PREFIX + contentUri.toString(),
                        title = cursor.getString(titleCol).orEmpty(),
                        artist = artist,
                        album = cursor.getString(albumCol)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                        artworkUrl = artUri,
                        durationMs = cursor.getLong(durationCol),
                    )
                }
            }
        }
        songs
    }

    private companion object {
        // Legacy-but-still-supported album art collection; Coil loads these content URIs directly.
        private val ALBUM_ART_URI = "content://media/external/audio/albumart".toUri()
    }
}
