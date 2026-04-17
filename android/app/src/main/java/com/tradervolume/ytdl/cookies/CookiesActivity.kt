package com.tradervolume.ytdl.cookies

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.tradervolume.ytdl.ui.theme.YDownTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class CookiesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = CookiesStore(this)
        setContent {
            YDownTheme {
                CookiesWebViewScreen(
                    initialCookie = store.loadCookieHeader().orEmpty(),
                    onSave = { header ->
                        store.saveCookieHeader(header)
                        finish()
                    },
                    onClear = { store.clear() }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookiesWebViewScreen(
    initialCookie: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var status by remember {
        mutableStateOf(if (initialCookie.isBlank()) "Sin cookies guardadas" else "Cookies previamente guardadas")
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            "Inicia sesión en YouTube. Cuando veas tu cuenta dentro, pulsa GUARDAR.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(status, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = WebSettings.getDefaultUserAgent(ctx)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(
                        "https://accounts.google.com/ServiceLogin" +
                        "?service=youtube" +
                        "&continue=https%3A%2F%2Fwww.youtube.com%2F"
                    )
                }
            }
        )

        Row(Modifier.padding(8.dp)) {
            Button(onClick = {
                val cm = CookieManager.getInstance()
                val header = listOfNotNull(
                    cm.getCookie("https://youtube.com"),
                    cm.getCookie("https://www.youtube.com"),
                    cm.getCookie("https://accounts.google.com")
                ).filter { it.isNotBlank() }.joinToString("; ").ifBlank { null }
                if (header != null) onSave(header)
                else status = "No se detectaron cookies. Inicia sesión primero."
            }) { Text("Guardar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                onClear()
                CookieManager.getInstance().removeAllCookies(null)
                status = "Cookies borradas"
            }) { Text("Borrar") }
        }
    }
}
