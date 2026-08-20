package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutBusyBrainStateTest {

    // --- Beginning a generation ---

    @Test fun `a fresh question begins pending from idle`() {
        val state = ScoutBusyBrainState()
        val began = state.tryBegin(1_000L)
        assertTrue(began)
        assertTrue(state.isPending)
        assertFalse(state.isDiscarded(state.currentGenerationId()))
    }

    @Test fun `a second question cannot begin while one is already pending`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val beganAgain = state.tryBegin(1_500L)
        assertFalse(beganAgain)
        assertTrue(state.isPending) // unchanged -- still the original generation
    }

    @Test fun `a same-question fallback calling tryBegin again is a harmless no-op and keeps the same generation id`() {
        // Models Gemini REQUEST_STARTED flipping isPending true, then Gemini
        // failing and falling back to TinyLlama, which also calls tryBegin()
        // for the same still-pending question -- and must read back the SAME
        // generation id Gemini's own successful tryBegin() already minted.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L) // Gemini's REQUEST_STARTED
        val geminiGenerationId = state.currentGenerationId()
        val fallbackBegan = state.tryBegin(1_200L) // TinyLlama fallback, same question
        assertFalse(fallbackBegan)
        assertTrue(state.isPending)
        assertEquals(geminiGenerationId, state.currentGenerationId())
    }

    // --- Completing normally ---

    @Test fun `complete(currentGenerationId) clears current pending state`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val id = state.currentGenerationId()
        state.complete(id)
        assertFalse(state.isPending)
        val beganNext = state.tryBegin(2_000L)
        assertTrue(beganNext)
    }

    @Test fun `complete on an idle state is harmless`() {
        val state = ScoutBusyBrainState()
        state.complete(0L)
        assertFalse(state.isPending)
    }

    @Test fun `stale complete(oldGenerationId) does not clear a newer current generation`() {
        // The exact bug this fix closes: a stale generation's late completion
        // must never clobber a newer generation's isPending/startedAt.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L) // A begins
        val idA = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.SUPERSEDED_BY_NEW_TURN) // B supersedes A
        state.tryBegin(2_000L) // C begins
        state.complete(idA) // A's late, stale completion
        assertTrue(state.isPending) // C's own in-flight state must be untouched
    }

    // --- Discarding (explicit close / watchdog timeout / superseded) ---

    @Test fun `discard frees the gate immediately`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val discarded = state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        assertTrue(discarded)
        assertFalse(state.isPending) // a new question must not hear "still thinking"
    }

    @Test fun `a new question can begin immediately after a discard and is not itself discarded`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        val beganNext = state.tryBegin(2_000L)
        assertTrue(beganNext)
        assertFalse(state.isDiscarded(state.currentGenerationId())) // the NEW question's own state, not tainted
    }

    @Test fun `discard on an idle state is a no-op`() {
        val state = ScoutBusyBrainState()
        val discarded = state.discard(BusyBrainDiscardReason.TIMEOUT)
        assertFalse(discarded)
    }

    @Test fun `isDiscarded stays true for the abandoned generation's own late callback`() {
        // The whole point of id-scoping: the generation that was discarded
        // must still see isDiscarded(itsOwnId) as true when its callback
        // eventually fires, even though isPending was already freed for the
        // next question.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val id = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        assertTrue(state.isDiscarded(id))
        assertEquals(BusyBrainDiscardReason.CONVERSATION_CLOSED, state.discardReason)
    }

    @Test fun `CONVERSATION_CLOSED discard reason is reported as passed`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val id = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        assertEquals(BusyBrainDiscardReason.CONVERSATION_CLOSED, state.discardReason)
        assertTrue(state.isDiscarded(id))
    }

    @Test fun `TIMEOUT discard reason is reported as passed`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val id = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.TIMEOUT)
        assertEquals(BusyBrainDiscardReason.TIMEOUT, state.discardReason)
        assertTrue(state.isDiscarded(id))
    }

    @Test fun `explicit SUPERSEDED_BY_NEW_TURN discard marks that exact generation discarded`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val id = state.currentGenerationId()
        val discarded = state.discard(BusyBrainDiscardReason.SUPERSEDED_BY_NEW_TURN)
        assertTrue(discarded)
        assertTrue(state.isDiscarded(id))
        assertEquals(BusyBrainDiscardReason.SUPERSEDED_BY_NEW_TURN, state.discardReason)
    }

    // --- Generation-ownership races (PR 2) ---

    @Test fun `a later generation beginning does not revalidate an earlier discarded generation`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L) // A begins
        val idA = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.SUPERSEDED_BY_NEW_TURN) // B supersedes A
        state.tryBegin(2_000L) // C begins
        val idC = state.currentGenerationId()
        assertTrue(state.isDiscarded(idA))  // A still rejected after C exists
        assertFalse(state.isDiscarded(idC)) // C itself is valid
        assertNotEquals(idA, idC)
    }

    @Test fun `a newer generation existing invalidates an earlier one even without its own explicit discard`() {
        // Covers the case where a second generation begins directly after an
        // earlier one was discarded, with no further discard() call on the
        // newer one -- id mismatch alone is enough to keep the older one
        // permanently rejected.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L) // A begins
        val idA = state.currentGenerationId()
        state.discard(BusyBrainDiscardReason.SUPERSEDED_BY_NEW_TURN)
        state.tryBegin(2_000L) // C begins, no explicit discard on C
        assertTrue(state.isDiscarded(idA))
    }

    @Test fun `legitimate no-supersede generation remains deliverable`() {
        // A begins, Busy-Brain filler occurs (touches nothing here), no
        // newer substantive turn is accepted -- A must still be deliverable.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val idA = state.currentGenerationId()
        assertFalse(state.isDiscarded(idA))
    }

    @Test fun `A to B to C to late-A ownership race is rejected end to end`() {
        // The exact real-device race this fix closes:
        //   A starts -> B (newer deterministic turn) supersedes A ->
        //   C starts (a new generation) -> A finally completes, AFTER C began.
        // A must remain permanently invalid, and its late completion must not
        // disturb C's own in-flight state.
        val state = ScoutBusyBrainState()

        // A starts.
        state.tryBegin(1_000L)
        val idA = state.currentGenerationId()

        // B: a newer deterministic turn is accepted and answered -- supersedes A.
        state.discard(BusyBrainDiscardReason.SUPERSEDED_BY_NEW_TURN)
        assertFalse(state.isPending)

        // C: a new generation starts.
        val cBegan = state.tryBegin(2_000L)
        assertTrue(cBegan)
        val idC = state.currentGenerationId()
        assertNotEquals(idA, idC)

        // A finally completes, after C began.
        assertTrue(state.isDiscarded(idA)) // A's completion callback must see itself as discarded
        state.complete(idA) // A's late completion attempt

        // C's own ownership state must be completely untouched by A's stale completion.
        assertTrue(state.isPending)
        assertFalse(state.isDiscarded(idC))
        assertEquals(idC, state.currentGenerationId())

        // C itself can still complete normally afterward.
        state.complete(idC)
        assertFalse(state.isPending)
    }

    // --- Watchdog ---

    @Test fun `isStuck is false while still within the max duration`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        assertFalse(state.isStuck(nowMs = 60_000L, maxDurationMs = 120_000L))
    }

    @Test fun `isStuck is true once the max duration has elapsed`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        assertTrue(state.isStuck(nowMs = 1_000L + 120_001L, maxDurationMs = 120_000L))
    }

    @Test fun `isStuck is false when nothing is pending`() {
        val state = ScoutBusyBrainState()
        assertFalse(state.isStuck(nowMs = 999_999L, maxDurationMs = 120_000L))
    }
}
