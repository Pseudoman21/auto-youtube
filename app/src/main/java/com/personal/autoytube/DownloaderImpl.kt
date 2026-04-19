package com.personal.autoytube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        @Volatile private var instance: DownloaderImpl? = null

        fun init(builder: OkHttpClient.Builder?): DownloaderImpl {
            return instance ?: synchronized(this) {
                instance ?: DownloaderImpl().also { instance = it }
            }
        }
    }

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = okhttp3.Request.Builder().url(url)

        headers.forEach { (name, values) ->
            values.forEach { value -> reqBuilder.addHeader(name, value) }
        }

        val body = dataToSend?.toRequestBody()
        reqBuilder.method(method, if (method == "GET" || method == "HEAD") null else body ?: ByteArray(0).toRequestBody())

        val response = client.newCall(reqBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            response.request.url.toString()
        )
    }
}
