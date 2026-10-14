package com.tradervolume.ytdl.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID

enum class SubsFormat(val label: String) {
    SRT("SRT"), TXT("TXT"), BOTH("SRT + TXT");

    val wantsSrt get() = this == SRT || this == BOTH
    val wantsTxt get() = this == TXT || this == BOTH
}

/**
 * Pseudo-idioma de subtítulos que viaja junto a los códigos ISO ("es", "en"):
 * [ORIGINAL] = la pista del idioma hablado del vídeo (zh, hi, ur…), sea cual sea.
 *
 * La traducción automática de YouTube (parámetro tlang) se probó el 2026-10-13 y no es viable:
 * HTTP 429 desde NewPipe y desde yt-dlp; el cliente web exige un PO token para esas pistas.
 */
object SubsLang {
    const val ORIGINAL = "orig"

    fun isOriginal(token: String) = token == ORIGINAL

    fun label(token: String): String = if (isOriginal(token)) "idioma original" else token.uppercase()
}

/**
 * Cola de descargas: una cadena única de WorkManager, así se bajan de una en una y en orden
 * (en paralelo se pisaban las notificaciones y YouTube corta antes con HTTP 429).
 * El worker devuelve siempre success para que un fallo no cancele el resto de la cadena.
 */
class DownloadRepository(private val context: Context) {

    companion object {
        const val QUEUE = "ydown-queue"
        const val TAG = "ydown"
        const val TAG_TITLE_PREFIX = "title:"
    }

    fun enqueue(url: String, spec: DownloadSpec, index: Int? = null, title: String? = null): UUID {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(spec.toData(url, index, title))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .addTag(TAG_TITLE_PREFIX + (title ?: url))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        return req.id
    }

    /**
     * Cancela un elemento sin romper la cadena: cancelWorkById también cancelaría todos los
     * que van detrás, así que se marca y el propio worker se detiene (o se salta al empezar).
     */
    fun cancel(id: UUID) = CancelRegistry.cancel(id)

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(QUEUE)
    }
}
