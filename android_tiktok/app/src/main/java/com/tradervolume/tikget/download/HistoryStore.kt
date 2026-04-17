package com.tradervolume.tikget.download

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HistoryEntry(
    val id: String,
    val title: String,
    val type: String,          // "VIDEO_NO_WM" | "VIDEO_WM" | "PHOTO" | "MUSIC"
    val mime: String,
    val uri: String,
    val displayPath: String,
    val sizeBytes: Long,
    val timestampMs: Long,
    val videoId: String?,
    val thumbnailUrl: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("type", type)
        .put("mime", mime)
        .put("uri", uri)
        .put("displayPath", displayPath)
        .put("sizeBytes", sizeBytes)
        .put("timestampMs", timestampMs)
        .put("videoId", videoId ?: JSONObject.NULL)
        .put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): HistoryEntry = HistoryEntry(
            id = o.getString("id"),
            title = o.getString("title"),
            type = o.getString("type"),
            mime = o.getString("mime"),
            uri = o.getString("uri"),
            displayPath = o.getString("displayPath"),
            sizeBytes = o.optLong("sizeBytes", 0L),
            timestampMs = o.optLong("timestampMs", 0L),
            videoId = o.optString("videoId", null).takeIf { it.isNotBlank() && it != "null" },
            thumbnailUrl = o.optString("thumbnailUrl", null).takeIf { it.isNotBlank() && it != "null" }
        )
    }
}

object HistoryStore {
    private const val FILE_NAME = "history.json"
    private const val MAX_ENTRIES = 500

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private val _flow = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val flow: StateFlow<List<HistoryEntry>> = _flow.asStateFlow()

    @Volatile private var initialized = false

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (initialized) return
        _flow.value = readFromDisk(context)
        initialized = true
    }

    private fun readFromDisk(context: Context): List<HistoryEntry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.timestampMs }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun loadAll(context: Context): List<HistoryEntry> {
        ensureLoaded(context)
        return _flow.value
    }

    @Synchronized
    fun append(
        context: Context,
        title: String,
        type: String,
        mime: String,
        uri: Uri?,
        fileFallback: File?,
        sizeBytes: Long,
        videoId: String?,
        thumbnailUrl: String?
    ): HistoryEntry {
        ensureLoaded(context)
        val uriString = when {
            uri != null -> uri.toString()
            fileFallback != null -> Uri.fromFile(fileFallback).toString()
            else -> ""
        }
        val display = fileFallback?.absolutePath ?: title
        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            mime = mime,
            uri = uriString,
            displayPath = display,
            sizeBytes = sizeBytes,
            timestampMs = System.currentTimeMillis(),
            videoId = videoId,
            thumbnailUrl = thumbnailUrl
        )
        val all = _flow.value.toMutableList()
        all.add(0, entry)
        while (all.size > MAX_ENTRIES) all.removeAt(all.size - 1)
        _flow.value = all.toList()
        saveAll(context, all)
        return entry
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        ensureLoaded(context)
        val all = _flow.value.filter { it.id != id }
        _flow.value = all
        saveAll(context, all)
    }

    @Synchronized
    fun clear(context: Context) {
        _flow.value = emptyList()
        initialized = true
        file(context).delete()
    }

    private fun saveAll(context: Context, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString(), Charsets.UTF_8)
    }
}
