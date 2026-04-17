package com.tradervolume.ytdl.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tradervolume.ytdl.cookies.CookiesStore
import com.tradervolume.ytdl.extractor.ExtractedContent
import com.tradervolume.ytdl.extractor.OkHttpDownloader
import com.tradervolume.ytdl.extractor.YoutubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_URL = "url"
        const val KEY_MODE = "mode"
        const val KEY_SUB_LANGS = "sub_langs"
        const val KEY_SUBS_FORMAT = "subs_format"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val cookiesStore by lazy { CookiesStore(applicationContext) }
    private val downloader by lazy {
        OkHttpDownloader(cookieProvider = { cookiesStore.loadCookieHeader() })
    }
    private val extractor by lazy { YoutubeExtractor(downloader) }

    // Último archivo descargado en este worker — para que el tap de la notificación lo abra.
    private var lastEntry: HistoryEntry? = null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val mode = inputData.getString(KEY_MODE) ?: DownloadMode.VIDEO.name
        val subLangs = inputData.getString(KEY_SUB_LANGS)
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()
            ?: setOf("es")
        val subsFormat = runCatching {
            SubsFormat.valueOf(inputData.getString(KEY_SUBS_FORMAT) ?: SubsFormat.SRT.name)
        }.getOrDefault(SubsFormat.SRT)

        setForeground(foregroundInfo("Preparando…", 0))

        try {
            when (val content = extractor.extract(url)) {
                is ExtractedContent.Video -> handleSingle(content.info, mode, subLangs, subsFormat, index = null)
                is ExtractedContent.Playlist -> {
                    val total = content.items.size
                    for (entry in content.items) {
                        setForeground(foregroundInfo("[${entry.index}/$total] ${entry.title}", 0))
                        val info = extractor.extractVideo(entry.url).info
                        val childMode = when (mode) {
                            DownloadMode.PLAYLIST_VIDEO.name -> DownloadMode.VIDEO.name
                            DownloadMode.PLAYLIST_MP3.name   -> DownloadMode.AUDIO_MP3.name
                            else -> mode
                        }
                        handleSingle(info, childMode, subLangs, subsFormat, index = entry.index)
                    }
                }
            }
            postDoneNotification()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("YTDL", "Descarga fallida", e)
            val detalle = buildString {
                append(e.javaClass.simpleName)
                e.message?.let { append(": "); append(it) }
                e.cause?.let {
                    append(" | causa: ").append(it.javaClass.simpleName)
                    it.message?.let { m -> append(": ").append(m) }
                }
            }.take(240)
            NotificationManagerCompat.from(applicationContext).notify(
                NotificationHelper.NOTIF_ID + 1,
                NotificationHelper.done(applicationContext, "Descarga fallida", detalle).build()
            )
            Result.failure(workDataOf("error" to detalle))
        }
    }

    private fun postDoneNotification() {
        val entry = lastEntry
        val notif = if (entry != null) {
            NotificationHelper.doneForEntry(applicationContext, entry)
        } else {
            NotificationHelper.done(
                applicationContext,
                "Descarga completada",
                "Revisa Downloads/YouTubeDownloader"
            ).build()
        }
        NotificationManagerCompat.from(applicationContext).notify(
            NotificationHelper.NOTIF_ID + 1,
            notif
        )
    }

    private suspend fun handleSingle(
        info: StreamInfo,
        mode: String,
        subLangs: Set<String>,
        subsFormat: SubsFormat,
        index: Int?
    ) {
        val vid = NameSanitizer.extractVideoId(info.url)
        val title = info.name.orEmpty().ifBlank { "video" }

        when (mode) {
            DownloadMode.VIDEO.name -> downloadVideo(info, title, vid, index)
            DownloadMode.AUDIO_MP3.name -> downloadAudioMp3(info, title, vid, index)
            DownloadMode.SUBTITLES.name -> downloadSubtitles(info, title, vid, index, subLangs, subsFormat)
            DownloadMode.VIDEO_PLUS_SUBS.name -> {
                downloadVideo(info, title, vid, index)
                downloadSubtitles(info, title, vid, index, subLangs, subsFormat)
            }
            else -> throw IllegalArgumentException("Unknown mode: $mode")
        }
    }

    private fun downloadVideo(info: StreamInfo, title: String, vid: String, index: Int?) {
        val stream = extractor.pickBestMuxed(info)
            ?: throw IllegalStateException(
                "Sin stream muxed disponible. 1080p+ llega como DASH separado y requiere muxing, no implementado en v1."
            )
        val ext = (stream.format?.suffix ?: "mp4").lowercase()
        val mime = "video/$ext"
        val name = NameSanitizer.build(index, title, vid, ext)
        val target = FileLocationResolver.createOutput(applicationContext, name, mime)
        val streamUrl = stream.content ?: stream.url
            ?: throw IllegalStateException("Stream URL vacío para video muxed")
        val bytes = httpDownload(streamUrl, target, "$title")
        FileLocationResolver.markComplete(applicationContext, target)
        recordHistory(title, "VIDEO", mime, target, bytes, vid)
    }

    private fun downloadAudioMp3(info: StreamInfo, title: String, vid: String, index: Int?) {
        val audio = extractor.pickBestAudio(info) ?: throw IllegalStateException("Sin stream de audio")
        val srcExt = (audio.format?.suffix ?: "m4a").lowercase()
        val mime = when (srcExt) {
            "m4a", "mp4" -> "audio/mp4"
            "webm" -> "audio/webm"
            "opus" -> "audio/ogg"
            "mp3"  -> "audio/mpeg"
            else -> "audio/*"
        }
        val name = NameSanitizer.build(index, title, vid, srcExt)
        val target = FileLocationResolver.createOutput(applicationContext, name, mime)
        val streamUrl = audio.content ?: audio.url
            ?: throw IllegalStateException("Stream URL vacío para audio")
        val bytes = httpDownload(streamUrl, target, "Audio $title")
        FileLocationResolver.markComplete(applicationContext, target)
        recordHistory(title, "AUDIO", mime, target, bytes, vid)
    }

    private fun downloadSubtitles(
        info: StreamInfo,
        title: String,
        vid: String,
        index: Int?,
        langs: Set<String>,
        format: SubsFormat
    ) {
        val subs = extractor.pickSubtitles(info, langs)
        if (subs.isEmpty()) return
        for (sub in subs) {
            val lang = sub.languageTag?.substringBefore('-')?.lowercase() ?: "xx"
            val url = sub.content ?: sub.url ?: continue
            val req = Request.Builder().url(url).apply {
                cookiesStore.loadCookieHeader()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }.build()
            val body = client.newCall(req).execute().use {
                if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code} subtítulos $lang")
                it.body?.string().orEmpty()
            }
            val srt = if ((sub.format?.suffix?.lowercase() == "vtt") || body.trimStart().startsWith("WEBVTT")) {
                SubtitleConverter.vttToSrt(body)
            } else body

            val payload: String
            val ext: String
            val mime: String
            val type: String
            if (format == SubsFormat.TXT) {
                payload = SubtitleConverter.srtToTxt(srt)
                ext = "txt"; mime = "text/plain"; type = "SUBS_TXT"
            } else {
                payload = srt
                ext = "srt"; mime = "application/x-subrip"; type = "SUBS_SRT"
            }
            val name = NameSanitizer.build(index, "$title.$lang", vid, ext)
            val target = FileLocationResolver.createOutput(applicationContext, name, mime)
            val bytes = payload.toByteArray(Charsets.UTF_8)
            FileLocationResolver.openOutputStream(applicationContext, target).use {
                it.write(bytes)
            }
            FileLocationResolver.markComplete(applicationContext, target)
            recordHistory("$title.$lang", type, mime, target, bytes.size.toLong(), vid)
        }
    }

    private fun recordHistory(
        title: String,
        type: String,
        mime: String,
        target: FileLocationResolver.Target,
        sizeBytes: Long,
        videoId: String?
    ) {
        val entry = HistoryStore.append(
            context = applicationContext,
            title = title,
            type = type,
            mime = mime,
            uri = target.uri,
            fileFallback = target.file,
            sizeBytes = sizeBytes,
            videoId = videoId
        )
        lastEntry = entry
    }

    private fun httpDownload(url: String, target: FileLocationResolver.Target, title: String): Long {
        val req = buildRequest(url)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} descargando")
            val total = resp.body?.contentLength() ?: -1L
            val input = resp.body!!.byteStream()
            val out = FileLocationResolver.openOutputStream(applicationContext, target)
            return copyWithProgress(input, out, total, title)
        }
    }

    private fun buildRequest(url: String): Request =
        Request.Builder().url(url).apply {
            header("User-Agent", "Mozilla/5.0 (Linux; Android) YouTubeDownloader/1.0")
            cookiesStore.loadCookieHeader()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }.build()

    private fun copyWithProgress(input: InputStream, out: OutputStream, total: Long, title: String): Long {
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        var lastPct = -1
        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                copied += n
                if (total > 0) {
                    val pct = ((copied * 100) / total).toInt()
                    if (pct != lastPct) {
                        lastPct = pct
                        updateProgress(title, pct)
                    }
                }
            }
        } finally {
            out.flush(); out.close()
        }
        return copied
    }

    private fun updateProgress(title: String, pct: Int) {
        try {
            val n = NotificationHelper.progress(applicationContext, title, pct).build()
            NotificationManagerCompat.from(applicationContext).notify(NotificationHelper.NOTIF_ID, n)
        } catch (_: SecurityException) {
        }
    }

    private fun foregroundInfo(title: String, pct: Int): ForegroundInfo {
        val n = NotificationHelper.progress(applicationContext, title, pct).build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationHelper.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationHelper.NOTIF_ID, n)
        }
    }
}
