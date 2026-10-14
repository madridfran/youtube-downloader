package com.tradervolume.ytdl.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Descargas que el usuario ha cancelado. Vive en el proceso (UI y worker comparten proceso). */
object CancelRegistry {
    private val ids: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun cancel(id: UUID) { ids += id }
    fun isCancelled(id: UUID) = id in ids
    fun clear(id: UUID) { ids -= id }
}

class UserCancelledException : RuntimeException("Cancelada por el usuario")

/** Botón "Cancelar" de la notificación de progreso. */
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(EXTRA_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(CancelRegistry::cancel)
    }

    companion object {
        const val ACTION = "com.tradervolume.ytdl.CANCEL"
        const val EXTRA_ID = "work_id"
    }
}
