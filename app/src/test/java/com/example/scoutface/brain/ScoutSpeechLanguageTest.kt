package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ScoutSpeechLanguageTest {

    @Test fun `spoken language name is derived from RECOGNITION_LOCALE, not a separate literal`() {
        // Locks in the design requirement this file exists for: the spoken
        // answer must be computed from the same constant the recognizer intent
        // is built from, not independently hardcoded. If RECOGNITION_LOCALE ever
        // changes, this assertion (and the spoken answer) changes with it.
        assertEquals(
            ScoutSpeechLanguage.RECOGNITION_LOCALE.getDisplayLanguage(Locale.US),
            ScoutSpeechLanguage.spokenLanguageName()
        )
    }

    @Test fun `current recognition locale speaks as English`() {
        assertEquals("English", ScoutSpeechLanguage.spokenLanguageName())
    }
}
