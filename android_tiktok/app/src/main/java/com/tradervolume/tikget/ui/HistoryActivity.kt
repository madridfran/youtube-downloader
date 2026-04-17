package com.tradervolume.tikget.ui

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
import com.tradervolume.tikget.download.HistoryEntry
import com.tradervolume.tikget.download.HistoryStore
import com.tradervolume.tikget.download.ShareHelper
import com.tradervolume.tikget.ui.components.TikThumb
import com.tradervolume.tikget.ui.theme.TgCyan
import com.tradervolume.tikget.ui.theme.TgPink
import com.tradervolume.tikget.ui.theme.TgSurface
import com.tradervolume.tikget.ui.theme.TikGetTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TikGetTheme { PantallaHistorial() } }
    }
}

@Composable
private fun PantallaHistorial() {
    val ctx = LocalContext.current
    val entries by HistoryStore.flow.collectAsState()

    LaunchedEffect(Unit) { HistoryStore.loadAll(ctx) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Historial",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { HistoryStore.clear(ctx) }) { Text("Vaciar") }
            }
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TgSurface)) {
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
                    items(entries, key = { it.id }) { EntryCard(it) }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: HistoryEntry) {
    val ctx = LocalContext.current
    val fecha = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(entry.timestampMs))
    val size = formatBytes(entry.sizeBytes)
    val compartibleDirecto = entry.type.startsWith("VIDEO") || entry.type == "PHOTO"

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TgSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(10.dp)) {
            TikThumb(
                thumbnailUrl = entry.thumbnailUrl,
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
                    AccionPeq("Abrir", TgCyan) { ShareHelper.openFile(ctx, entry) }
                    AccionPeq("Compartir", TgCyan) {
                        ctx.startActivity(ShareHelper.buildShareChooser(ctx, entry))
                    }
                    if (compartibleDirecto) {
                        AccionPeq("WA", TgPink) { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_WHATSAPP) }
                        AccionPeq("TG", TgPink) { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_TELEGRAM) }
                        AccionPeq("IG", TgPink) { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_INSTAGRAM) }
                    }
                }
                TextButton(onClick = { HistoryStore.remove(ctx, entry.id) }) {
                    Text("Borrar", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AccionPeq(texto: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = androidx.compose.ui.graphics.Color.Black),
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
