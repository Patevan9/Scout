package com.example.scoutface.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ScoutPromptBuilder.buildSystemInstruction() itself takes a real TruthDb and
 * HabitLayer (Android/SQLite-backed), so it has no pure-Kotlin test surface --
 * see the class doc comment. geminiVisionCapabilityLine() is the one piece of
 * that prompt narrow and pure enough to assert on directly: the secondary
 * reinforcement line covering the real-device "I don't have a camera" finding.
 * This is deliberately NOT the integrity guarantee (that's ScoutVisionGate's
 * deterministic routing plus ScoutFactExtractor.containsVisionCapabilityDenial()'s
 * Layer-2 output guard) -- these tests only confirm the prompt line says what
 * it's supposed to say.
 *
 * Was GEMINI_VISION_CAPABILITY_LINE, a fixed const val -- now a function taking
 * the companion's name (so this line can never say a different name than the
 * rest of the same system instruction). Tests pass a fixed example name;
 * nothing here depends on which name that is except the one assertion that
 * checks the name appears where expected.
 */
class ScoutPromptBuilderTest {

    private val line = ScoutPromptBuilder.geminiVisionCapabilityLine("Tolliver")

    @Test fun `the line confirms the companion has a real camera and vision system`() {
        val lower = line.lowercase()
        assertTrue(lower.contains("real camera"))
        assertTrue(lower.contains("vision system"))
    }

    @Test fun `the line forbids denying camera or vision capability, naming the companion passed in`() {
        val lower = line.lowercase()
        assertTrue(lower.contains("do not claim tolliver has no camera"))
        assertTrue(lower.contains("no vision capability"))
    }

    @Test fun `the line honestly distinguishes app capability from this request's live data`() {
        // The exact distinction that was missing before: the companion can
        // see, but THIS Gemini request carries no live camera frame -- so
        // Gemini must not invent a current visual observation either.
        val lower = line.lowercase()
        assertTrue(lower.contains("do not receive the live camera view"))
        assertTrue(lower.contains("never invent or claim current visual observations"))
    }

    @Test fun `the line never claims Gemini itself currently sees something`() {
        // Regression guard against reintroducing the original overcorrection
        // risk: asserting camera capability must not slide into asserting
        // Gemini has an actual live view in hand.
        val lower = line.lowercase()
        assertFalse(lower.contains("i can see you right now"))
        assertFalse(lower.contains("i am currently looking at"))
    }
}
