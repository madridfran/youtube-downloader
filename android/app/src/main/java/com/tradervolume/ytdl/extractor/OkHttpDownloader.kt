package com.tradervolume.ytdl.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse

/**
 * NewPipeExtractor Downloader backed by OkHttp.
 * Supports optional cookie injection so authenticated requests (after WebView login) work.
 */
class OkHttpDownloader(
    private val client: OkHttpClient = defaultClient(),
    private val cookieProvider: () -> String? = { null }
) : Downloader() {

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun execute(request: NpRequest): NpResponse {
        val builder = Request.Builder().url(request.url())

        // Headers supplied by NewPipe (including Accept-Language when automaticLocalizationHeader = true)
        for ((k, values) in request.headers()) {
            for (v in values) builder.addHeader(k, v)
        }

        // Cookie injection (not overwritten if NewPipe already provided one)
        val existingCookie = request.headers()["Cookie"]?.firstOrNull()
        if (existingCookie.isNullOrBlank()) {
            cookieProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        }

        val body = request.dataToSend()?.toRequestBody()
        when (request.httpMethod().uppercase()) {
            "POST"   -> builder.post(body ?: ByteArray(0).toRequestBody())
            "HEAD"   -> builder.head()
            "PUT"    -> builder.put(body ?: ByteArray(0).toRequestBody())
            "DELETE" -> builder.delete(body)
            else     -> builder.get()
        }

        client.newCall(builder.build()).execute().use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            val headerMap = mutableMapOf<String, List<String>>()
            for (name in resp.headers.names()) headerMap[name] = resp.headers.values(name)
            return NpResponse(
                resp.code,
                resp.message,
                headerMap,
                responseBody,
                resp.request.url.toString()
            )
        }
    }
}
