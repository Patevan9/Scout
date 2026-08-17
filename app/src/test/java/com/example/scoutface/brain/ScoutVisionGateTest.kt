package com.example.scoutface.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutVisionGateTest {

    private fun gate(q: String) = ScoutVisionGate.isPossibleVisionQuery(q.lowercase().trim())

    // --- Topic word + self word (the requested real-device phrasings) ---

    @Test fun `do you see anything`() {
        assertTrue(gate("Do you see anything?"))
    }

    @Test fun `can you see anyone`() {
        assertTrue(gate("Can you see anyone?"))
    }

    @Test fun `can you see me right now`() {
        assertTrue(gate("Can you see me right now?"))
    }

    @Test fun `do you have a camera`() {
        assertTrue(gate("Do you have a camera?"))
    }

    @Test fun `is your vision working`() {
        assertTrue(gate("Is your vision working?"))
    }

    // --- Standalone topic phrases that already imply "you" ---

    @Test fun `who's in front of you`() {
        assertTrue(gate("Who's in front of you?"))
    }

    @Test fun `what's in front of you`() {
        assertTrue(gate("What's in front of you?"))
    }

    // --- False positives: "look"/"looking"/"watching" deliberately excluded
    // -- too common in unrelated conversational filler. ---

    @Test fun `you look nice today is not a vision question`() {
        assertFalse(gate("You look nice today"))
    }

    @Test fun `look, I need help with something is not a vision question`() {
        assertFalse(gate("Look, I need help with something"))
    }

    @Test fun `let's look into that later is not a vision question`() {
        assertFalse(gate("Let's look into that later"))
    }

    // --- A topic word with no self word never fires (matches the same
    // recall-over-precision balance as ScoutMemoryGate, but still requires
    // both halves) ---

    @Test fun `i saw a great movie yesterday is not a vision question`() {
        assertFalse(gate("I saw a great movie yesterday"))
    }

    // --- A self word with no vision topic never fires ---

    @Test fun `what's my favorite color is not a vision question`() {
        assertFalse(gate("What's my favorite color?"))
    }

    @Test fun `you're doing great is not a vision question`() {
        assertFalse(gate("You're doing great"))
    }
}
