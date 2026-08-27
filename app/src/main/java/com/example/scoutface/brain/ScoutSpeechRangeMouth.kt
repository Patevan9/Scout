package com.example.scoutface.brain

/**
 * Speaking Mouth v1 -- pure, testable math for turning TextToSpeech's
 * `onRangeStart(utteranceId, start, end, frame)` callbacks into a bounded,
 * decaying mouth-openness impulse. Kept out of `ScoutFaceView` (an Android
 * `View`, not unit-testable without instrumentation) for the same reason
 * `ScoutExpressionPriority` is kept out of it -- this object owns none of
 * `ScoutFaceView`'s per-frame decay/smoothing state, only the two small
 * decisions that actually needed to be correct and testable: how large an
 * impulse a given range should produce, and whether the range-timed path
 * should currently own mouth rendering at all.
 *
 * Deliberately NOT audio amplitude -- `onRangeStart()` provides none, only
 * the character span `[start, end)` of the text currently being spoken. The
 * only input this object accepts is that span's length, used purely as a
 * simple, deterministic timing/size heuristic (a longer range gets a
 * slightly larger impulse) -- never represented as, or intended to
 * approximate, real audio level. The goal is natural word-paced cadence,
 * not phoneme-accurate lip sync.
 *
 * No dispatch-id/ownership logic lives here -- that's a separate concern,
 * already solved by the existing `ScoutSpeechDispatchGuard.
 * ownsGlobalSpeakingState()` at the `MainActivity` call site (see
 * `onRangeStart()`'s own doc comment there). By the time a range length
 * reaches this object, the caller has already confirmed it belongs to the
 * dispatch that currently owns Scout's global speaking state.
 */
object ScoutSpeechRangeMouth {

    /**
     * Maps a spoken range's character count to a bounded impulse magnitude
     * between [minMagnitude] and [maxMagnitude] (inclusive), linearly scaled
     * by [rangeChars] against [charsNormalizer] (the character count at
     * which the mapping saturates to [maxMagnitude]). Always within
     * `[minMagnitude, maxMagnitude]` for any input, including zero, negative,
     * or unexpectedly large [rangeChars] -- a defensively-clamped engine
     * quirk is never allowed to produce an out-of-range or negative impulse.
     */
    fun impulseMagnitude(
        rangeChars: Int,
        minMagnitude: Float,
        maxMagnitude: Float,
        charsNormalizer: Float
    ): Float {
        val normalized = (rangeChars.toFloat() / charsNormalizer).coerceIn(0f, 1f)
        return minMagnitude + normalized * (maxMagnitude - minMagnitude)
    }

    /**
     * Whether the range-timed path should currently own mouth rendering
     * rather than the existing synthetic fallback. True only once at least
     * one real range event has been seen for the CURRENT speaking dispatch
     * ([lastEventAtMs] != 0L -- callers reset this to 0L at the start of
     * every new speaking dispatch, see `ScoutFaceView.setSpeaking()`) and
     * that event happened recently enough ([nowMs] - [lastEventAtMs] is
     * still under [activeWindowMs]) to still be trusted. A gap at or beyond
     * [activeWindowMs] -- the engine stalled mid-utterance, or genuinely
     * never calls `onRangeStart()` at all for this dispatch -- falls back to
     * the synthetic animation gracefully rather than freezing the mouth or
     * fighting the fallback; a later event within the same dispatch simply
     * flips this back to true.
     */
    fun isRangeDriven(lastEventAtMs: Long, nowMs: Long, activeWindowMs: Long): Boolean =
        lastEventAtMs != 0L && (nowMs - lastEventAtMs) < activeWindowMs
}
