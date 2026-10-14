package com.tradervolume.ytdl.extractor

import org.junit.Assert.assertEquals
import org.junit.Test

class TierTest {
    @Test fun youtubeQualityLabels() {
        assertEquals(720, YoutubeExtractor.tierOf(1280, 720))
        assertEquals(720, YoutubeExtractor.tierOf(1280, 616))   // 2,08:1, caso real D9JSxCk7F4w
        assertEquals(360, YoutubeExtractor.tierOf(640, 308))
        assertEquals(1080, YoutubeExtractor.tierOf(1920, 1080))
        assertEquals(1080, YoutubeExtractor.tierOf(1080, 1920))  // vertical (Shorts)
        assertEquals(1080, YoutubeExtractor.tierOf(1440, 1080))  // 4:3
    }
}
