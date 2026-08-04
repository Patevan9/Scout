package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoutCourtesyMatcherTest {

    private val scoutName = "Scout"

    // --- Bare forms (no name) ---

    @Test fun `bare hi matches GREET`() {
        assertEquals(CourtesyIntent.GREET, ScoutCourtesyMatcher.match("hi", scoutName))
    }

    @Test fun `bare hello matches GREET`() {
        assertEquals(CourtesyIntent.GREET, ScoutCourtesyMatcher.match("hello", scoutName))
    }

    @Test fun `bare hey matches GREET`() {
        assertEquals(CourtesyIntent.GREET, ScoutCourtesyMatcher.match("hey", scoutName))
    }

    @Test fun `bare good morning matches GOOD_MORNING`() {
        assertEquals(CourtesyIntent.GOOD_MORNING, ScoutCourtesyMatcher.match("good morning", scoutName))
    }

    @Test fun `bare thank you matches THANKS`() {
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thank you", scoutName))
    }

    @Test fun `bare thanks matches THANKS`() {
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thanks", scoutName))
    }

    @Test fun `bare good night matches GOOD_NIGHT`() {
        assertEquals(CourtesyIntent.GOOD_NIGHT, ScoutCourtesyMatcher.match("good night", scoutName))
    }

    @Test fun `bare goodbye matches GOODBYE`() {
        assertEquals(CourtesyIntent.GOODBYE, ScoutCourtesyMatcher.match("goodbye", scoutName))
    }

    @Test fun `bare bye matches GOODBYE`() {
        assertEquals(CourtesyIntent.GOODBYE, ScoutCourtesyMatcher.match("bye", scoutName))
    }

    // --- Name-included forms, only for categories with no router intent today ---

    @Test fun `thank you plus name matches THANKS`() {
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thank you scout", scoutName))
    }

    @Test fun `thanks plus name matches THANKS`() {
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thanks scout", scoutName))
    }

    @Test fun `good morning plus name matches GOOD_MORNING`() {
        assertEquals(CourtesyIntent.GOOD_MORNING, ScoutCourtesyMatcher.match("good morning scout", scoutName))
    }

    @Test fun `good night plus name matches GOOD_NIGHT`() {
        assertEquals(CourtesyIntent.GOOD_NIGHT, ScoutCourtesyMatcher.match("good night scout", scoutName))
    }

    @Test fun `name-included form uses whichever name Scout is currently configured with`() {
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thanks charlie", "Charlie"))
        assertNull(ScoutCourtesyMatcher.match("thanks scout", "Charlie"))
    }

    // --- Configured name is normalized the same way the incoming speech already was ---
    // (a bare trim()/lowercase() isn't enough for a name containing punctuation or
    // unusual spacing, since speech goes through the fuller TextNormalizer.normalizeUtterance())

    @Test fun `a punctuated configured name still matches its name-included form`() {
        // TextNormalizer.normalizeUtterance("R2-D2") -> "r2 d2" (hyphen becomes a space,
        // same as it would for the incoming speech text).
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thanks r2 d2", "R2-D2"))
        assertEquals(CourtesyIntent.GOOD_MORNING, ScoutCourtesyMatcher.match("good morning r2 d2", "R2-D2"))
    }

    @Test fun `a multiword configured name with punctuation still matches`() {
        // TextNormalizer.normalizeUtterance("Scout Jr.") -> "scout jr" (period stripped).
        assertEquals(CourtesyIntent.GOOD_NIGHT, ScoutCourtesyMatcher.match("good night scout jr", "Scout Jr."))
    }

    @Test fun `an unnormalized punctuated name would not have matched -- regression guard`() {
        // Documents exactly the bug the review caught: scoutName.trim().lowercase()
        // alone would have produced "r2-d2" (hyphen kept), which can never equal the
        // already-normalized incoming speech's "r2 d2". Asserting the *fixed* behavior
        // here doubles as a regression guard against reintroducing the bare trim/lowercase.
        assertEquals(CourtesyIntent.THANKS, ScoutCourtesyMatcher.match("thanks r2 d2", "R2-D2"))
        assertNull(ScoutCourtesyMatcher.match("thanks r2-d2", "R2-D2"))
    }

    // --- Deliberately NOT matched: name-included hi/bye already have a working router path ---

    @Test fun `hi plus name is not matched here -- stays on the existing router path`() {
        assertNull(ScoutCourtesyMatcher.match("hi scout", scoutName))
    }

    @Test fun `hello plus name is not matched here`() {
        assertNull(ScoutCourtesyMatcher.match("hello scout", scoutName))
    }

    @Test fun `bye plus name is not matched here -- stays on the existing router path`() {
        assertNull(ScoutCourtesyMatcher.match("bye scout", scoutName))
    }

    @Test fun `goodbye plus name is not matched here`() {
        assertNull(ScoutCourtesyMatcher.match("goodbye scout", scoutName))
    }

    // --- Must not swallow real questions that merely start with a courtesy word ---

    @Test fun `a real question starting with hi is not matched`() {
        assertNull(ScoutCourtesyMatcher.match("hi what is the weather", scoutName))
    }

    @Test fun `thanks followed by more words is not matched`() {
        assertNull(ScoutCourtesyMatcher.match("thanks for that", scoutName))
    }

    @Test fun `good morning followed by more words is not matched`() {
        assertNull(ScoutCourtesyMatcher.match("good morning everyone", scoutName))
    }

    @Test fun `an unrelated utterance matches nothing`() {
        assertNull(ScoutCourtesyMatcher.match("what time is it", scoutName))
    }

    // --- Contract: caller is responsible for normalization (matches how MainActivity wires this) ---

    @Test fun `match does not itself fold case -- relies on already-normalized input`() {
        assertNull(ScoutCourtesyMatcher.match("Hi", scoutName))
    }
}
