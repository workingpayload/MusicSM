package com.example.musicsm

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject

@HiltAndroidApp
class MusicSmApp : Application(), SingletonImageLoader.Factory {

    /** The app-wide client from `NetworkModule`; shared with NewPipe so both reuse connections. */
    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // NewPipeExtractor must be initialized once with a Downloader before any extraction.
        NewPipe.init(NewPipeDownloaderImpl.create(okHttpClient))
    }

    /**
     * Artwork is the app's heaviest network traffic and the same thumbnails reappear constantly
     * (home shelves, search, queue, player). An explicit disk cache keeps them across process
     * death, so scrolling back to a shelf costs nothing.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()

    private companion object {
        const val IMAGE_CACHE_BYTES = 128L * 1024 * 1024
    }
}
