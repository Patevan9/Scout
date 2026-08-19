package com.example.scoutface.brain

/**
 * Return-greeting personalization -- the one, narrow policy decision of
 * whether an already-resolved face-recognition match is confident enough to
 * be SPOKEN as a person's name, versus MainActivity falling back to the
 * existing generic return greeting.
 *
 * Deliberately owns nothing about how the match was produced: no PeopleDb
 * query, no TruthDb, no HabitLayer, no Android/embedding knowledge at all.
 * PeopleDb.findBestMatchNameWithScore() already owns the actual embedding
 * match plus its own base threshold/margin-vs-second-best-candidate
 * protection (see that function's own doc comment) -- this object never
 * re-implements or weakens either of those. It only adds one further,
 * stricter bar on top: a proactive SPOKEN identity claim ("Hey Patrick,
 * good to see you again.") is a materially stronger commitment than a
 * silent internal match, so it requires MainActivity's existing
 * CONFIDENT_EMBED_THRESHOLD (0.72, already used to gate whether a match is
 * confident enough to be added to a person's stored profile) rather than
 * PeopleDb's own looser default acceptance threshold (0.65).
 *
 * Real-device finding (Fold 7): MainActivity.maybeMakeReturnGreeting()
 * previously never considered identity at all -- confirmed via investigation
 * that it's a real missing integration, not a deliberate safety choice (the
 * one existing personalized greeting, the first-contact "I see $name."
 * block, is explicitly scoped to fire once per app launch only, never on a
 * return). The fix must call PeopleDb fresh from the CURRENT frame's
 * embedding at speak-time -- MainActivity's own lastKnownFaceName field is
 * NOT safe to reuse here: it's only cleared to null when no face is
 * detected at all, not when a face IS detected but fails to match
 * confidently that specific frame, so it can silently lag a stale name
 * forward. This object plays no part in that fix beyond the threshold
 * decision below -- the fresh-lookup fix itself lives in MainActivity.
 */
object ScoutGreetingIdentity {

    /**
     * matchedName/matchScore -- the exact pair PeopleDb.findBestMatchNameWithScore()
     * already returned for the current frame's embedding (or null/null if there
     * was no embedding, or PeopleDb found no match, or its own margin check
     * rejected a too-close call). confidenceThreshold -- the stricter bar the
     * caller applies for a spoken claim (CONFIDENT_EMBED_THRESHOLD).
     *
     * Returns the name only when it's non-blank, a score is present, and that
     * score clears confidenceThreshold -- null in every other case, meaning
     * "the caller must use the generic greeting instead."
     */
    fun resolveSpeakableName(matchedName: String?, matchScore: Float?, confidenceThreshold: Float): String? {
        if (matchedName.isNullOrBlank() || matchScore == null) return null
        return if (matchScore >= confidenceThreshold) matchedName else null
    }
}
