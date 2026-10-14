package com.tradervolume.ytdl.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tradervolume.ytdl.cookies.CookiesActivity
import com.tradervolume.ytdl.cookies.CookiesStore
import com.tradervolume.ytdl.download.AudioFormat
import com.tradervolume.ytdl.download.DownloadRepository
import com.tradervolume.ytdl.download.DownloadSpec
import com.tradervolume.ytdl.download.DownloadWorker
import com.tradervolume.ytdl.download.HistoryEntry
import com.tradervolume.ytdl.download.HistoryStore
import com.tradervolume.ytdl.download.NameSanitizer
import com.tradervolume.ytdl.download.Parts
import com.tradervolume.ytdl.download.SubsFormat
import com.tradervolume.ytdl.download.VideoQuality
import com.tradervolume.ytdl.extractor.OkHttpDownloader
import com.tradervolume.ytdl.extractor.PlaylistEntry
import com.tradervolume.ytdl.extractor.YoutubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PlaylistPreviewItem(
    val index: Int,
    val title: String,
    val url: String,
    val videoId: String,
    val durationSec: Long,
    val selected: Boolean = true,
    /** Qué bajar de este vídeo; null = lo elegido arriba para todos. */
    val parts: Parts? = null
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

/** Elemento de la cola visible en la pantalla principal. */
data class QueueItem(val id: UUID, val title: String, val label: String, val pct: Int, val running: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)
    private val prefs = app.getSharedPreferences("ydown_ui", Context.MODE_PRIVATE)

    // ---- Historial observable (reactivo: lo alimenta HistoryStore.flow) ----
    val history: StateFlow<List<HistoryEntry>> = HistoryStore.flow

    // ---- Enlace actual (escrito, pegado o compartido desde YouTube) ----
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    fun setUrl(value: String) { _url.value = value }

    // ---- Qué descargar (se recuerda entre sesiones) ----
    private val _spec = MutableStateFlow(loadSpec())
    val spec: StateFlow<DownloadSpec> = _spec.asStateFlow()

    // ---- Cola de descargas ----
    val queue: StateFlow<List<QueueItem>> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(DownloadRepository.QUEUE)
        .map { infos ->
            infos.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                .sortedByDescending { it.state == WorkInfo.State.RUNNING }
                .map { wi ->
                    val running = wi.state == WorkInfo.State.RUNNING
                    QueueItem(
                        id = wi.id,
                        title = wi.progress.getString(DownloadWorker.P_TITLE)
                            ?: wi.tags.firstOrNull { it.startsWith(DownloadRepository.TAG_TITLE_PREFIX) }
                                ?.removePrefix(DownloadRepository.TAG_TITLE_PREFIX)
                            ?: "Vídeo",
                        label = wi.progress.getString(DownloadWorker.P_LABEL) ?: if (running) "Preparando" else "En cola",
                        pct = wi.progress.getInt(DownloadWorker.P_PCT, 0),
                        running = running
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Playlist preview ----
    private val _playlist = MutableStateFlow(PlaylistState())
    val playlist: StateFlow<PlaylistState> = _playlist.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryStore.loadAll(getApplication<Application>().applicationContext)
        }
    }

    fun reloadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryStore.loadAll(getApplication<Application>().applicationContext)
        }
    }

    fun updateSpec(change: (DownloadSpec) -> DownloadSpec) {
        val s = change(_spec.value)
        _spec.value = s
        saveSpec(s)
    }

    /**
     * Descarga el vídeo de [url] con lo elegido. Si el enlace es solo de una lista, la carga
     * abajo para elegir vídeos. @return mensaje de estado para la pantalla.
     */
    fun download(url: String): String {
        val u = url.trim()
        val s = _spec.value
        return when {
            u.isBlank() -> "Pega primero una URL de YouTube"
            !s.parts.any -> "Elige qué descargar: vídeo, audio o subtítulos"
            !s.isValid -> "Elige al menos un idioma de subtítulos"
            isPlaylistOnly(u) -> {
                loadPlaylistPreview(u)
                "Es una playlist: marca abajo qué vídeos bajar"
            }
            else -> {
                repo.enqueue(u, s)
                "En cola: ${s.summary()}"
            }
        }
    }

    fun cancel(id: UUID) = repo.cancel(id)
    fun cancelAll() = repo.cancelAll()

    // ---- Playlist ----
    fun hasPlaylist(url: String) = Regex("""[?&]list=""").containsMatchIn(url)

    /** Enlace de lista sin vídeo concreto (playlist?list=…). */
    private fun isPlaylistOnly(url: String) =
        hasPlaylist(url) && !Regex("""[?&]v=|youtu\.be/|/shorts/""").containsMatchIn(url)

    fun loadPlaylistPreview(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        if (!hasPlaylist(trimmed)) {
            _playlist.value = PlaylistState(error = "El enlace no es de una playlist (no lleva list=…).")
            return
        }
        _playlist.value = PlaylistState(loading = true)
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>().applicationContext
                val cookiesHeader = CookiesStore(ctx).loadCookieHeader()
                val extractor = YoutubeExtractor(OkHttpDownloader(cookieProvider = { cookiesHeader }))
                val content = withContext(Dispatchers.IO) { extractor.extractPlaylistOf(trimmed) }
                _playlist.value = if (content.items.isEmpty()) {
                    PlaylistState(error = "La playlist está vacía o es privada.")
                } else {
                    PlaylistState(
                        title = content.title,
                        uploader = content.uploader,
                        items = content.items.map { it.toPreview() }
                    )
                }
            } catch (e: Exception) {
                _playlist.value = PlaylistState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun togglePlaylistItem(index: Int) = updateItems { if (it.index == index) it.copy(selected = !it.selected) else it }

    fun selectAllPlaylist(selected: Boolean) = updateItems { it.copy(selected = selected) }

    /** Cambia V, A o S solo en este vídeo. Si vuelve a coincidir con lo global, deja de ser excepción. */
    fun toggleItemPart(index: Int, part: Char) {
        val global = _spec.value.parts
        updateItems { item ->
            if (item.index != index) return@updateItems item
            val p = item.parts ?: global
            val next = when (part) {
                'V' -> p.copy(video = !p.video)
                'A' -> p.copy(audio = !p.audio)
                else -> p.copy(subs = !p.subs)
            }
            item.copy(parts = next.takeIf { it != global })
        }
    }

    fun resetItemParts(index: Int) = updateItems { if (it.index == index) it.copy(parts = null) else it }

    fun clearPlaylist() { _playlist.value = PlaylistState() }

    /** Encola los vídeos marcados, cada uno con su selección. @return cuántos se encolaron. */
    fun downloadSelected(): Int {
        val s = _spec.value
        var n = 0
        for (item in _playlist.value.items.filter { it.selected }) {
            var itemSpec = s.withParts(item.parts ?: s.parts)
            if (itemSpec.subs && itemSpec.subLangs.isEmpty()) itemSpec = itemSpec.copy(subs = false)
            if (!itemSpec.parts.any) continue
            repo.enqueue(item.url, itemSpec, index = item.index, title = item.title)
            n++
        }
        return n
    }

    private fun updateItems(f: (PlaylistPreviewItem) -> PlaylistPreviewItem) {
        val current = _playlist.value
        _playlist.value = current.copy(items = current.items.map(f))
    }

    // ---- Preferencias ----
    private fun loadSpec() = DownloadSpec(
        video = prefs.getBoolean("video", true),
        audio = prefs.getBoolean("audio", false),
        subs = prefs.getBoolean("subs", false),
        quality = runCatching { VideoQuality.valueOf(prefs.getString("quality", null)!!) }.getOrDefault(VideoQuality.P720),
        audioFormat = runCatching { AudioFormat.valueOf(prefs.getString("audio_format", null)!!) }.getOrDefault(AudioFormat.M4A),
        subLangs = prefs.getStringSet("sub_langs", null)?.toSet() ?: setOf("es"),
        subsFormat = runCatching { SubsFormat.valueOf(prefs.getString("subs_format", null)!!) }.getOrDefault(SubsFormat.SRT)
    )

    private fun saveSpec(s: DownloadSpec) {
        prefs.edit()
            .putBoolean("video", s.video)
            .putBoolean("audio", s.audio)
            .putBoolean("subs", s.subs)
            .putString("quality", s.quality.name)
            .putString("audio_format", s.audioFormat.name)
            .putStringSet("sub_langs", s.subLangs)
            .putString("subs_format", s.subsFormat.name)
            .apply()
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
    durationSec = durationSec
)
