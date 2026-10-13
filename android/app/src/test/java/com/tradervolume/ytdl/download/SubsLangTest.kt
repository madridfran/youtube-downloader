package com.tradervolume.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsLangTest {

    @Test fun original() {
        assertTrue(SubsLang.isOriginal(SubsLang.ORIGINAL))
        assertEquals("idioma original", SubsLang.label(SubsLang.ORIGINAL))
    }

    @Test fun isoCodesAreNotOriginal() {
        assertFalse(SubsLang.isOriginal("es"))
        assertEquals("ES", SubsLang.label("es"))
    }
}
