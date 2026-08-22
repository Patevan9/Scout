package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Busy-Brain polish -- the delayed thinking-phrase filler.
 *
 * MainActivity's scheduleBusyBrainFiller() itself (a Handler.postDelayed
 * callback) can't be unit-tested without Android/Robolectric, same as every
 * other MainActivity method in this codebase. What CAN be locked in by a
 * test is the exact state sequencing that callback relies on at fire time --
 * ScoutBusyBrainState.isPending -- and the delivery-arbitration decision
 * (ScoutBusyBrainDelivery, unchanged since PR 2) that governs the "And about
 * your earlier question--" bridge. Each test below models one of the five
 * scenarios from the delayed-filler design, using millisecond comments
 * modeled after MainActivity's actual BUSY_BRAIN_FILLER_DELAY_MS (5000ms) so
 * the sequencing described matches the real timeline, even though the
 * constant itself isn't reachable from this pure-brain-package test.
 */
class ScoutBusyBrainFillerTimingTest {

    @Test fun `answer completes before the filler threshold -- no filler needed`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(0L) // generation dispatched at t=0
        val id = state.currentGenerationId()
        state.complete(id) // a fast Gemini answer resolves at, say, t=900ms

        // At the scheduled t=5000ms check, MainActivity reads isPending fresh:
        assertFalse(state.isPending) // -- false, so nothing is said.
    }

    @Test fun `generation still pending at the filler threshold -- exactly one filler is due`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(0L)
        // No complete()/discard() by t=5000ms -- generation genuinely still running.

        assertTrue(state.isPending) // -- MainActivity speaks exactly one randomly-picked filler here.

        // tryBegin() cannot be re-entered for this same question, so a second
        // scheduled check for the same question is structurally impossible --
        // covered directly by the fallback test below.
    }

    @Test fun `Gemini fallback to TinyLlama for the same question schedules no second filler`() {
        val state = ScoutBusyBrainState()
        val geminiBegan = state.tryBegin(0L) // Gemini REQUEST_STARTED -- filler check scheduled
        assertTrue(geminiBegan)

        val tinyLlamaBegan = state.tryBegin(500L) // Gemini failed fast, TinyLlama dispatched
        assertFalse(tinyLlamaBegan) // no-op -- MainActivity does NOT schedule a second filler check

        // The one filler check already scheduled from the original tryBegin()
        // is still valid -- isPending is unaffected by the fallback handoff.
        assertTrue(state.isPending)
    }

    @Test fun `conversation explicitly closed before the filler threshold -- no filler speaks`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(0L)
        val id = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED) // goodbye said at t=800ms

        // At the scheduled t=5000ms check:
        assertFalse(state.isPending) // -- false, so nothing is said, even though the
        assertTrue(state.isDiscarded(id)) //    underlying generation may still be running.
    }

    @Test fun `the busy-brain filler is being spoken when the generation resolves -- the earlier-question bridge still applies`() {
        // Models the legitimate case PR #55 and the generation-ownership
        // design (PR 2) both require to keep working: the AI generation
        // begins, the filler threshold is reached and MainActivity speaks
        // exactly one thinking phrase (BUSY_BRAIN_FILLERS) -- Scout's OWN
        // status speech, not a reaction to any new user utterance -- and the
        // generation resolves while that filler is still being spoken.
        //
        // No newer substantive user turn was ever accepted here, so
        // supersedeAnyPendingGeneration() (PR 2) is never called and
        // isPending stays genuinely true the whole time -- this is exactly
        // why a deterministic follow-up is NOT modeled in this test: since
        // PR 2, a deterministic follow-up that Scout actually answers WOULD
        // discard this generation instead (see ScoutBusyBrainStateTest's
        // SUPERSEDED_BY_NEW_TURN coverage) -- that is intentional, correct
        // behavior for PR 2 and is not what this test is about.
        val state = ScoutBusyBrainState()
        state.tryBegin(0L)
        val id = state.currentGenerationId()

        assertTrue(state.isPending) // the AI generation is still genuinely in flight

        state.complete(id) // the AI answer resolves now, while the filler is being spoken

        // deliverAiResult() consults ScoutBusyBrainDelivery (unchanged since
        // PR 2) with the real isSpeaking flag -- true here, since Scout is
        // genuinely mid-utterance on its own filler, not idle:
        assertTrue(ScoutBusyBrainDelivery.shouldQueue(isSpeaking = true, isThinking = false))
        assertEquals(
            "And about your earlier question — 72 degrees.",
            ScoutBusyBrainDelivery.phraseDelivery("72 degrees.", wasQueued = true)
        )
    }
}
