package com.example.scoutface.brain

/**
 * Face-sample storage quality gate (v1) -- answers exactly one question:
 * "Is THIS face image good enough to be STORED as a future recognition
 * sample?" It has no opinion on, and no knowledge of, whether the current
 * frame's face was successfully identified -- that's PeopleDb's job
 * (scoreByPerson()/findBestMatchNameWithScore()/findBestMatch()), runs
 * unconditionally before this gate is ever consulted, and is completely
 * unaffected by what this object returns. Rejecting a sample here only
 * means MainActivity skips storeEmbedding()/addNamedEmbedding() for it --
 * the current frame's own recognition result stands either way.
 *
 * Deliberately owns nothing beyond two cheap geometry checks: no Android
 * imports, no PeopleDb, no identity, no match confidence/margin logic, no
 * TruthDb, no HabitLayer, no enrollment awareness, no bitmap processing
 * (no blur/brightness -- see the design's explicit v1 scope). Both
 * primary- and secondary-face automatic storage decisions call this same
 * function with their own measurements -- one policy, not two.
 *
 * minFaceHeightFraction/maxAbsYawDegrees are caller-supplied, not
 * hardcoded here, so MainActivity's own constants (intentionally more
 * permissive than the unrelated direct-address thresholds --
 * LISTENING_REMINDER_MIN_FACE_HEIGHT_FRACTION/LISTENING_REMINDER_MAX_YAW_DEGREES --
 * which answer a stricter, different question: "is this person actively
 * addressing Scout right now") stay the single source of truth for the
 * actual numbers.
 */
object ScoutFaceSampleQuality {

    /**
     * faceHeightFraction -- the detected face's bounding-box height divided
     * by the upright frame height (same measurement shape MainActivity
     * already computes for its direct-address gate, just applied to a
     * different, looser threshold here).
     * yawDegrees -- the face's headEulerAngleY (ML Kit) for this frame.
     *
     * Returns true (boundary inclusive on both checks) only when the face
     * is not obviously too small/far away AND not obviously posed too far
     * sideways to be a useful future comparison sample.
     */
    fun isGoodForAutomaticStorage(
        faceHeightFraction: Float,
        yawDegrees: Float,
        minFaceHeightFraction: Float,
        maxAbsYawDegrees: Float
    ): Boolean {
        if (faceHeightFraction < minFaceHeightFraction) return false
        if (kotlin.math.abs(yawDegrees) > maxAbsYawDegrees) return false
        return true
    }
}
