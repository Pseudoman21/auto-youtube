package com.personal.autoytube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoItem(
    val title: String,
    val url: String,
    val uploader: String,
    val duration: Long
)

enum class StreamType { PROGRESSIVE, HLS, DASH }

data class StreamResult(val url: String, val type: StreamType)

object YouTubeHelper {

    fun init() {
        NewPipe.init(DownloaderImpl.init(null))
    }

    fun search(query: String): List<VideoItem> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                VideoItem(
                    title = item.name,
                    url = item.url,
                    uploader = item.uploaderName ?: "",
                    duration = item.duration
                )
            }
    }

    fun getStreamResult(videoUrl: String): StreamResult {
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

        // Prefer DASH manifest — ExoPlayer handles muxing automatically
        if (!info.dashMpdUrl.isNullOrBlank()) {
            return StreamResult(info.dashMpdUrl, StreamType.DASH)
        }

        // HLS fallback
        if (!info.hlsUrl.isNullOrBlank()) {
            return StreamResult(info.hlsUrl, StreamType.HLS)
        }

        // Progressive stream (video+audio in one file, up to 720p)
        val progressive = info.videoStreams
            .filter { !it.isVideoOnly && it.height in 1..720 }
            .maxByOrNull { it.height }
            ?: info.videoStreams.firstOrNull { !it.isVideoOnly }
            ?: throw Exception("No playable stream found for: $videoUrl")

        return StreamResult(progressive.content, StreamType.PROGRESSIVE)
    }
}
