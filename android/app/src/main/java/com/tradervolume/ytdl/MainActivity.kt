package com.tradervolume.ytdl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MainActivity — Sprint 1.
 *
 * Objetivos:
 *  - Recibir share intent con una URL de YouTube.
 *  - Detectar si la URL es valida.
 *  - Mostrar una pantalla placeholder con la URL y botones de opcion.
 *
 * Los botones aun NO descargan nada. Eso llega en el Sprint 2.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // launchMode singleTask hace que reciba aqui los intents posteriores.
        // Para Sprint 1 basta con recrear la Activity:
        if (url != null) {
            setContent {
                MaterialTheme {
                    PantallaPrincipal(url)
                }
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
            // El texto compartido a veces incluye "Mira esto: https://..."
            // Extraer la primera URL de youtube que aparezca.
            val regex = Regex("https?://\\S*(youtube\\.com|youtu\\.be)\\S*")
            regex.find(texto)?.value
        }
    }
}


@Composable
private fun PantallaPrincipal(urlInicial: String?) {
    var opcion by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "YouTube Downloader",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = "www.tradervolume.com",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            if (urlInicial.isNullOrBlank()) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("No has compartido ninguna URL todavía.",
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Abre YouTube, elige un vídeo y pulsa Compartir → YouTube Downloader.",
                            fontSize = 13.sp
                        )
                    }
                }
                return@Column
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("URL detectada:", fontWeight = FontWeight.SemiBold)
                    Text(urlInicial, fontSize = 12.sp)
                }
            }

            Text(
                text = "¿Qué quieres descargar?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            BotonOpcion("Vídeo (MP4 - mejor calidad)") { opcion = "video" }
            BotonOpcion("Audio (MP3)") { opcion = "mp3" }
            BotonOpcion("Subtítulos — Español") { opcion = "subs_es" }
            BotonOpcion("Subtítulos — Inglés") { opcion = "subs_en" }
            BotonOpcion("Subtítulos ES + EN") { opcion = "subs_es_en" }
            BotonOpcion("Subtítulos + Vídeo") { opcion = "subs_video" }

            opcion?.let {
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Seleccionado: $it", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sprint 1 listo. La descarga real llega en Sprint 2 " +
                                "(integración NewPipeExtractor).",
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
