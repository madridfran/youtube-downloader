package com.tradervolume.tikget.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object FileLocationResolver {

    private const val SUBDIR = "TikGet"

    data class Target(val uri: Uri?, val file: File?, val displayName: String)

    fun createOutput(context: Context, displayName: String, mimeType: String): Target =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createViaMediaStore(context, displayName, mimeType)
        } else {
            createViaLegacyDir(context, displayName)
        }

    private fun createViaMediaStore(context: Context, displayName: String, mimeType: String): Target {
        val resolver = context.contentResolver
        val collection = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            val relPath = when {
                mimeType.startsWith("image/") -> "${Environment.DIRECTORY_PICTURES}/$SUBDIR"
                mimeType.startsWith("video/") -> "${Environment.DIRECTORY_MOVIES}/$SUBDIR"
                else -> "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR"
            }
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("Could not create MediaStore entry for $displayName")
        return Target(uri = uri, file = null, displayName = displayName)
    }

    private fun createViaLegacyDir(context: Context, displayName: String): Target {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), SUBDIR)
            .apply { mkdirs() }
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
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(target.uri, values, null, null)
        }
    }
}
