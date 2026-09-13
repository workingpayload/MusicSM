package com.example.musicsm

import android.app.Application
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe

@HiltAndroidApp
class MusicSmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // NewPipeExtractor must be initialized once with a Downloader before any extraction.
        NewPipe.init(NewPipeDownloaderImpl.instance)
    }
}
