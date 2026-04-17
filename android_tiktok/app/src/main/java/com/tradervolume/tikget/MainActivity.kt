package com.tradervolume.tikget

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
import androidx.compose.material.icons.filled.Warning
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
import com.tradervolume.tikget.download.HistoryEntry
import com.tradervolume.tikget.download.ShareHelper
import com.tradervolume.tikget.ui.MainViewModel
import com.tradervolume.tikget.ui.components.LogoTG
import com.tradervolume.tikget.ui.components.TikThumb
import com.tradervolume.tikget.ui.theme.TgCyan
import com.tradervolume.tikget.ui.theme.TgPink
import com.tradervolume.tikget.ui.theme.TgSurface
import com.tradervolume.tikget.ui.theme.TikGetTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedirPermisoNotificaciones()
        val urlInicial = extraerUrlDelIntent(intent)
        setContent { TikGetTheme { PantallaPrincipal(urlInicial) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = extraerUrlDelIntent(intent)
        if (url != null) setContent { TikGetTheme { PantallaPrincipal(url) } }
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
            val regex = Regex("https?://\\S*tiktok\\.com\\S*")
            regex.find(texto)?.value ?: texto.takeIf { it.contains("tiktok", ignoreCase = true) }
        }
    }
}

@Composable
private fun PantallaPrincipal(urlInicial: String?) {
    val vm: MainViewModel = viewModel()
    var url by remember { mutableStateOf(urlInicial ?: "") }
    var estado by remember { mutableStateOf<String?>(null) }
    var mostrarAdvertenciaLogin by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val history by vm.history.collectAsState()
    val session by vm.session.collectAsState()

    LaunchedEffect(Unit) { vm.refreshSession() }

    if (mostrarAdvertenciaLogin) {
        DialogoAdvertenciaBaneo(
            onCancelar = { mostrarAdvertenciaLogin = false },
            onContinuar = {
                mostrarAdvertenciaLogin = false
                vm.openLogin()
            }
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BarraSuperior(
                onHistorial = { vm.openHistory() },
                onLogin = { mostrarAdvertenciaLogin = true }
            )

            if (session) ChipSesionActiva(onCerrarSesion = { vm.logout() })

            CampoUrl(url = url, onUrlChange = { url = it })

            val ultimo = history.firstOrNull()
            if (ultimo != null) UltimoDescargadoHero(entry = ultimo)

            if (history.size > 1) TiraHistorial(entries = history, onVerTodo = { vm.openHistory() })

            Divider(color = Color(0xFF222222))

            Text("¿Qué quieres descargar?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonCian(
                    texto = "Vídeo sin marca",
                    modifier = Modifier.weight(1f)
                ) {
                    vm.onVideoNoWm(url); estado = "Vídeo sin marca encolado"
                }
                BotonCian(
                    texto = "Con marca",
                    modifier = Modifier.weight(1f)
                ) {
                    vm.onVideoWm(url); estado = "Vídeo con marca encolado"
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonRosa(
                    texto = "Fotos (carrusel)",
                    modifier = Modifier.weight(1f)
                ) {
                    vm.onPhotos(url); estado = "Carrusel encolado"
                }
                BotonRosa(
                    texto = "Audio (música)",
                    modifier = Modifier.weight(1f)
                ) {
                    vm.onMusic(url); estado = "Música encolada"
                }
            }

            estado?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TgCyan.copy(alpha = 0.12f))
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = TgCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "TikGet · Uso personal. Respeta los derechos de autor.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ================== Componentes ==================

@Composable
private fun BarraSuperior(onHistorial: () -> Unit, onLogin: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LogoTG(size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text("TikGet", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "TikTok Downloader",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onHistorial) {
            Icon(Icons.Filled.History, contentDescription = "Historial")
        }
        IconButton(onClick = onLogin) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Login")
        }
    }
}

@Composable
private fun ChipSesionActiva(onCerrarSesion: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TgPink.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = TgPink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Sesión activa — riesgo de baneo. Usa cuenta secundaria.",
                fontSize = 11.sp,
                color = TgPink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCerrarSesion) {
                Text("Cerrar sesión", fontSize = 11.sp, color = TgPink)
            }
        }
    }
}

@Composable
private fun CampoUrl(url: String, onUrlChange: (String) -> Unit) {
    val ctx = LocalContext.current
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("URL de TikTok") },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cb?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                if (text.isNotBlank()) onUrlChange(text)
            }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Pegar") }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UltimoDescargadoHero(entry: HistoryEntry) {
    val ctx = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = TgSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            TikThumb(
                thumbnailUrl = entry.thumbnailUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).heightIn(max = 340.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { ShareHelper.openFile(ctx, entry) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TgCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Abrir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { ctx.startActivity(ShareHelper.buildShareChooser(ctx, entry)) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TgCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Compartir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ChipShare("WhatsApp", Modifier.weight(1f)) { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_WHATSAPP) }
                    ChipShare("Telegram", Modifier.weight(1f)) { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_TELEGRAM) }
                    ChipShare("Instagram", Modifier.weight(1f)) { ShareHelper.shareTo(ctx, entry, ShareHelper.PKG_INSTAGRAM) }
                }
            }
        }
    }
}

@Composable
private fun TiraHistorial(entries: List<HistoryEntry>, onVerTodo: () -> Unit) {
    val ctx = LocalContext.current
    val tope = entries.drop(1).take(10)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onVerTodo) { Text("Ver todo") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(tope, key = { it.id }) { entry ->
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 120.dp)
                        .clickable { ShareHelper.openFile(ctx, entry) }
                ) {
                    TikThumb(thumbnailUrl = entry.thumbnailUrl, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            when (entry.type) {
                                "VIDEO_NO_WM" -> "HD"
                                "VIDEO_WM" -> "WM"
                                "PHOTO" -> "FOTO"
                                "MUSIC" -> "MP3"
                                else -> entry.type
                            },
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonCian(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TgCyan, contentColor = Color.Black)
    ) { Text(texto, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun BotonRosa(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TgPink, contentColor = Color.White)
    ) { Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
private fun ChipShare(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2F2F2F), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(texto, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DialogoAdvertenciaBaneo(onCancelar: () -> Unit, onContinuar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = TgPink) },
        title = {
            Text("Riesgo de baneo", fontWeight = FontWeight.Bold, color = TgPink)
        },
        text = {
            Column {
                Text(
                    "Iniciar sesión en TikTok desde apps no oficiales puede provocar:",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("• Suspensión temporal o permanente de tu cuenta", fontSize = 12.sp)
                Text("• Bloqueos por \"actividad sospechosa\"", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recomendación: crea una cuenta secundaria solo para esto. Nunca uses tu cuenta principal.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TgCyan
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tus cookies se guardan cifradas (AES-256) en este dispositivo y no salen de aquí.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onContinuar) {
                Text("Entiendo el riesgo, continuar", color = TgPink, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
        containerColor = TgSurface
    )
}
