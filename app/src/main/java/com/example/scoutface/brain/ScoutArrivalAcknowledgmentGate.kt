package com.example.scoutface.brain

/**
 * Silent Arrival Acknowledgment v1. Pure decision logic for whether Scout's
 * face should give its one deliberate, silent "I noticed you" brow
 * acknowledgment right now -- kept out of MainActivity so the rule is
 * unit-testable without a camera/Activity, the same separation
 * ScoutPostBootQuietGate/ScoutReturnGreetingGate already use for their own
 * MainActivity call sites.
 *
 * Deliberately reuses the exact same stabilization signal the existing
 * (speech-producing) startup greeting already computes -- faceAppearanceMs,
 * reset to 0 the instant a frame has no face at all, restamped the moment a
 * face reappears -- rather than inventing a second, parallel notion of "how
 * long has this arrival lasted." That field is already the smallest existing
 * anti-flicker mechanism in the codebase: a single missed detection frame
 * resets it, so a momentary detector glitch can never look like a stable
 * arrival. This gate does NOT reuse PR #68's 5-minute post-boot Companion
 * Moment quiet period -- that gate exists specifically to suppress
 * spontaneous SPEECH, not silent facial awareness, and Scout should be able
 * to quietly notice someone reasonably soon after startup.
 *
 * alreadyAcknowledged is the caller's own one-shot latch for the CURRENT
 * continuous arrival -- this function only ever answers "should it fire
 * right now," never mutates or resets that latch itself. The caller resets
 * it to false only once a genuine absence is independently confirmed (see
 * MainActivity's own genuineAbsenceMarked), so a brief camera flicker during
 * continued presence can never re-arm a fresh acknowledgment -- only a real
 * departure and a subsequent, freshly-stabilized return can.
 */
object ScoutArrivalAcknowledgmentGate {

    fun shouldAcknowledge(
        alreadyAcknowledged: Boolean,
        isSpeaking: Boolean,
        isThinking: Boolean,
        faceAppearanceMs: Long,
        nowMs: Long,
        stabilizeMs: Long
    ): Boolean =
        !alreadyAcknowledged &&
            !isSpeaking &&
            !isThinking &&
            faceAppearanceMs != 0L &&
            (nowMs - faceAppearanceMs) >= stabilizeMs
}
