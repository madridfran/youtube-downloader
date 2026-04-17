package com.tradervolume.ytdl

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradervolume.ytdl.download.DownloadMode
import com.tradervolume.ytdl.download.HistoryEntry
import com.tradervolume.ytdl.download.ShareHelper
import com.tradervolume.ytdl.download.SubsFormat
import com.tradervolume.ytdl.ui.MainViewModel
import com.tradervolume.ytdl.ui.PlaylistPreviewItem
import com.tradervolume.ytdl.ui.components.LogoYD
import com.tradervolume.ytdl.ui.components.YtThumb
import com.tradervolume.ytdl.ui.theme.YDownTheme
import com.tradervolume.ytdl.ui.theme.YdGreen
import com.tradervolume.ytdl.ui.theme.YdRed
import com.tradervolume.ytdl.ui.theme.YdSurface
import com.tradervolume.ytdl.ui.theme.YdSurface2

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedirPermisoNotificaciones()
        val urlInicial = extraerUrlDelIntent(intent)
        setContent {
            YDownTheme { PantallaPrincipal(urlInicial) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = extraerUrlDelIntent(intent)
        if (url != null) {
            setContent { YDownTheme { PantallaPrincipal(url) } }
        }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }

    private fun extraerUrlDelIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }?.let { texto ->
            val regex = Regex("https?://\\S*(youtube\\.com|youtu\\.be)\\S*")
            regex.find(texto)?.value
        }
    }
}

// ==================== PANTALLA PRINCIPAL ====================

@Composable
private fun PantallaPrincipal(urlInicial: String?) {
    val vm: MainViewModel = viewModel()
    var url by remember { mutableStateOf(urlInicial ?: "") }
    var subsEnTxt by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val history by vm.history.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val subsFormat = if (subsEnTxt) SubsFormat.TXT else SubsFormat.SRT

    // Refrescar historial al recomponer (p.ej. tras descarga)
    LaunchedEffect(Unit) { vm.reloadHistory() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BarraSuperior(
                onHistorial = { vm.openHistory() },
                onCookies = { vm.openCookiesSettings() }
            )

            CampoUrl(url = url, onUrlChange = { url = it })

            // Hero: último vídeo descargado
            val ultimo = history.firstOrNull()
            if (ultimo != null) {
                UltimoDescargadoHero(entry = ultimo)
            }

            // Tira de historial
            if (history.size > 1) {
                TiraHistorial(entries = history, onVerTodo = { vm.openHistory() })
            }

            Divider(color = Color(0xFF222222))

            // Qué descargar — 2 botones minimalistas
            Text(
                "¿Qué quieres descargar?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonPrincipal(
                    texto = "Vídeo",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.onVideo(url); estado = "Vídeo encolado"
                    }
                )
                BotonPrincipal(
                    texto = "Audio M4A",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.onAudioMp3(url); estado = "Audio encolado"
                    }
                )
            }

            // Subtítulos — grid 2x2
            SubtitulosGrid(
                subsEnTxt = subsEnTxt,
                onToggleTxt = { subsEnTxt = it },
                onAccion = { langs, plusVideo ->
                    if (plusVideo) {
                        vm.onVideoPlusSubs(url, langs, subsFormat)
                    } else {
                        vm.onSubtitles(url, langs, subsFormat)
                    }
                    estado = "Subtítulos encolados (${subsFormat.name})"
                }
            )

            Divider(color = Color(0xFF222222))

            // Playlist
            SeccionPlaylist(
                url = url,
                state = playlist,
                onCargar = { vm.loadPlaylistPreview(url) },
                onToggle = { vm.togglePlaylistItem(it) },
                onSelectAll = { vm.selectAllPlaylist(it) },
                onDescargar = { mode, format ->
                    vm.downloadSelected(mode, format)
                    estado = "Descargando ${playlist.selectedCount} ítems (${mode.name})"
                },
                onCerrar = { vm.clearPlaylist() }
            )

            Spacer(Modifier.height(4.dp))

            // Estado
            estado?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = YdRed.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = YdGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "www.tradervolume.com",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== SECCIONES ====================

@Composable
private fun BarraSuperior(onHistorial: () -> Unit, onCookies: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LogoYD(size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text("YDown", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "YouTube Downloader",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onHistorial) {
            Icon(Icons.Filled.History, contentDescription = "Historial")
        }
        IconButton(onClick = onCookies) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Ajustes")
        }
    }
}

@Composable
private fun CampoUrl(url: String, onUrlChange: (String) -> Unit) {
    val ctx = LocalContext.current
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("URL de YouTube") },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cb?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                if (text.isNotBlank()) onUrlChange(text)
            }) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "Pegar")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UltimoDescargadoHero(entry: HistoryEntry) {
    val ctx = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = YdSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            YtThumb(
                videoId = entry.videoId,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                cornerRadius = 0.dp
            )
            Column(Modifier.padding(12.dp)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BotonIconoTexto(
                        texto = "Abrir",
                        onClick = { ShareHelper.openFile(ctx, entry) },
                        modifier = Modifier.weight(1f)
                    )
                    BotonIconoTexto(
                        texto = "Compartir",
                        onClick = { ctx.startActivity(ShareHelper.buildShareChooser(ctx, entry)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ChipCompartir("WhatsApp", Modifier.weight(1f)) {
                        ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_WHATSAPP)
                    }
                    ChipCompartir("Telegram", Modifier.weight(1f)) {
                        ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_TELEGRAM)
                    }
                    ChipCompartir("Instagram", Modifier.weight(1f)) {
                        ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_INSTAGRAM)
                    }
                }
            }
        }
    }
}

@Composable
private fun TiraHistorial(entries: List<HistoryEntry>, onVerTodo: () -> Unit) {
    val ctx = LocalContext.current
    val tope = entries.drop(1).take(10) // el primero ya está en el hero
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Historial",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onVerTodo) { Text("Ver todo") }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tope, key = { it.id }) { entry ->
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 72.dp)
                        .clickable { ShareHelper.openFile(ctx, entry) }
                ) {
                    YtThumb(
                        videoId = entry.videoId,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Etiqueta del tipo
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            when (entry.type) {
                                "VIDEO" -> "MP4"
                                "AUDIO" -> "M4A"
                                "SUBS_SRT" -> "SRT"
                                "SUBS_TXT" -> "TXT"
                                else -> entry.type
                            },
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitulosGrid(
    subsEnTxt: Boolean,
    onToggleTxt: (Boolean) -> Unit,
    onAccion: (langs: Set<String>, plusVideo: Boolean) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Subtitles,
                contentDescription = null,
                tint = YdGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Subtítulos",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (subsEnTxt) "TXT" else "SRT",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = subsEnTxt,
                onCheckedChange = onToggleTxt
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CeldaSubs("ES", Modifier.weight(1f)) { onAccion(setOf("es"), false) }
            CeldaSubs("EN", Modifier.weight(1f)) { onAccion(setOf("en"), false) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CeldaSubs("ES + EN", Modifier.weight(1f)) { onAccion(setOf("es", "en"), false) }
            CeldaSubs("+ Vídeo", Modifier.weight(1f)) { onAccion(setOf("es", "en"), true) }
        }
    }
}

@Composable
private fun SeccionPlaylist(
    url: String,
    state: com.tradervolume.ytdl.ui.PlaylistState,
    onCargar: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDescargar: (DownloadMode, SubsFormat) -> Unit,
    onCerrar: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Playlist",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (state.isLoaded) {
                TextButton(onClick = onCerrar) { Text("Cerrar") }
            }
        }

        if (!state.isLoaded && !state.loading) {
            OutlinedButton(
                onClick = onCargar,
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cargar playlist") }
        }

        if (state.loading) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Cargando lista…", fontSize = 13.sp)
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        if (state.isLoaded) {
            // Fila acciones inline: Vídeo | MP3 | Subs (con TXT / SRT)
            Card(
                colors = CardDefaults.cardColors(containerColor = YdSurface2),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.title ?: "Playlist",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${state.selectedCount}/${state.items.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniBoton("Vídeo", Modifier.weight(1f)) {
                            onDescargar(DownloadMode.VIDEO, SubsFormat.SRT)
                        }
                        MiniBoton("MP3", Modifier.weight(1f)) {
                            onDescargar(DownloadMode.AUDIO_MP3, SubsFormat.SRT)
                        }
                        MiniBoton("Subs TXT", Modifier.weight(1f)) {
                            onDescargar(DownloadMode.SUBTITLES, SubsFormat.TXT)
                        }
                        MiniBoton("Subs SRT", Modifier.weight(1f)) {
                            onDescargar(DownloadMode.SUBTITLES, SubsFormat.SRT)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = { onSelectAll(true) }) { Text("Todos") }
                        TextButton(onClick = { onSelectAll(false) }) { Text("Ninguno") }
                    }
                    // Lista de ítems (máx alto ~400dp con scroll interno)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        state.items.forEach { it -> ItemPlaylist(it, onToggle = { onToggle(it.index) }) }
                    }
                }
            }
        }
    }
}

// ==================== COMPONENTES PEQUEÑOS ====================

@Composable
private fun BotonPrincipal(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = YdRed, contentColor = Color.White)
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CeldaSubs(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, YdRed.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = YdGreen)
    ) {
        Text(texto, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun BotonIconoTexto(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = YdRed)
    ) { Text(texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ChipCompartir(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2F2F2F), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = YdGreen)
            Spacer(Modifier.width(4.dp))
            Text(texto, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MiniBoton(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = YdRed)
    ) { Text(texto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ItemPlaylist(item: PlaylistPreviewItem, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
        YtThumb(
            videoId = item.videoId,
            modifier = Modifier.size(width = 72.dp, height = 42.dp),
            cornerRadius = 6.dp
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { "Sin título" },
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.durationSec > 0) {
                val m = item.durationSec / 60
                val s = item.durationSec % 60
                Text(
                    String.format("%d:%02d", m, s),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
