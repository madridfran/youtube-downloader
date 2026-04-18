package com.tradervolume.tikget.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Resultado extraído de una URL de TikTok.
 */
data class TikMedia(
    val videoId: String,
    val author: String?,
    val description: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val videoNoWatermarkUrl: String?,
    val videoWatermarkUrl: String?,
    val photoUrls: List<String>,
    val musicTitle: String?,
    val musicUrl: String?
) {
    val isPhotoCarousel: Boolean get() = photoUrls.isNotEmpty()
    val isVideo: Boolean get() = !isPhotoCarousel &&
        (!videoNoWatermarkUrl.isNullOrBlank() || !videoWatermarkUrl.isNullOrBlank())
}

/**
 * Parsea páginas de TikTok (www.tiktok.com/@user/video/ID, vm.tiktok.com/xxx,
 * vt.tiktok.com/xxx) y extrae las URLs de los streams.
 *
 * Estrategia:
 *  1. Si la URL es un shortener, seguimos redirecciones hasta la URL canónica.
 *  2. Descargamos el HTML con cabeceras tipo-navegador (+ cookies si las hay).
 *  3. Buscamos el JSON "__UNIVERSAL_DATA_FOR_REHYDRATION__" y lo parseamos.
 *  4. Mapeamos los campos relevantes a [TikMedia].
 *
 * Todas las llamadas son bloqueantes; llamar desde Dispatchers.IO.
 */
class TiktokExtractor(
    private val cookieProvider: () -> String? = { null },
    client: OkHttpClient? = null
) {

    private val client: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // UA de escritorio: TikTok sirve la página "webapp" completa (con
    // __UNIVERSAL_DATA_FOR_REHYDRATION__ y webapp.video-detail). Con UA móvil
    // redirige a m.tiktok.com que usa otro layout.
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun extract(url: String): TikMedia {
        val canonical = forceDesktopHost(resolveUrl(url.trim()))
        val html = fetchHtml(canonical)
        val data = findUniversalData(html)
            ?: throw IllegalStateException(
                "No se encontró el bloque de datos de TikTok. " +
                    "Puede ser un enlace no soportado o un cambio reciente del frontend."
            )
        return parseUniversalData(data)
    }

    // ---- Red ----

    /** Fuerza www.tiktok.com y quita m./vm./vt. — necesario para recibir el JSON webapp. */
    private fun forceDesktopHost(url: String): String {
        return url
            .replace(Regex("^https?://m\\.tiktok\\.com", RegexOption.IGNORE_CASE), "https://www.tiktok.com")
            .replace(Regex("^https?://vm\\.tiktok\\.com", RegexOption.IGNORE_CASE), "https://www.tiktok.com")
            .replace(Regex("^https?://vt\\.tiktok\\.com", RegexOption.IGNORE_CASE), "https://www.tiktok.com")
    }

    private fun resolveUrl(url: String): String {
        // Los enlaces vm.tiktok.com y vt.tiktok.com son shorteners con 301/302.
        val req = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", userAgent)
            .apply {
                cookieProvider()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun fetchHtml(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .apply {
                cookieProvider()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} pidiendo TikTok")
            return resp.body?.string().orEmpty()
        }
    }

    // ---- Parseo ----

    private fun findUniversalData(html: String): JSONObject? {
        val re = Regex(
            """<script[^>]+id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val json = re.find(html)?.groupValues?.get(1)?.trim() ?: return null
        return runCatching { JSONObject(json) }.getOrNull()
    }

    private fun parseUniversalData(root: JSONObject): TikMedia {
        // Ruta típica: __DEFAULT_SCOPE__.webapp.video-detail.itemInfo.itemStruct
        val scope = root.optJSONObject("__DEFAULT_SCOPE__")
            ?: throw IllegalStateException("JSON sin __DEFAULT_SCOPE__")

        // Buscar video-detail en cualquiera de sus variantes conocidas, o recorrer las
        // claves de __DEFAULT_SCOPE__ buscando la que contenga itemInfo.itemStruct.
        val videoDetail = scope.optJSONObject("webapp.video-detail")
            ?: scope.optJSONObject("webapp.reflow.video-detail")
            ?: findVideoDetailFallback(scope)
            ?: run {
                val keys = scope.keys().asSequence().toList().joinToString(",")
                throw IllegalStateException(
                    "Sin video-detail (claves: ${keys.take(200)})"
                )
            }

        if (videoDetail.optInt("statusCode", 0) != 0 &&
            videoDetail.optInt("statusCode", -1) != -1
        ) {
            val msg = videoDetail.optString("statusMsg", "")
            throw IllegalStateException(
                "TikTok devolvió error: ${msg.ifBlank { "statusCode=${videoDetail.optInt("statusCode")}" }}"
            )
        }

        val item = videoDetail.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
            ?: throw IllegalStateException("Sin itemStruct")

        val id = item.optString("id")
        val desc = item.optString("desc").takeIf { it.isNotBlank() }
        val author = item.optJSONObject("author")?.optString("uniqueId").orEmpty()
            .ifBlank { item.optJSONObject("author")?.optString("nickname").orEmpty() }
            .takeIf { it.isNotBlank() }

        val video = item.optJSONObject("video")
        val cover = video?.optString("cover").takeIf { !it.isNullOrBlank() }
        val duration = video?.optLong("duration", 0L) ?: 0L

        // Vídeo sin marca de agua — normalmente "playAddr"
        val playAddr = video?.optString("playAddr").takeIf { !it.isNullOrBlank() }
        val downloadAddr = video?.optString("downloadAddr").takeIf { !it.isNullOrBlank() }

        // Algunos scrapes devuelven una lista de bitrates
        val bitrateInfo = video?.optJSONArray("bitrateInfo")
        val bitratePlay = firstUrlFromBitrate(bitrateInfo)
        val noWm = playAddr ?: bitratePlay

        // Carrusel de fotos
        val photoUrls = mutableListOf<String>()
        val imagePost = item.optJSONObject("imagePost")
        if (imagePost != null) {
            val images = imagePost.optJSONArray("images")
            if (images != null) {
                for (i in 0 until images.length()) {
                    val img = images.optJSONObject(i) ?: continue
                    val imageURLlist = img.optJSONObject("imageURL")?.optJSONArray("urlList")
                    val best = imageURLlist?.let { pickLast(it) }
                    if (!best.isNullOrBlank()) photoUrls += best
                }
            }
        }

        // Música
        val music = item.optJSONObject("music")
        val musicTitle = music?.optString("title").takeIf { !it.isNullOrBlank() }
        val musicUrl = music?.optString("playUrl").takeIf { !it.isNullOrBlank() }

        return TikMedia(
            videoId = id,
            author = author,
            description = desc,
            durationSec = duration,
            thumbnailUrl = cover,
            videoNoWatermarkUrl = noWm,
            videoWatermarkUrl = downloadAddr,
            photoUrls = photoUrls,
            musicTitle = musicTitle,
            musicUrl = musicUrl
        )
    }

    /** Recorre las claves de __DEFAULT_SCOPE__ buscando una que contenga itemInfo.itemStruct. */
    private fun findVideoDetailFallback(scope: JSONObject): JSONObject? {
        val keys = scope.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val obj = scope.optJSONObject(k) ?: continue
            val itemStruct = obj.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
            if (itemStruct != null) return obj
        }
        return null
    }

    private fun firstUrlFromBitrate(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val playAddr = item.optJSONObject("PlayAddr")
            val list = playAddr?.optJSONArray("UrlList")
            val pick = list?.let { pickLast(it) }
            if (!pick.isNullOrBlank()) return pick
        }
        return null
    }

    private fun pickLast(arr: JSONArray): String? {
        if (arr.length() == 0) return null
        return arr.optString(arr.length() - 1).takeIf { it.isNotBlank() }
    }
}
