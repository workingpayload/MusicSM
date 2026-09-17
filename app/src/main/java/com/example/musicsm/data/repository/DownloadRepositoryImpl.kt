package com.example.musicsm.data.repository

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.example.musicsm.data.local.dao.DownloadDao
import com.example.musicsm.data.local.dao.SongDao
import com.example.musicsm.data.local.entity.DownloadEntity
import com.example.musicsm.data.local.entity.toEntity
import com.example.musicsm.data.local.entity.toSong
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.DownloadingSong
import com.example.musicsm.domain.repository.FailedDownload
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.download.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val songDao: SongDao,
    private val musicRepository: MusicRepository,
    private val preferences: AppPreferences,
    private val client: OkHttpClient,
) : DownloadRepository {

    // App-scoped: downloads keep running after the triggering screen/ViewModel is gone.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dir: File by lazy { File(context.filesDir, "downloads").apply { mkdirs() } }

    // Active downloads carry the full Song (thumbnail/title) so the UI can show artwork.
    private val _active = MutableStateFlow<List<DownloadingSong>>(emptyList())
    override val activeDownloads: StateFlow<List<DownloadingSong>> = _active.asStateFlow()

    private val _failed = MutableStateFlow<List<FailedDownload>>(emptyList())
    override val failedDownloads: StateFlow<List<FailedDownload>> = _failed.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    override val progress: StateFlow<Map<String, Float>> =
        _active.map { list -> list.associate { it.song.id to it.progress } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun isDownloaded(songId: String): Flow<Boolean> = downloadDao.isDownloaded(songId)

    override fun downloads(): Flow<List<Song>> =
        downloadDao.downloadedSongs().map { list -> list.map { it.toSong() } }

    override suspend fun localPath(songId: String): String? {
        val path = downloadDao.pathOf(songId) ?: return null
        return if (File(path).exists()) path else { downloadDao.delete(songId); null }
    }

    override suspend fun download(song: Song) {
        if (jobs.containsKey(song.id)) return                      // already downloading
        if (_active.value.any { it.song.id == song.id }) return
        if (localPath(song.id) != null) return                     // already downloaded
        if (preferences.wifiOnlyDownloadsNow && !isUnmetered()) {
            markFailed(song, "Waiting for Wi\u2011Fi")
            return
        }
        _failed.value = _failed.value.filterNot { it.song.id == song.id }
        // Resume shows the progress already on disk rather than snapping back to 0%.
        _active.value = _active.value + DownloadingSong(song, 0f)
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        val job = scope.launch { doDownload(song) }
        jobs[song.id] = job
        job.invokeOnCompletion { jobs.remove(song.id) }
    }

    override suspend fun downloadAll(songs: List<Song>) {
        songs.forEach { download(it) }
    }

    override fun cancel(songId: String) {
        jobs.remove(songId)?.cancel()
        _active.value = _active.value.filterNot { it.song.id == songId }
    }

    override suspend fun retry(songId: String) {
        val song = _failed.value.firstOrNull { it.song.id == songId }?.song ?: return
        _failed.value = _failed.value.filterNot { it.song.id == songId }
        download(song)
    }

    private suspend fun doDownload(song: Song) {
        val part = File(dir, "${song.id}.part")
        val target = File(dir, "${song.id}.audio")
        try {
            val stream = musicRepository.resolveStream(song.id)
            // First attempt resumes from the partial file; a 416 means the saved bytes no
            // longer match the (re-resolved) stream, so fall back to a clean download.
            val resumed = runCatching { fetch(song, stream.url, part, resume = part.length() > 0) }
            if (resumed.exceptionOrNull() is RangeRejected) {
                part.delete()
                setProgress(song.id, 0f)
                fetch(song, stream.url, part, resume = false)
            } else {
                resumed.getOrThrow()
            }

            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            songDao.upsert(song.toEntity())
            downloadDao.insert(DownloadEntity(song.id, target.absolutePath, stream.mimeType, System.currentTimeMillis()))
            _failed.value = _failed.value.filterNot { it.song.id == song.id }
        } catch (c: CancellationException) {
            throw c                                   // keep the .part file so it can resume
        } catch (t: Throwable) {
            android.util.Log.w("Download", "failed for ${song.id}", t)
            markFailed(song, t.message ?: "Download failed")
        } finally {
            _active.value = _active.value.filterNot { it.song.id == song.id }
        }
    }

    /** Streams [url] into [part], appending when [resume] and the server honours the range. */
    private suspend fun fetch(song: Song, url: String, part: File, resume: Boolean) {
        val existing = if (resume) part.length() else 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NewPipeDownloaderImpl.USER_AGENT)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == HTTP_RANGE_NOT_SATISFIABLE) throw RangeRejected()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            // A 200 to a ranged request means the server ignored it: start over.
            val appending = existing > 0 && resp.code == HTTP_PARTIAL_CONTENT
            val startAt = if (appending) existing else 0L
            val body = resp.body ?: error("empty body")
            val total = body.contentLength().takeIf { it > 0 }?.plus(startAt)
            body.byteStream().use { input ->
                FileOutputStream(part, appending).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var written = startAt
                    var lastPublished = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        if (total != null && written - lastPublished >= PROGRESS_STEP_BYTES) {
                            lastPublished = written
                            setProgress(song.id, (written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    override suspend fun delete(songId: String) = withContext(Dispatchers.IO) {
        cancel(songId)
        downloadDao.pathOf(songId)?.let { File(it).delete() }
        File(dir, "$songId.part").delete()
        _failed.value = _failed.value.filterNot { it.song.id == songId }
        downloadDao.delete(songId)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        jobs.keys.toList().forEach { cancel(it) }
        downloadDao.deleteAll()
        dir.listFiles()?.forEach { it.delete() }
        _failed.value = emptyList()
    }

    override suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun markFailed(song: Song, reason: String) {
        _failed.value = _failed.value.filterNot { it.song.id == song.id } + FailedDownload(song, reason)
    }

    /** True when the active network is unmetered (Wi-Fi/Ethernet). */
    private fun isUnmetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun setProgress(id: String, value: Float) {
        _active.value = _active.value.map { if (it.song.id == id) it.copy(progress = value) else it }
    }

    /** Thrown when the server refuses the resume range, signalling a restart from zero. */
    private class RangeRejected : Exception("range not satisfiable")

    private companion object {
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val PROGRESS_STEP_BYTES = 64 * 1024L
    }
}
