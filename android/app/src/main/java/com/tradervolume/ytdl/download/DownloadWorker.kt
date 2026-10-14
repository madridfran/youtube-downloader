package com.tradervolume.ytdl.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tradervolume.ytdl.cookies.CookiesStore
import com.tradervolume.ytdl.extractor.OkHttpDownloader
import com.tradervolume.ytdl.extractor.YoutubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class NoSubtitlesException(msg: String) : IllegalStateException(msg)

/**
 * Descarga UN vídeo según su [DownloadSpec]: vídeo (360p directo o 720p/1080p uniendo vídeo y
 * audio), audio y/o subtítulos. Las descargas van en cola (ver [DownloadRepository]); este worker
 * devuelve siempre success para no cancelar los siguientes y deja el resultado en su notificación.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        // Progreso visible en la cola de la pantalla principal
        const val P_TITLE = "p_title"
        const val P_LABEL = "p_label"
        const val P_PCT = "p_pct"
        const val OUT_RESULT = "ydown_result"

        /** Trozos de 10 MB con cabecera Range: googlevideo limita la velocidad de las peticiones enteras. */
        private const val CHUNK = 10L shl 20
        private const val UA = "Mozilla/5.0 (Linux; Android) YouTubeDownloader/1.0"
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

    // Último archivo guardado — el toque de la notificación lo abre.
    private var lastEntry: HistoryEntry? = null
    private var displayTitle = ""
    private var lastPct = -1
    private var lastLabel = ""
    private val tempFiles = mutableListOf<File>()
    /** Archivo de Descargas a medio escribir: se elimina si hay error o cancelación. */
    private var openTarget: FileLocationResolver.Target? = null
    /** Audio M4A ya bajado para unirlo al vídeo HD: se reutiliza si también se pide el audio. */
    private var cachedM4a: File? = null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(DownloadSpec.KEY_URL) ?: return@withContext Result.success()
        val spec = DownloadSpec.fromData(inputData)
        val index = inputData.getInt(DownloadSpec.KEY_INDEX, -1).takeIf { it > 0 }
        displayTitle = inputData.getString(DownloadSpec.KEY_TITLE) ?: url

        if (CancelRegistry.isCancelled(id)) {
            CancelRegistry.clear(id)
            return@withContext Result.success(workDataOf(OUT_RESULT to "Cancelada"))
        }
        try {
            setForeground(foregroundInfo("Preparando…"))
        } catch (e: IllegalStateException) {
            // Android 12+ puede negar el servicio en primer plano si la app está en segundo plano.
            android.util.Log.w("YTDL", "Sin servicio en primer plano", e)
        }

        val saved = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var title: String
        var text: String
        try {
            val info = extractor.extractVideo(url).info
            val vid = NameSanitizer.extractVideoId(info.url)
            val name = info.name.orEmpty().ifBlank { "video" }
            displayTitle = name
            report("Preparando", 0)

            if (spec.video) saved += downloadVideo(info, name, vid, index, spec.quality, warnings)
            if (spec.audio) saved += downloadAudio(info, name, vid, index, spec.audioFormat)
            if (spec.subs) {
                val (files, warning) = downloadSubtitles(info, name, vid, index, spec.subLangs, spec.subsFormat)
                saved += files
                warning?.let(warnings::add)
            }

            title = when {
                saved.isEmpty() && spec.subs && !spec.video && !spec.audio -> "Sin subtítulos"
                saved.isEmpty() -> "Nada descargado"
                warnings.isNotEmpty() -> "Completada con avisos"
                else -> "Descarga completada"
            }
            text = buildString {
                append(name)
                if (saved.isNotEmpty()) append("\nGuardado: ").append(saved.joinToString(" · "))
                warnings.forEach { append("\nAviso: ").append(it) }
            }
        } catch (e: Exception) {
            openTarget?.let { FileLocationResolver.discard(applicationContext, it) }
            val cancelled = e is UserCancelledException || isStopped
            title = if (cancelled) "Cancelada" else "Descarga fallida"
            text = if (cancelled) displayTitle else {
                android.util.Log.e("YTDL", "Descarga fallida", e)
                displayTitle + "\n" + buildString {
                    append(e.javaClass.simpleName)
                    e.message?.let { append(": ").append(it) }
                    e.cause?.let { c ->
                        append(" | causa: ").append(c.javaClass.simpleName)
                        c.message?.let { append(": ").append(it) }
                    }
                }.take(240)
            }
        } finally {
            tempFiles.forEach { it.delete() }
            CancelRegistry.clear(id)
        }

        try {
            NotificationManagerCompat.from(applicationContext).notify(
                NotificationHelper.resultId(id),
                NotificationHelper.result(applicationContext, title, text, lastEntry.takeIf { saved.isNotEmpty() })
            )
        } catch (_: SecurityException) {
        }
        Result.success(workDataOf(OUT_RESULT to "$title: $text"))
    }

    // ------------------------------------------------------------------ Vídeo y audio

    /**
     * 360p: el fichero combinado vídeo+audio (itag 18) si NewPipe lo ofrece; en algunos vídeos no
     * lo devuelve (visto con EDeK6LYVxRw el 2026-10-13), y entonces se une como en HD.
     * 720p/1080p: vídeo H.264 + audio AAC unidos en el móvil; sin H.264, último recurso el 360p combinado.
     * @return descripción de lo guardado ("vídeo 720p").
     */
    private fun downloadVideo(
        info: StreamInfo, name: String, vid: String, index: Int?,
        quality: VideoQuality, warnings: MutableList<String>
    ): String {
        if (!quality.needsMux) extractor.pickBestMuxed(info)?.let { return saveMuxed(it, name, vid, index) }

        val v = extractor.pickVideoOnlyAvc(info, quality.maxHeight)
        val a = extractor.pickAudio(info, AudioFormat.M4A)?.takeIf { it.format == MediaFormat.M4A }
        if (v != null && a != null) {
            val tier = YoutubeExtractor.tierOf(v)
            if (tier < quality.maxHeight) warnings += "este vídeo no tiene ${quality.label}; se ha bajado en ${tier}p"
            val vFile = temp("v.mp4")
            val aFile = temp("a.m4a")
            val mFile = temp("mux.mp4")
            fetch(v.content, vFile.outputStream(), "Vídeo ${tier}p")
            fetch(a.content, aFile.outputStream(), "Audio")
            cachedM4a = aFile
            Mp4Muxer.mux(
                vFile, aFile, mFile,
                onProgress = { report("Uniendo vídeo y audio", it) },
                checkCancelled = ::checkCancelled
            )
            vFile.delete()
            saveFile(mFile, NameSanitizer.build(index, "$name.${tier}p", vid, "mp4"), "video/mp4", "VIDEO", vid)
            return "vídeo ${tier}p"
        }

        val muxed = extractor.pickBestMuxed(info)
            ?: throw IllegalStateException("Sin vídeo descargable: ni combinado ni H.264 + AAC por separado")
        warnings += "sin vídeo ${quality.label} en H.264 para este vídeo; se ha bajado el combinado"
        return saveMuxed(muxed, name, vid, index)
    }

    /** Vídeo que ya trae el audio (progresivo): se descarga directo a Descargas. */
    private fun saveMuxed(stream: VideoStream, name: String, vid: String, index: Int?): String {
        val ext = (stream.format?.suffix ?: "mp4").lowercase()
        val res = YoutubeExtractor.tierOf(stream).takeIf { it > 0 }?.let { "${it}p" } ?: "360p"
        val target = FileLocationResolver.createOutput(
            applicationContext, NameSanitizer.build(index, "$name.$res", vid, ext), "video/$ext"
        )
        openTarget = target
        val bytes = fetch(stream.content, FileLocationResolver.openOutputStream(applicationContext, target), "Vídeo $res")
        FileLocationResolver.markComplete(applicationContext, target)
        openTarget = null
        recordHistory(name, "VIDEO", "video/$ext", target, bytes, vid)
        return "vídeo $res"
    }

    private fun downloadAudio(info: StreamInfo, name: String, vid: String, index: Int?, format: AudioFormat): String {
        val cached = cachedM4a
        if (format == AudioFormat.M4A && cached != null && cached.exists()) {
            saveFile(cached, NameSanitizer.build(index, name, vid, "m4a"), "audio/mp4", "AUDIO", vid)
            return "audio M4A"
        }
        val audio = extractor.pickAudio(info, format) ?: throw IllegalStateException("Sin audio disponible")
        val (ext, mime) = when (audio.format) {
            MediaFormat.M4A -> "m4a" to "audio/mp4"
            MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "webm" to "audio/webm"
            MediaFormat.OPUS -> "opus" to "audio/ogg"
            MediaFormat.MP3 -> "mp3" to "audio/mpeg"
            else -> (audio.format?.suffix ?: "audio") to "audio/*"
        }
        val target = FileLocationResolver.createOutput(applicationContext, NameSanitizer.build(index, name, vid, ext), mime)
        openTarget = target
        val bytes = fetch(audio.content, FileLocationResolver.openOutputStream(applicationContext, target), "Audio")
        FileLocationResolver.markComplete(applicationContext, target)
        openTarget = null
        recordHistory(name, "AUDIO", mime, target, bytes, vid)
        return "audio ${ext.uppercase()}"
    }

    // ------------------------------------------------------------------ Subtítulos

    /**
     * Descarga los subtítulos de [langs] como SRT, TXT o ambos.
     * @return archivos guardados ("SRT es-auto") y, si falta algo, el aviso para la notificación.
     */
    private fun downloadSubtitles(
        info: StreamInfo,
        title: String,
        vid: String,
        index: Int?,
        langs: Set<String>,
        format: SubsFormat
    ): Pair<List<String>, String?> {
        fun baseLang(tag: String?) = tag?.substringBefore('-')?.lowercase()?.ifBlank { null } ?: "xx"
        report("Subtítulos", 0)

        val picks = buildList<Pair<String, SubtitlesStream>> {
            // Idiomas concretos: una pista por idioma, la manual antes que la automática (ASR).
            extractor.pickSubtitles(info, langs.filterNot(SubsLang::isOriginal).toSet())
                .groupBy { baseLang(it.languageTag) }
                .forEach { (lang, list) -> add(lang to list.sortedBy { it.isAutoGenerated }.first()) }
            // Idioma original: cada pista automática (ASR) corresponde a un audio hablado. Los vídeos
            // con varias pistas de audio (doblajes) traen una ASR por idioma (p. ej. bn + en + hi):
            // se bajan todas, prefiriendo la manual del mismo idioma si existe. Sin ASR, la primera pista.
            if (langs.any(SubsLang::isOriginal)) {
                val autos = info.subtitles.filter { it.isAutoGenerated }.distinctBy { baseLang(it.languageTag) }
                if (autos.isEmpty()) {
                    info.subtitles.firstOrNull()?.let { add(baseLang(it.languageTag) to it) }
                } else {
                    for (auto in autos) {
                        val lang = baseLang(auto.languageTag)
                        val manual = info.subtitles.firstOrNull { !it.isAutoGenerated && baseLang(it.languageTag) == lang }
                        add(lang to (manual ?: auto))
                    }
                }
            }
        }.distinctBy { (lang, sub) -> lang to sub.isAutoGenerated }

        if (picks.isEmpty()) {
            val disponibles = info.subtitles.map { baseLang(it.languageTag) }.distinct().sorted()
            val msg = if (disponibles.isEmpty()) {
                "Este vídeo no tiene subtítulos"
            } else {
                "Sin subtítulos ${langs.joinToString("+") { SubsLang.label(it) }}. " +
                    "Disponibles: ${disponibles.joinToString(", ") { it.uppercase() }} → usa «Original»"
            }
            return emptyList<String>() to msg
        }

        val written = mutableListOf<String>()
        var lastError: Exception? = null
        for ((lang, sub) in picks) {
            checkCancelled()
            // Un fallo en un idioma (HTTP, formato no reconocido, pista vacía) no tumba el resto.
            val cues = try {
                val url = sub.content ?: throw NoSubtitlesException("Subtítulos $lang sin URL")
                val body = client.newCall(buildRequest(url)).execute().use {
                    if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code} subtítulos $lang")
                    it.body?.string().orEmpty()
                }
                SubtitleConverter.parse(body).ifEmpty { throw NoSubtitlesException("Subtítulos $lang vacíos") }
            } catch (e: Exception) {
                android.util.Log.w("YTDL", "Subtítulos $lang descartados — $title", e)
                lastError = e
                continue
            }
            val suffix = if (sub.isAutoGenerated) "$lang-auto" else lang

            if (format.wantsSrt) {
                writeSubsFile(title, suffix, vid, index, "srt", "application/x-subrip", "SUBS_SRT",
                    SubtitleConverter.renderSrt(cues))
                written += "SRT $suffix"
            }
            if (format.wantsTxt) {
                writeSubsFile(title, suffix, vid, index, "txt", "text/plain", "SUBS_TXT",
                    SubtitleConverter.renderTxt(cues))
                written += "TXT $suffix"
            }
        }
        val warning = lastError?.let { e ->
            if (written.isEmpty()) e.message ?: "No se pudo generar ningún subtítulo"
            else "algún subtítulo falló (${e.message})"
        }
        return written to warning
    }

    private fun writeSubsFile(
        title: String, lang: String, vid: String, index: Int?,
        ext: String, mime: String, type: String, payload: String
    ) {
        val name = NameSanitizer.build(index, "$title.$lang", vid, ext)
        val target = FileLocationResolver.createOutput(applicationContext, name, mime)
        openTarget = target
        val bytes = payload.toByteArray(Charsets.UTF_8)
        FileLocationResolver.openOutputStream(applicationContext, target).use { it.write(bytes) }
        FileLocationResolver.markComplete(applicationContext, target)
        openTarget = null
        recordHistory("$title.$lang.$ext", type, mime, target, bytes.size.toLong(), vid)
    }

    // ------------------------------------------------------------------ Ficheros y red

    private fun temp(suffix: String) = File(applicationContext.cacheDir, "ydown-$id-$suffix").also { tempFiles += it }

    /** Copia un fichero temporal a Descargas/YouTubeDownloader y lo apunta en el historial. */
    private fun saveFile(src: File, displayName: String, mime: String, type: String, vid: String) {
        val target = FileLocationResolver.createOutput(applicationContext, displayName, mime)
        openTarget = target
        val bytes = src.inputStream().use { input ->
            FileLocationResolver.openOutputStream(applicationContext, target).use { out ->
                copy(input, out, 0, src.length(), "Guardando")
            }
        }
        FileLocationResolver.markComplete(applicationContext, target)
        openTarget = null
        recordHistory(displayName.substringBeforeLast(" ["), type, mime, target, bytes, vid)
    }

    /** Descarga [url] en [out] (que se cierra al terminar) por trozos con Range. */
    private fun fetch(url: String, out: OutputStream, label: String): Long = out.use { fetchInto(url, it, label) }

    private fun fetchInto(url: String, out: OutputStream, label: String): Long {
        var pos = 0L
        var total = -1L
        while (true) {
            checkCancelled()
            val req = buildRequest(url).newBuilder().header("Range", "bytes=$pos-${pos + CHUNK - 1}").build()
            val got = client.newCall(req).execute().use { resp ->
                if (resp.code == 416 && pos > 0) return pos
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} descargando ${label.lowercase()}")
                val body = resp.body ?: throw IllegalStateException("Respuesta vacía descargando ${label.lowercase()}")
                if (resp.code == 200) {
                    // El servidor ignoró Range: llega el archivo entero de una vez.
                    if (pos > 0) throw IllegalStateException("El servidor dejó de aceptar descargas por trozos")
                    return copy(body.byteStream(), out, 0, body.contentLength(), label)
                }
                total = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: total
                copy(body.byteStream(), out, pos, total, label)
            }
            pos += got
            if (total in 0..pos || got < CHUNK) return pos
        }
    }

    private fun copy(input: InputStream, out: OutputStream, base: Long, total: Long, label: String): Long {
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            checkCancelled()
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            copied += n
            if (total > 0) report(label, ((base + copied) * 100 / total).toInt())
        }
        out.flush()
        return copied
    }

    private fun buildRequest(url: String): Request =
        Request.Builder().url(url).apply {
            header("User-Agent", UA)
            cookiesStore.loadCookieHeader()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }.build()

    private fun checkCancelled() {
        if (isStopped || CancelRegistry.isCancelled(id)) throw UserCancelledException()
    }

    private fun recordHistory(
        title: String,
        type: String,
        mime: String,
        target: FileLocationResolver.Target,
        sizeBytes: Long,
        videoId: String?
    ) {
        lastEntry = HistoryStore.append(
            context = applicationContext,
            title = title,
            type = type,
            mime = mime,
            uri = target.uri,
            fileFallback = target.file,
            sizeBytes = sizeBytes,
            videoId = videoId
        )
    }

    // ------------------------------------------------------------------ Progreso

    /** Actualiza la cola de la app (setProgress) y la notificación, solo si cambia algo. */
    private fun report(label: String, pct: Int) {
        if (pct == lastPct && label == lastLabel) return
        lastPct = pct
        lastLabel = label
        setProgressAsync(workDataOf(P_TITLE to displayTitle, P_LABEL to label, P_PCT to pct))
        try {
            NotificationManagerCompat.from(applicationContext).notify(
                NotificationHelper.NOTIF_ID,
                NotificationHelper.progress(applicationContext, displayTitle, pct, text = label, workId = id).build()
            )
        } catch (_: SecurityException) {
        }
    }

    private fun foregroundInfo(label: String): ForegroundInfo {
        val n = NotificationHelper.progress(applicationContext, displayTitle, 0, text = label, workId = id).build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationHelper.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationHelper.NOTIF_ID, n)
        }
    }
}
