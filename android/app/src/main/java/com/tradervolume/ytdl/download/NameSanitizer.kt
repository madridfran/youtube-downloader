package com.tradervolume.ytdl.download

object NameSanitizer {
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun build(index: Int?, title: String, videoId: String, ext: String): String {
        val safe = title.replace(illegal, "_").trim().take(120).ifBlank { "video" }
        val prefix = if (index != null) "%02d - ".format(index) else ""
        return "$prefix$safe [$videoId].$ext"
    }

    fun extractVideoId(url: String): String {
        val m = Regex("""(?:v=|/shorts/|youtu\.be/|/embed/)([A-Za-z0-9_-]{11})""").find(url)
        return m?.groupValues?.get(1) ?: "unknown"
    }
}
