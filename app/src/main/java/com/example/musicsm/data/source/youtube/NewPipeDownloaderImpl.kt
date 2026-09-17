package com.example.musicsm.data.source.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * OkHttp-backed [Downloader] required by NewPipeExtractor. Mirrors NewPipe's reference
 * implementation. Blocking — callers must run extraction off the main thread.
 *
 * The [USER_AGENT] is shared with the ExoPlayer HTTP data source so googlevideo hosts
 * see a consistent client and don't 403 the stream request.
 */
class NewPipeDownloaderImpl private constructor(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.toRequestBody(null, 0, dataToSend.size)

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (headerName, headerValueList) ->
            when {
                headerValueList.size > 1 -> {
                    requestBuilder.removeHeader(headerName)
                    headerValueList.forEach { headerValue ->
                        requestBuilder.addHeader(headerName, headerValue)
                    }
                }
                headerValueList.size == 1 -> requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBody = response.body?.string()
        val latestUrl = response.request.url.toString()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            latestUrl,
        )
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * Builds the downloader around the app-wide [OkHttpClient] so NewPipe shares the same
         * connection pool and response cache as everything else.
         */
        fun create(client: OkHttpClient): NewPipeDownloaderImpl = NewPipeDownloaderImpl(client)
    }
}
