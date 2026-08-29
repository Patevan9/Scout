package com.example.scoutface.brain

/**
 * Expression System v3 -- one coordinated whole-face pose per expression,
 * computed once per frame in ScoutFaceView.updateLife() and consumed by
 * drawBrow()/drawEye()/the gaze composition/drawMouth(), replacing the
 * scattered per-function owner-switches Expression Visibility v1/v2 left
 * behind (each of those functions independently re-deriving the same
 * owner-gated amounts). Deliberately covers only ATTENTIVE/PLEASED/
 * UNCERTAIN -- NOTICE's own pulse and THINKING's own shape terms
 * (thinkTilt/thinkInward/thinkInnerLift/thinkGazeX/thinkLidSmooth) are
 * separate, pre-existing mechanisms, untouched by this class.
 *
 * Every field is a *target amount*, already scaled by whatever 0..1
 * progress the caller is driving (ScoutFaceView owns every rise/hold/decay
 * clock -- this class reads wall-clock time from nothing). Two mouth
 * concepts are deliberately independent inputs, not one shared "mouth
 * progress":
 *  - mouthCornerL/R (closed-mouth corner shape) is driven by the existing,
 *    unmodified pleasedMouthIntensity/uncertainMouthIntensity -- the
 *    "round 2" deferred-release state that already correctly waits until
 *    speaking has genuinely finished.
 *  - mouthOpenBiasL/R (open/speaking-mouth outline bias) is driven by a
 *    NEW, separately-sustained intensity that starts the moment real
 *    speech begins (see ScoutFaceView's pleasedSpeakingMouthIntensity/
 *    uncertainSpeakingMouthIntensity) -- never gated on brow ownership,
 *    which may have already decayed by the time a delayed TTS dispatch
 *    actually starts talking. This is the fix for the exact bug the
 *    existing deferred-mouth mechanism already exists to prevent.
 */
data class ExpressionPose(
    val browLiftL: Float = 0f,
    val browLiftR: Float = 0f,
    val browArchOuterL: Float = 0f,
    val browArchOuterR: Float = 0f,
    val browArchMid: Float = 0f,
    // Signed closure delta, not an "open boost" -- positive = MORE closure
    // (narrowing), negative = MORE opening. See ScoutExpressionPose.eyeClosure()
    // for exactly how this composes with blinking (blink is authoritative).
    val upperLidClosureDeltaL: Float = 0f,
    val upperLidClosureDeltaR: Float = 0f,
    val lowerLidL: Float = 0f,
    val lowerLidR: Float = 0f,
    // UNCERTAIN's brief glance overlay. Consumed at DRAW time, directly in
    // drawEye()'s iris-position formula -- never fed into updateLife()'s
    // gaze target/spring computation, which is forced to 0 while vSpeaking
    // and would silently discard anything added upstream of it.
    val gazeOffsetX: Float = 0f,
    val gazeOffsetY: Float = 0f,
    // ATTENTIVE only. 0..1 -- damps ONLY the ambient idle-drift/micro-tremor
    // *targets*, never vLookTargetX/Y or the tracking spring's own gains.
    val gazeDamping: Float = 0f,
    val mouthCornerL: Float = 0f,
    val mouthCornerR: Float = 0f,
    val mouthOpenBiasL: Float = 0f,
    val mouthOpenBiasR: Float = 0f
) {
    companion object {
        val NONE = ExpressionPose()
    }
}

object ScoutExpressionPose {

    // Amplitudes marked "unchanged from v2" carry forward the values that
    // already read correctly on-device for the channels v2 already used
    // (brow lift/arch, existing lower lid, existing closed-mouth corners).
    // Amplitudes marked NEW are this pass's best starting point for a
    // genuinely new channel -- per the approved design, amplitude here is
    // secondary to coordinated movement and is expected to be judged (and
    // adjusted if needed) on the Fold 7, not treated as final by this pass.

    // ATTENTIVE
    private const val ATTENTIVE_BROW_LIFT_PX = 20f          // unchanged from v2
    private const val ATTENTIVE_OUTER_ARCH_PX = 6f           // unchanged from v2
    private const val ATTENTIVE_EYE_OPEN_MAX = 0.09f         // unchanged from v2 (now a negative closure delta)
    private const val ATTENTIVE_GAZE_DAMPING_MAX = 0.6f      // NEW

    // PLEASED
    private const val PLEASED_BROW_LIFT_PX = 22f             // unchanged from v2
    private const val PLEASED_ARCH_PX = 10f                  // unchanged from v2
    private const val PLEASED_LOWER_LID_PX = 5f               // unchanged from v2
    private const val PLEASED_UPPER_LID_NARROW = 0.06f        // NEW -- eye-smile crinkle, paired with the lower lid
    private const val PLEASED_MOUTH_CORNER_PX = 12f           // unchanged from v2 (closed mouth)
    private const val PLEASED_MOUTH_OPEN_BIAS_PX = 5f         // NEW (open/speaking mouth outline bias)

    // UNCERTAIN
    private const val UNCERTAIN_BROW_PRIMARY_PX = 18f          // unchanged from v2 (left brow only)
    private const val UNCERTAIN_OUTER_TILT_PX = 8f             // unchanged from v2 (left brow only)
    private const val UNCERTAIN_LID_NARROW_PX = 5f              // unchanged from v2 (right lower lid only)
    private const val UNCERTAIN_UPPER_LID_NARROW = 0.07f        // NEW -- right eye only, paired with the lower lid;
                                                                 // deliberately well under blink-level closure so
                                                                 // this can never read as a wink (see eyeClosure()).
    private const val UNCERTAIN_GLANCE_X = -12f                 // NEW -- toward the raised (left) brow's side
    private const val UNCERTAIN_GLANCE_Y = -14f                 // NEW -- negative = up, matching thinkGazeTargetY's
                                                                 // existing sign convention
    private const val UNCERTAIN_MOUTH_PRIMARY_PX = 10f          // unchanged from v2 (closed mouth, left)
    private const val UNCERTAIN_MOUTH_SECONDARY_PX = 2f          // unchanged from v2 (closed mouth, right)
    private const val UNCERTAIN_MOUTH_OPEN_BIAS_PX = 4f          // NEW (open/speaking mouth, left only)

    /**
     * The single pure resolver. [browOwner]/[browProgress] drive brow, lid,
     * and gaze fields -- exactly the pre-existing browExpressionOwner and a
     * 0..1 normalized version of whichever pulse/sustained value already
     * backs it (attentiveSmooth for ATTENTIVE, pleasedPulse/
     * PLEASED_BROW_LIFT_PX for PLEASED, uncertainPulse/
     * UNCERTAIN_BROW_PRIMARY_PX for UNCERTAIN -- unchanged inputs, just
     * consolidated into one call instead of four scattered ones).
     *
     * [mouthOwner]/[closedMouthIntensity] drive the closed-mouth corner
     * fields -- the pre-existing mouthExpressionOwner and whichever of
     * pleasedMouthIntensity/uncertainMouthIntensity applies to it.
     *
     * [pleasedSpeakingIntensity]/[uncertainSpeakingIntensity] drive the
     * open-mouth bias fields independently of both owners above -- see the
     * class doc comment for why that independence is deliberate.
     *
     * browOwner and mouthOwner are NOT required to agree (and routinely
     * won't -- e.g. mid-speech, browOwner may still be PLEASED while
     * mouthOwner is correctly NONE and the open-mouth bias is what's
     * active instead). Each family of fields is driven by its own,
     * independently-correct inputs.
     */
    fun forOwner(
        browOwner: ScoutExpressionLayer,
        browProgress: Float,
        mouthOwner: ScoutExpressionLayer,
        closedMouthIntensity: Float,
        pleasedSpeakingIntensity: Float,
        uncertainSpeakingIntensity: Float
    ): ExpressionPose {
        val p = browProgress.coerceIn(0f, 1f)

        val browPose = when (browOwner) {
            ScoutExpressionLayer.ATTENTIVE -> ExpressionPose(
                browLiftL = p * ATTENTIVE_BROW_LIFT_PX,
                browLiftR = p * ATTENTIVE_BROW_LIFT_PX,
                browArchOuterL = p * ATTENTIVE_OUTER_ARCH_PX,
                browArchOuterR = p * ATTENTIVE_OUTER_ARCH_PX,
                upperLidClosureDeltaL = -(p * ATTENTIVE_EYE_OPEN_MAX),
                upperLidClosureDeltaR = -(p * ATTENTIVE_EYE_OPEN_MAX),
                gazeDamping = p * ATTENTIVE_GAZE_DAMPING_MAX
            )
            ScoutExpressionLayer.PLEASED -> ExpressionPose(
                browLiftL = p * PLEASED_BROW_LIFT_PX,
                browLiftR = p * PLEASED_BROW_LIFT_PX,
                browArchMid = p * PLEASED_ARCH_PX,
                upperLidClosureDeltaL = p * PLEASED_UPPER_LID_NARROW,
                upperLidClosureDeltaR = p * PLEASED_UPPER_LID_NARROW,
                lowerLidL = p * PLEASED_LOWER_LID_PX,
                lowerLidR = p * PLEASED_LOWER_LID_PX
            )
            ScoutExpressionLayer.UNCERTAIN -> ExpressionPose(
                // Asymmetric by design, same as v2: only the left brow lifts.
                browLiftL = p * UNCERTAIN_BROW_PRIMARY_PX,
                browArchOuterL = p * UNCERTAIN_OUTER_TILT_PX,
                // Only the RIGHT eye narrows -- the opposite side from the
                // raised brow. Left eye's delta stays exactly 0f (neutral,
                // never an opposite-signed value) so this cannot read as a
                // wink -- see eyeClosure()'s own doc comment.
                upperLidClosureDeltaR = p * UNCERTAIN_UPPER_LID_NARROW,
                lowerLidR = p * UNCERTAIN_LID_NARROW_PX,
                gazeOffsetX = p * UNCERTAIN_GLANCE_X,
                gazeOffsetY = p * UNCERTAIN_GLANCE_Y
            )
            else -> ExpressionPose.NONE
        }

        val cm = closedMouthIntensity.coerceIn(0f, 1f)
        val cornerL: Float
        val cornerR: Float
        when (mouthOwner) {
            ScoutExpressionLayer.PLEASED -> {
                cornerL = cm * PLEASED_MOUTH_CORNER_PX
                cornerR = cm * PLEASED_MOUTH_CORNER_PX
            }
            ScoutExpressionLayer.UNCERTAIN -> {
                cornerL = cm * UNCERTAIN_MOUTH_PRIMARY_PX
                cornerR = cm * UNCERTAIN_MOUTH_SECONDARY_PX
            }
            else -> {
                cornerL = 0f
                cornerR = 0f
            }
        }

        val ps = pleasedSpeakingIntensity.coerceIn(0f, 1f)
        val us = uncertainSpeakingIntensity.coerceIn(0f, 1f)
        // Additive: in practice at most one of ps/us is ever meaningfully
        // nonzero (pleasedBeat()/uncertainBeat() come from disjoint
        // MainActivity call sites), and even in a theoretical overlap this
        // just blends two already-small biases rather than misbehaving.
        val openBiasL = ps * PLEASED_MOUTH_OPEN_BIAS_PX + us * UNCERTAIN_MOUTH_OPEN_BIAS_PX
        val openBiasR = ps * PLEASED_MOUTH_OPEN_BIAS_PX   // UNCERTAIN biases the left corner only

        return browPose.copy(
            mouthCornerL = cornerL,
            mouthCornerR = cornerR,
            mouthOpenBiasL = openBiasL,
            mouthOpenBiasR = openBiasR
        )
    }

    /**
     * Per-eye upper-lid closure, composing blink and expression with blink
     * always authoritative. [blinkAmount]/[lidDroop] must be that SAME
     * eye's own values (ScoutFaceView.drawEye() is already called once per
     * eye with its own blinkL/blinkR -- this function must always be
     * called once per eye too, never with a shared/combined value).
     *
     * At blinkAmount = 1 (full closure), the expression contribution is
     * forced to exactly zero regardless of [closureDelta]'s sign or
     * magnitude -- a full blink always reaches full closure on that eye,
     * whether ATTENTIVE, PLEASED, or UNCERTAIN is active. At
     * blinkAmount = 0, the expression delta applies at full strength.
     * Blink's own generation/timing (ScoutFaceView's blinkL/blinkR/
     * blinkLagPhase) is entirely outside this function -- it only ever
     * reads those two already-computed values, never influences them, so
     * normal blink timing (both eyes together, only the existing tiny
     * organic lead/lag) is unaffected by anything expression-related.
     */
    fun eyeClosure(blinkAmount: Float, lidDroop: Float, closureDelta: Float): Float {
        val blinkDominance = blinkAmount.coerceIn(0f, 1f)
        val expressionNet = closureDelta * (1f - blinkDominance)
        return (blinkAmount + lidDroop + expressionNet).coerceIn(0f, 1f)
    }

    /**
     * Dispatch-safe speaking-mouth intensity target. [armed] is the
     * existing pleasedMouthArmed/uncertainMouthArmed flag (set true at
     * pleasedBeat()/uncertainBeat() call time, dispatch-scoped, unaffected
     * by this function) -- [isSpeaking] is the current, real vSpeaking.
     * Returns 1f the instant both are true (real speech has genuinely
     * started while still armed, regardless of how long the pre-dispatch
     * delay before that was), 0f otherwise -- including before speech has
     * started (still armed, not yet speaking) and after it ends (no longer
     * speaking). Never reads or depends on browExpressionOwner or any
     * brow-pulse magnitude.
     */
    fun speakingMouthIntensityTarget(armed: Boolean, isSpeaking: Boolean): Float =
        if (armed && isSpeaking) 1f else 0f
}
