package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutSpeechRangeMouthTest {

    // --- impulseMagnitude() -- unchanged by the PR #80 review correction ---

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

    // --- isRangeDriven() -- PR #80 review correction: now a sticky,
    // dispatch-scoped boolean with NO time component at all. The signature
    // itself changed (no more nowMs/activeWindowMs) specifically so that no
    // caller can accidentally reintroduce a recency check here -- these
    // tests lock in that contract. ---

    @Test fun `a dispatch that has never established range ownership is not range-driven -- fallback remains available`() {
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(everEstablishedThisDispatch = false))
    }

    @Test fun `once established, range-driven ownership is true`() {
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(everEstablishedThisDispatch = true))
    }

    @Test fun `isRangeDriven has no time dependency -- there is no elapsed-interval input that could revert an established dispatch to fallback`() {
        // Guards against reintroducing PR #80's bug: the function's entire
        // contract is now "did this dispatch ever see a real event," full
        // stop -- calling it "again later" (there being no clock it could
        // consult) still returns true. If a future change ever needs a
        // recency check again, it must not live inside this function.
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(true))
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(true))
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(true))
    }

    @Test fun `a fresh dispatch after completion again begins in fallback`() {
        // Mirrors ScoutFaceView resetting speechRangeEstablished to false in
        // setSpeaking(true) (a new dispatch starting), setSpeaking(false)/
        // updateLife()'s !vSpeaking branch (natural completion, engine
        // error, or user interruption -- all funnel through
        // finishSpeechDispatch() -> setSpeaking(false)), and resetFace().
        // The pure predicate must treat that reset identically to "never
        // established," regardless of how established the PREVIOUS dispatch
        // was.
        val previousDispatchEstablished = true
        assertTrue(ScoutSpeechRangeMouth.isRangeDriven(previousDispatchEstablished))

        val nextDispatchEstablished = false // reset at the dispatch boundary
        assertFalse(ScoutSpeechRangeMouth.isRangeDriven(nextDispatchEstablished))
    }
}
