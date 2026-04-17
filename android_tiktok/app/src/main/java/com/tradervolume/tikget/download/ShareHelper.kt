package com.tradervolume.tikget.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    private fun providerAuthority(context: Context) = "${context.packageName}.fileprovider"

    fun shareableUri(context: Context, entry: HistoryEntry): Uri {
        val raw = Uri.parse(entry.uri)
        return if (raw.scheme == "file") {
            val path = raw.path ?: return raw
            FileProvider.getUriForFile(context, providerAuthority(context), File(path))
        } else raw
    }

    fun buildShareChooser(context: Context, entry: HistoryEntry, uri: Uri? = null): Intent {
        val shareUri = uri ?: shareableUri(context, entry)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = entry.mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
            putExtra(Intent.EXTRA_TEXT, entry.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartir ${entry.title}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareTo(context: Context, entry: HistoryEntry, targetPackage: String) {
        val shareUri = shareableUri(context, entry)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = entry.mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TEXT, entry.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(targetPackage)
        }
        val installed = context.packageManager.queryIntentActivities(send, 0).isNotEmpty()
        if (installed) context.startActivity(send)
        else context.startActivity(buildShareChooser(context, entry, shareUri))
    }

    fun openFile(context: Context, entry: HistoryEntry) {
        val shareUri = shareableUri(context, entry)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, entry.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(view, "Abrir con").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_TELEGRAM = "org.telegram.messenger"
    const val PKG_INSTAGRAM = "com.instagram.android"
}
