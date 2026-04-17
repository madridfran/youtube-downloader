package com.tradervolume.ytdl.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradervolume.ytdl.cookies.CookiesActivity
import com.tradervolume.ytdl.cookies.CookiesStore
import com.tradervolume.ytdl.download.DownloadMode
import com.tradervolume.ytdl.download.DownloadRepository
import com.tradervolume.ytdl.download.HistoryEntry
import com.tradervolume.ytdl.download.HistoryStore
import com.tradervolume.ytdl.download.NameSanitizer
import com.tradervolume.ytdl.download.SubsFormat
import com.tradervolume.ytdl.extractor.ExtractedContent
import com.tradervolume.ytdl.extractor.OkHttpDownloader
import com.tradervolume.ytdl.extractor.PlaylistEntry
import com.tradervolume.ytdl.extractor.YoutubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaylistPreviewItem(
    val index: Int,
    val title: String,
    val url: String,
    val videoId: String,
    val durationSec: Long,
    val selected: Boolean = true
)

data class PlaylistState(
    val loading: Boolean = false,
    val error: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val items: List<PlaylistPreviewItem> = emptyList()
) {
    val isLoaded get() = items.isNotEmpty()
    val selectedCount get() = items.count { it.selected }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)

    // ---- Historial observable ----
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    // ---- Playlist preview ----
    private val _playlist = MutableStateFlow(PlaylistState())
    val playlist: StateFlow<PlaylistState> = _playlist.asStateFlow()

    init { reloadHistory() }

    fun reloadHistory() {
        viewModelScope.launch {
            _history.value = withContext(Dispatchers.IO) {
                HistoryStore.loadAll(getApplication<Application>().applicationContext)
            }
        }
    }

    // ---- Descargas single ----
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

    // ---- Playlist ----
    fun loadPlaylistPreview(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        _playlist.value = PlaylistState(loading = true)
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>().applicationContext
                val cookiesHeader = CookiesStore(ctx).loadCookieHeader()
                val downloader = OkHttpDownloader(cookieProvider = { cookiesHeader })
                val extractor = YoutubeExtractor(downloader)
                val content = withContext(Dispatchers.IO) { extractor.extract(trimmed) }
                when (content) {
                    is ExtractedContent.Playlist -> {
                        _playlist.value = PlaylistState(
                            loading = false,
                            title = content.title,
                            uploader = content.uploader,
                            items = content.items.map { it.toPreview() }
                        )
                    }
                    is ExtractedContent.Video -> {
                        _playlist.value = PlaylistState(
                            loading = false,
                            error = "La URL no es una playlist. Usa los botones de arriba para un vídeo suelto."
                        )
                    }
                }
            } catch (e: Exception) {
                _playlist.value = PlaylistState(
                    loading = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    fun togglePlaylistItem(index: Int) {
        val current = _playlist.value
        _playlist.value = current.copy(
            items = current.items.map {
                if (it.index == index) it.copy(selected = !it.selected) else it
            }
        )
    }

    fun selectAllPlaylist(selected: Boolean) {
        val current = _playlist.value
        _playlist.value = current.copy(items = current.items.map { it.copy(selected = selected) })
    }

    fun clearPlaylist() { _playlist.value = PlaylistState() }

    /** Encola uno por uno los vídeos marcados con el modo indicado. */
    fun downloadSelected(mode: DownloadMode, subsFormat: SubsFormat = SubsFormat.SRT) {
        val current = _playlist.value
        val selected = current.items.filter { it.selected }
        for (item in selected) {
            repo.enqueue(item.url, mode, setOf("es", "en"), subsFormat)
        }
    }

    // ---- Navegación ----
    fun openCookiesSettings() {
        val ctx = getApplication<Application>().applicationContext
        ctx.startActivity(
            Intent(ctx, CookiesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openHistory() {
        val ctx = getApplication<Application>().applicationContext
        ctx.startActivity(
            Intent(ctx, HistoryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

private fun PlaylistEntry.toPreview() = PlaylistPreviewItem(
    index = index,
    title = title,
    url = url,
    videoId = NameSanitizer.extractVideoId(url),
    durationSec = durationSec,
    selected = true
)
