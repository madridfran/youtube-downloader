package com.tradervolume.ytdl.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradervolume.ytdl.download.HistoryEntry
import com.tradervolume.ytdl.download.HistoryStore
import com.tradervolume.ytdl.download.ShareHelper
import com.tradervolume.ytdl.ui.components.YtThumb
import com.tradervolume.ytdl.ui.theme.YDownTheme
import com.tradervolume.ytdl.ui.theme.YdRed
import com.tradervolume.ytdl.ui.theme.YdSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YDownTheme { PantallaHistorial() }
        }
    }
}

@Composable
private fun PantallaHistorial() {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(HistoryStore.loadAll(ctx)) }
    fun refresh() { entries = HistoryStore.loadAll(ctx) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Historial",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { HistoryStore.clear(ctx); refresh() }) {
                    Text("Vaciar")
                }
            }
            Spacer(Modifier.height(8.dp))

            if (entries.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = YdSurface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sin descargas todavía.", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cuando descargues algo aparecerá aquí con botones para abrir y compartir.",
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(entry = entry, onDeleted = { refresh() })
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
    val compartibleDirecto = entry.type == "VIDEO" || entry.type == "AUDIO"

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = YdSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(10.dp)) {
            YtThumb(
                videoId = entry.videoId,
                modifier = Modifier.size(width = 110.dp, height = 62.dp),
                cornerRadius = 8.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${entry.type} · $size · $fecha",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccionPeq("Abrir") { ShareHelper.openFile(ctx, entry) }
                    AccionPeq("Compartir") {
                        ctx.startActivity(ShareHelper.buildShareChooser(ctx, entry))
                    }
                    if (compartibleDirecto) {
                        AccionPeq("WA") { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_WHATSAPP) }
                        AccionPeq("TG") { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_TELEGRAM) }
                        AccionPeq("IG") { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_INSTAGRAM) }
                    }
                }
                TextButton(onClick = { HistoryStore.remove(ctx, entry.id); onDeleted() }) {
                    Text("Borrar", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AccionPeq(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = YdRed),
        shape = RoundedCornerShape(8.dp)
    ) { Text(texto, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
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
