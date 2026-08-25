package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers ScoutExpressionPriority's two ownership resolvers -- the pure rule
 * behind Emotional Face v1's expression layer. ScoutFaceView's own pulse
 * curves (rise/hold/decay magnitude tracking) and MainActivity's own trigger
 * call sites are not re-implemented here; this only exercises the priority
 * decision itself against every input it reads, matching the same
 * pure-function test architecture already used for
 * ScoutArrivalAcknowledgmentGate/ScoutSpeechCompletionPolicy.
 */
class ScoutExpressionPriorityTest {

    // --- resolveBrowOwner(): neutral baseline ---

    @Test fun `neutral when nothing active`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = false,
                noticeActive = false, attentiveActive = false
            )
        )
    }

    @Test fun `mouth neutral when nothing active`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = false, isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = false
            )
        )
    }

    // --- resolveBrowOwner(): full priority ladder ---

    @Test fun `attentive eligible while directly addressed and nothing else active`() {
        assertEquals(
            ScoutExpressionLayer.ATTENTIVE,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = false,
                noticeActive = false, attentiveActive = true
            )
        )
    }

    @Test fun `pleased overrides attentive`() {
        assertEquals(
            ScoutExpressionLayer.PLEASED,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = true,
                noticeActive = false, attentiveActive = true
            )
        )
    }

    @Test fun `uncertain overrides attentive`() {
        assertEquals(
            ScoutExpressionLayer.UNCERTAIN,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = true, pleasedActive = false,
                noticeActive = false, attentiveActive = true
            )
        )
    }

    @Test fun `notice pulse owns the brow when nothing higher-priority is active`() {
        assertEquals(
            ScoutExpressionLayer.NOTICE,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = false,
                noticeActive = true, attentiveActive = true
            )
        )
    }

    @Test fun `notice pulse does not combine destructively with a higher-priority expression -- uncertain wins outright`() {
        assertEquals(
            ScoutExpressionLayer.UNCERTAIN,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = true, pleasedActive = false,
                noticeActive = true, attentiveActive = false
            )
        )
    }

    @Test fun `notice pulse does not combine destructively with a higher-priority expression -- pleased wins outright`() {
        assertEquals(
            ScoutExpressionLayer.PLEASED,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = true,
                noticeActive = true, attentiveActive = false
            )
        )
    }

    @Test fun `uncertain and pleased interaction resolves deterministically -- uncertain wins the tie-break`() {
        assertEquals(
            ScoutExpressionLayer.UNCERTAIN,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = true, pleasedActive = true,
                noticeActive = true, attentiveActive = true
            )
        )
    }

    // --- resolveBrowOwner(): existing-state suppression ---

    @Test fun `thinking suppresses the entire new expression layer regardless of any pulse`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = true, isListening = false,
                uncertainActive = true, pleasedActive = true,
                noticeActive = true, attentiveActive = true
            )
        )
    }

    @Test fun `listening suppresses the entire new expression layer regardless of any pulse`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = true,
                uncertainActive = true, pleasedActive = true,
                noticeActive = true, attentiveActive = true
            )
        )
    }

    @Test fun `speaking alone does NOT suppress the brow layer -- pleased brow remains visible while speaking`() {
        // resolveBrowOwner() takes no isSpeaking parameter at all -- this
        // test documents that omission is deliberate, matching the approved
        // design's explicit "brow/eye portion may remain visible while
        // speaking" instruction for PLEASED.
        assertEquals(
            ScoutExpressionLayer.PLEASED,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = true,
                noticeActive = false, attentiveActive = false
            )
        )
    }

    // --- resolveMouthOwner(): speaking suppresses the mouth layer specifically ---

    @Test fun `speaking suppresses conflicting new mouth expression even when pleased and uncertain are both active`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = true, isThinking = false, isListening = false,
                uncertainActive = true, pleasedActive = true
            )
        )
    }

    @Test fun `pleased owns the mouth once speaking ends`() {
        assertEquals(
            ScoutExpressionLayer.PLEASED,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = false, isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = true
            )
        )
    }

    @Test fun `thinking suppresses the new mouth expression layer`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = false, isThinking = true, isListening = false,
                uncertainActive = true, pleasedActive = true
            )
        )
    }

    @Test fun `listening suppresses the new mouth expression layer`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = false, isThinking = false, isListening = true,
                uncertainActive = true, pleasedActive = true
            )
        )
    }

    @Test fun `uncertain and pleased mouth interaction resolves deterministically -- uncertain wins the same tie-break as brow`() {
        assertEquals(
            ScoutExpressionLayer.UNCERTAIN,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = false, isThinking = false, isListening = false,
                uncertainActive = true, pleasedActive = true
            )
        )
    }

    // --- Expression expiration / no stale ownership ---

    @Test fun `ownership falls through correctly once a higher-priority pulse expires`() {
        // Simulates uncertainActive flipping to false once its own pulse
        // magnitude has decayed below the caller's epsilon -- ownership
        // must fall through to the next-highest still-active candidate, not
        // get stuck on the one that just expired.
        assertEquals(
            ScoutExpressionLayer.PLEASED,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = true,
                noticeActive = false, attentiveActive = false
            )
        )
    }

    @Test fun `no stale pulse remains active indefinitely -- once every candidate clears, ownership returns to NONE`() {
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveBrowOwner(
                isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = false,
                noticeActive = false, attentiveActive = false
            )
        )
        assertEquals(
            ScoutExpressionLayer.NONE,
            ScoutExpressionPriority.resolveMouthOwner(
                isSpeaking = false, isThinking = false, isListening = false,
                uncertainActive = false, pleasedActive = false
            )
        )
    }
}
