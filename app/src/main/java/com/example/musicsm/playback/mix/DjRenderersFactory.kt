package com.example.musicsm.playback.mix

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/** Default renderers, with [filter] inserted at the head of the audio processing chain. */
@UnstableApi
class DjRenderersFactory(
    context: Context,
    private val filter: DjFilterProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val sink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(filter))
            .build()
        return object : ForwardingAudioSink(sink) {
            override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
                val format = audioSinkConfig.format
                filter.setEncoderTrim(format.encoderDelay, format.encoderPadding)
                super.configure(audioSinkConfig)
            }
        }
    }
}
