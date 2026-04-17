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

enum class SubsFormat { SRT, TXT }

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
