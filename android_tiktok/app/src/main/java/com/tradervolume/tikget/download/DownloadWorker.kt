package com.tradervolume.tikget.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tradervolume.tikget.cookies.CookiesStore
import com.tradervolume.tikget.extractor.TikMedia
import com.tradervolume.tikget.extractor.TiktokExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_URL = "url"
        const val KEY_MODE = "mode"
    }

    // CookieJar compartido: TikTok emite cookies (tt_chain_token, etc.) al
    // servir la página y el CDN (*.tiktokcdn.com) las exige para no devolver 403.
    private val cookieJar = JavaNetCookieJar(
        CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cookieJar(cookieJar)
            .build()
    }

    private val cookiesStore by lazy { CookiesStore(applicationContext) }
    private val extractor by lazy {
        TiktokExtractor(
            cookieProvider = { cookiesStore.loadCookieHeader() },
            client = client
        )
    }

    // UA escritorio idéntico al del extractor: TikTok CDN valida coherencia UA/cookies.
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val modeStr = inputData.getString(KEY_MODE) ?: DownloadMode.VIDEO_NO_WM.name
        val mode = runCatching { DownloadMode.valueOf(modeStr) }.getOrDefault(DownloadMode.VIDEO_NO_WM)

        setForeground(foregroundInfo("Analizando enlace…", 0))

        try {
            val media = extractor.extract(url)
            when (mode) {
                DownloadMode.VIDEO_NO_WM -> downloadVideo(media, preferNoWm = true)
                DownloadMode.VIDEO_WM   -> downloadVideo(media, preferNoWm = false)
                DownloadMode.PHOTOS     -> downloadPhotos(media)
                DownloadMode.MUSIC      -> downloadMusic(media)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TIKGET", "Descarga fallida", e)
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

    // ---- Vídeo ----
    private suspend fun downloadVideo(media: TikMedia, preferNoWm: Boolean) {
        val primary = if (preferNoWm) media.videoNoWatermarkUrl else media.videoWatermarkUrl
        val secondary = if (preferNoWm) media.videoWatermarkUrl else media.videoNoWatermarkUrl
        val src = primary ?: secondary
            ?: throw IllegalStateException("No hay URL de vídeo disponible en el bloque de TikTok.")

        val title = buildTitle(media)
        val name = NameSanitizer.build(media.author, media.description, media.videoId, "mp4")
        val target = FileLocationResolver.createOutput(applicationContext, name, "video/mp4")
        val bytes = httpDownload(src, target, title, referer = "https://www.tiktok.com/")
        FileLocationResolver.markComplete(applicationContext, target)

        val typeTag = if (preferNoWm && primary != null) "VIDEO_NO_WM" else "VIDEO_WM"
        val entry = HistoryStore.append(
            context = applicationContext,
            title = title,
            type = typeTag,
            mime = "video/mp4",
            uri = target.uri,
            fileFallback = target.file,
            sizeBytes = bytes,
            videoId = media.videoId,
            thumbnailUrl = media.thumbnailUrl
        )
        postDoneFor(entry)
    }

    // ---- Fotos (carrusel) ----
    private suspend fun downloadPhotos(media: TikMedia) {
        if (media.photoUrls.isEmpty()) {
            throw IllegalStateException("Este TikTok no es un carrusel de fotos.")
        }
        val title = buildTitle(media)
        var lastEntry: HistoryEntry? = null
        media.photoUrls.forEachIndexed { i, photoUrl ->
            setForeground(foregroundInfo("Foto ${i + 1}/${media.photoUrls.size}", 0))
            val name = NameSanitizer.build(media.author, media.description, media.videoId, "jpg", i + 1)
            val target = FileLocationResolver.createOutput(applicationContext, name, "image/jpeg")
            val bytes = httpDownload(photoUrl, target, "$title [${i + 1}]", referer = "https://www.tiktok.com/")
            FileLocationResolver.markComplete(applicationContext, target)
            lastEntry = HistoryStore.append(
                context = applicationContext,
                title = "$title [${i + 1}/${media.photoUrls.size}]",
                type = "PHOTO",
                mime = "image/jpeg",
                uri = target.uri,
                fileFallback = target.file,
                sizeBytes = bytes,
                videoId = media.videoId,
                thumbnailUrl = media.thumbnailUrl ?: photoUrl
            )
        }
        lastEntry?.let { postDoneFor(it) }
    }

    // ---- Audio / música ----
    private suspend fun downloadMusic(media: TikMedia) {
        val src = media.musicUrl
            ?: throw IllegalStateException("Sin URL de música en el bloque de TikTok.")
        val title = (media.musicTitle ?: buildTitle(media))
        val name = NameSanitizer.build(media.author, media.musicTitle ?: media.description, media.videoId, "mp3")
        val target = FileLocationResolver.createOutput(applicationContext, name, "audio/mpeg")
        val bytes = httpDownload(src, target, "Audio $title", referer = "https://www.tiktok.com/")
        FileLocationResolver.markComplete(applicationContext, target)
        val entry = HistoryStore.append(
            context = applicationContext,
            title = title,
            type = "MUSIC",
            mime = "audio/mpeg",
            uri = target.uri,
            fileFallback = target.file,
            sizeBytes = bytes,
            videoId = media.videoId,
            thumbnailUrl = media.thumbnailUrl
        )
        postDoneFor(entry)
    }

    // ---- Red ----
    private fun httpDownload(
        url: String,
        target: FileLocationResolver.Target,
        title: String,
        referer: String
    ): Long {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .header("Accept", "*/*")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .header("Range", "bytes=0-")
            .header("Sec-Fetch-Dest", "video")
            .header("Sec-Fetch-Mode", "no-cors")
            .header("Sec-Fetch-Site", "same-site")
            .apply {
                // Cookies manuales del login (si hay) — añadimos al CookieJar.
                cookiesStore.loadCookieHeader()?.takeIf { it.isNotBlank() }
                    ?.let { header("Cookie", it) }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            // 200 OK o 206 Partial Content son válidos (pedimos Range).
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code} descargando")
            }
            val total = resp.body?.contentLength() ?: -1L
            val input = resp.body!!.byteStream()
            val out = FileLocationResolver.openOutputStream(applicationContext, target)
            return copyWithProgress(input, out, total, title)
        }
    }

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
                    if (pct != lastPct) { lastPct = pct; updateProgress(title, pct) }
                }
            }
        } finally { out.flush(); out.close() }
        return copied
    }

    // ---- Helpers ----
    private fun buildTitle(media: TikMedia): String {
        val a = media.author?.let { "@$it" }.orEmpty()
        val d = media.description?.take(50).orEmpty()
        return listOf(a, d).filter { it.isNotBlank() }.joinToString(" — ")
            .ifBlank { "TikTok ${media.videoId}" }
    }

    private fun postDoneFor(entry: HistoryEntry) {
        NotificationManagerCompat.from(applicationContext).notify(
            NotificationHelper.NOTIF_ID + 1,
            NotificationHelper.doneForEntry(applicationContext, entry)
        )
    }

    private fun updateProgress(title: String, pct: Int) {
        try {
            val n = NotificationHelper.progress(applicationContext, title, pct).build()
            NotificationManagerCompat.from(applicationContext).notify(NotificationHelper.NOTIF_ID, n)
        } catch (_: SecurityException) { }
    }

    private fun foregroundInfo(title: String, pct: Int): ForegroundInfo {
        val n = NotificationHelper.progress(applicationContext, title, pct).build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationHelper.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NotificationHelper.NOTIF_ID, n)
    }
}
