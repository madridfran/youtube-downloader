package com.tradervolume.ytdl.download

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Vacía Descargas/YouTubeDownloader. Android (almacenamiento con ámbito, API 29+) solo deja a una
 * app ver y borrar los archivos que creó ella: los de instalaciones anteriores o copiados desde
 * otro sitio no aparecen aquí y hay que quitarlos con el gestor de archivos.
 */
object FolderCleaner {

    data class Stats(val count: Int, val bytes: Long)

    private const val SUBDIR = "YouTubeDownloader"

    /** Archivos de la carpeta que la app puede borrar (los suyos, ya terminados). */
    fun stats(context: Context): Stats {
        var count = 0
        var bytes = 0L
        forEachOwned(context) { _, size -> count++; bytes += size }
        return Stats(count, bytes)
    }

    /** Borra los archivos de la app en la carpeta. @return lo realmente borrado. */
    fun deleteAll(context: Context): Stats {
        var count = 0
        var bytes = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ids = mutableListOf<Pair<Long, Long>>()
            forEachOwned(context) { id, size -> ids += id to size }
            for ((id, size) in ids) {
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                if (runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0) > 0) {
                    count++
                    bytes += size
                }
            }
        } else {
            legacyDir(context).listFiles()?.filter { it.isFile }?.forEach { f ->
                val size = f.length()
                if (f.delete()) { count++; bytes += size }
            }
        }
        return Stats(count, bytes)
    }

    private fun forEachOwned(context: Context, action: (id: Long, size: Long) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            legacyDir(context).listFiles()?.filter { it.isFile }?.forEach { action(0L, it.length()) }
            return
        }
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/%"),
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            while (c.moveToNext()) action(c.getLong(idCol), c.getLong(sizeCol))
        }
    }

    private fun legacyDir(context: Context) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SUBDIR)
}
