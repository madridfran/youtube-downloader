package com.tradervolume.ytdl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradervolume.ytdl.ui.MainViewModel

/**
 * MainActivity — Sprint 1 + integración Sprints 2–5.
 *
 * - Recibe share intent con una URL de YouTube.
 * - Muestra la URL y 6 botones + ajustes de cookies.
 * - Los botones llaman al MainViewModel que encola el trabajo en WorkManager.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedirPermisoNotificaciones()
        val urlInicial = extraerUrlDelIntent(intent)
        setContent {
            MaterialTheme {
                PantallaPrincipal(urlInicial)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = extraerUrlDelIntent(intent)
        if (url != null) {
            setContent {
                MaterialTheme {
                    PantallaPrincipal(url)
                }
            }
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


@Composable
private fun PantallaPrincipal(urlInicial: String?) {
    val vm: MainViewModel = viewModel()
    var url by remember { mutableStateOf(urlInicial ?: "") }
    var ultimaAccion by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("YouTube Downloader", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(
                "www.tradervolume.com",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL de YouTube") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (url.isBlank()) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Pega una URL o comparte un vídeo desde YouTube → YouTube Downloader.",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                "¿Qué quieres descargar?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            BotonOpcion("Vídeo (MP4)") {
                vm.onVideo(url); ultimaAccion = "Vídeo encolado"
            }
            BotonOpcion("Audio (MP3 192 kbps)") {
                vm.onAudioMp3(url); ultimaAccion = "MP3 encolado"
            }
            BotonOpcion("Subtítulos — Español") {
                vm.onSubtitles(url, setOf("es")); ultimaAccion = "Subtítulos ES encolados"
            }
            BotonOpcion("Subtítulos — Inglés") {
                vm.onSubtitles(url, setOf("en")); ultimaAccion = "Subtítulos EN encolados"
            }
            BotonOpcion("Subtítulos ES + EN") {
                vm.onSubtitles(url, setOf("es", "en")); ultimaAccion = "Subtítulos ES+EN encolados"
            }
            BotonOpcion("Subtítulos + Vídeo") {
                vm.onVideoPlusSubs(url); ultimaAccion = "Vídeo + subtítulos encolados"
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Playlists",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            BotonOpcion("Playlist → Vídeos") {
                vm.onPlaylistVideo(url); ultimaAccion = "Playlist de vídeos encolada"
            }
            BotonOpcion("Playlist → MP3") {
                vm.onPlaylistMp3(url); ultimaAccion = "Playlist de MP3 encolada"
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.openCookiesSettings() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ajustes (cookies / login)") }

            ultimaAccion?.let {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(it, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Mira la notificación de progreso. Los archivos acaban en " +
                                "Descargas/YouTubeDownloader/ (Android 10+) o en la carpeta privada " +
                                "de la app en versiones anteriores.",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun BotonOpcion(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(texto)
    }
}
