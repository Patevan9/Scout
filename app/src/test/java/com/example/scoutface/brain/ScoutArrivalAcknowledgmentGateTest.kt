package com.example.scoutface.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers ScoutArrivalAcknowledgmentGate.shouldAcknowledge() -- the pure rule
 * behind Silent Arrival Acknowledgment v1. MainActivity's own latch
 * (arrivalAcknowledged) and reset timing (only on a genuine absence) are not
 * re-implemented here; this only exercises the gate function itself against
 * every input it reads.
 */
class ScoutArrivalAcknowledgmentGateTest {

    private val stabilizeMs = 3_000L
    private val faceAppearanceMs = 1_000_000L

    private fun shouldAcknowledge(
        alreadyAcknowledged: Boolean = false,
        isSpeaking: Boolean = false,
        isThinking: Boolean = false,
        faceAppearanceMs: Long = this.faceAppearanceMs,
        stabilizeMs: Long = this.stabilizeMs,
        nowMs: Long = faceAppearanceMs + stabilizeMs
    ) = ScoutArrivalAcknowledgmentGate.shouldAcknowledge(
        alreadyAcknowledged, isSpeaking, isThinking, faceAppearanceMs, nowMs, stabilizeMs
    )

    // --- Genuine arrival triggers once ---

    @Test fun `a stabilized arrival with nothing else in the way should acknowledge`() {
        assertTrue(shouldAcknowledge())
    }

    @Test fun `exactly at the stabilization boundary still acknowledges -- boundary inclusive`() {
        assertTrue(shouldAcknowledge(nowMs = faceAppearanceMs + stabilizeMs))
    }

    // --- Continued presence does not retrigger ---

    @Test fun `already-acknowledged arrival does not fire again while still present`() {
        assertFalse(shouldAcknowledge(alreadyAcknowledged = true))
    }

    @Test fun `already-acknowledged stays false no matter how much more time passes`() {
        assertFalse(shouldAcknowledge(alreadyAcknowledged = true, nowMs = faceAppearanceMs + 999_999L))
    }

    // --- Brief detector flicker (not yet stabilized) does not spam ---

    @Test fun `not yet stabilized -- arrival too recent -- does not acknowledge`() {
        assertFalse(shouldAcknowledge(nowMs = faceAppearanceMs + stabilizeMs - 1L))
    }

    @Test fun `faceAppearanceMs of 0 (no current arrival) never acknowledges`() {
        // Guards against a caller passing the "no face" sentinel directly --
        // MainActivity's own faceAppearanceMs is only ever read from inside
        // its face-present branch, where it can't be 0 by the time this runs,
        // but the gate itself must not treat 0 as "arrived a very long time
        // ago" (nowMs - 0 would trivially exceed any stabilizeMs).
        assertFalse(shouldAcknowledge(faceAppearanceMs = 0L, nowMs = 10_000_000L))
    }

    // --- State safety: speaking and thinking each independently suppress ---

    @Test fun `speaking suppresses the acknowledgment`() {
        assertFalse(shouldAcknowledge(isSpeaking = true))
    }

    @Test fun `thinking suppresses the acknowledgment`() {
        assertFalse(shouldAcknowledge(isThinking = true))
    }

    @Test fun `speaking and thinking together still suppress`() {
        assertFalse(shouldAcknowledge(isSpeaking = true, isThinking = true))
    }

    // --- Listening does NOT suppress (explicit design decision -- differs
    // from the speech-producing startup greeting's own extra !isListening
    // gate, which this gate deliberately does not take as a parameter at all) ---

    @Test fun `a stabilized arrival still acknowledges regardless of any other true booleans not modeled here`() {
        // The function has no isListening parameter at all -- this test
        // documents that omission is deliberate, not an oversight: listening
        // must never be able to suppress the silent acknowledgment.
        assertTrue(shouldAcknowledge())
    }
}
