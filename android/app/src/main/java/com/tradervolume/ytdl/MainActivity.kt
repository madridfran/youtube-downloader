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
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.tradervolume.ytdl.download.AudioFormat
import com.tradervolume.ytdl.download.DownloadSpec
import com.tradervolume.ytdl.download.HistoryEntry
import com.tradervolume.ytdl.download.Parts
import com.tradervolume.ytdl.download.ShareHelper
import com.tradervolume.ytdl.download.SubsFormat
import com.tradervolume.ytdl.download.SubsLang
import com.tradervolume.ytdl.download.VideoQuality
import com.tradervolume.ytdl.ui.MainViewModel
import com.tradervolume.ytdl.ui.PlaylistPreviewItem
import com.tradervolume.ytdl.ui.PlaylistState
import com.tradervolume.ytdl.ui.QueueItem
import com.tradervolume.ytdl.ui.components.LogoYD
import com.tradervolume.ytdl.ui.components.YtThumb
import com.tradervolume.ytdl.ui.theme.YDownTheme
import com.tradervolume.ytdl.ui.theme.YdGreen
import com.tradervolume.ytdl.ui.theme.YdRed
import com.tradervolume.ytdl.ui.theme.YdSurface
import com.tradervolume.ytdl.ui.theme.YdSurface2

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedirPermisoNotificaciones()
        if (savedInstanceState == null) extraerUrlDelIntent(intent)?.let(vm::setUrl)
        setContent {
            YDownTheme { PantallaPrincipal(vm) }
        }
    }

    // Enlace compartido con la app ya abierta: va al ViewModel (antes se volvía a llamar a
    // setContent y la URL recordada en la composición podía quedarse con el enlace anterior).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extraerUrlDelIntent(intent)?.let(vm::setUrl)
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
private fun PantallaPrincipal(vm: MainViewModel) {
    val url by vm.url.collectAsState()
    var estado by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val history by vm.history.collectAsState()
    val playlist by vm.playlist.collectAsState()
    val spec by vm.spec.collectAsState()
    val queue by vm.queue.collectAsState()

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

            CampoUrl(url = url, onUrlChange = vm::setUrl)

            // 1) Qué descargar
            SeccionQue(spec = spec, onChange = vm::updateSpec)

            // 2) Opciones de lo elegido
            if (spec.parts.any) SeccionOpciones(spec = spec, onChange = vm::updateSpec)

            // 3) Descargar
            Text(
                spec.summary(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { estado = vm.download(url) },
                enabled = url.isNotBlank() && spec.isValid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YdRed, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Descargar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            estado?.let { EstadoCard(it) }

            if (queue.isNotEmpty()) {
                SeccionCola(queue = queue, onCancel = vm::cancel, onCancelAll = vm::cancelAll)
            }

            HorizontalDivider(color = Color(0xFF222222))

            SeccionPlaylist(
                url = url,
                state = playlist,
                spec = spec,
                puedeCargar = vm.hasPlaylist(url),
                onCargar = { vm.loadPlaylistPreview(url) },
                onToggle = vm::togglePlaylistItem,
                onSelectAll = vm::selectAllPlaylist,
                onPart = vm::toggleItemPart,
                onReset = vm::resetItemParts,
                onDescargar = {
                    val n = vm.downloadSelected()
                    estado = if (n == 0) "No hay vídeos marcados con algo que bajar" else "En cola: $n vídeos"
                },
                onCerrar = { vm.clearPlaylist() }
            )

            HorizontalDivider(color = Color(0xFF222222))

            // Último descargado + historial
            history.firstOrNull()?.let { UltimoDescargadoHero(entry = it) }
            if (history.size > 1) {
                TiraHistorial(entries = history, onVerTodo = { vm.openHistory() })
            }

            Text(
                "www.tradervolume.com",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== QUÉ Y OPCIONES ====================

@Composable
private fun SeccionQue(spec: DownloadSpec, onChange: ((DownloadSpec) -> DownloadSpec) -> Unit) {
    Column {
        Text("¿Qué quieres descargar?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "Puedes combinarlos: vídeo + subtítulos, audio + subtítulos…",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TarjetaTipo("Vídeo", Icons.Filled.Movie, spec.video, Modifier.weight(1f)) {
                onChange { it.copy(video = !it.video) }
            }
            TarjetaTipo("Audio", Icons.Filled.MusicNote, spec.audio, Modifier.weight(1f)) {
                onChange { it.copy(audio = !it.audio) }
            }
            TarjetaTipo("Subtítulos", Icons.Filled.Subtitles, spec.subs, Modifier.weight(1f)) {
                onChange { it.copy(subs = !it.subs) }
            }
        }
    }
}

@Composable
private fun SeccionOpciones(spec: DownloadSpec, onChange: ((DownloadSpec) -> DownloadSpec) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = YdSurface2),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (spec.video) {
                Opcion("Calidad de vídeo") {
                    Segmentado(
                        VideoQuality.entries.map { it to it.label },
                        esActivo = { it == spec.quality },
                        onClick = { q -> onChange { it.copy(quality = q) } }
                    )
                    if (spec.quality.needsMux) {
                        Nota("${spec.quality.label}: vídeo y audio llegan por separado y se unen en el móvil (tarda algo más).")
                    }
                }
            }
            if (spec.audio) {
                Opcion("Formato de audio") {
                    Segmentado(
                        listOf(AudioFormat.M4A to "M4A (compatible)", AudioFormat.OPUS to "Opus (más calidad)"),
                        esActivo = { it == spec.audioFormat },
                        onClick = { f -> onChange { it.copy(audioFormat = f) } }
                    )
                }
            }
            if (spec.subs) {
                Opcion("Idiomas de subtítulos") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("es" to "ES", "en" to "EN", SubsLang.ORIGINAL to "Original").forEach { (code, etiqueta) ->
                            Chip(etiqueta, code in spec.subLangs, Modifier.weight(1f)) {
                                onChange { s ->
                                    s.copy(subLangs = if (code in s.subLangs) s.subLangs - code else s.subLangs + code)
                                }
                            }
                        }
                    }
                    Nota(
                        if (spec.subLangs.isEmpty()) "Marca al menos un idioma."
                        else "Original = el idioma que se habla en el vídeo (chino, hindi, urdu…)."
                    )
                }
                Opcion("Formato de subtítulos") {
                    Segmentado(
                        listOf(SubsFormat.SRT to "SRT", SubsFormat.TXT to "TXT", SubsFormat.BOTH to "Ambos"),
                        esActivo = { it == spec.subsFormat },
                        onClick = { f -> onChange { it.copy(subsFormat = f) } }
                    )
                }
            }
        }
    }
}

// ==================== COLA ====================

@Composable
private fun SeccionCola(queue: List<QueueItem>, onCancel: (java.util.UUID) -> Unit, onCancelAll: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = YdSurface2), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Descargas (${queue.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (queue.size > 1) TextButton(onClick = onCancelAll) { Text("Cancelar todo") }
            }
            queue.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(q.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (q.running) "${q.label} · ${q.pct}%" else q.label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (q.running) {
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { q.pct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = YdRed
                            )
                        }
                    }
                    IconButton(onClick = { onCancel(q.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                    }
                }
            }
        }
    }
}

// ==================== PLAYLIST ====================

@Composable
private fun SeccionPlaylist(
    url: String,
    state: PlaylistState,
    spec: DownloadSpec,
    puedeCargar: Boolean,
    onCargar: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onPart: (Int, Char) -> Unit,
    onReset: (Int) -> Unit,
    onDescargar: () -> Unit,
    onCerrar: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playlist", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (state.isLoaded) TextButton(onClick = onCerrar) { Text("Cerrar") }
        }

        if (!state.isLoaded && !state.loading) {
            OutlinedButton(
                onClick = onCargar,
                enabled = url.isNotBlank() && puedeCargar,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (puedeCargar || url.isBlank()) "Cargar playlist" else "El enlace no es de una playlist") }
        }

        if (state.loading) {
            Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Cargando lista…", fontSize = 13.sp)
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        if (state.isLoaded) {
            Card(colors = CardDefaults.cardColors(containerColor = YdSurface2), shape = RoundedCornerShape(12.dp)) {
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
                        Text("${state.selectedCount}/${state.items.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Nota("A los marcados se les aplica lo elegido arriba (${spec.parts.label()}). Toca V, A o S en un vídeo para cambiar solo ese.")
                    Row {
                        TextButton(onClick = { onSelectAll(true) }) { Text("Todos") }
                        TextButton(onClick = { onSelectAll(false) }) { Text("Ninguno") }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        state.items.forEach { item ->
                            ItemPlaylist(
                                item = item,
                                efectivo = item.parts ?: spec.parts,
                                onToggle = { onToggle(item.index) },
                                onPart = { onPart(item.index, it) },
                                onReset = { onReset(item.index) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDescargar,
                        enabled = state.selectedCount > 0,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YdRed, contentColor = Color.White)
                    ) { Text("Descargar ${state.selectedCount} vídeos", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun ItemPlaylist(
    item: PlaylistPreviewItem,
    efectivo: Parts,
    onToggle: () -> Unit,
    onPart: (Char) -> Unit,
    onReset: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
        YtThumb(videoId = item.videoId, modifier = Modifier.size(width = 64.dp, height = 36.dp), cornerRadius = 6.dp)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${item.index}. " + item.title.ifBlank { "Sin título" },
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.durationSec > 0) {
                    Text(
                        String.format("%d:%02d", item.durationSec / 60, item.durationSec % 60),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.parts != null) {
                    Text(
                        "  personalizado · quitar",
                        fontSize = 10.sp,
                        color = YdGreen,
                        modifier = Modifier.clickable { onReset() }
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        MiniToggle("V", efectivo.video, item.parts != null) { onPart('V') }
        Spacer(Modifier.width(3.dp))
        MiniToggle("A", efectivo.audio, item.parts != null) { onPart('A') }
        Spacer(Modifier.width(3.dp))
        MiniToggle("S", efectivo.subs, item.parts != null) { onPart('S') }
    }
}

// ==================== SECCIONES EXISTENTES ====================

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
private fun EstadoCard(texto: String) {
    Card(colors = CardDefaults.cardColors(containerColor = YdSurface2)) {
        Text(
            texto,
            modifier = Modifier.padding(12.dp),
            color = YdGreen,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
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
                                "AUDIO" -> "AUDIO"
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

// ==================== COMPONENTES PEQUEÑOS ====================

@Composable
private fun TarjetaTipo(texto: String, icono: ImageVector, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (activo) YdRed else Color.Transparent)
            .border(1.dp, if (activo) YdRed else Color(0xFF3A3A3A), RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            texto,
            fontSize = 13.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
            color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Opcion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        contenido()
    }
}

@Composable
private fun Nota(texto: String) {
    Text(texto, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Selector de una opción entre varias (SRT | TXT | Ambos, 360p | 720p | 1080p…). */
@Composable
private fun <T> Segmentado(opciones: List<Pair<T, String>>, esActivo: (T) -> Boolean, onClick: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF2F2F2F), RoundedCornerShape(10.dp))
    ) {
        opciones.forEach { (valor, etiqueta) ->
            val activo = esActivo(valor)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(if (activo) YdRed else Color.Transparent)
                    .clickable { onClick(valor) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    etiqueta,
                    fontSize = 12.sp,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                    color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Opción que se marca o desmarca (idiomas de subtítulos). */
@Composable
private fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (activo) YdRed else Color.Transparent)
            .border(1.dp, if (activo) YdRed else Color(0xFF3A3A3A), RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Text(
            texto,
            fontSize = 12.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
            color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** V / A / S de cada vídeo de la playlist. Borde verde = cambiado solo para ese vídeo. */
@Composable
private fun MiniToggle(texto: String, activo: Boolean, personalizado: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (activo) YdRed else Color.Transparent)
            .border(
                1.dp,
                when {
                    personalizado -> YdGreen
                    activo -> YdRed
                    else -> Color(0xFF3A3A3A)
                },
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
    ) {
        Text(
            texto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
