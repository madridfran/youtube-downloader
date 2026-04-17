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

object NotificationHelper {
    const val CHANNEL_ID = "downloads"
    const val NOTIF_ID = 1000

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

    fun progress(context: Context, title: String, progress: Int, max: Int = 100): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(max, progress, progress == 0 && max == 0)
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

    /**
     * Notification for a completed download that, when tapped, opens the file
     * with the system viewer/player. Also adds a "Compartir" action button and
     * an "Historial" one that opens the in-app history screen.
     */
    fun doneForEntry(context: Context, entry: HistoryEntry): android.app.Notification {
        ensureChannel(context)

        val shareUri: Uri = ShareHelper.shareableUri(context, entry)

        // Tap: open the file
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, entry.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPI = PendingIntent.getActivity(
            context,
            entry.id.hashCode(),
            Intent.createChooser(openIntent, "Abrir").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Share action
        val sharePI = PendingIntent.getActivity(
            context,
            entry.id.hashCode() + 1,
            ShareHelper.buildShareChooser(context, entry, shareUri),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // History action
        val historyIntent = Intent(context, HistoryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val historyPI = PendingIntent.getActivity(
            context,
            entry.id.hashCode() + 2,
            historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shortPath = entry.displayPath.substringAfterLast('/').ifBlank { entry.title }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descarga completada")
            .setContentText(shortPath)
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
