package com.tradervolume.tikget.download

object NameSanitizer {
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun build(author: String?, desc: String?, videoId: String, ext: String, index: Int? = null): String {
        val parts = listOfNotNull(author, desc).joinToString(" - ")
        val safe = parts.replace(illegal, "_").trim().take(100).ifBlank { "tiktok" }
        val idx = if (index != null) " (${index})" else ""
        return "$safe [$videoId]$idx.$ext"
    }
}
