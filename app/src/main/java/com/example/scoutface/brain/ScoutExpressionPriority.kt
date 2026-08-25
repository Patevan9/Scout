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
    // and listening still win outright, exactly as the investigation's
    // ownership sketch (SPEAKING/THINKING/LISTENING > new expressions)
    // requires. Speaking is intentionally NOT checked here for the brow --
    // see resolveBrowOwner()'s own doc comment for why the brow/eye portion
    // is allowed to remain visible while speaking.
    //
    // Below thinking/listening: UNCERTAIN and PLEASED (the deliberate
    // expressive beats) outrank PR #73's own arrival-notice pulse, which
    // outranks the sustained ATTENTIVE cue -- exactly the tier order from
    // the approved design ("UNCERTAIN/PLEASED > PR #73 arrival ack >
    // ATTENTIVE > NEUTRAL"). UNCERTAIN is checked before PLEASED as the one
    // arbitrary (but now fixed and tested) tie-break for the rare case both
    // are simultaneously active -- e.g., "thank you" said while an
    // UNCERTAIN beat from moments earlier hasn't finished decaying yet.
    // Communicating a still-unresolved misunderstanding clearly takes
    // priority over layering warmth on top of it.

    /**
     * Resolves which expression owns the brow/eye portion of the face this
     * frame. Speaking is deliberately NOT a suppressing input here -- a
     * pleased/uncertain beat's brow may remain visible while Scout is
     * mid-utterance (see the PLEASED/UNCERTAIN design notes: only the
     * *mouth* portion is exclusively owned by the speaking animation).
     */
    fun resolveBrowOwner(
        isThinking: Boolean,
        isListening: Boolean,
        uncertainActive: Boolean,
        pleasedActive: Boolean,
        noticeActive: Boolean,
        attentiveActive: Boolean
    ): ScoutExpressionLayer {
        if (isThinking || isListening) return ScoutExpressionLayer.NONE
        return when {
            uncertainActive -> ScoutExpressionLayer.UNCERTAIN
            pleasedActive   -> ScoutExpressionLayer.PLEASED
            noticeActive    -> ScoutExpressionLayer.NOTICE
            attentiveActive -> ScoutExpressionLayer.ATTENTIVE
            else            -> ScoutExpressionLayer.NONE
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
}
