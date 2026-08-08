package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutConversationStateTest {

    // --- Opening ---

    @Test fun `wake-name turn starts a conversation from idle`() {
        val state = ScoutConversationState()
        val opened = state.openFromUserTurn(1_000L)
        assertTrue(opened)
        assertTrue(state.isActive)
        assertEquals(1_000L, state.startedAt)
        assertEquals(1_000L, state.lastUserTurnAt)
    }

    @Test fun `greeting starts a conversation from idle`() {
        // "hi"/"hello"/"hey"/"good morning" all route through the same
        // openFromUserTurn() call as a wake-name turn in MainActivity --
        // covered here at the state level, since the courtesy category
        // routing itself lives in MainActivity, not this pure class.
        val state = ScoutConversationState()
        val opened = state.openFromUserTurn(5_000L)
        assertTrue(opened)
        assertTrue(state.isActive)
        assertEquals(5_000L, state.startedAt)
    }

    @Test fun `presence-initiated Scout speech opens the reply opportunity`() {
        val state = ScoutConversationState()
        val opened = state.openFromScoutInitiated(2_000L)
        assertTrue(opened)
        assertTrue(state.isActive)
        assertEquals(2_000L, state.startedAt)
        assertEquals(2_000L, state.lastScoutTurnAt)
        assertEquals(0L, state.lastUserTurnAt) // no user turn has happened yet
    }

    @Test fun `a reply right after a Scout-initiated boot greeting needs no wake word`() {
        // Models the exact mechanism the boot-greeting fix relies on: the boot
        // announcement is a genuine spoken greeting, routed through
        // respond(isPresenceInitiated = true) in MainActivity exactly like any
        // other presence-initiated remark, so openFromScoutInitiated() runs at
        // boot. A reply that arrives while the underlying presence reply
        // window is still open must then read as an active conversation --
        // MainActivity's wake-gate check treats that as "no wake word needed",
        // which is the whole point of this fix (MainActivity itself has no
        // unit coverage, so this is the closest verifiable proxy).
        val state = ScoutConversationState()
        state.openFromScoutInitiated(10_000L)
        val evaluation = state.evaluate(nowMs = 12_000L, convoWindowOpen = true, presenceReplyWindowOpen = true)
        assertTrue(evaluation.isActive)
        assertFalse(evaluation.justTimedOut)
    }

    @Test fun `opening while already active does not reset startedAt`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        val openedAgain = state.openFromUserTurn(1_500L)
        assertFalse(openedAgain)
        assertEquals(1_000L, state.startedAt) // unchanged
        assertEquals(1_500L, state.lastUserTurnAt) // still updates
    }

    // --- Thanks: extend-only, never opens from idle ---

    @Test fun `thanks does not open a conversation from idle`() {
        val state = ScoutConversationState()
        state.extend(1_000L)
        assertFalse(state.isActive)
        assertEquals(0L, state.startedAt)
    }

    @Test fun `thanks extends an already-active conversation`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        state.extend(1_200L)
        assertTrue(state.isActive)
        assertEquals(1_000L, state.startedAt) // unchanged
        assertEquals(1_200L, state.lastUserTurnAt)
    }

    // --- Normal turns extend without resetting startedAt ---

    @Test fun `a normal user turn extends without resetting startedAt`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        state.openFromUserTurn(10_000L) // still active -- extend, not reopen
        assertEquals(1_000L, state.startedAt)
        assertEquals(10_000L, state.lastUserTurnAt)
    }

    @Test fun `a normal Scout turn extends without resetting startedAt`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        state.onScoutTurn(1_100L)
        assertEquals(1_000L, state.startedAt)
        assertEquals(1_100L, state.lastScoutTurnAt)
        assertTrue(state.isActive)
    }

    @Test fun `Scout turn is a no-op when not active`() {
        val state = ScoutConversationState()
        state.onScoutTurn(1_000L)
        assertFalse(state.isActive)
        assertEquals(0L, state.lastScoutTurnAt)
    }

    // --- Explicit close ---

    @Test fun `goodbye closes immediately`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        val closed = state.closeExplicitly(1_500L)
        assertTrue(closed)
        assertFalse(state.isActive)
        assertEquals(1_500L, state.endedAt)
        assertEquals(ConversationEndReason.EXPLICIT_END, state.endReason)
    }

    @Test fun `explicit close overrides a still-recent 30s timer`() {
        // The underlying window hasn't expired (caller would still compute
        // convoWindowOpen = true at this point), but closeExplicitly() must
        // still win outright.
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        state.closeExplicitly(1_500L) // well within a hypothetical 30s window
        val evaluation = state.evaluate(nowMs = 2_000L, convoWindowOpen = true, presenceReplyWindowOpen = false)
        assertFalse(evaluation.isActive)
        assertFalse(evaluation.justTimedOut) // already closed explicitly, not a timeout
    }

    @Test fun `closing when not active is harmless and reports no transition`() {
        val state = ScoutConversationState()
        val closed = state.closeExplicitly(1_000L)
        assertFalse(closed)
        assertFalse(state.isActive)
    }

    @Test fun `saying Scout's name after an explicit close starts a fresh conversation`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        state.closeExplicitly(1_500L)
        val reopened = state.openFromUserTurn(1_600L)
        assertTrue(reopened)
        assertTrue(state.isActive)
        assertEquals(1_600L, state.startedAt) // fresh start, not 1_000L
        assertEquals(0L, state.endedAt)
        assertNull(state.endReason)
    }

    // --- Silence timeout, explicit transition ---

    @Test fun `silence timeout closes cleanly with a real transition, not a stale read`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        val evaluation = state.evaluate(nowMs = 40_000L, convoWindowOpen = false, presenceReplyWindowOpen = false)
        assertFalse(evaluation.isActive)
        assertTrue(evaluation.justTimedOut)
        // The transition actually happened -- not merely a false return with
        // stale active-state values left behind.
        assertFalse(state.isActive)
        assertEquals(40_000L, state.endedAt)
        assertEquals(ConversationEndReason.SILENCE_TIMEOUT, state.endReason)
    }

    @Test fun `evaluate reports justTimedOut only once, not on every subsequent check`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        val first = state.evaluate(nowMs = 40_000L, convoWindowOpen = false, presenceReplyWindowOpen = false)
        val second = state.evaluate(nowMs = 41_000L, convoWindowOpen = false, presenceReplyWindowOpen = false)
        assertTrue(first.justTimedOut)
        assertFalse(second.justTimedOut) // already inactive, nothing new happened
        assertFalse(second.isActive)
    }

    @Test fun `evaluate does not close while either underlying window is still open`() {
        val state = ScoutConversationState()
        state.openFromUserTurn(1_000L)
        val stillInConvoWindow = state.evaluate(nowMs = 20_000L, convoWindowOpen = true, presenceReplyWindowOpen = false)
        assertTrue(stillInConvoWindow.isActive)
        assertFalse(stillInConvoWindow.justTimedOut)

        val stillInPresenceWindow = state.evaluate(nowMs = 35_000L, convoWindowOpen = false, presenceReplyWindowOpen = true)
        assertTrue(stillInPresenceWindow.isActive)
        assertFalse(stillInPresenceWindow.justTimedOut)
    }

    @Test fun `evaluate on an idle conversation never opens one`() {
        val state = ScoutConversationState()
        val evaluation = state.evaluate(nowMs = 1_000L, convoWindowOpen = true, presenceReplyWindowOpen = true)
        assertFalse(evaluation.isActive) // evaluate() never opens, only open*() do
        assertFalse(evaluation.justTimedOut)
    }
}
