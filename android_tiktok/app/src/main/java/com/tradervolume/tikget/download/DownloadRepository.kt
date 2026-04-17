package com.tradervolume.tikget.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

enum class DownloadMode {
    VIDEO_NO_WM,   // vídeo sin marca de agua
    VIDEO_WM,      // vídeo con marca de agua (downloadAddr)
    PHOTOS,        // carrusel de fotos
    MUSIC          // pista de audio
}

class DownloadRepository(private val context: Context) {

    fun enqueue(url: String, mode: DownloadMode) {
        val data = workDataOf(
            DownloadWorker.KEY_URL to url,
            DownloadWorker.KEY_MODE to mode.name
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
