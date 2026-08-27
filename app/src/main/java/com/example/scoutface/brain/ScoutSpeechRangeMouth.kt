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
     * Whether the range-timed path currently owns mouth rendering for the
     * active speaking dispatch, rather than the existing synthetic fallback.
     *
     * PR #80 review correction: this is now a STICKY, dispatch-scoped
     * decision with no time component at all -- once [everEstablishedThisDispatch]
     * is true (a real, correctly-owned `onRangeStart()` event has been seen
     * for the CURRENT speaking dispatch -- see `ScoutFaceView.speechRangePulse()`),
     * it stays true for the remainder of that same dispatch, no matter how
     * long a gap opens up between individual range events. A genuine spoken
     * pause must read as a pause -- the separately-decaying impulse (see
     * `ScoutFaceView.updateLife()`) settles toward 0 and closes the mouth --
     * never as a reason to silently fall back to the unrelated synthetic
     * animation mid-utterance, which would produce exactly the
     * real-mouth/synthetic-mouth flicker the synthetic fallback is NOT meant
     * to cause. The synthetic fallback exists only for a dispatch that never
     * produces a single usable range callback in the first place -- it is
     * compatibility protection, not a mid-utterance inactivity fallback.
     *
     * The previous version of this function additionally required the most
     * recent event to be within a fixed recency window, which incorrectly
     * let an ordinary pause between words fall back to the synthetic
     * animation and then flip back once speech resumed. That time-based
     * check has been removed entirely, not merely widened -- there is no
     * elapsed-time input this function could still be given that would let
     * time alone revert an established dispatch back to fallback; only a
     * genuine dispatch boundary can, by [everEstablishedThisDispatch] itself
     * being reset to false (see `ScoutFaceView.setSpeaking()`/`resetFace()`
     * for exactly where that reset happens: a new dispatch starting, or this
     * one ending via natural completion, engine error, user interruption, or
     * a full recovery reset).
     */
    fun isRangeDriven(everEstablishedThisDispatch: Boolean): Boolean = everEstablishedThisDispatch
}
