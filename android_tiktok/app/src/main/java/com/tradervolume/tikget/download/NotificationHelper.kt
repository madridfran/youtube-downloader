package com.tradervolume.tikget.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tradervolume.tikget.ui.HistoryActivity

object NotificationHelper {
    const val CHANNEL_ID = "tikget-downloads"
    const val NOTIF_ID = 2100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "TikGet", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    fun progress(context: Context, title: String, pct: Int): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, pct, pct == 0)
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

    fun doneForEntry(context: Context, entry: HistoryEntry): android.app.Notification {
        ensureChannel(context)
        val shareUri: Uri = ShareHelper.shareableUri(context, entry)

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, entry.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPI = PendingIntent.getActivity(
            context, entry.id.hashCode(),
            Intent.createChooser(openIntent, "Abrir").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sharePI = PendingIntent.getActivity(
            context, entry.id.hashCode() + 1,
            ShareHelper.buildShareChooser(context, entry, shareUri),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val historyPI = PendingIntent.getActivity(
            context, entry.id.hashCode() + 2,
            Intent(context, HistoryActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descarga completada")
            .setContentText(entry.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.displayPath))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPI)
            .addAction(android.R.drawable.ic_menu_share, "Compartir", sharePI)
            .addAction(android.R.drawable.ic_menu_recent_history, "Historial", historyPI)
            .build()
    }
}
