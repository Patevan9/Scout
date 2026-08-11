package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScoutVoiceSelectorTest {

    private fun voice(
        name: String,
        languageEn: Boolean = true,
        countryUs: Boolean = true,
        quality: Int = 400,
        networkRequired: Boolean = false,
        notInstalled: Boolean = false
    ) = VoiceCandidate(name, languageEn, countryUs, quality, networkRequired, notInstalled)

    // --- Preferred name wins when present ---

    @Test fun `preferred name is chosen even when a higher-quality alternative exists`() {
        val voices = listOf(
            voice("en-us-x-iom-local", quality = 300),
            voice("en-us-x-other-local", quality = 500)
        )
        val chosen = ScoutVoiceSelector.choose(voices, listOf("en-us-x-iom-local"))
        assertEquals("en-us-x-iom-local", chosen)
    }

    @Test fun `first matching preferred name wins when multiple preferred names are configured`() {
        val voices = listOf(voice("second-choice"), voice("third-choice"))
        val chosen = ScoutVoiceSelector.choose(
            voices, listOf("first-choice", "second-choice", "third-choice")
        )
        assertEquals("second-choice", chosen)
    }

    // --- Falls through to quality ranking when preferred name is absent ---

    @Test fun `falls through to the highest-quality offline candidate when preferred name is absent`() {
        val voices = listOf(
            voice("low-quality", quality = 200),
            voice("high-quality", quality = 500)
        )
        val chosen = ScoutVoiceSelector.choose(voices, listOf("en-us-x-iom-local"))
        assertEquals("high-quality", chosen)
        // The fallback result must never be mistaken for the preferred voice.
        assertNotEquals("en-us-x-iom-local", chosen)
    }

    @Test fun `quality ties break on name for determinism`() {
        val voices = listOf(
            voice("zeta", quality = 400),
            voice("alpha", quality = 400)
        )
        val chosen = ScoutVoiceSelector.choose(voices, emptyList())
        assertEquals("alpha", chosen)
    }

    // --- Offline guarantee: network-required voices are never chosen ---

    @Test fun `a network-required voice is never chosen even if it is the preferred name`() {
        val voices = listOf(
            voice("en-us-x-iom-local", networkRequired = true, quality = 500),
            voice("offline-fallback", quality = 300)
        )
        val chosen = ScoutVoiceSelector.choose(voices, listOf("en-us-x-iom-local"))
        assertEquals("offline-fallback", chosen)
    }

    @Test fun `a network-required voice is never chosen even if it has the highest quality`() {
        val voices = listOf(
            voice("network-voice", networkRequired = true, quality = 500),
            voice("offline-voice", quality = 200)
        )
        val chosen = ScoutVoiceSelector.choose(voices, emptyList())
        assertEquals("offline-voice", chosen)
    }

    // --- Offline guarantee: notInstalled voices are never chosen ---

    @Test fun `a notInstalled voice is never chosen even if it is the preferred name`() {
        val voices = listOf(
            voice("en-us-x-iom-local", notInstalled = true, quality = 500),
            voice("installed-fallback", quality = 300)
        )
        val chosen = ScoutVoiceSelector.choose(voices, listOf("en-us-x-iom-local"))
        assertEquals("installed-fallback", chosen)
    }

    @Test fun `a notInstalled voice is never chosen even if it has the highest quality`() {
        val voices = listOf(
            voice("not-installed-voice", notInstalled = true, quality = 500),
            voice("installed-voice", quality = 100)
        )
        val chosen = ScoutVoiceSelector.choose(voices, emptyList())
        assertEquals("installed-voice", chosen)
    }

    // --- Locale filtering ---

    @Test fun `non-English or non-US voices are excluded entirely`() {
        val voices = listOf(
            voice("en-gb-voice", countryUs = false, quality = 500),
            voice("fr-fr-voice", languageEn = false, quality = 500),
            voice("en-us-voice", quality = 100)
        )
        val chosen = ScoutVoiceSelector.choose(voices, emptyList())
        assertEquals("en-us-voice", chosen)
    }

    // --- No usable candidates at all ---

    @Test fun `returns null when there are no candidates`() {
        assertNull(ScoutVoiceSelector.choose(emptyList(), listOf("en-us-x-iom-local")))
    }

    @Test fun `returns null when every candidate is filtered out`() {
        val voices = listOf(
            voice("network-only", networkRequired = true),
            voice("not-installed-only", notInstalled = true),
            voice("wrong-locale", countryUs = false)
        )
        assertNull(ScoutVoiceSelector.choose(voices, emptyList()))
    }
}
