package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutBusyBrainStateTest {

    // --- Beginning a generation ---

    @Test fun `a fresh question begins pending from idle`() {
        val state = ScoutBusyBrainState()
        val began = state.tryBegin(1_000L)
        assertTrue(began)
        assertTrue(state.isPending)
        assertFalse(state.isDiscarded())
    }

    @Test fun `a second question cannot begin while one is already pending`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val beganAgain = state.tryBegin(1_500L)
        assertFalse(beganAgain)
        assertTrue(state.isPending) // unchanged -- still the original generation
    }

    @Test fun `a same-question fallback calling tryBegin again is a harmless no-op`() {
        // Models Gemini REQUEST_STARTED flipping isPending true, then Gemini
        // failing and falling back to TinyLlama, which also calls tryBegin()
        // for the same still-pending question.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L) // Gemini's REQUEST_STARTED
        val fallbackBegan = state.tryBegin(1_200L) // TinyLlama fallback, same question
        assertFalse(fallbackBegan)
        assertTrue(state.isPending)
    }

    // --- Completing normally ---

    @Test fun `complete frees the gate for the next question`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        state.complete()
        assertFalse(state.isPending)
        val beganNext = state.tryBegin(2_000L)
        assertTrue(beganNext)
    }

    @Test fun `complete on an idle state is harmless`() {
        val state = ScoutBusyBrainState()
        state.complete()
        assertFalse(state.isPending)
    }

    // --- Discarding (explicit close / watchdog timeout) ---

    @Test fun `discard frees the gate immediately`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        val discarded = state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        assertTrue(discarded)
        assertFalse(state.isPending) // a new question must not hear "still thinking"
    }

    @Test fun `a new question can begin immediately after a discard`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        val beganNext = state.tryBegin(2_000L)
        assertTrue(beganNext)
        assertFalse(state.isDiscarded()) // the NEW question's own state, not tainted
    }

    @Test fun `discard on an idle state is a no-op and reports no transition`() {
        val state = ScoutBusyBrainState()
        val discarded = state.discard(BusyBrainDiscardReason.TIMEOUT)
        assertFalse(discarded)
        assertFalse(state.isDiscarded())
    }

    @Test fun `isDiscarded stays true for the abandoned generation's own late callback`() {
        // The whole point of keeping discardReason independent of isPending:
        // the generation that was discarded must still see isDiscarded() as
        // true when its callback eventually fires, even though isPending was
        // already freed for the next question.
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        assertTrue(state.isDiscarded())
        assertEquals(BusyBrainDiscardReason.CONVERSATION_CLOSED, state.discardReason)
    }

    @Test fun `discard reason is CONVERSATION_CLOSED or TIMEOUT as passed`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        state.discard(BusyBrainDiscardReason.TIMEOUT)
        assertEquals(BusyBrainDiscardReason.TIMEOUT, state.discardReason)
    }

    @Test fun `discardReason resets to null on the next tryBegin`() {
        val state = ScoutBusyBrainState()
        state.tryBegin(1_000L)
        state.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)
        state.tryBegin(2_000L)
        assertNull(state.discardReason)
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
