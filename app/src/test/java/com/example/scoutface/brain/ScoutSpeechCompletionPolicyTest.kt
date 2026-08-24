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

    // --- PR #71 review round 2: the same function, reused by onStop() to
    // tell a genuine user tap apart from any other reason Android might
    // deliver onStop() (e.g. a QUEUE_FLUSH from a newer dispatch). Framed
    // here as MainActivity.onStop() actually calls it:
    // isStillPending(tapInterruptTargetDispatchId, stoppedDispatchId) ---

    @Test fun `onStop matching the tap's own target dispatch id is recognized as the user's tap`() {
        assertTrue(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 12, candidateDispatchId = 12))
    }

    @Test fun `onStop for a different dispatch than the tap targeted is NOT misattributed to the tap`() {
        // e.g. tts.stop() actually interrupted an older, still-genuinely-
        // audible dispatch than the one tapInterruptTargetDispatchId names
        // (the QUEUE_ADD edge case) -- must fall back to NATURAL, not be
        // misread as the user's own interruption.
        assertFalse(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 12, candidateDispatchId = 13))
    }

    @Test fun `onStop arriving with no tap in flight (target id 0) is never treated as a user tap`() {
        // An internal QUEUE_FLUSH replacement, or any onStop() Android
        // delivers when interruptCurrentSpeech() never set a target at all.
        assertFalse(ScoutSpeechDispatchGuard.isStillPending(activeDispatchId = 0, candidateDispatchId = 12))
    }

    // --- PR #71 review round 3: ownsGlobalSpeakingState() -- "submitted to
    // the TTS engine" (submittedDispatchId) is NOT the same as "actually
    // audible" (audibleDispatchId, ground truth from onStart()). These map
    // directly onto the review's scenarios A-D. ---

    @Test fun `a single active dispatch with no queued follow-up owns global state`() {
        // Scenario A: dispatch 1 reached onStart() and nothing else is queued.
        assertTrue(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 1, audibleDispatchId = 1, submittedDispatchId = 1
        ))
    }

    @Test fun `a dispatch merely submitted, before its own onStart arrives, owns global state via the fallback`() {
        // The narrow gap between tts.speak() and Android's onStart()
        // callback -- nothing has been confirmed audible yet (0), so the
        // submitted dispatch is the best available ground truth. Without
        // this fallback, a tap or a synchronous dispatch failure landing in
        // this exact gap would find nothing owning global state at all.
        assertTrue(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 1, audibleDispatchId = 0, submittedDispatchId = 1
        ))
    }

    @Test fun `an older audible dispatch is NOT superseded merely because a newer one was submitted behind it`() {
        // Scenario C, the exact bug this round fixes: dispatch 1 (A) is
        // genuinely audible; dispatch 2 (B) has been accepted via QUEUE_ADD
        // but has NOT started yet. Dispatch 1 must still own global state --
        // being merely submitted does not steal ownership from what's
        // actually playing.
        assertTrue(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 1, audibleDispatchId = 1, submittedDispatchId = 2
        ))
        assertFalse(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 2, audibleDispatchId = 1, submittedDispatchId = 2
        ))
    }

    @Test fun `ownership passes to the newer dispatch only once its own onStart is confirmed`() {
        // Scenario D: dispatch 1 (A) finished, dispatch 2 (B) has since been
        // confirmed audible -- B now owns global state, A no longer does.
        assertTrue(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 2, audibleDispatchId = 2, submittedDispatchId = 2
        ))
        assertFalse(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 1, audibleDispatchId = 2, submittedDispatchId = 2
        ))
    }

    @Test fun `a dispatch that is neither audible nor the most recently submitted never owns global state`() {
        // A stale/superseded dispatch's own (possibly delayed) terminal
        // callback must never claim ownership away from whichever dispatch
        // genuinely holds it now.
        assertFalse(ScoutSpeechDispatchGuard.ownsGlobalSpeakingState(
            dispatchId = 1, audibleDispatchId = 3, submittedDispatchId = 3
        ))
    }
}
