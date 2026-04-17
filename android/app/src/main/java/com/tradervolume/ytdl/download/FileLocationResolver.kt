package com.tradervolume.ytdl.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * Writes files into Downloads/YouTubeDownloader:
 *   - API 29+ (Q): MediaStore.Downloads (scoped storage, no WRITE_EXTERNAL_STORAGE).
 *   - API 26-28: app-scoped external files dir. Not under /Downloads/, but no perms needed.
 *
 * Trade-off on API 26-28: the user will not see files in the public Downloads folder.
 * If the user insists on public Downloads on older OS versions we would need
 * WRITE_EXTERNAL_STORAGE runtime permission, which this spec explicitly rules out.
 */
object FileLocationResolver {

    private const val SUBDIR = "YouTubeDownloader"

    data class Target(val uri: Uri?, val file: File?, val displayName: String)

    fun createOutput(context: Context, displayName: String, mimeType: String): Target =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createViaMediaStore(context, displayName, mimeType)
        } else {
            createViaLegacyDir(context, displayName)
        }

    private fun createViaMediaStore(context: Context, displayName: String, mimeType: String): Target {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create MediaStore entry for $displayName")
        return Target(uri = uri, file = null, displayName = displayName)
    }

    private fun createViaLegacyDir(context: Context, displayName: String): Target {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SUBDIR).apply { mkdirs() }
        return Target(uri = null, file = File(dir, displayName), displayName = displayName)
    }

    fun openOutputStream(context: Context, target: Target): OutputStream =
        if (target.uri != null) {
            context.contentResolver.openOutputStream(target.uri, "w")
                ?: error("Could not open output stream for ${target.displayName}")
        } else {
            target.file!!.outputStream()
        }

    fun markComplete(context: Context, target: Target) {
        if (target.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(target.uri, values, null, null)
        }
    }
}
