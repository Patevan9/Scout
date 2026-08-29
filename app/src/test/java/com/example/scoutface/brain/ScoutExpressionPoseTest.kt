package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers ScoutExpressionPose -- Expression System v3's single coordinated
 * pose resolver, plus its two small composition helpers (per-eye blink/
 * expression closure, dispatch-safe speaking-mouth intensity). Mirrors the
 * same pure-function test architecture as ScoutExpressionPriorityTest;
 * ScoutFaceView's own rise/hold/decay clocks and MainActivity's trigger
 * call sites are not re-implemented here.
 */
class ScoutExpressionPoseTest {

    // --- forOwner(): NONE / neutral baseline ---

    @Test fun `NONE owner and zero speaking intensity produces the all-zero pose`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.NONE, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 1f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(ExpressionPose.NONE, pose)
    }

    @Test fun `mouthOpenBias is NOT gated by browOwner or mouthOwner -- that independence is the whole point of Correction 1`() {
        // browOwner and mouthOwner are both NONE here (the exact post-decay
        // scenario the dispatch-safe fix exists for), yet the open-mouth
        // bias must still be fully active because it depends on neither --
        // only on pleasedSpeakingIntensity/uncertainSpeakingIntensity,
        // which ScoutFaceView drives from real vSpeaking, never from brow
        // ownership.
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.NONE, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 1f,
            pleasedSpeakingIntensity = 1f, uncertainSpeakingIntensity = 1f
        )
        assertTrue(pose.mouthOpenBiasL > 0f)
        assertTrue(pose.mouthOpenBiasR > 0f)
        // Every brow/lid/gaze/closed-mouth field, by contrast, IS gated by
        // its own owner and stays exactly zero here.
        assertEquals(0f, pose.browLiftL, 0.0001f)
        assertEquals(0f, pose.upperLidClosureDeltaL, 0.0001f)
        assertEquals(0f, pose.mouthCornerL, 0.0001f)
    }

    @Test fun `THINKING maps to NONE brow owner and also produces the all-zero pose`() {
        // resolveBrowOwner()/resolveMouthOwner() already force NONE whenever
        // isThinking is true -- this documents that ScoutExpressionPose
        // itself has no separate isThinking check, because it never needs
        // one: a NONE owner already guarantees the zero pose by construction.
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.NONE, browProgress = 0.7f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0.5f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(ExpressionPose.NONE, pose)
    }

    // --- forOwner(): ATTENTIVE ---

    @Test fun `ATTENTIVE at full progress hits exact target amplitudes, symmetric, no mouth or lower-lid change`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.ATTENTIVE, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(20f, pose.browLiftL, 0.0001f)
        assertEquals(20f, pose.browLiftR, 0.0001f)
        assertEquals(pose.browLiftL, pose.browLiftR, 0.0001f)
        assertEquals(6f, pose.browArchOuterL, 0.0001f)
        assertEquals(pose.browArchOuterL, pose.browArchOuterR, 0.0001f)
        // Negative -- ATTENTIVE OPENS the eye, never narrows it.
        assertEquals(-0.09f, pose.upperLidClosureDeltaL, 0.0001f)
        assertEquals(pose.upperLidClosureDeltaL, pose.upperLidClosureDeltaR, 0.0001f)
        assertEquals(0.6f, pose.gazeDamping, 0.0001f)
        // ATTENTIVE never touches lower lid, gaze offset, or mouth.
        assertEquals(0f, pose.lowerLidL, 0.0001f)
        assertEquals(0f, pose.lowerLidR, 0.0001f)
        assertEquals(0f, pose.gazeOffsetX, 0.0001f)
        assertEquals(0f, pose.gazeOffsetY, 0.0001f)
        assertEquals(0f, pose.mouthCornerL, 0.0001f)
    }

    // --- forOwner(): PLEASED symmetry ---

    @Test fun `PLEASED is fully symmetric across every left-right field pair`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.PLEASED, browProgress = 0.6f,
            mouthOwner = ScoutExpressionLayer.PLEASED, closedMouthIntensity = 0.8f,
            pleasedSpeakingIntensity = 0.4f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(pose.browLiftL, pose.browLiftR, 0.0001f)
        assertEquals(pose.upperLidClosureDeltaL, pose.upperLidClosureDeltaR, 0.0001f)
        assertEquals(pose.lowerLidL, pose.lowerLidR, 0.0001f)
        assertEquals(pose.mouthCornerL, pose.mouthCornerR, 0.0001f)
        // PLEASED's own upper-lid narrowing is positive (closure), pairing
        // with the existing lower-lid rise -- both eyes crinkle together.
        assertTrue(pose.upperLidClosureDeltaL > 0f)
        assertTrue(pose.lowerLidL > 0f)
        // PLEASED never touches brow arch-outer or gaze -- those are
        // ATTENTIVE/UNCERTAIN-only channels.
        assertEquals(0f, pose.browArchOuterL, 0.0001f)
        assertEquals(0f, pose.gazeOffsetX, 0.0001f)
    }

    @Test fun `PLEASED open-mouth bias is symmetric and independent of closedMouthIntensity`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.NONE, browProgress = 0f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 1f, uncertainSpeakingIntensity = 0f
        )
        // Brow has already decayed (NONE) and the closed-mouth path hasn't
        // released yet (also NONE) -- exactly the mid-speech scenario the
        // dispatch-safe fix exists for. The open-mouth bias is still fully
        // active because it depends on neither.
        assertEquals(5f, pose.mouthOpenBiasL, 0.0001f)
        assertEquals(5f, pose.mouthOpenBiasR, 0.0001f)
        assertEquals(0f, pose.mouthCornerL, 0.0001f)
    }

    // --- forOwner(): UNCERTAIN asymmetry ---

    @Test fun `UNCERTAIN brow lift and arch are left-only, right stays exactly zero`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.UNCERTAIN, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(18f, pose.browLiftL, 0.0001f)
        assertEquals(0f, pose.browLiftR, 0.0001f)
        assertEquals(8f, pose.browArchOuterL, 0.0001f)
        assertEquals(0f, pose.browArchOuterR, 0.0001f)
    }

    @Test fun `UNCERTAIN upper and lower lid narrowing are right-eye-only, left stays exactly neutral`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.UNCERTAIN, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        // Left eye is exactly 0f -- neutral, never an opposite-signed
        // opening value -- this is what keeps the asymmetry from reading
        // as a wink (see ScoutExpressionPose.eyeClosure()'s doc comment).
        assertEquals(0f, pose.upperLidClosureDeltaL, 0.0001f)
        assertTrue(pose.upperLidClosureDeltaR > 0f)
        assertEquals(0f, pose.lowerLidL, 0.0001f)
        assertTrue(pose.lowerLidR > 0f)
        // The narrowing amplitude must stay well under full closure (1.0)
        // at rest, or it stops reading as a restrained squint.
        assertTrue(pose.upperLidClosureDeltaR < 0.5f)
    }

    @Test fun `UNCERTAIN glance is toward the raised-brow side and upward, and is zero for every other owner`() {
        val uncertainPose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.UNCERTAIN, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertTrue("glance X should bias toward the raised (left) brow side", uncertainPose.gazeOffsetX < 0f)
        assertTrue("glance Y should bias upward", uncertainPose.gazeOffsetY < 0f)

        val pleasedPose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.PLEASED, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(0f, pleasedPose.gazeOffsetX, 0.0001f)
        assertEquals(0f, pleasedPose.gazeOffsetY, 0.0001f)

        val attentivePose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.ATTENTIVE, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(0f, attentivePose.gazeOffsetX, 0.0001f)
        assertEquals(0f, attentivePose.gazeOffsetY, 0.0001f)
    }

    @Test fun `UNCERTAIN closed-mouth corners are asymmetric -- left primary, right secondary and smaller`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.NONE, browProgress = 0f,
            mouthOwner = ScoutExpressionLayer.UNCERTAIN, closedMouthIntensity = 1f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertEquals(10f, pose.mouthCornerL, 0.0001f)
        assertEquals(2f, pose.mouthCornerR, 0.0001f)
        assertTrue(pose.mouthCornerL > pose.mouthCornerR)
    }

    @Test fun `UNCERTAIN open-mouth bias is left-only, independent of brow or closed-mouth state`() {
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.NONE, browProgress = 0f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 1f
        )
        assertEquals(4f, pose.mouthOpenBiasL, 0.0001f)
        assertEquals(0f, pose.mouthOpenBiasR, 0.0001f)
    }

    // --- forOwner(): browOwner and mouthOwner are independent inputs ---

    @Test fun `brow and mouth owners may legitimately disagree -- each field family follows its own input`() {
        // Mirrors the real mid-speech case: the brow pulse may still be
        // PLEASED while mouthOwner is correctly NONE (isSpeaking suppresses
        // it) and the closed-mouth corner must stay at zero regardless.
        val pose = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.PLEASED, browProgress = 1f,
            mouthOwner = ScoutExpressionLayer.NONE, closedMouthIntensity = 0f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0f
        )
        assertTrue(pose.browLiftL > 0f)
        assertEquals(0f, pose.mouthCornerL, 0.0001f)
    }

    // --- forOwner(): determinism ---

    @Test fun `identical inputs always produce an equal pose`() {
        val a = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.UNCERTAIN, browProgress = 0.42f,
            mouthOwner = ScoutExpressionLayer.UNCERTAIN, closedMouthIntensity = 0.3f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0.9f
        )
        val b = ScoutExpressionPose.forOwner(
            browOwner = ScoutExpressionLayer.UNCERTAIN, browProgress = 0.42f,
            mouthOwner = ScoutExpressionLayer.UNCERTAIN, closedMouthIntensity = 0.3f,
            pleasedSpeakingIntensity = 0f, uncertainSpeakingIntensity = 0.9f
        )
        assertEquals(a, b)
    }

    // --- eyeClosure(): blink is authoritative ---

    @Test fun `at full blink, expression closure delta is completely overridden regardless of sign or magnitude`() {
        val opening = ScoutExpressionPose.eyeClosure(blinkAmount = 1f, lidDroop = 0.07f, closureDelta = -0.5f)
        val narrowing = ScoutExpressionPose.eyeClosure(blinkAmount = 1f, lidDroop = 0.07f, closureDelta = 0.5f)
        val neutral = ScoutExpressionPose.eyeClosure(blinkAmount = 1f, lidDroop = 0.07f, closureDelta = 0f)
        assertEquals(neutral, opening, 0.0001f)
        assertEquals(neutral, narrowing, 0.0001f)
        // Full blink must still visibly close normally -- clamped to 1.
        assertEquals(1f, neutral, 0.0001f)
    }

    @Test fun `at no blink, the expression delta applies at full strength`() {
        val opened = ScoutExpressionPose.eyeClosure(blinkAmount = 0f, lidDroop = 0.07f, closureDelta = -0.09f)
        assertEquals((0.07f - 0.09f).coerceIn(0f, 1f), opened, 0.0001f)

        val narrowed = ScoutExpressionPose.eyeClosure(blinkAmount = 0f, lidDroop = 0.07f, closureDelta = 0.07f)
        assertEquals(0.07f + 0.07f, narrowed, 0.0001f)
    }

    @Test fun `mid-blink, the expression delta is proportionally damped, not all-or-nothing`() {
        val halfBlink = ScoutExpressionPose.eyeClosure(blinkAmount = 0.5f, lidDroop = 0f, closureDelta = 0.2f)
        // expressionNet = 0.2 * (1 - 0.5) = 0.1; b = 0.5 + 0 + 0.1 = 0.6
        assertEquals(0.6f, halfBlink, 0.0001f)
    }

    @Test fun `eyeClosure is called independently per eye -- one eye's blink never affects the other's result`() {
        // Left eye mid-blink with its own narrowing delta; right eye not
        // blinking at all with zero delta -- confirms the function itself
        // has no shared/global state that could leak between the two calls
        // ScoutFaceView makes per frame (one per eye).
        val leftDuringBlink = ScoutExpressionPose.eyeClosure(blinkAmount = 1f, lidDroop = 0.07f, closureDelta = 0.07f)
        val rightNotBlinking = ScoutExpressionPose.eyeClosure(blinkAmount = 0f, lidDroop = 0.07f, closureDelta = 0f)
        assertEquals(1f, leftDuringBlink, 0.0001f)
        assertEquals(0.07f, rightNotBlinking, 0.0001f)
    }

    @Test fun `eyeClosure matches the pre-existing formula exactly when closureDelta is zero -- regression safe`() {
        // b = (blinkAmount + lidDroop - 0).coerceIn(0f,1f), the exact
        // pre-v3 formula, for every closureDelta = 0f case.
        assertEquals((0.3f + 0.07f).coerceIn(0f, 1f), ScoutExpressionPose.eyeClosure(0.3f, 0.07f, 0f), 0.0001f)
        assertEquals(1f, ScoutExpressionPose.eyeClosure(1f, 0.5f, 0f), 0.0001f)
    }

    // --- speakingMouthIntensityTarget(): dispatch-safe transition ---

    @Test fun `armed but not yet speaking -- target is zero (pre-dispatch delay window)`() {
        assertEquals(0f, ScoutExpressionPose.speakingMouthIntensityTarget(armed = true, isSpeaking = false), 0.0001f)
    }

    @Test fun `armed and speaking -- target rises to full regardless of how long armed beforehand`() {
        assertEquals(1f, ScoutExpressionPose.speakingMouthIntensityTarget(armed = true, isSpeaking = true), 0.0001f)
    }

    @Test fun `speaking but never armed -- target stays zero, this is not a general speaking-mouth effect`() {
        assertEquals(0f, ScoutExpressionPose.speakingMouthIntensityTarget(armed = false, isSpeaking = true), 0.0001f)
    }

    @Test fun `neither armed nor speaking -- target is zero`() {
        assertEquals(0f, ScoutExpressionPose.speakingMouthIntensityTarget(armed = false, isSpeaking = false), 0.0001f)
    }

    @Test fun `target falls back to zero the instant speaking ends, even while still armed`() {
        // Mirrors the exact frame vSpeaking flips false -- armed may not
        // have been cleared by the release logic yet, but the target must
        // already be zero so the intensity begins decaying immediately.
        assertEquals(0f, ScoutExpressionPose.speakingMouthIntensityTarget(armed = true, isSpeaking = false), 0.0001f)
    }
}
