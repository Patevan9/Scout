package com.example.scoutface.brain

/**
 * Busy-Brain -- pendingAiAnswer lifecycle fix. Pure decision logic for
 * whether a queued Gemini/TinyLlama answer (MainActivity's pendingAiAnswer)
 * should be delivered, held, or discarded as expired, evaluated once from
 * the shared TTS onDone()/onError() drain point each time an utterance
 * finishes -- kept separate from MainActivity's own fields the same way
 * ScoutBusyBrainDelivery is, so the priority order is locked in by a unit
 * test rather than only exercised by hand on a real device.
 *
 * Real-device finding (Fold 7): Scout spoke its boot greeting ("Hello, I'm
 * back."), the user replied "Welcome back!", and Scout answered with a
 * fabricated "And about your earlier question--" reply to a question that
 * was never actually asked in that exchange. pendingAiAnswer had no expiry
 * and no relevance check -- it drained onto the very next TTS completion
 * regardless of what that completion actually was, including an unrelated
 * proactive remark Scout initiated itself (a presence/return greeting, a
 * Companion Moment), however much time had actually passed.
 */
object ScoutPendingAnswerGate {

    enum class Decision { NONE, DELIVER, HOLD, EXPIRED }

    /**
     * queuedAtMs -- when the answer was actually placed into pendingAiAnswer
     * (deliverAiResult() queuing it), not when generation began. maxAgeMs is
     * a standalone constant (MainActivity's PENDING_AI_ANSWER_MAX_AGE_MS),
     * deliberately not derived from PRESENCE_REPLY_WINDOW_MS -- a different
     * concept (how long Scout's own proactive remark stays wake-word-free
     * follow-up-able) that happens to share a similar magnitude.
     *
     * Priority, in order:
     *   1. No queued answer at all                                -> NONE
     *   2. Queued answer older than maxAgeMs                      -> EXPIRED
     *   3. Fresh answer, this completion was presence-initiated   -> HOLD
     *   4. Fresh answer, this completion was NOT presence-initiated -> DELIVER
     *
     * Expiry is deliberately checked before HOLD: a presence-initiated
     * completion (boot greeting, idle-silence remark, return greeting,
     * Companion Moment) must not indefinitely protect an answer that has
     * actually gone stale -- an answer sitting through several presence
     * remarks in a row still expires once maxAgeMs has passed, rather than
     * being held forever by the next presence-initiated completion.
     */
    fun decide(
        hasQueuedAnswer: Boolean,
        wasPresenceInitiated: Boolean,
        queuedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long
    ): Decision {
        if (!hasQueuedAnswer) return Decision.NONE
        if (nowMs - queuedAtMs > maxAgeMs) return Decision.EXPIRED
        return if (wasPresenceInitiated) Decision.HOLD else Decision.DELIVER
    }
}
