package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers only the two methods added for ScoutCompanionMomentsEngine's shared
 * proactive-speech clock (msSinceLastPresenceRemark/onExternalProactiveRemark).
 * Not a full test suite for ScoutPresenceDecider -- that class's existing
 * behavior is unchanged by this addition and out of scope here.
 */
class ScoutPresenceDeciderSharedClockTest {

    private fun decider() = ScoutPresenceDecider(
        isSpontaneousCommentsEnabled = { true },
        isPresenceModeEnabled = { true }
    )

    @Test fun `a never-fired clock reports Long MAX_VALUE, not zero`() {
        val decider = decider()
        assertEquals(Long.MAX_VALUE, decider.msSinceLastPresenceRemark(nowMs = 10_000L))
    }

    @Test fun `onExternalProactiveRemark stamps the clock other proactive systems read`() {
        val decider = decider()
        decider.onExternalProactiveRemark(nowMs = 1_000L)
        assertEquals(4_000L, decider.msSinceLastPresenceRemark(nowMs = 5_000L))
    }

    // Not asserted through canMakeIdleSilenceRemark()/canMakeReturnGreeting() --
    // both also gate on real wall-clock time-of-day (QUIET/SLEEP hours), which
    // this test can't control, so a pass/fail there wouldn't reliably prove
    // anything about the shared clock specifically. Asserting on the accessor
    // directly is the precise, deterministic way to prove the same field is
    // being read and written from both directions.

    @Test fun `Presence's own idle-silence remark is visible through the shared accessor`() {
        // onIdleSilenceRemarkMade() is Presence's existing, unmodified method --
        // it stamps using real System.currentTimeMillis() internally, so the
        // query side must use the same real-time scale too, not an arbitrary
        // synthetic nowMs (mixing the two would produce a meaningless negative
        // "elapsed" value that happens to satisfy a loose assertion for the
        // wrong reason).
        val decider = decider()
        val before = decider.msSinceLastPresenceRemark() // never fired -> Long.MAX_VALUE
        decider.onIdleSilenceRemarkMade()
        val after = decider.msSinceLastPresenceRemark()
        assertNotEquals(before, after)
        assertTrue(after in 0..5_000L)
    }

    @Test fun `Presence's own return-greeting remark is visible through the shared accessor`() {
        // onReturnGreetingMade() is Presence's existing, unmodified method --
        // same proof as above for the other existing presence-moment method.
        val decider = decider()
        val before = decider.msSinceLastPresenceRemark()
        decider.onReturnGreetingMade()
        val after = decider.msSinceLastPresenceRemark()
        assertNotEquals(before, after)
        assertTrue(after in 0..5_000L)
    }

    @Test fun `an external stamp and Presence's own stamp write the identical field`() {
        // If onExternalProactiveRemark() wrote a *different* field than
        // onIdleSilenceRemarkMade() does, this would fail: the second call's
        // timestamp would win by overwriting, not by being a separate value.
        val decider = decider()
        decider.onExternalProactiveRemark(nowMs = 1_000L)
        decider.onIdleSilenceRemarkMade() // stamps with the real current time, not 1_000L
        // If these were separate fields, msSinceLastPresenceRemark would still
        // reflect the 1_000L stamp relative to "now" (a huge, stale number).
        // Since they're the same field, it instead reflects the just-made real-time stamp.
        assertTrue(decider.msSinceLastPresenceRemark() < 5_000L)
    }
}
