package com.tradervolume.ytdl.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.tradervolume.ytdl.cookies.CookiesActivity
import com.tradervolume.ytdl.download.DownloadMode
import com.tradervolume.ytdl.download.DownloadRepository

/**
 * Bridge between the 6 Compose buttons (Sprint 1) and the Sprint 2-5 pipeline.
 *
 * Wire each button in your MainActivity like:
 *
 *   val vm: MainViewModel = viewModel()
 *   Button(onClick = { vm.onVideo(url) }) { Text("Video") }
 *   Button(onClick = { vm.onAudioMp3(url) }) { Text("MP3") }
 *   Button(onClick = { vm.onSubtitles(url) }) { Text("Subs") }
 *   Button(onClick = { vm.onVideoPlusSubs(url) }) { Text("Video + Subs") }
 *   Button(onClick = { vm.onPlaylistVideo(url) }) { Text("Playlist video") }
 *   Button(onClick = { vm.onPlaylistMp3(url) }) { Text("Playlist MP3") }
 *   Button(onClick = { vm.openCookiesSettings() }) { Text("Ajustes") }
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)

    fun onVideo(url: String) = repo.enqueue(url.trim(), DownloadMode.VIDEO)
    fun onAudioMp3(url: String) = repo.enqueue(url.trim(), DownloadMode.AUDIO_MP3)

    fun onSubtitles(url: String, langs: Set<String> = setOf("es", "en")) =
        repo.enqueue(url.trim(), DownloadMode.SUBTITLES, langs)

    fun onVideoPlusSubs(url: String, langs: Set<String> = setOf("es", "en")) =
        repo.enqueue(url.trim(), DownloadMode.VIDEO_PLUS_SUBS, langs)

    fun onPlaylistVideo(url: String) = repo.enqueue(url.trim(), DownloadMode.PLAYLIST_VIDEO)
    fun onPlaylistMp3(url: String) = repo.enqueue(url.trim(), DownloadMode.PLAYLIST_MP3)

    fun openCookiesSettings() {
        val ctx = getApplication<Application>().applicationContext
        val i = Intent(ctx, CookiesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
