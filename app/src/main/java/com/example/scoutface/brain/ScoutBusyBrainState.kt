package com.example.scoutface.brain

/**
 * Why a pending generation's eventual answer was marked unspeakable.
 * Mirrored as DiagLog.BusyBrainDiscardReason, matching how
 * ScoutConversationState's ConversationEndReason is mirrored as
 * DiagLog.ConversationEndReason -- keeps DiagLog independent of this package.
 */
enum class BusyBrainDiscardReason { CONVERSATION_CLOSED, TIMEOUT, SUPERSEDED_BY_NEW_TURN }

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
 *
 * Generation-ownership fix (PR 2). Real-device finding: isPending/
 * discardReason used to be the ONLY validity signal, shared globally across
 * whichever generation happened to be pending at any given moment. That let
 * a stale generation A -- discarded because a newer deterministic turn B
 * superseded it -- become wrongly "valid" again the instant a still-later
 * generation C called tryBegin(), because the old tryBegin() unconditionally
 * reset discardReason = null for the whole class, not for any one specific
 * generation. A's own eventual completion, arriving after C had already
 * begun, would then read discardReason == null and treat itself as
 * deliverable, even though it belonged to an already-superseded question.
 *
 * Fixed by scoping validity to a RAM-only, monotonically increasing
 * generation id owned entirely by this class (currentGenerationId, private,
 * never persisted, never decreases). Every generation's own async completion
 * callback must capture the id tryBegin() was current for and pass it back
 * to isDiscarded()/complete() -- never trusting "whatever is current now."
 * isDiscarded(id) is true if EITHER that exact id was explicitly discarded
 * (discardedGenerationId), OR a newer generation has since begun (id no
 * longer equals currentGenerationId) -- the second clause is what makes a
 * later generation's tryBegin() structurally unable to ever revalidate an
 * older one: tryBegin() only ever moves currentGenerationId forward, never
 * back, and nothing resets it. complete(id) is the same shape: a no-op
 * unless id is still the current generation, so a stale generation's late
 * completion can never clear a newer generation's isPending/startedAt out
 * from under it.
 */
class ScoutBusyBrainState {

    var isPending: Boolean = false
        private set

    private var startedAt: Long = 0L

    var discardReason: BusyBrainDiscardReason? = null
        private set

    // Generation-ownership fix (PR 2). RAM-only, monotonically increasing --
    // never persisted, never decremented, never reset. Ids start at 1 (0 is
    // reserved below as "never a real generation," so a caller that somehow
    // never captured a real id can't accidentally read as "current").
    private var currentGenerationId: Long = 0L

    // Which generation id (if any) was the target of the most recent
    // discard() call. Distinct from currentGenerationId itself so an older
    // generation's discarded status survives regardless of how many newer
    // generations begin afterward -- see the class doc comment above.
    private var discardedGenerationId: Long = 0L

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
     * same question. The fallback reads currentGenerationId() itself (see
     * that function's doc comment) to recover the SAME id Gemini's own
     * successful tryBegin() already minted, rather than needing this call to
     * hand back a value only on the invocation that actually incremented it.
     */
    fun tryBegin(nowMs: Long): Boolean {
        if (isPending) return false
        isPending = true
        startedAt = nowMs
        currentGenerationId += 1
        return true
    }

    /**
     * Read-only snapshot of the generation id currently in flight (or, once
     * completed/discarded, the most recently active one). Callers capture
     * this immediately after their own tryBegin() call, regardless of its
     * boolean result -- see tryBegin()'s doc comment for why the same-
     * question Gemini -> TinyLlama fallback relies on that.
     */
    fun currentGenerationId(): Long = currentGenerationId

    /**
     * Call once the pending question's definitive outcome (a real answer, or
     * a final failure message) has actually been handled -- frees the gate
     * for the next question. generationId must be the id the caller captured
     * via currentGenerationId() when its own generation began. A no-op if
     * generationId is no longer the current generation -- a stale generation
     * finally completing after a newer one has already begun must never
     * clear that newer generation's isPending/startedAt out from under it
     * (see the class doc comment's A -> B -> C race).
     */
    fun complete(generationId: Long) {
        if (generationId != currentGenerationId) return
        isPending = false
        startedAt = 0L
    }

    /**
     * The conversation was explicitly closed (goodbye/stop listening/good
     * night), the stuck-generation watchdog gave up waiting, or a genuinely
     * newer user turn was accepted and answered while this generation was
     * still pending -- either way, this generation's eventual answer must
     * never be spoken.
     *
     * Frees isPending immediately, not just discardReason -- a genuinely new
     * question (almost always in a freshly re-opened conversation, since
     * closing requires the wake word again) must never be told Scout is
     * "still thinking about your last question" about a question that's
     * already been abandoned. The still-in-flight generation's own result is
     * separately marked unspeakable via discardedGenerationId, checked by
     * its completion callback (isDiscarded(generationId)) independent of
     * isPending.
     *
     * No-op if nothing is pending.
     */
    fun discard(reason: BusyBrainDiscardReason): Boolean {
        if (!isPending) return false
        isPending = false
        startedAt = 0L
        discardReason = reason
        discardedGenerationId = currentGenerationId
        return true
    }

    /**
     * The one question every generation's completion callback must ask
     * before ever calling deliverAiResult()/respond() with its result: "am I
     * still the current, non-discarded generation?" True if EITHER this
     * exact generationId was the target of the most recent discard() call,
     * OR a newer generation has since begun (generationId no longer equals
     * currentGenerationId). The second clause covers the case where a second
     * generation begins directly (no explicit discard() in between, e.g. a
     * legitimate new question after the first one was already discarded) --
     * and, combined with tryBegin() only ever moving currentGenerationId
     * forward, is what makes a later generation's tryBegin() structurally
     * unable to ever revalidate an older, already-discarded one: this class
     * never resets discardedGenerationId or "un-advances" currentGenerationId
     * for any reason.
     */
    fun isDiscarded(generationId: Long): Boolean =
        generationId == discardedGenerationId || generationId != currentGenerationId

    /**
     * Watchdog check -- mirrors MAX_THINKING_DURATION_MS's existing shape
     * (see MainActivity's isThinking watchdog). Caller is responsible for
     * calling discard(TIMEOUT) when this returns true.
     */
    fun isStuck(nowMs: Long, maxDurationMs: Long): Boolean =
        isPending && startedAt > 0L && (nowMs - startedAt) > maxDurationMs
}
