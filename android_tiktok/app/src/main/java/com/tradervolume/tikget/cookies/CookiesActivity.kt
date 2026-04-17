package com.tradervolume.tikget.cookies

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tradervolume.tikget.ui.theme.TikGetTheme

class CookiesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = CookiesStore(this)
        setContent {
            TikGetTheme {
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

@Composable
private fun CookiesWebViewScreen(
    initialCookie: String,
    onSave: (String?) -> Unit,
    onClear: () -> Unit
) {
    var cookieHeader by remember { mutableStateOf(initialCookie) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Inicia sesión en TikTok dentro de este WebView. Al tocar 'Guardar' las cookies " +
                    "se cifran localmente y se usan para descargas privadas.",
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/122 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val host = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                                if (host.contains("tiktok.com")) {
                                    val c = CookieManager.getInstance().getCookie("https://www.tiktok.com/")
                                    if (!c.isNullOrBlank()) cookieHeader = c
                                }
                            }
                        }
                        loadUrl("https://www.tiktok.com/login")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onClear(); onSave(null) },
                    modifier = Modifier.weight(1f)
                ) { Text("Borrar") }
                Button(
                    onClick = { onSave(cookieHeader.takeIf { it.isNotBlank() }) },
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar") }
            }
        }
    }
}
