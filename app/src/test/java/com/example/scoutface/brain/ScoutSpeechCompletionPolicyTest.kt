package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers ScoutSpeechCompletionPolicy -- the pure fork MainActivity's
 * finishSpeechDispatch() delegates to so a natural TTS completion, an engine
 * error, and a user-initiated tap interruption can share one cleanup body
 * while still differing in the few places they must.
 */
class ScoutSpeechCompletionPolicyTest {

    // --- Presence reply window: only a natural completion ever opens one ---

    @Test fun `natural completion opens a presence reply window`() {
        assertTrue(ScoutSpeechCompletionPolicy.opensPresenceReplyWindow(ScoutSpeechCompletionPolicy.Kind.NATURAL))
    }

    @Test fun `an engine error never opens a presence reply window`() {
        assertFalse(ScoutSpeechCompletionPolicy.opensPresenceReplyWindow(ScoutSpeechCompletionPolicy.Kind.ENGINE_ERROR))
    }

    @Test fun `a user interruption never opens a presence reply window`() {
        assertFalse(ScoutSpeechCompletionPolicy.opensPresenceReplyWindow(ScoutSpeechCompletionPolicy.Kind.USER_INTERRUPTED))
    }

    // --- pendingAiAnswer drain: everything EXCEPT a user interruption ---
    // (this is the entire reason tap-to-interrupt v1 needed a Kind instead
    // of reusing onError()'s existing wasError boolean)

    @Test fun `natural completion still allows the normal pending-answer drain`() {
        assertTrue(ScoutSpeechCompletionPolicy.drainsPendingAnswer(ScoutSpeechCompletionPolicy.Kind.NATURAL))
    }

    @Test fun `an engine error still allows the normal pending-answer drain -- unchanged pre-existing behavior`() {
        assertTrue(ScoutSpeechCompletionPolicy.drainsPendingAnswer(ScoutSpeechCompletionPolicy.Kind.ENGINE_ERROR))
    }

    @Test fun `a user interruption suppresses the immediate pending-answer drain`() {
        assertFalse(ScoutSpeechCompletionPolicy.drainsPendingAnswer(ScoutSpeechCompletionPolicy.Kind.USER_INTERRUPTED))
    }

    // --- STARTUP_GREETING / PR #68 anchor: every kind counts as "finished" ---

    @Test fun `natural completion of the startup greeting counts as finished`() {
        assertTrue(ScoutSpeechCompletionPolicy.countsAsStartupGreetingFinished(ScoutSpeechCompletionPolicy.Kind.NATURAL))
    }

    @Test fun `an engine error on the startup greeting still counts as finished`() {
        assertTrue(ScoutSpeechCompletionPolicy.countsAsStartupGreetingFinished(ScoutSpeechCompletionPolicy.Kind.ENGINE_ERROR))
    }

    @Test fun `a deliberate tap interrupting the startup greeting counts as finished`() {
        assertTrue(ScoutSpeechCompletionPolicy.countsAsStartupGreetingFinished(ScoutSpeechCompletionPolicy.Kind.USER_INTERRUPTED))
    }

    // --- Every Kind is covered, so this policy can never silently fall through ---

    @Test fun `every Kind has an explicit answer for all three questions`() {
        for (kind in ScoutSpeechCompletionPolicy.Kind.values()) {
            // Just confirming these don't throw for any Kind -- the real
            // assertions are the per-Kind tests above.
            ScoutSpeechCompletionPolicy.opensPresenceReplyWindow(kind)
            ScoutSpeechCompletionPolicy.drainsPendingAnswer(kind)
            ScoutSpeechCompletionPolicy.countsAsStartupGreetingFinished(kind)
        }
        assertEquals(3, ScoutSpeechCompletionPolicy.Kind.values().size)
    }
}

/**
 * Covers ScoutSpeechDispatchGuard -- the pure id-matching check that keeps
 * the pre-dispatch delay window in MainActivity.speak() safe: a tap must be
 * able to tell whether a pending dispatch is still cancellable, and the
 * pending dispatch's own delayed Runnable must be able to tell, right before
 * it fires, that a newer dispatch hasn't since taken over the one pending
 * slot.
 */
class ScoutSpeechDispatchGuardTest {

    @Test fun `a dispatch matching the active slot is still pending`() {
        assertTrue(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 7, candidateDispatchId = 7))
    }

    @Test fun `the sentinel candidate id (0) is never treated as pending`() {
        assertFalse(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 0, candidateDispatchId = 0))
    }

    @Test fun `a cancelled dispatch (slot cleared to 0) is no longer pending`() {
        // Mirrors interruptCurrentSpeech()/the Runnable's own cleanup: once
        // pendingSpeechDispatchId is reset to 0, the original dispatch id
        // must never read back as still pending.
        assertFalse(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 0, candidateDispatchId = 7))
    }

    @Test fun `a dispatch superseded by a newer one is no longer pending -- cannot affect a later utterance`() {
        // The pending slot now belongs to dispatch 8; dispatch 7's own
        // delayed Runnable must recognize it no longer owns the slot and
        // must not dispatch to the TTS engine on its own stale schedule.
        assertFalse(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 8, candidateDispatchId = 7))
    }
}
