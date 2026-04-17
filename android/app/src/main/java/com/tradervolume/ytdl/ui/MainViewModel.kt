package com.tradervolume.ytdl.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.tradervolume.ytdl.cookies.CookiesActivity
import com.tradervolume.ytdl.download.DownloadMode
import com.tradervolume.ytdl.download.DownloadRepository
import com.tradervolume.ytdl.download.SubsFormat

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)

    fun onVideo(url: String) = repo.enqueue(url.trim(), DownloadMode.VIDEO)
    fun onAudioMp3(url: String) = repo.enqueue(url.trim(), DownloadMode.AUDIO_MP3)

    fun onSubtitles(
        url: String,
        langs: Set<String> = setOf("es", "en"),
        format: SubsFormat = SubsFormat.SRT
    ) = repo.enqueue(url.trim(), DownloadMode.SUBTITLES, langs, format)

    fun onVideoPlusSubs(
        url: String,
        langs: Set<String> = setOf("es", "en"),
        format: SubsFormat = SubsFormat.SRT
    ) = repo.enqueue(url.trim(), DownloadMode.VIDEO_PLUS_SUBS, langs, format)

    fun onPlaylistVideo(url: String) = repo.enqueue(url.trim(), DownloadMode.PLAYLIST_VIDEO)
    fun onPlaylistMp3(url: String) = repo.enqueue(url.trim(), DownloadMode.PLAYLIST_MP3)

    fun openCookiesSettings() {
        val ctx = getApplication<Application>().applicationContext
        val i = Intent(ctx, CookiesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }

    fun openHistory() {
        val ctx = getApplication<Application>().applicationContext
        val i = Intent(ctx, HistoryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
