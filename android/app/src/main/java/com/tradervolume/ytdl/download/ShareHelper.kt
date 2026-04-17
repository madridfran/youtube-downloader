package com.tradervolume.ytdl.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helpers para compartir un archivo descargado a otras apps (WhatsApp, Telegram,
 * Instagram, cualquier app que acepte SEND con ese MIME).
 *
 * - Si la entrada viene de MediaStore (API 29+), su URI ya es content:// y se
 *   puede compartir directamente con FLAG_GRANT_READ_URI_PERMISSION.
 * - Si viene de un file:// (API 26-28, carpeta privada), lo exponemos vía
 *   FileProvider (authority "<package>.fileprovider").
 */
object ShareHelper {

    private fun providerAuthority(context: Context) = "${context.packageName}.fileprovider"

    /** URI que se puede pasar a otra app sin que reviente por FileUriExposedException. */
    fun shareableUri(context: Context, entry: HistoryEntry): Uri {
        val raw = Uri.parse(entry.uri)
        return if (raw.scheme == "file") {
            val path = raw.path ?: return raw
            FileProvider.getUriForFile(context, providerAuthority(context), File(path))
        } else {
            raw
        }
    }

    /** Intent listo para abrir un chooser general (Compartir con…). */
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

    /**
     * Lanza un share directo a una app concreta (WhatsApp, Telegram, Instagram).
     * Si la app no está instalada cae al chooser genérico.
     */
    fun shareTo(context: Context, entry: HistoryEntry, targetPackage: String) {
        val shareUri = shareableUri(context, entry)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = entry.mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
            putExtra(Intent.EXTRA_TEXT, entry.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(targetPackage)
        }
        val installed = context.packageManager.queryIntentActivities(send, 0).isNotEmpty()
        if (installed) {
            context.startActivity(send)
        } else {
            context.startActivity(buildShareChooser(context, entry, shareUri))
        }
    }

    /** Lanza el visor/reproductor del sistema para abrir el archivo. */
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
