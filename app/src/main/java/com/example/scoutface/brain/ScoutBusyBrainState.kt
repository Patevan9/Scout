package com.example.scoutface.brain

/**
 * Why a pending generation's eventual answer was marked unspeakable.
 * Mirrored as DiagLog.BusyBrainDiscardReason, matching how
 * ScoutConversationState's ConversationEndReason is mirrored as
 * DiagLog.ConversationEndReason -- keeps DiagLog independent of this package.
 */
enum class BusyBrainDiscardReason { CONVERSATION_CLOSED, TIMEOUT }

/**
 * Busy-Brain -- PR 1 (foundation/correctness only).
 *
 * RAM-only, in-memory tracker for "is a real Gemini/TinyLlama generation
 * currently pending for the question just asked." Never persisted -- no
 * TruthDb, no ConversationDb, no disk of any kind, same "live state, not
 * persisted" precedent as ScoutConversationState and AwarenessState. Gone on
 * process death or Activity recreation, rebuilt from nothing (isPending =
 * false).
 *
 * PR 1 scope only: this class tracks pending/discarded state and enforces
 * "never start a second generation while one is already pending." It does
 * NOT change when isThinking clears or when the microphone reopens -- that's
 * PR 2. Preserving today's mic-off-while-thinking behavior means the usual
 * way a *second* question could reach this class while isPending is still
 * true is narrow (mainly the existing MAX_THINKING_DURATION_MS watchdog
 * reopening the mic before a hung generation has actually returned) -- but
 * the arbitration here has to be correct regardless of how that second
 * question arrives, since it's also what protects the explicit-close case
 * (see discard()).
 */
class ScoutBusyBrainState {

    var isPending: Boolean = false
        private set

    private var startedAt: Long = 0L

    var discardReason: BusyBrainDiscardReason? = null
        private set

    /**
     * Call right when a real Gemini/TinyLlama generation actually starts
     * (Gemini's REQUEST_STARTED decision, or right before TinyLlama's
     * generateAsync() call). Returns false -- and changes nothing -- if a
     * generation is already pending.
     *
     * Deliberately NOT called at the top of "handle a new AI-bound question"
     * -- tryGemini() can resolve synchronously (a cached reply, a cooldown
     * block, an already-in-flight block) without ever truly starting an
     * async generation, and marking isPending true for those would leave it
     * stuck since nothing would ever call complete() for them.
     *
     * A same-question fallback (Gemini fails, falls back to TinyLlama) is
     * expected to call this too -- it's a harmless no-op in that case, since
     * Gemini's own REQUEST_STARTED already flipped isPending true for this
     * same question.
     */
    fun tryBegin(nowMs: Long): Boolean {
        if (isPending) return false
        isPending = true
        startedAt = nowMs
        discardReason = null
        return true
    }

    /**
     * Call once the pending question's definitive outcome (a real answer, or
     * a final failure message) has actually been handled -- frees the gate
     * for the next question.
     */
    fun complete() {
        isPending = false
        startedAt = 0L
    }

    /**
     * The conversation was explicitly closed (goodbye/stop listening/good
     * night), or the stuck-generation watchdog gave up waiting -- either
     * way, this generation's eventual answer must never be spoken.
     *
     * Frees isPending immediately, not just discardReason -- a genuinely new
     * question (almost always in a freshly re-opened conversation, since
     * closing requires the wake word again) must never be told Scout is
     * "still thinking about your last question" about a question that's
     * already been abandoned. The still-in-flight generation's own result is
     * separately marked unspeakable via discardReason, checked by its
     * completion callback (isDiscarded()) independent of isPending.
     *
     * No-op if nothing is pending.
     */
    fun discard(reason: BusyBrainDiscardReason): Boolean {
        if (!isPending) return false
        isPending = false
        startedAt = 0L
        discardReason = reason
        return true
    }

    /**
     * Checked by a generation's completion callback before ever calling
     * respond() with its result. Stays true until the next tryBegin() starts
     * a genuinely new question (which resets it to null).
     */
    fun isDiscarded(): Boolean = discardReason != null

    /**
     * Watchdog check -- mirrors MAX_THINKING_DURATION_MS's existing shape
     * (see MainActivity's isThinking watchdog). Caller is responsible for
     * calling discard(TIMEOUT) when this returns true.
     */
    fun isStuck(nowMs: Long, maxDurationMs: Long): Boolean =
        isPending && startedAt > 0L && (nowMs - startedAt) > maxDurationMs
}
