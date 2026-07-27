package top.rootu.lampa.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsLangTest {

    @Test
    fun keepsSupportedLanguages() {
        assertEquals("uk", Prefs.normalizeLang("uk"))
        assertEquals("en", Prefs.normalizeLang("en"))
    }

    @Test
    fun stripsRegionAndScript() {
        assertEquals("uk", Prefs.normalizeLang("uk-UA"))
        assertEquals("en", Prefs.normalizeLang("en_US"))
        assertEquals("uk", Prefs.normalizeLang("UK"))
    }

    @Test
    fun mapsUnsupportedToEnglish() {
        assertEquals("en", Prefs.normalizeLang("ru"))
        assertEquals("en", Prefs.normalizeLang("de"))
        assertEquals("en", Prefs.normalizeLang("ru-RU"))
    }

    @Test
    fun handlesNullAndBlank() {
        assertEquals("en", Prefs.normalizeLang(null))
        assertEquals("en", Prefs.normalizeLang(""))
        assertEquals("en", Prefs.normalizeLang("   "))
    }
}
