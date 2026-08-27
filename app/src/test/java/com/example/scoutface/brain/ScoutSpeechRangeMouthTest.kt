package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutSpeechRangeMouthTest {

    // --- impulseMagnitude() ---

    private val MIN = 0.22f
    private val MAX = 0.55f
    private val NORMALIZER = 8f

    @Test fun `zero-length range maps to the minimum magnitude`() {
        assertEquals(MIN, ScoutSpeechRangeMouth.impulseMagnitude(0, MIN, MAX, NORMALIZER), 0.0001f)
    }

    @Test fun `negative-length range clamps to the minimum magnitude -- never negative`() {
        assertEquals(MIN, ScoutSpeechRangeMouth.impulseMagnitude(-3, MIN, MAX, NORMALIZER), 0.0001f)
    }

    @Test fun `range length at the normalizer maps to the maximum magnitude`() {
        assertEquals(MAX, ScoutSpeechRangeMouth.impulseMagnitude(8, MIN, MAX, NORMALIZER), 0.0001f)
    }

    @Test fun `range length well beyond the normalizer still clamps to the maximum -- never exceeds it`() {
        assertEquals(MAX, ScoutSpeechRangeMouth.impulseMagnitude(500, MIN, MAX, NORMALIZER), 0.0001f)
    }

    @Test fun `half the normalizer maps to roughly the midpoint magnitude`() {
        val expectedMid = MIN + 0.5f * (MAX - MIN)
        assertEquals(expectedMid, ScoutSpeechRangeMouth.impulseMagnitude(4, MIN, MAX, NORMALIZER), 0.0001f)
    }

    @Test fun `impulseMagnitude is monotonically non-decreasing in range length`() {
        var prev = ScoutSpeechRangeMouth.impulseMagnitude(0, MIN, MAX, NORMALIZER)
        for (chars in 1..12) {
            val cur = ScoutSpeechRangeMouth.impulseMagnitude(chars, MIN, MAX, NORMALIZER)
            assertTrue("magnitude should not decrease as range length grows", cur >= prev)
            prev = cur
        }
    }

    // --- isRangeDriven() ---

    @Test fun `no range event ever seen -- not range-driven, fallback remains available`() {
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(lastEventAtMs = 0L, nowMs = 10_000L, activeWindowMs = 1000L))
    }

    @Test fun `a range event just now -- range-driven becomes active`() {
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(lastEventAtMs = 5000L, nowMs = 5000L, activeWindowMs = 1000L))
    }

    @Test fun `a range event just inside the active window -- still range-driven`() {
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(lastEventAtMs = 5000L, nowMs = 5000L + 999L, activeWindowMs = 1000L))
    }

    @Test fun `a range event exactly at the active window boundary -- no longer range-driven`() {
        // Strict less-than: nowMs - lastEventAtMs == activeWindowMs is expired, not still active.
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(lastEventAtMs = 5000L, nowMs = 5000L + 1000L, activeWindowMs = 1000L))
    }

    @Test fun `a range event well beyond the active window -- falls back gracefully`() {
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(lastEventAtMs = 5000L, nowMs = 5000L + 5000L, activeWindowMs = 1000L))
    }

    @Test fun `a later event within the same dispatch re-establishes range-driven mode`() {
        // Simulates: event, gap exceeding the window (falls back), then a fresh event (re-activates).
        val firstEventAt = 1000L
        val afterGap = firstEventAt + 5000L
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(firstEventAt, afterGap, activeWindowMs = 1000L))

        val secondEventAt = afterGap // a fresh event arrives, resetting lastEventAtMs
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(secondEventAt, secondEventAt + 200L, activeWindowMs = 1000L))
    }

    @Test fun `speech completion resets lastEventAtMs to 0 -- immediately not range-driven again`() {
        // Mirrors ScoutFaceView resetting lastSpeechRangeEventAtMs to 0L in the
        // !vSpeaking branch of updateLife() and in resetFace() -- the pure
        // predicate must treat that reset identically to "never seen."
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(lastEventAtMs = 0L, nowMs = 999_999L, activeWindowMs = 1000L))
    }
}
