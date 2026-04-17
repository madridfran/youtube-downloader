package com.tradervolume.ytdl.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradervolume.ytdl.download.HistoryEntry
import com.tradervolume.ytdl.download.HistoryStore
import com.tradervolume.ytdl.download.ShareHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PantallaHistorial()
            }
        }
    }
}

@Composable
private fun PantallaHistorial() {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(HistoryStore.loadAll(ctx)) }

    fun refresh() { entries = HistoryStore.loadAll(ctx) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "Historial de descargas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    HistoryStore.clear(ctx); refresh()
                }) { Text("Vaciar") }
            }
            Spacer(Modifier.height(8.dp))

            if (entries.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sin descargas todavía.", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cuando descargues un vídeo, audio o subtítulos aparecerán aquí " +
                                "con botones para abrir y compartir.",
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onDeleted = { refresh() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: HistoryEntry, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val fecha = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(entry.timestampMs))
    val size = formatBytes(entry.sizeBytes)
    val isVideo = entry.type == "VIDEO"
    val isAudio = entry.type == "AUDIO"

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(entry.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "${entry.type} · $size · $fecha",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                entry.displayPath,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { ShareHelper.openFile(ctx, entry) }) { Text("Abrir") }
                OutlinedButton(onClick = {
                    ctx.startActivity(ShareHelper.buildShareChooser(ctx, entry))
                }) { Text("Compartir") }
            }

            if (isVideo || isAudio) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_WHATSAPP)
                    }) { Text("WhatsApp") }
                    TextButton(onClick = {
                        ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_TELEGRAM)
                    }) { Text("Telegram") }
                    TextButton(onClick = {
                        ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_INSTAGRAM)
                    }) { Text("Instagram") }
                }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                HistoryStore.remove(ctx, entry.id); onDeleted()
            }) { Text("Borrar del historial") }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}
