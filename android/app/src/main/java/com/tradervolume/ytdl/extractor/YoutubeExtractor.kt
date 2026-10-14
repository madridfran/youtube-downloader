package com.tradervolume.ytdl.extractor

import com.tradervolume.ytdl.download.AudioFormat
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
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

    /**
     * Vídeo sin audio en MP4/H.264 (lo único que MediaMuxer une a AAC sin recodificar), la mayor
     * altura que no pase de [maxHeight]. YouTube suele darlo hasta 1080p (itag 137).
     */
    fun pickVideoOnlyAvc(info: StreamInfo, maxHeight: Int): VideoStream? =
        info.videoOnlyStreams
            .filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.format == MediaFormat.MPEG_4 && it.codec?.startsWith("avc1") == true &&
                    tierOf(it) in 1..maxHeight
            }
            .maxWithOrNull(compareBy<VideoStream>({ tierOf(it) }, { it.fps }, { it.bitrate }))

    companion object {
        /**
         * Calidad como la etiqueta YouTube ("720p"), no la altura en píxeles: un vídeo 1280×616
         * (no 16:9) es "720p". Equivale a max(lado corto, lado largo × 9/16).
         */
        fun tierOf(width: Int, height: Int): Int =
            maxOf(minOf(width, height), maxOf(width, height) * 9 / 16)

        fun tierOf(s: VideoStream): Int = tierOf(s.width, s.height)
    }

    /**
     * Audio. En vídeos doblados (varias pistas de audio) se usa la original, no un doblaje.
     * M4A = AAC (necesario para unir con el vídeo HD); OPUS = WebM/Opus.
     */
    fun pickAudio(info: StreamInfo, format: AudioFormat): AudioStream? {
        val usable = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val original = usable.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { usable }
        val wanted = original.filter {
            when (format) {
                AudioFormat.M4A -> it.format == MediaFormat.M4A
                AudioFormat.OPUS -> it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBMA ||
                    it.format == MediaFormat.OPUS
            }
        }
        return wanted.ifEmpty { original }.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
    }

    /** ID de lista de un enlace (playlist?list=… o watch?v=…&list=…). */
    fun playlistIdOf(url: String): String? = Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1)

    /** Carga la lista aunque el enlace sea de un vídeo dentro de ella. Los mixes (RD…) necesitan el enlace original. */
    fun extractPlaylistOf(url: String): ExtractedContent.Playlist {
        val id = playlistIdOf(url) ?: return extractPlaylist(url)
        return extractPlaylist(if (id.startsWith("RD")) url else "https://www.youtube.com/playlist?list=$id")
    }

    /**
     * @param languages ISO 639-1 codes (e.g. "es", "en"). Matches against the base language tag.
     */
    fun pickSubtitles(info: StreamInfo, languages: Set<String>): List<SubtitlesStream> =
        info.subtitles.filter { sub ->
            val code = sub.languageTag?.substringBefore('-')?.lowercase().orEmpty()
            code in languages
        }
}
