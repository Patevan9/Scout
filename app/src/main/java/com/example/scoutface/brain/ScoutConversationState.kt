package com.example.scoutface.brain

/**
 * Why a conversation transitioned from active to inactive. Mirrored as its
 * own controlled token inside DiagLog (DiagLog.ConversationEndReason) rather
 * than imported there directly -- matches how DiagIntent mirrors IntentType
 * and DiagMomentCategory mirrors MomentCategory, keeping DiagLog independent
 * of this package.
 */
enum class ConversationEndReason { SILENCE_TIMEOUT, EXPLICIT_END }

/**
 * Result of evaluate() -- distinguishes "still active" / "was already
 * inactive" from "just timed out on this exact call". A plain Boolean return
 * can't tell those apart, and the caller needs to, so it can log the
 * SILENCE_TIMEOUT transition exactly once rather than on every subsequent
 * already-inactive check.
 */
data class ScoutConversationEvaluation(val isActive: Boolean, val justTimedOut: Boolean)

/**
 * Better Conversation State -- Phase 1.
 *
 * RAM-only, in-memory turn-taking state for one MainActivity instance. Never
 * persisted -- no TruthDb, no HabitLayer, no ConversationDb, no disk of any
 * kind, matching the same "live state, not persisted" precedent already
 * established for AwarenessState (see Scout_Awareness_Layer_Spec.md SS8).
 * Gone on process death or Activity recreation, rebuilt from nothing
 * (isActive = false) -- exactly like every other raw timing field already in
 * MainActivity today (lastScoutResponseMs, presenceReplyWindowUntilMs, ...).
 *
 * Deliberately additive, not a replacement: CONVO_WINDOW_MS (30s) and
 * PRESENCE_REPLY_WINDOW_MS (40s) in MainActivity are untouched and remain the
 * actual timing source, passed in by the caller. This class adds exactly one
 * thing those two timers cannot express on their own: an explicit "this
 * conversation was closed on purpose" signal that overrides a still-recent
 * timer -- so saying "goodbye" (or "stop listening") stops wake-word-free
 * follow-ups immediately, instead of only after the full 30/40 seconds
 * happens to elapse on its own.
 *
 * Does NOT touch, read, or depend on lastSpeechDoneMs, ttsLockoutUntilMs, the
 * mic-restart cooldown math (ScoutMicRestartTiming), or the self-echo guard
 * (lastScoutUtteranceNormalized matching). Those audio-safety mechanisms live
 * entirely outside this class and keep working exactly as before regardless
 * of conversation state -- this class is consulted only for the wake-gate
 * decision (does this utterance need the wake word), nothing else.
 */
class ScoutConversationState {

    var isActive: Boolean = false
        private set

    var startedAt: Long = 0L
        private set

    var lastUserTurnAt: Long = 0L
        private set

    var lastScoutTurnAt: Long = 0L
        private set

    var endedAt: Long = 0L
        private set

    var endReason: ConversationEndReason? = null
        private set

    private fun openIfIdle(nowMs: Long): Boolean {
        val opened = !isActive
        if (opened) {
            isActive = true
            startedAt = nowMs
            endedAt = 0L
            endReason = null
        }
        return opened
    }

    /**
     * Wake-word heard, an opening courtesy phrase ("hi"/"hello"/"hey"/"good
     * morning"), or a real question dispatched while already active. Opens a
     * fresh conversation if idle (startedAt = nowMs, lastScoutTurnAt reset to
     * 0 since this new conversation hasn't had one yet); if already active,
     * behaves like an ordinary turn (startedAt unchanged). Returns true only
     * when this call is what opened it, so the caller can log a start event
     * exactly once.
     */
    fun openFromUserTurn(nowMs: Long): Boolean {
        val opened = openIfIdle(nowMs)
        if (opened) lastScoutTurnAt = 0L
        lastUserTurnAt = nowMs
        return opened
    }

    /**
     * Scout speaks first (a presence-initiated remark -- idle-silence
     * acknowledgment, return greeting, or a Companion Moment). Opens a fresh
     * conversation if idle (lastUserTurnAt reset to 0 for the same reason as
     * above); if already active, just records the turn.
     */
    fun openFromScoutInitiated(nowMs: Long): Boolean {
        val opened = openIfIdle(nowMs)
        if (opened) lastUserTurnAt = 0L
        lastScoutTurnAt = nowMs
        return opened
    }

    /**
     * An EXTEND-only user turn ("thanks"/"thank you") -- keeps an already-
     * active conversation open, but deliberately does NOT open one from idle.
     * A no-op if the conversation isn't already active.
     */
    fun extend(nowMs: Long) {
        if (isActive) lastUserTurnAt = nowMs
    }

    /**
     * Scout's own turn during an already-active conversation (an ordinary
     * respond() call, not a presence-initiated one). A no-op if not active --
     * Scout speaking without an active conversation to extend (e.g. a
     * presence-initiated remark, handled by openFromScoutInitiated() instead)
     * shouldn't silently open state here too.
     */
    fun onScoutTurn(nowMs: Long) {
        if (isActive) lastScoutTurnAt = nowMs
    }

    /**
     * An explicit ending phrase ("goodbye"/"bye"/"good night"/"that's
     * all"/"that will be all"/"stop listening"/"you can stop listening").
     * Closes immediately, regardless of how recently the underlying 30s/40s
     * timer was extended -- an explicit close always overrides a still-recent
     * timer. Harmless if the conversation wasn't active to begin with.
     * Returns true only if this call is what closed it, so the caller can log
     * the end event exactly once.
     */
    fun closeExplicitly(nowMs: Long): Boolean {
        val wasActive = isActive
        isActive = false
        endedAt = nowMs
        endReason = ConversationEndReason.EXPLICIT_END
        return wasActive
    }

    /**
     * The "next evaluation" point -- called once per recognized utterance,
     * right where the wake-gate decision is made. [convoWindowOpen] and
     * [presenceReplyWindowOpen] are computed by the caller from the existing,
     * unchanged CONVO_WINDOW_MS/PRESENCE_REPLY_WINDOW_MS timers -- this class
     * never reads a clock or a timer constant itself.
     *
     * If the conversation is active but both underlying windows have lapsed,
     * performs the actual active -> inactive / SILENCE_TIMEOUT transition
     * here -- not merely a stale read that would leave isActive/endedAt/
     * endReason holding leftover values from the last real turn -- and
     * reports it via justTimedOut so the caller can log it exactly once.
     *
     * If already inactive (idle, or already explicitly closed), this simply
     * reports that. It never reopens a conversation on its own -- only
     * openFromUserTurn()/openFromScoutInitiated() do that -- which is exactly
     * what makes an explicit close override a still-recent timer: once
     * closeExplicitly() has run, evaluate() keeps reporting inactive even if
     * the raw 30s/40s window hasn't technically expired yet.
     */
    fun evaluate(nowMs: Long, convoWindowOpen: Boolean, presenceReplyWindowOpen: Boolean): ScoutConversationEvaluation {
        val justTimedOut = isActive && !convoWindowOpen && !presenceReplyWindowOpen
        if (justTimedOut) {
            isActive = false
            endedAt = nowMs
            endReason = ConversationEndReason.SILENCE_TIMEOUT
        }
        return ScoutConversationEvaluation(isActive, justTimedOut)
    }
}
