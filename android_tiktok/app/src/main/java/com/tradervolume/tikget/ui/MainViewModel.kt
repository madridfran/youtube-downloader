package com.tradervolume.tikget.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradervolume.tikget.cookies.CookiesActivity
import com.tradervolume.tikget.cookies.CookiesStore
import com.tradervolume.tikget.download.DownloadMode
import com.tradervolume.tikget.download.DownloadRepository
import com.tradervolume.tikget.download.HistoryEntry
import com.tradervolume.tikget.download.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)
    private val cookies = CookiesStore(app)

    val history: StateFlow<List<HistoryEntry>> = HistoryStore.flow

    private val _session = MutableStateFlow(cookies.hasSession())
    val session: StateFlow<Boolean> = _session.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryStore.loadAll(getApplication<Application>().applicationContext)
        }
    }

    // Descargas
    fun onVideoNoWm(url: String) = repo.enqueue(url.trim(), DownloadMode.VIDEO_NO_WM)
    fun onVideoWm(url: String) = repo.enqueue(url.trim(), DownloadMode.VIDEO_WM)
    fun onPhotos(url: String) = repo.enqueue(url.trim(), DownloadMode.PHOTOS)
    fun onMusic(url: String) = repo.enqueue(url.trim(), DownloadMode.MUSIC)

    fun refreshSession() { _session.value = cookies.hasSession() }

    fun logout() {
        cookies.clear()
        _session.value = false
    }

    fun openLogin() {
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
