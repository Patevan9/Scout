package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoutPendingAnswerGateTest {

    private val maxAgeMs = 30_000L
    private val queuedAtMs = 1_000_000L

    // --- No queued answer: unaffected by every other input ---

    @Test fun `no queued answer returns NONE`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.NONE,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = false,
                wasPresenceInitiated = false,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs,
                maxAgeMs = maxAgeMs
            )
        )
    }

    @Test fun `no queued answer returns NONE even if presence-initiated and old`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.NONE,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = false,
                wasPresenceInitiated = true,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + 60_000L,
                maxAgeMs = maxAgeMs
            )
        )
    }

    // --- Non-presence completions: deliver when fresh, expire when stale ---

    @Test fun `fresh non-presence completion delivers`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.DELIVER,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = false,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + 5_000L,
                maxAgeMs = maxAgeMs
            )
        )
    }

    @Test fun `exactly 30 seconds old non-presence completion still delivers -- boundary inclusive`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.DELIVER,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = false,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + maxAgeMs,
                maxAgeMs = maxAgeMs
            )
        )
    }

    @Test fun `older than 30 seconds non-presence completion expires`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.EXPIRED,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = false,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + maxAgeMs + 1L,
                maxAgeMs = maxAgeMs
            )
        )
    }

    // --- Presence-initiated completions: hold when fresh, still expire when stale ---
    // (real-device finding this fix closes -- a presence remark, e.g. a boot
    // greeting or return greeting, must never drain a fresh queued answer)

    @Test fun `fresh presence-initiated completion holds`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.HOLD,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = true,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + 5_000L,
                maxAgeMs = maxAgeMs
            )
        )
    }

    @Test fun `presence-initiated completion exactly at 30 seconds still holds -- boundary inclusive`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.HOLD,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = true,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + maxAgeMs,
                maxAgeMs = maxAgeMs
            )
        )
    }

    @Test fun `presence-initiated completion older than 30 seconds expires -- expiry checked before hold`() {
        assertEquals(
            ScoutPendingAnswerGate.Decision.EXPIRED,
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = true,
                queuedAtMs = queuedAtMs,
                nowMs = queuedAtMs + maxAgeMs + 1L,
                maxAgeMs = maxAgeMs
            )
        )
    }

    // --- Sequential real-world shape: a presence hold, then a later completion ---
    // (the same queuedAtMs carries across both calls -- HOLD never resets it,
    // matching MainActivity: only clearPendingAiAnswer() ever changes it)

    @Test fun `presence hold followed by a non-presence completion within 30 seconds delivers`() {
        val heldDecision = ScoutPendingAnswerGate.decide(
            hasQueuedAnswer = true,
            wasPresenceInitiated = true,
            queuedAtMs = queuedAtMs,
            nowMs = queuedAtMs + 5_000L,
            maxAgeMs = maxAgeMs
        )
        assertEquals(ScoutPendingAnswerGate.Decision.HOLD, heldDecision)

        val laterDecision = ScoutPendingAnswerGate.decide(
            hasQueuedAnswer = true,
            wasPresenceInitiated = false,
            queuedAtMs = queuedAtMs,
            nowMs = queuedAtMs + 20_000L,
            maxAgeMs = maxAgeMs
        )
        assertEquals(ScoutPendingAnswerGate.Decision.DELIVER, laterDecision)
    }

    @Test fun `presence hold followed by a non-presence completion after 30 seconds expires`() {
        val heldDecision = ScoutPendingAnswerGate.decide(
            hasQueuedAnswer = true,
            wasPresenceInitiated = true,
            queuedAtMs = queuedAtMs,
            nowMs = queuedAtMs + 5_000L,
            maxAgeMs = maxAgeMs
        )
        assertEquals(ScoutPendingAnswerGate.Decision.HOLD, heldDecision)

        val laterDecision = ScoutPendingAnswerGate.decide(
            hasQueuedAnswer = true,
            wasPresenceInitiated = false,
            queuedAtMs = queuedAtMs,
            nowMs = queuedAtMs + maxAgeMs + 1L,
            maxAgeMs = maxAgeMs
        )
        assertEquals(ScoutPendingAnswerGate.Decision.EXPIRED, laterDecision)
    }

    // --- Two-presence-completions-in-a-row: still holds, still bound by the
    // ORIGINAL queued time, not refreshed by the intervening hold ---

    @Test fun `a second presence-initiated completion still holds if still fresh from the original queue time`() {
        val firstHold = ScoutPendingAnswerGate.decide(
            hasQueuedAnswer = true,
            wasPresenceInitiated = true,
            queuedAtMs = queuedAtMs,
            nowMs = queuedAtMs + 5_000L,
            maxAgeMs = maxAgeMs
        )
        assertEquals(ScoutPendingAnswerGate.Decision.HOLD, firstHold)

        val secondHold = ScoutPendingAnswerGate.decide(
            hasQueuedAnswer = true,
            wasPresenceInitiated = true,
            queuedAtMs = queuedAtMs,
            nowMs = queuedAtMs + 25_000L,
            maxAgeMs = maxAgeMs
        )
        assertEquals(ScoutPendingAnswerGate.Decision.HOLD, secondHold)
    }
}
