package com.tradervolume.ytdl.extractor

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

sealed class ExtractedContent {
    data class Video(val info: StreamInfo) : ExtractedContent()
    data class Playlist(
        val title: String,
        val uploader: String?,
        val items: List<PlaylistEntry>
    ) : ExtractedContent()
}

data class PlaylistEntry(
    val index: Int,
    val title: String,
    val url: String,
    val durationSec: Long
)

/**
 * Thin wrapper on top of NewPipeExtractor:
 *  - detects whether a URL is a single video or a playlist
 *  - returns title, duration, available streams (muxed video+audio, audio-only)
 *  - enumerates playlists with title + URL per entry (handles paging)
 *  - exposes subtitle streams
 *
 * All calls are blocking network I/O — do not invoke from the main thread.
 */
class YoutubeExtractor(downloader: OkHttpDownloader) {

    init { NewPipeInitializer.ensureInitialized(downloader) }

    private val service get() = ServiceList.YouTube

    fun isPlaylist(url: String): Boolean {
        val u = url.lowercase()
        val hasList = u.contains("list=")
        val hasWatch = u.contains("watch?v=")
        return u.contains("/playlist?list=") || (hasList && !hasWatch)
    }

    fun extract(url: String): ExtractedContent =
        if (isPlaylist(url)) extractPlaylist(url) else extractVideo(url)

    fun extractVideo(url: String): ExtractedContent.Video {
        val info = StreamInfo.getInfo(service, url)
        return ExtractedContent.Video(info)
    }

    fun extractPlaylist(url: String): ExtractedContent.Playlist {
        val info = PlaylistInfo.getInfo(service, url)
        val items = mutableListOf<PlaylistEntry>()
        var idx = 1

        for (item in info.relatedItems) {
            items += PlaylistEntry(
                index = idx++,
                title = item.name.orEmpty(),
                url = item.url.orEmpty(),
                durationSec = item.duration
            )
        }

        var page = info.nextPage
        while (page != null) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            for (item in more.items) {
                items += PlaylistEntry(
                    index = idx++,
                    title = item.name.orEmpty(),
                    url = item.url.orEmpty(),
                    durationSec = item.duration
                )
            }
            page = more.nextPage
        }

        return ExtractedContent.Playlist(
            title = info.name.orEmpty(),
            uploader = info.uploaderName,
            items = items
        )
    }

    /**
     * Best progressive (video+audio muxed) stream. YouTube typically caps these at 720p;
     * anything above is DASH-only (video-only + audio-only) and requires muxing.
     */
    fun pickBestMuxed(info: StreamInfo): VideoStream? =
        info.videoStreams
            .filter { !it.isVideoOnly }
            .maxByOrNull { it.resolution?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 0 }

    fun pickBestAudio(info: StreamInfo): AudioStream? =
        info.audioStreams.maxByOrNull { it.averageBitrate }

    /**
     * @param languages ISO 639-1 codes (e.g. "es", "en"). Matches against the base language tag.
     */
    fun pickSubtitles(info: StreamInfo, languages: Set<String>): List<SubtitlesStream> =
        info.subtitles.filter { sub ->
            val code = sub.languageTag?.substringBefore('-')?.lowercase().orEmpty()
            code in languages
        }
}
