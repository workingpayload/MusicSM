package com.example.musicsm.playback.mix

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/**
 * PCM for a slice of a track; [startMs] is the media time of the first frame. Mono unless
 * [channels] > 1, in which case [samples] are interleaved.
 */
class PcmSnippet(val samples: FloatArray, val sampleRate: Int, val startMs: Double, val channels: Int = 1)

/**
 * Decodes a time range of a [MediaItem] to mono PCM for beat analysis, reading through the same
 * (cache-backed, URL-resolving) [DataSource.Factory] the player uses, so analysis bytes are shared
 * with playback instead of downloaded twice.
 */
@UnstableApi
class PcmSnippetDecoder(private val dataSourceFactory: DataSource.Factory) {

    /** Decodes [startMs, endMs] of [item]: mono, or with its own channels if [keepChannels]. */
    suspend fun decode(item: MediaItem, startMs: Long, endMs: Long, keepChannels: Boolean = false): PcmSnippet? {
        val config = item.localConfiguration ?: return null
        val source = DataSourceMedia(dataSourceFactory, config.uri, config.customCacheKey)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val startUs = startMs.coerceAtLeast(0) * 1000
            val endUs = endMs * 1000
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            var isFloat = false
            val pcm = FloatAccumulator()
            var firstUs = -1L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0 || extractor.sampleTime > endUs) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val bytesPerSample = if (isFloat) 4 else 2
                        val frames = info.size / (bytesPerSample * channels)
                        // Drop decoded frames before the requested start (seeks land on a sync point).
                        val skip = if (info.presentationTimeUs >= startUs) 0
                        else (((startUs - info.presentationTimeUs) * sampleRate) / 1_000_000L)
                            .toInt().coerceAtMost(frames)
                        if (firstUs < 0 && skip < frames) {
                            firstUs = info.presentationTimeUs + skip * 1_000_000L / sampleRate
                        }
                        for (f in 0 until frames) {
                            if (keepChannels) {
                                for (c in 0 until channels) {
                                    val s = if (isFloat) buf.getFloat() else buf.getShort() / 32768f
                                    if (f >= skip) pcm.add(s)
                                }
                            } else {
                                var sum = 0f
                                for (c in 0 until channels) {
                                    sum += if (isFloat) buf.getFloat() else buf.getShort() / 32768f
                                }
                                if (f >= skip) pcm.add(sum / channels)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (eos || info.presentationTimeUs >= endUs) break
                        val outChannels = if (keepChannels) channels else 1
                        if (pcm.size / outChannels > sampleRate.toLong() * MAX_SECONDS) break
                    }
                }
            }
            if (firstUs < 0 || pcm.size == 0) return null
            return PcmSnippet(pcm.toArray(), sampleRate, firstUs / 1000.0, if (keepChannels) channels else 1)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
    }

    private class FloatAccumulator {
        private var data = FloatArray(1 shl 16)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }

    /** Random-access [MediaDataSource] over a Media3 [DataSource], reopening only on real jumps. */
    private class DataSourceMedia(
        private val factory: DataSource.Factory,
        private val uri: android.net.Uri,
        private val key: String?,
    ) : MediaDataSource() {
        private var source: DataSource? = null
        private var position = -1L
        private var length = C.LENGTH_UNSET.toLong()
        private val skipBuffer = ByteArray(16 * 1024)

        private fun openAt(at: Long) {
            runCatching { source?.close() }
            val ds = factory.createDataSource()
            val spec = DataSpec.Builder().setUri(uri).setKey(key).setPosition(at).build()
            val remaining = ds.open(spec)
            if (remaining != C.LENGTH_UNSET.toLong()) length = at + remaining
            source = ds
            position = at
        }

        override fun readAt(at: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (length != C.LENGTH_UNSET.toLong() && at >= length) return -1
            val current = source
            if (current == null || at < position || at - position > MAX_FORWARD_SKIP) {
                openAt(at)
            } else {
                while (position < at) {
                    val n = current.read(skipBuffer, 0, minOf(skipBuffer.size.toLong(), at - position).toInt())
                    if (n == C.RESULT_END_OF_INPUT) return -1
                    position += n
                }
            }
            val ds = source!!
            var total = 0
            while (total < size) {
                val n = ds.read(buffer, offset + total, size - total)
                if (n == C.RESULT_END_OF_INPUT) break
                total += n
                position += n
            }
            return if (total == 0) -1 else total
        }

        override fun getSize(): Long {
            if (length == C.LENGTH_UNSET.toLong() && source == null) runCatching { openAt(0) }
            return if (length == C.LENGTH_UNSET.toLong()) -1 else length
        }

        override fun close() {
            runCatching { source?.close() }
            source = null
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_SECONDS = 45
        const val MAX_FORWARD_SKIP = 256 * 1024L
    }
}
