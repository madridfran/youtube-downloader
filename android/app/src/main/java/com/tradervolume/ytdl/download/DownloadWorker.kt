package com.tradervolume.ytdl.download

import android.content.Context
import android.content.pm.ServiceInfo
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
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // no total timeout for big files
            .retryOnConnectionFailure(true)
            .build()
    }

    private val cookiesStore by lazy { CookiesStore(applicationContext) }
    private val downloader by lazy {
        OkHttpDownloader(cookieProvider = { cookiesStore.loadCookieHeader() })
    }
    private val extractor by lazy { YoutubeExtractor(downloader) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val mode = inputData.getString(KEY_MODE) ?: DownloadMode.VIDEO.name
        val subLangs = inputData.getString(KEY_SUB_LANGS)
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()
            ?: setOf("es")

        setForeground(foregroundInfo("Preparando…", 0))

        try {
            when (val content = extractor.extract(url)) {
                is ExtractedContent.Video -> handleSingle(content.info, mode, subLangs, index = null)
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
                        handleSingle(info, childMode, subLangs, index = entry.index)
                    }
                }
            }
            NotificationManagerCompat.from(applicationContext).notify(
                NotificationHelper.NOTIF_ID + 1,
                NotificationHelper.done(applicationContext, "Descarga completada", "Revisa Downloads/YouTubeDownloader").build()
            )
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

    private suspend fun handleSingle(info: StreamInfo, mode: String, subLangs: Set<String>, index: Int?) {
        val vid = NameSanitizer.extractVideoId(info.url)
        val title = info.name.orEmpty().ifBlank { "video" }

        when (mode) {
            DownloadMode.VIDEO.name -> downloadVideo(info, title, vid, index)
            DownloadMode.AUDIO_MP3.name -> downloadAudioMp3(info, title, vid, index)
            DownloadMode.SUBTITLES.name -> downloadSubtitles(info, title, vid, index, subLangs)
            DownloadMode.VIDEO_PLUS_SUBS.name -> {
                downloadVideo(info, title, vid, index)
                downloadSubtitles(info, title, vid, index, subLangs)
            }
            else -> throw IllegalArgumentException("Unknown mode: $mode")
        }
    }

    private suspend fun downloadVideo(info: StreamInfo, title: String, vid: String, index: Int?) {
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
        httpDownload(streamUrl, target, "$title", isFinal = true)
        FileLocationResolver.markComplete(applicationContext, target)
    }

    private fun downloadAudioMp3(info: StreamInfo, title: String, vid: String, index: Int?) {
        // v1: se guarda el stream de audio nativo (m4a normalmente) sin reconvertir.
        // La conversión real a MP3 con FFmpeg llegará en v1.1; el artefacto
        // com.arthenica:ffmpeg-kit-audio dejó de publicarse en Maven Central.
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
        httpDownload(streamUrl, target, "Audio $title", isFinal = true)
        FileLocationResolver.markComplete(applicationContext, target)
    }

    private fun downloadSubtitles(info: StreamInfo, title: String, vid: String, index: Int?, langs: Set<String>) {
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
            val name = NameSanitizer.build(index, "$title.$lang", vid, "srt")
            val target = FileLocationResolver.createOutput(applicationContext, name, "application/x-subrip")
            FileLocationResolver.openOutputStream(applicationContext, target).use { it.write(srt.toByteArray(Charsets.UTF_8)) }
            FileLocationResolver.markComplete(applicationContext, target)
        }
    }

    private fun httpDownload(url: String, target: FileLocationResolver.Target, title: String, isFinal: Boolean) {
        val req = buildRequest(url)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} descargando")
            val total = resp.body?.contentLength() ?: -1L
            val input = resp.body!!.byteStream()
            val out = FileLocationResolver.openOutputStream(applicationContext, target)
            copyWithProgress(input, out, total, title)
        }
    }

    private fun buildRequest(url: String): Request =
        Request.Builder().url(url).apply {
            header("User-Agent", "Mozilla/5.0 (Linux; Android) YouTubeDownloader/1.0")
            cookiesStore.loadCookieHeader()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }.build()

    private fun copyWithProgress(input: InputStream, out: OutputStream, total: Long, title: String) {
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
    }

    private fun updateProgress(title: String, pct: Int) {
        try {
            val n = NotificationHelper.progress(applicationContext, title, pct).build()
            NotificationManagerCompat.from(applicationContext).notify(NotificationHelper.NOTIF_ID, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied on API 33+. Downloading still works.
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
