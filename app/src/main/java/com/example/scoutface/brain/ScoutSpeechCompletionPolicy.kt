package com.example.scoutface.brain

/**
 * Tap-to-interrupt v1. Pure decision logic kept out of MainActivity so it's
 * unit-testable without an Activity/Android TTS engine -- the same
 * separation ScoutPendingAnswerGate/ScoutPostBootQuietGate already use for
 * their own MainActivity call sites.
 *
 * MainActivity's finishSpeechDispatch() is the one place a TTS dispatch's
 * lifecycle actually ends -- natural completion (onDone), an engine error
 * (onError), or a deliberate user tap (the explicit onStop() override, or
 * cancelling the pre-dispatch delay before tts.speak() ever ran). All three
 * need the exact same isSpeaking/face/captions/cooldown-timer/
 * STARTUP_GREETING/wantListening cleanup; Kind captures the small number of
 * ways they're allowed to legitimately differ.
 */
object ScoutSpeechCompletionPolicy {

    enum class Kind { NATURAL, ENGINE_ERROR, USER_INTERRUPTED }

    /**
     * Whether this completion may open a presence reply window. Never true
     * for an engine error or a user interruption -- as far as the person is
     * concerned Scout didn't finish speaking normally either way, the same
     * reasoning onError() already used before tap-to-interrupt existed.
     */
    fun opensPresenceReplyWindow(kind: Kind): Boolean = kind == Kind.NATURAL

    /**
     * Whether this completion may drain a queued pendingAiAnswer. True for
     * both natural completion and an engine error (pre-existing behavior,
     * unchanged) -- false ONLY for a user interruption. This is the entire
     * point of tap-to-interrupt v1's completion-kind split: without it, a
     * tap meant to silence Scout could immediately trigger a second,
     * unrelated queued answer to start speaking right after it -- the
     * failure mode tap-to-interrupt exists to avoid.
     */
    fun drainsPendingAnswer(kind: Kind): Boolean = kind != Kind.USER_INTERRUPTED

    /**
     * Whether this completion counts as the STARTUP_GREETING finishing, for
     * PR #68's startupGreetingFinishedAtMs anchor. True for every kind,
     * including USER_INTERRUPTED -- a deliberate tap ending "Hello. I am
     * Scout." early still means the greeting is over; PR #68's 5-minute
     * Companion Moment quiet period must not wait for a greeting Patrick
     * has already ended himself. Kept as an explicit function (not just
     * "always true, don't bother checking") so a future Kind added here
     * can't silently inherit this answer without a deliberate decision.
     */
    fun countsAsStartupGreetingFinished(kind: Kind): Boolean = true
}

/**
 * Tap-to-interrupt v1. Guards the pre-dispatch delay window in speak() (the
 * 220-650ms "natural pause" before tts.speak() actually reaches the TTS
 * engine) against two distinct risks: a tap arriving in that window needs to
 * know it can still cancel the pending dispatch, and the pending dispatch's
 * own delayed Runnable needs to confirm, right before it actually fires,
 * that no newer dispatch has since taken over the one pending slot out from
 * under it.
 */
object ScoutSpeechDispatchGuard {
    /**
     * True only if `candidateDispatchId` is still the one and only pending
     * dispatch -- i.e. it matches `activeDispatchId`, and neither is the
     * "nothing pending" sentinel (0). A mismatch means a newer speak() call
     * already replaced this pending slot; the candidate dispatch must not
     * be treated as cancellable by a tap, nor allowed to actually reach the
     * TTS engine from its own Runnable -- it no longer owns that slot.
     */
    fun isStillPending(activeDispatchId: Int, candidateDispatchId: Int): Boolean =
        candidateDispatchId != 0 && activeDispatchId == candidateDispatchId

    /**
     * PR #71 review round 3. Whether `dispatchId` is the one whose
     * completion should drive MainActivity's GLOBAL speaking state cleanup
     * (isSpeaking, face, captions, cooldown timers, wantListening, presence
     * reply window, pendingAiAnswer drain) -- as opposed to a dispatch that
     * merely finished (or was cancelled) while a DIFFERENT dispatch still
     * owns that state.
     *
     * "Submitted to the TTS engine" and "actually audible" are NOT the same
     * thing: with QUEUE_ADD, a newer dispatch can be accepted by the engine
     * while an older one is still genuinely playing (real-device-shaped
     * scenario: the boot announcement, still audible, with the
     * STT-unavailable follow-up already queued behind it). audibleDispatchId
     * -- set only from onStart()'s own resolved id, the one ground-truth
     * signal Android gives for "playing right now" -- is authoritative
     * whenever it's known (non-zero). submittedDispatchId (set at
     * tts.speak()-call time) is used ONLY as a fallback for the narrow
     * window between a dispatch reaching the engine and Android actually
     * confirming it started, or for an engine that never reliably calls
     * onStart() at all -- without this fallback, a dispatch finishing before
     * its own onStart() ever arrives (or a tap landing in that exact gap)
     * would be treated as if nothing owned Scout's speaking state, which
     * would leave Scout stuck appearing to speak forever.
     */
    fun ownsGlobalSpeakingState(
        dispatchId: Int,
        audibleDispatchId: Int,
        submittedDispatchId: Int
    ): Boolean =
        dispatchId == audibleDispatchId ||
            (audibleDispatchId == 0 && dispatchId == submittedDispatchId)
}
