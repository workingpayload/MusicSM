package com.example.musicsm.data.source.youtube

import android.util.Log
import com.example.innertube.InnerTube
import com.example.innertube.model.YtPlayerClient
import com.example.innertube.model.YtPlayerStreams
import com.example.innertube.model.YtStreamFormat
import com.example.musicsm.data.auth.YouTubeAccount
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.SignInRequiredException
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.utils.Parser
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a stream with the listener's signed-in YouTube session, for when YouTube has blocked
 * anonymous playback on this network ("Sign in to confirm you're not a bot").
 *
 * NewPipe can't help here: its streams come from YouTube's Android / iOS app clients, which don't
 * accept browser cookies. So this asks YouTube's TV and YouTube Music web players instead (both
 * take the session's cookies) and decodes their ciphered URLs with NewPipe's copy of the web
 * player's JavaScript, the same way NewPipe does for its own web requests.
 */
@Singleton
class SignedInStreamResolver @Inject constructor(
    private val innerTube: InnerTube,
    private val account: YouTubeAccount,
) {
    private val random = SecureRandom()

    /**
     * @throws SignInRequiredException when signed out, or when YouTube rejects the saved session
     *   (which is then forgotten and the listener asked to sign in again).
     * @throws IOException when no client produced a playable stream for another reason.
     */
    suspend fun resolve(videoId: String): PlayableStream {
        val cookie = account.cookie ?: throw SignInRequiredException("Not signed in to YouTube")
        val signatureTimestamp = try {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        } catch (failure: Exception) {
            Log.w(TAG, "No signature timestamp; ciphered streams may not play", failure)
            null
        }
        val cpn = newCpn()
        var rejected = 0
        var lastFailure: Exception? = null
        for (client in YtPlayerClient.entries) {
            val streams = try {
                innerTube.signedInPlayer(videoId, cookie, client, signatureTimestamp, cpn)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                lastFailure = failure
                continue
            }
            if (streams.sessionRejected) {
                rejected++
                continue
            }
            if (!streams.isPlayable) {
                lastFailure = IOException("${client.name}: ${streams.status} ${streams.reason.orEmpty()}")
                continue
            }
            try {
                return toPlayable(videoId, streams, cpn).also {
                    Log.i(TAG, "$videoId plays through the signed-in ${client.name} client")
                }
            } catch (failure: Exception) {
                lastFailure = failure
            }
        }
        if (rejected == YtPlayerClient.entries.size) {
            account.onSessionRejected()
            throw SignInRequiredException("YouTube no longer accepts the saved sign-in")
        }
        throw IOException("No signed-in stream for $videoId", lastFailure)
    }

    private fun toPlayable(videoId: String, streams: YtPlayerStreams, cpn: String): PlayableStream {
        val format = pickSignedInFormat(streams) ?: throw IOException("No playable format for $videoId")
        val ttlMs = streams.expiresInSeconds
            ?.let { (it * 1000 - EXPIRY_MARGIN_MS).coerceIn(MIN_TTL_MS, MAX_TTL_MS) }
            ?: MAX_TTL_MS
        return PlayableStream(
            url = playableUrl(videoId, format, cpn),
            mimeType = format.baseMimeType,
            bitrate = (format.averageBitrate ?: format.bitrate) / 1000,
            expiresAtMs = System.currentTimeMillis() + ttlMs,
        )
    }

    /** Mirrors NewPipe's own handling of a web client's format URL. */
    private fun playableUrl(videoId: String, format: YtStreamFormat, cpn: String): String {
        val base = format.url ?: run {
            val cipher = Parser.compatParseMap(format.signatureCipher.orEmpty())
            val url = cipher["url"] ?: throw IOException("Ciphered format without a URL")
            val signature = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, cipher["s"].orEmpty())
            "$url&${cipher["sp"] ?: "signature"}=$signature"
        }
        // The throttling parameter must be decoded too, or the stream servers answer 403.
        return YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, base) +
            "&cpn=$cpn"
    }

    /** A client playback nonce: 16 characters from YouTube's URL-safe alphabet. */
    private fun newCpn(): String = buildString {
        repeat(CPN_LENGTH) { append(CPN_ALPHABET[random.nextInt(CPN_ALPHABET.length)]) }
    }

    private companion object {
        const val TAG = "SignedInStream"
        const val CPN_LENGTH = 16
        const val CPN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        const val EXPIRY_MARGIN_MS = 30 * 60 * 1000L
        const val MIN_TTL_MS = 10 * 60 * 1000L
        const val MAX_TTL_MS = 5 * 60 * 60 * 1000L
    }
}

/**
 * The format to play from a signed-in player response, chosen with the same rules as NewPipe's
 * streams ([pickAudioStream], then [pickMuxedStream]). A dubbed video's alternate-language audio
 * tracks are skipped in favour of its original one.
 */
internal fun pickSignedInFormat(streams: YtPlayerStreams): YtStreamFormat? {
    val audio = streams.adaptive.filter { it.isAudioOnly && it.hasSource }
    val original = audio.filter { it.isDefaultAudio != false }.ifEmpty { audio }
    pickAudioStream(
        streams = original,
        isAac = YtStreamFormat::isAac,
        isProgressive = { true },
        bitrate = { it.averageBitrate ?: it.bitrate },
    )?.let { return it }
    return pickMuxedStream(
        streams = streams.muxed.filter { it.hasSource },
        isAac = YtStreamFormat::isAac,
        isProgressive = { true },
        height = { it.height ?: 0 },
    )
}
