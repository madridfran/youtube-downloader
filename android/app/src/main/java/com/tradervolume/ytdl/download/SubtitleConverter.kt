package com.tradervolume.ytdl.download

/**
 * Conversor de subtítulos sin dependencias (sin ffmpeg).
 *
 * YouTube (vía NewPipeExtractor) entrega hoy los subtítulos en TTML (XML), aunque
 * según la ruta también pueden llegar como WebVTT, SRT o srv1 (<transcript>).
 * Todo se normaliza a una lista de [Cue] y desde ahí se genera SRT o TXT.
 */
object SubtitleConverter {

    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    class UnsupportedSubtitleFormat(msg: String) : IllegalStateException(msg)

    // ------------------------------------------------------------------ API pública

    /** Convierte cualquier formato soportado a SRT válido. */
    fun toSrt(raw: String): String = renderSrt(parse(raw))

    /** Convierte cualquier formato soportado a texto plano (sin tiempos). */
    fun toTxt(raw: String): String = renderTxt(parse(raw))

    /** Detecta el formato y devuelve los cues. Lanza [UnsupportedSubtitleFormat] si no lo reconoce. */
    fun parse(raw: String): List<Cue> {
        val s = raw.removePrefix("﻿").trimStart()
        val cues = when {
            s.startsWith("WEBVTT") -> parseVtt(s)
            Regex("""<tt[\s>]""").containsMatchIn(s.take(4000)) -> parseTtml(s)
            s.contains("<transcript") || s.contains("<timedtext") -> parseSrv1(s)
            s.contains("-->") -> parseSrt(s)
            s.isBlank() -> throw UnsupportedSubtitleFormat("YouTube devolvió el subtítulo vacío")
            else -> throw UnsupportedSubtitleFormat(
                "Formato de subtítulo no reconocido: " + s.take(60).replace('\n', ' ')
            )
        }
        return cues.filter { it.text.isNotBlank() }.sortedBy { it.startMs }
    }

    // ------------------------------------------------------------------ Renderizado

    fun renderSrt(cues: List<Cue>): String {
        val sb = StringBuilder()
        cues.forEachIndexed { i, c ->
            // Los subtítulos automáticos (ASR) de YouTube se solapan: cada cue sigue
            // visible cuando empieza el siguiente. Recortamos el final al inicio del
            // siguiente para que el reproductor no muestre dos líneas a la vez.
            val next = cues.getOrNull(i + 1)
            val end = if (next != null && next.startMs in (c.startMs + 1) until c.endMs) next.startMs else c.endMs
            sb.append(i + 1).append('\n')
            sb.append(fmtSrt(c.startMs)).append(" --> ").append(fmtSrt(maxOf(end, c.startMs + 1))).append('\n')
            sb.append(c.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    fun renderTxt(cues: List<Cue>): String {
        val sb = StringBuilder()
        val recent = ArrayDeque<String>()
        for (c in cues) {
            for (raw in c.text.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                // Los VTT automáticos repiten la línea anterior en el cue siguiente.
                if (line in recent) continue
                sb.append(line).append('\n')
                recent.addLast(line)
                if (recent.size > 3) recent.removeFirst()
            }
        }
        return sb.toString()
    }

    private fun fmtSrt(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val f = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, f)
    }

    // ------------------------------------------------------------------ Parsers

    private val pRegex = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val attrRegex = Regex("""([\w:]+)\s*=\s*"([^"]*)"""")

    internal fun parseTtml(xml: String): List<Cue> {
        val tickRate = Regex("""ttp:tickRate\s*=\s*"(\d+)"""").find(xml)?.groupValues?.get(1)?.toLongOrNull()
        val out = mutableListOf<Cue>()
        for (m in pRegex.findAll(xml)) {
            val attrs = attrRegex.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val begin = attrs["begin"]?.let { ttmlTime(it, tickRate) } ?: continue
            val end = attrs["end"]?.let { ttmlTime(it, tickRate) }
                ?: attrs["dur"]?.let { ttmlTime(it, tickRate)?.plus(begin) }
                ?: (begin + 2000)
            val text = m.groupValues[2]
                .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""<[^>]+>"""), "")
                .let(::decodeEntities)
                .lines().joinToString("\n") { it.trim() }
                .trim()
            out += Cue(begin, end, text)
        }
        return out
    }

    /** Tiempos TTML: "HH:MM:SS.mmm", "HH:MM:SS:FF", "12.5s", "1500ms", "30000t". */
    private fun ttmlTime(v: String, tickRate: Long?): Long? {
        val t = v.trim()
        Regex("""^(\d+):(\d{2}):(\d{2})(?:[.,](\d+))?$""").matchEntire(t)?.let { g ->
            val (h, m, s) = g.destructured
            val frac = g.groupValues[4]
            val ms = if (frac.isEmpty()) 0 else (frac + "00").take(3).toLong()
            return h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1000 + ms
        }
        Regex("""^(\d+):(\d{2}):(\d{2}):(\d+)$""").matchEntire(t)?.let { g ->
            val (h, m, s, fr) = g.destructured
            return h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1000 + fr.toLong() * 1000 / 30
        }
        Regex("""^([\d.]+)(h|m|s|ms|t)$""").matchEntire(t)?.let { g ->
            val n = g.groupValues[1].toDoubleOrNull() ?: return null
            return when (g.groupValues[2]) {
                "h" -> (n * 3_600_000).toLong()
                "m" -> (n * 60_000).toLong()
                "s" -> (n * 1000).toLong()
                "ms" -> n.toLong()
                "t" -> if (tickRate != null && tickRate > 0) (n * 1000 / tickRate).toLong() else null
                else -> null
            }
        }
        return null
    }

    private val tcRegex = Regex(
        """((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})"""
    )

    internal fun parseVtt(vtt: String): List<Cue> = parseTimecodeBlocks(vtt)

    internal fun parseSrt(srt: String): List<Cue> = parseTimecodeBlocks(srt)

    /** Parser común para VTT y SRT: bloques separados por línea en blanco con una línea "-->". */
    private fun parseTimecodeBlocks(src: String): List<Cue> {
        val out = mutableListOf<Cue>()
        val blocks = src.replace("\r\n", "\n").replace('\r', '\n').split(Regex("""\n\s*\n"""))
        for (block in blocks) {
            val lines = block.lines()
            val tcIdx = lines.indexOfFirst { tcRegex.containsMatchIn(it) }
            if (tcIdx < 0) continue
            val g = tcRegex.find(lines[tcIdx])!!
            val start = clockMs(g.groupValues[1]) ?: continue
            val end = clockMs(g.groupValues[2]) ?: continue
            val text = lines.drop(tcIdx + 1)
                .joinToString("\n")
                .replace(Regex("""<[^>]+>"""), "")
                .let(::decodeEntities)
                .lines().joinToString("\n") { it.trim() }
                .trim()
            out += Cue(start, end, text)
        }
        return out
    }

    private fun clockMs(v: String): Long? {
        val parts = v.replace(',', '.').split(':')
        return try {
            val (h, m, s) = when (parts.size) {
                3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
                2 -> Triple(0L, parts[0].toLong(), parts[1])
                else -> return null
            }
            val sec = s.substringBefore('.').toLong()
            val frac = s.substringAfter('.', "0")
            h * 3_600_000 + m * 60_000 + sec * 1000 + (frac + "00").take(3).toLong()
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** srv1: <text start="1.23" dur="4.5">texto</text> */
    internal fun parseSrv1(xml: String): List<Cue> {
        val re = Regex("""<text\b([^>]*)>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(xml).mapNotNull { m ->
            val attrs = attrRegex.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val start = attrs["start"]?.toDoubleOrNull() ?: return@mapNotNull null
            val dur = attrs["dur"]?.toDoubleOrNull() ?: 2.0
            // srv1 suele venir con entidades doblemente escapadas (&amp;#39;)
            val text = decodeEntities(decodeEntities(m.groupValues[2].replace(Regex("""<[^>]+>"""), ""))).trim()
            Cue((start * 1000).toLong(), ((start + dur) * 1000).toLong(), text)
        }.toList()
    }

    private val entityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos|nbsp);""")

    internal fun decodeEntities(s: String): String = entityRegex.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
            else -> {
                val cp = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
            }
        }
    }
}
