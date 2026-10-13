package com.tradervolume.ytdl.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

enum class DownloadMode {
    VIDEO, AUDIO_MP3, SUBTITLES, VIDEO_PLUS_SUBS, PLAYLIST_VIDEO, PLAYLIST_MP3
}

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

class DownloadRepository(private val context: Context) {

    fun enqueue(
        url: String,
        mode: DownloadMode,
        subLangs: Set<String> = setOf("es", "en"),
        subsFormat: SubsFormat = SubsFormat.SRT
    ) {
        val data = workDataOf(
            DownloadWorker.KEY_URL to url,
            DownloadWorker.KEY_MODE to mode.name,
            DownloadWorker.KEY_SUB_LANGS to subLangs.joinToString(","),
            DownloadWorker.KEY_SUBS_FORMAT to subsFormat.name
        )
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
