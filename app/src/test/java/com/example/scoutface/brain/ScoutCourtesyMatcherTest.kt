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

    // --- ACKNOWLEDGE: bare conversational closers, real-device finding (Galaxy A32) ---
    // Previously had no deterministic handling anywhere and fell through to
    // Gemini/TinyLlama like a real open-ended question.

    @Test fun `bare okay matches ACKNOWLEDGE`() {
        assertEquals(CourtesyIntent.ACKNOWLEDGE, ScoutCourtesyMatcher.match("okay", scoutName))
    }

    @Test fun `bare ok matches ACKNOWLEDGE`() {
        assertEquals(CourtesyIntent.ACKNOWLEDGE, ScoutCourtesyMatcher.match("ok", scoutName))
    }

    @Test fun `bare alright matches ACKNOWLEDGE`() {
        assertEquals(CourtesyIntent.ACKNOWLEDGE, ScoutCourtesyMatcher.match("alright", scoutName))
    }

    @Test fun `bare got it matches ACKNOWLEDGE`() {
        assertEquals(CourtesyIntent.ACKNOWLEDGE, ScoutCourtesyMatcher.match("got it", scoutName))
    }

    @Test fun `bare sounds good matches ACKNOWLEDGE`() {
        assertEquals(CourtesyIntent.ACKNOWLEDGE, ScoutCourtesyMatcher.match("sounds good", scoutName))
    }

    @Test fun `youre welcome (normalized to you are welcome) matches ACKNOWLEDGE`() {
        // TextNormalizer.normalizeUtterance("You're welcome") -> "you are welcome"
        // -- exercised here via the real normalizer, not a hand-typed remainder.
        assertEquals(
            CourtesyIntent.ACKNOWLEDGE,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("You're welcome"), scoutName)
        )
    }

    // --- Lead-in tolerance: at most one filler lead-in stripped from the start ---

    @Test fun `okay comma thank you resolves to THANKS`() {
        assertEquals(
            CourtesyIntent.THANKS,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("okay, thank you"), scoutName)
        )
    }

    @Test fun `got it comma thanks resolves to THANKS`() {
        assertEquals(
            CourtesyIntent.THANKS,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("got it, thanks"), scoutName)
        )
    }

    @Test fun `alright comma thanks resolves to THANKS`() {
        assertEquals(
            CourtesyIntent.THANKS,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("alright, thanks"), scoutName)
        )
    }

    @Test fun `okay comma good night resolves to GOOD_NIGHT`() {
        assertEquals(
            CourtesyIntent.GOOD_NIGHT,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("okay, good night"), scoutName)
        )
    }

    @Test fun `alright comma goodbye resolves to GOODBYE -- falls out of the generic design for free`() {
        assertEquals(
            CourtesyIntent.GOODBYE,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("alright, goodbye"), scoutName)
        )
    }

    @Test fun `ok comma thank you plus name resolves to THANKS`() {
        assertEquals(
            CourtesyIntent.THANKS,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("ok, thank you Scout"), scoutName)
        )
    }

    // --- Adversarial: lead-in stripping must never swallow a real sentence ---

    @Test fun `I thanked Diana yesterday is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("I thanked Diana yesterday."), scoutName))
    }

    @Test fun `what does sounds good mean is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("What does 'sounds good' mean?"), scoutName))
    }

    @Test fun `okay what is the weather is not matched -- real question survives lead-in stripping`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("Okay, what is the weather?"), scoutName))
    }

    @Test fun `youre welcome to stay for dinner is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("You're welcome to stay for dinner."), scoutName))
    }

    @Test fun `alright lets begin is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("Alright, let's begin."), scoutName))
    }

    @Test fun `okay thank you but what about tomorrow is not matched -- real trailing question survives`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("Okay, thank you, but what about tomorrow?"), scoutName))
    }

    @Test fun `a bare lead-in word alone with nothing after it is handled by direct ACKNOWLEDGE matching, not stripped to a blank remainder`() {
        // "okay" alone matches ACKNOWLEDGE directly (step 1) -- it never reaches the
        // lead-in-stripping loop at all, but confirm the outcome is correct either way.
        assertEquals(CourtesyIntent.ACKNOWLEDGE, ScoutCourtesyMatcher.match("okay", scoutName))
    }

    @Test fun `got itchy is not matched -- word-boundary guard against a lead-in matching inside a longer word`() {
        assertNull(ScoutCourtesyMatcher.match("got itchy", scoutName))
    }

    // --- WELCOME_BACK: real-device finding (Fold 7) -- "Welcome back!" said in
    // reply to Scout's own boot greeting had no deterministic handling anywhere
    // (not here, not in ScoutIntentRouter), so it reached Gemini/TinyLlama like a
    // real open question, and the presence-reply window Scout's own boot/return
    // greeting opens let it through without the wake name at all. ---

    @Test fun `bare welcome back matches WELCOME_BACK`() {
        assertEquals(CourtesyIntent.WELCOME_BACK, ScoutCourtesyMatcher.match("welcome back", scoutName))
    }

    @Test fun `welcome back plus name matches WELCOME_BACK`() {
        assertEquals(CourtesyIntent.WELCOME_BACK, ScoutCourtesyMatcher.match("welcome back scout", scoutName))
    }

    @Test fun `glad you are back matches WELCOME_BACK`() {
        // "Glad you're back." normalizes to "glad you are back" via TextNormalizer's
        // existing "you're" -> "you are" contraction expansion.
        assertEquals(
            CourtesyIntent.WELCOME_BACK,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("Glad you're back."), scoutName)
        )
    }

    @Test fun `good to have you back matches WELCOME_BACK`() {
        assertEquals(CourtesyIntent.WELCOME_BACK, ScoutCourtesyMatcher.match("good to have you back", scoutName))
    }

    @Test fun `nice to have you back matches WELCOME_BACK`() {
        assertEquals(CourtesyIntent.WELCOME_BACK, ScoutCourtesyMatcher.match("nice to have you back", scoutName))
    }

    @Test fun `okay comma welcome back resolves to WELCOME_BACK -- falls out of the generic lead-in design for free`() {
        assertEquals(
            CourtesyIntent.WELCOME_BACK,
            ScoutCourtesyMatcher.match(TextNormalizer.normalizeUtterance("Okay, welcome back!"), scoutName)
        )
    }

    // --- Adversarial: must not become a "welcome back" substring/contains match ---

    @Test fun `welcome back to the show is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("Welcome back to the show."), scoutName))
    }

    @Test fun `welcome back everyone to the meeting is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("Welcome back everyone to the meeting."), scoutName))
    }

    @Test fun `youre welcome back there whenever youre ready is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("You're welcome back there whenever you're ready."), scoutName))
    }

    @Test fun `i dont want you to welcome back the neighbors dog is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("I don't want you to welcome back the neighbor's dog."), scoutName))
    }

    @Test fun `was that a warm welcome back home is not matched`() {
        assertNull(ScoutCourtesyMatcher.match(
            TextNormalizer.normalizeUtterance("Was that a warm welcome back home?"), scoutName))
    }
}
