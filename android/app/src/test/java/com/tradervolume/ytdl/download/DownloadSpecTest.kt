package com.tradervolume.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSpecTest {

    @Test fun summaryCombinations() {
        assertEquals("Vídeo 720p", DownloadSpec().summary())
        assertEquals(
            "Vídeo 1080p + subtítulos ES+idioma original (SRT + TXT)",
            DownloadSpec(quality = VideoQuality.P1080, subs = true, subLangs = setOf("orig", "es"), subsFormat = SubsFormat.BOTH).summary()
        )
        assertEquals(
            "Audio M4A + subtítulos EN (SRT)",
            DownloadSpec(video = false, audio = true, subs = true, subLangs = setOf("en")).summary()
        )
        assertEquals("Nada seleccionado", DownloadSpec(video = false).summary())
    }

    @Test fun validity() {
        assertTrue(DownloadSpec().isValid)
        assertFalse(DownloadSpec(video = false).isValid)
        assertFalse(DownloadSpec(video = false, subs = true, subLangs = emptySet()).isValid)
        assertTrue(DownloadSpec(video = false, subs = true, subLangs = setOf("orig")).isValid)
    }

    @Test fun dataRoundTrip() {
        val s = DownloadSpec(
            video = true, audio = true, subs = true,
            quality = VideoQuality.P1080, audioFormat = AudioFormat.OPUS,
            subLangs = setOf("es", "orig"), subsFormat = SubsFormat.TXT
        )
        val d = s.toData("https://youtu.be/x", 3, "Título")
        assertEquals(s, DownloadSpec.fromData(d))
        assertEquals(3, d.getInt(DownloadSpec.KEY_INDEX, -1))
        assertEquals(-1, DownloadSpec().toData("u", null, null).getInt(DownloadSpec.KEY_INDEX, 0))
    }

    @Test fun partsOverride() {
        val s = DownloadSpec(video = true, audio = false, subs = true)
        assertEquals(Parts(true, false, true), s.parts)
        assertEquals("V+S", s.parts.label())
        val only = s.withParts(Parts(false, true, false))
        assertFalse(only.video); assertTrue(only.audio); assertFalse(only.subs)
        assertEquals(VideoQuality.P720, only.quality)
    }

    @Test fun muxOnlyAbove360() {
        assertFalse(VideoQuality.P360.needsMux)
        assertTrue(VideoQuality.P720.needsMux)
        assertTrue(VideoQuality.P1080.needsMux)
    }
}
