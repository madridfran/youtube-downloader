package com.tradervolume.ytdl.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tradervolume.ytdl.ui.HistoryActivity
import java.util.UUID

object NotificationHelper {
    const val CHANNEL_ID = "downloads"
    const val NOTIF_ID = 1000
    private const val GROUP_DONE = "ydown_done"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    /** Progreso de la descarga en curso, con botón "Cancelar" si se pasa [workId]. */
    fun progress(
        context: Context,
        title: String,
        progress: Int,
        max: Int = 100,
        text: String? = null,
        workId: UUID? = null
    ): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(max, progress, progress == 0 && max == 0)
            .apply {
                if (workId != null) {
                    val cancel = Intent(context, CancelReceiver::class.java).apply {
                        action = CancelReceiver.ACTION
                        putExtra(CancelReceiver.EXTRA_ID, workId.toString())
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, workId.hashCode(), cancel,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", pi)
                }
            }
    }

    fun done(context: Context, title: String, text: String): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    /** ID propio por descarga: con la cola, cada resultado queda en su notificación. */
    fun resultId(workId: UUID) = 2000 + Math.floorMod(workId.hashCode(), 100_000)

    /**
     * Resultado de una descarga. Con [entry], el toque abre el último archivo guardado y se
     * añaden "Compartir" e "Historial"; sin él (fallo, cancelación) solo informa.
     */
    fun result(context: Context, title: String, text: String, entry: HistoryEntry?): android.app.Notification {
        val b = done(context, title, text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setGroup(GROUP_DONE)
        if (entry != null) addEntryActions(context, b, entry)
        return b.build()
    }

    private fun addEntryActions(context: Context, b: NotificationCompat.Builder, entry: HistoryEntry) {
        val shareUri: Uri = ShareHelper.shareableUri(context, entry)

        // Tap: abrir el archivo
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, entry.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPI = PendingIntent.getActivity(
            context,
            entry.id.hashCode(),
            Intent.createChooser(openIntent, "Abrir").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sharePI = PendingIntent.getActivity(
            context,
            entry.id.hashCode() + 1,
            ShareHelper.buildShareChooser(context, entry, shareUri),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val historyPI = PendingIntent.getActivity(
            context,
            entry.id.hashCode() + 2,
            Intent(context, HistoryActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        b.setContentIntent(openPI)
            .addAction(android.R.drawable.ic_menu_share, "Compartir", sharePI)
            .addAction(android.R.drawable.ic_menu_recent_history, "Historial", historyPI)
    }
}
