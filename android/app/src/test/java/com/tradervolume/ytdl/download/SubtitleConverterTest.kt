package com.tradervolume.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubtitleConverterTest {

    private val ttml = """<?xml version="1.0" encoding="utf-8" ?>
<tt xml:lang="es" xmlns="http://www.w3.org/ns/ttml">
<head><styling><style xml:id="s1"/></styling></head>
<body region="r1"><div>
<p begin="00:00:00.840" end="00:00:08.280" style="s2">La bolsa toca techo antes de que el</p>
<p begin="00:00:04.680" end="00:00:09.960" style="s2">Capex se frene, mucho&#39;s &amp; antes.</p>
<p begin="00:01:02.000" end="00:01:03.500">línea uno<br/>línea dos</p>
</div></body></tt>"""

    @Test fun ttmlToSrt() {
        val srt = SubtitleConverter.toSrt(ttml)
        assertTrue(srt.startsWith("1\n00:00:00,840 --> 00:00:04,680\nLa bolsa toca techo antes de que el\n\n"))
        assertTrue(srt.contains("2\n00:00:04,680 --> 00:00:09,960\nCapex se frene, mucho's & antes.\n"))
        assertTrue(srt.contains("3\n00:01:02,000 --> 00:01:03,500\nlínea uno\nlínea dos\n"))
        assertFalse(srt.contains("<"))
    }

    @Test fun ttmlToTxt() {
        assertEquals(
            "La bolsa toca techo antes de que el\nCapex se frene, mucho's & antes.\nlínea uno\nlínea dos\n",
            SubtitleConverter.toTxt(ttml)
        )
    }

    @Test fun vttRollingToTxtDedupes() {
        val vtt = "WEBVTT\nKind: captions\n\n00:00:01.000 --> 00:00:02.000 align:start\nhola <c>mundo</c>\n\n" +
            "00:00:02.000 --> 00:00:03.000\nhola mundo\nqué tal\n"
        assertEquals("hola mundo\nqué tal\n", SubtitleConverter.toTxt(vtt))
        assertTrue(SubtitleConverter.toSrt(vtt).startsWith("1\n00:00:01,000 --> 00:00:02,000\nhola mundo"))
    }

    @Test fun srv1() {
        val x = """<transcript><text start="1.5" dur="2">it&amp;#39;s ok</text></transcript>"""
        assertEquals("1\n00:00:01,500 --> 00:00:03,500\nit's ok\n\n", SubtitleConverter.toSrt(x))
    }

    @Test(expected = SubtitleConverter.UnsupportedSubtitleFormat::class)
    fun emptyFails() { SubtitleConverter.parse("   ") }

    /** Muestra real descargada de YouTube (opcional): -Dydown.sample=/ruta/sample.ttml */
    @Test fun realSample() {
        val path = System.getProperty("ydown.sample") ?: System.getenv("YDOWN_SAMPLE") ?: return
        val raw = File(path).readText()
        val outDir = File(path).parentFile
        val srt = SubtitleConverter.toSrt(raw)
        val txt = SubtitleConverter.toTxt(raw)
        File(outDir, "sample.srt").writeText(srt)
        File(outDir, "sample.txt").writeText(txt)
        assertTrue(srt.lines().size > 100)
        assertFalse(srt.contains("<p"))
    }
}
