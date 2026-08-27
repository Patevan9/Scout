package com.example.scoutface.brain

/**
 * Which expression currently owns a given portion (brow/eye, or mouth) of
 * ScoutFaceView's Emotional Face v1 expression layer -- the "new" layer, on
 * top of (never replacing) the existing speaking/thinking/listening state
 * machinery, PR #73's arrival acknowledgment, and ambient idle animation.
 *
 * NOTICE and ATTENTIVE are brow/eye-only expressions -- they have no mouth
 * component, so they never appear as a mouth owner (see
 * ScoutExpressionPriority.resolveMouthOwner()'s doc comment).
 */
enum class ScoutExpressionLayer {
    NONE, ATTENTIVE, NOTICE, PLEASED, UNCERTAIN
}

/**
 * Emotional Face v1 -- the small, deterministic expression-ownership
 * resolver the investigation called for. Pure and stateless: every input is
 * a plain boolean the caller (ScoutFaceView) already computes each frame
 * from its own existing or newly-added state (current speaking/thinking/
 * listening flags, and whether each pulse's own already-tracked magnitude
 * is currently above a small epsilon) -- this object owns none of that
 * state itself, and owns no pulse timing/decay math either. Its only job is
 * the ownership *decision*, which is what actually needed to stop new
 * expression signals from just adding conflicting brow/mouth offsets
 * together the way today's independent listeningLift/thinkingLift/
 * speechBrowLift/noticePulse terms already do.
 *
 * Deliberately not a state machine: no transitions are stored here, nothing
 * is mutated, and no AI or generative signal ever reaches this function --
 * it is a plain, ordered priority check over booleans, called fresh every
 * frame from ScoutFaceView.updateLife().
 */
object ScoutExpressionPriority {

    // Priority, highest first: existing speaking/thinking/listening behavior
    // is enforced by the caller never even offering an "active" expression
    // candidate while those states hold the face (see MainActivity/
    // ScoutFaceView call sites) -- but thinking and listening are also
    // checked directly here, as the second, structural line of defense: even
    // if a caller ever changed to offer a candidate unconditionally, thinking
    // still wins outright, exactly as the investigation's ownership sketch
    // (THINKING > new expressions) requires. Speaking is intentionally NOT
    // checked here for the brow -- see resolveBrowOwner()'s own doc comment
    // for why the brow/eye portion is allowed to remain visible while
    // speaking.
    //
    // Expression Visibility v2: LISTENING no longer vetoes ATTENTIVE. The
    // investigation's real-device finding was that ATTENTIVE -- the cue that
    // exists specifically to communicate "I am focused on you / listening to
    // you" -- was structurally impossible to ever see, because the one state
    // it's meant to describe (listening) was exactly the state that hid it.
    // Listening still vetoes UNCERTAIN/PLEASED/NOTICE unchanged -- those are
    // deliberate reactive beats about something Scout just heard or
    // concluded, not a sustained "I'm paying attention" cue, so it remains
    // correct for a fresh listening state to take over from a still-decaying
    // one of those. UNCERTAIN and PLEASED (the deliberate expressive beats)
    // outrank PR #73's own arrival-notice pulse, which outranks the
    // sustained ATTENTIVE cue -- exactly the tier order from the approved
    // design ("UNCERTAIN/PLEASED > PR #73 arrival ack > ATTENTIVE >
    // NEUTRAL"), preserved unchanged for the non-listening case. UNCERTAIN
    // is checked before PLEASED as the one arbitrary (but now fixed and
    // tested) tie-break for the rare case both are simultaneously active --
    // e.g., "thank you" said while an UNCERTAIN beat from moments earlier
    // hasn't finished decaying yet. Communicating a still-unresolved
    // misunderstanding clearly takes priority over layering warmth on top of
    // it.

    /**
     * Resolves which expression owns the brow/eye portion of the face this
     * frame. Speaking is deliberately NOT a suppressing input here -- a
     * pleased/uncertain beat's brow may remain visible while Scout is
     * mid-utterance (see the PLEASED/UNCERTAIN design notes: only the
     * *mouth* portion is exclusively owned by the speaking animation).
     *
     * Expression Visibility v2: [isListening] no longer suppresses
     * [attentiveActive] (see this object's own class-level comment for why)
     * -- it still suppresses [uncertainActive]/[pleasedActive]/[noticeActive]
     * exactly as before. [isThinking] still suppresses every candidate,
     * [attentiveActive] included, unchanged: THINKING keeps deterministic,
     * exclusive ownership of the brow whenever it's true.
     */
    fun resolveBrowOwner(
        isThinking: Boolean,
        isListening: Boolean,
        uncertainActive: Boolean,
        pleasedActive: Boolean,
        noticeActive: Boolean,
        attentiveActive: Boolean
    ): ScoutExpressionLayer {
        if (isThinking) return ScoutExpressionLayer.NONE
        return when {
            uncertainActive && !isListening -> ScoutExpressionLayer.UNCERTAIN
            pleasedActive && !isListening   -> ScoutExpressionLayer.PLEASED
            noticeActive && !isListening    -> ScoutExpressionLayer.NOTICE
            attentiveActive                 -> ScoutExpressionLayer.ATTENTIVE
            else                            -> ScoutExpressionLayer.NONE
        }
    }

    /**
     * Resolves which expression owns the mouth-corner portion of the face
     * this frame. Unlike resolveBrowOwner(), speaking DOES suppress this
     * layer entirely -- the mouth is already fully owned by the open/closed
     * speech animation while vSpeaking is true, and a static corner-bias
     * target must never fight that geometry (this is what the "speaking
     * suppresses conflicting new mouth expression" requirement means).
     * NOTICE and ATTENTIVE never own the mouth -- both are brow/eye-only by
     * design, so they are not even accepted as parameters here.
     */
    fun resolveMouthOwner(
        isSpeaking: Boolean,
        isThinking: Boolean,
        isListening: Boolean,
        uncertainActive: Boolean,
        pleasedActive: Boolean
    ): ScoutExpressionLayer {
        if (isSpeaking || isThinking || isListening) return ScoutExpressionLayer.NONE
        return when {
            uncertainActive -> ScoutExpressionLayer.UNCERTAIN
            pleasedActive   -> ScoutExpressionLayer.PLEASED
            else            -> ScoutExpressionLayer.NONE
        }
    }

    // Round 2 fix: PLEASED/UNCERTAIN's mouth expression is armed by
    // pleasedBeat()/uncertainBeat() at the same moment as the brow pulse,
    // but speaking (which owns the mouth exclusively per
    // resolveMouthOwner() above) doesn't actually become true until TTS's
    // own onStart() callback fires -- MainActivity's "natural pause"
    // pre-dispatch delay (220-650ms) sits entirely BEFORE that, and the
    // brow pulse's own hold/decay clock keeps running the whole time
    // regardless. Left as originally implemented, the brow pulse's
    // magnitude (which the mouth's target was directly derived from) could
    // fully decay before speaking ever starts, let alone finishes -- so the
    // approved mouth shape would exist in code but never actually render in
    // a real interaction. This function is the one small addition that
    // fixes it: it decides, once per frame, whether an armed mouth
    // expression should release now and start its OWN independent,
    // freshly-timed hold/decay window (see ScoutFaceView's
    // pleasedMouthIntensity/uncertainMouthIntensity) -- never whether to
    // render anything, and never the brow pulse's timing, which is
    // completely unaffected by this and stays on its original immediate
    // clock.
    //
    // [sawSpeakingWhileArmed] is the caller's own bookkeeping of whether
    // isSpeaking has been observed true at least once since arming --
    // required because "isSpeaking is currently false" is ambiguous on its
    // own (it's equally true during the pre-dispatch delay, before speech
    // has started, as it is after speech has genuinely finished). Release
    // fires on the real falling edge (was speaking, now isn't) once that
    // flag is set. [armedForMs]/[armTimeoutMs] are a safety fallback only,
    // for the case speech evidently never starts at all -- every real
    // MainActivity call site always speaks shortly after arming, so this
    // exists purely so an unanticipated edge case can't leave the mouth
    // silently armed forever, not because it's expected to fire in
    // practice.
    fun shouldReleaseDeferredMouthExpression(
        armed: Boolean,
        isSpeaking: Boolean,
        sawSpeakingWhileArmed: Boolean,
        armedForMs: Long,
        armTimeoutMs: Long
    ): Boolean {
        if (!armed || isSpeaking) return false
        return sawSpeakingWhileArmed || armedForMs >= armTimeoutMs
    }
}
