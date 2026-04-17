package com.tradervolume.ytdl.download

/**
 * Minimal WebVTT -> SubRip (.srt) converter. YouTube subtitle streams from NewPipe
 * usually arrive as VTT; this lets us save them directly as .srt without ffmpeg.
 */
object SubtitleConverter {

    fun vttToSrt(vtt: String): String {
        val lines = vtt.lines()
        // Find end of header (first blank line)
        val headerEnd = lines.indexOfFirst { it.isBlank() }.takeIf { it >= 0 } ?: 0
        val body = lines.drop(headerEnd + 1)

        val sb = StringBuilder()
        var index = 1
        var buffer = mutableListOf<String>()

        fun flush() {
            if (buffer.isEmpty()) return
            val tcIdx = buffer.indexOfFirst { it.contains("-->") }
            if (tcIdx >= 0) {
                val tcLine = buffer[tcIdx]
                    .replace('.', ',')
                    .replace(Regex("""\s+(line|position|align|size|vertical):\S+"""), "")
                val text = buffer.drop(tcIdx + 1).joinToString("\n").trim()
                if (text.isNotEmpty()) {
                    sb.append(index++).append('\n')
                    sb.append(tcLine.trim()).append('\n')
                    sb.append(stripVttTags(text)).append("\n\n")
                }
            }
            buffer = mutableListOf()
        }

        for (line in body + listOf("")) {
            if (line.isBlank()) flush() else if (!line.startsWith("NOTE")) buffer += line
        }
        return sb.toString()
    }

    private fun stripVttTags(s: String): String =
        s.replace(Regex("""<[^>]*>"""), "")
         .replace(Regex("""&amp;"""), "&")
         .replace(Regex("""&lt;"""), "<")
         .replace(Regex("""&gt;"""), ">")

    /**
     * Convert SRT to plain TXT: remove cue numbers, timecodes and blank separators,
     * keep only the spoken text (joined with line breaks). Collapses duplicate adjacent lines.
     */
    fun srtToTxt(srt: String): String {
        val timecode = Regex("""^\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->\s*\d{2}:\d{2}:\d{2}[,.]\d{3}.*""")
        val cueNumber = Regex("""^\d+$""")
        val sb = StringBuilder()
        var lastLine = ""
        for (raw in srt.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (cueNumber.matches(line)) continue
            if (timecode.matches(line)) continue
            val clean = line
                .replace(Regex("""<[^>]*>"""), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
            if (clean.isEmpty()) continue
            if (clean == lastLine) continue
            sb.append(clean).append('\n')
            lastLine = clean
        }
        return sb.toString()
    }
}
