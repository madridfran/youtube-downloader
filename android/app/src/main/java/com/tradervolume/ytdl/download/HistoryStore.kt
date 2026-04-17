package com.tradervolume.ytdl.download

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persistent list of completed downloads. Stored as JSON in the app's internal files dir.
 * No external DB, no external libs — just a plain JSON array.
 */
data class HistoryEntry(
    val id: String,
    val title: String,
    val type: String,          // "VIDEO" | "AUDIO" | "SUBS_SRT" | "SUBS_TXT"
    val mime: String,
    val uri: String,           // content:// or file://
    val displayPath: String,   // human-readable, e.g. "Descargas/YouTubeDownloader/foo.mp4"
    val sizeBytes: Long,
    val timestampMs: Long,
    val videoId: String?
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
            videoId = o.optString("videoId", null).takeIf { it.isNotBlank() && it != "null" }
        )
    }
}

object HistoryStore {

    private const val FILE_NAME = "history.json"
    private const val MAX_ENTRIES = 500

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun loadAll(context: Context): List<HistoryEntry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.timestampMs }
        } catch (_: Exception) {
            emptyList()
        }
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
        videoId: String?
    ): HistoryEntry {
        val uriString = when {
            uri != null -> uri.toString()
            fileFallback != null -> Uri.fromFile(fileFallback).toString()
            else -> ""
        }
        val display = fileFallback?.absolutePath
            ?: "Descargas/YouTubeDownloader/$title"
        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            mime = mime,
            uri = uriString,
            displayPath = display,
            sizeBytes = sizeBytes,
            timestampMs = System.currentTimeMillis(),
            videoId = videoId
        )
        val all = loadAll(context).toMutableList()
        all.add(0, entry)
        if (all.size > MAX_ENTRIES) {
            while (all.size > MAX_ENTRIES) all.removeAt(all.size - 1)
        }
        saveAll(context, all)
        return entry
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val all = loadAll(context).filter { it.id != id }
        saveAll(context, all)
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    private fun saveAll(context: Context, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString(), Charsets.UTF_8)
    }
}
