package com.example.scoutface.brain

/**
 * Reference-counts overlapping "mute the system beep" windows so two
 * independent callers -- the start side (maybeStartListening(), before
 * startListening()) and the stop side (stopListeningSafe(), before
 * cancel()) -- can each mute/restore around their own recognizer call
 * without stepping on each other.
 *
 * Before this existed, MainActivity tracked "are we currently muted" with a
 * pair of nullable Int fields guarded by a null-check -- fine when only one
 * caller ever muted, but unsafe once two independent call sites can each
 * open a mute window that may overlap in time: whichever restore fires
 * first would null out the saved volumes, silently defeating the *other*
 * still-outstanding window's own restore.
 *
 * This class tracks nothing about AudioManager or the saved volume values
 * themselves -- it only answers two questions a caller needs before doing
 * the real work: "is this the first (outermost) mute window opening, so I
 * should actually capture+zero the real volumes?" and "is this the last
 * (innermost) mute window closing, so I should actually write them back?"
 * beginMute()/endMute() must be called in matched pairs by the caller for
 * every real mute/restore attempt; this class doesn't touch any system API
 * itself.
 */
class ScoutBeepMuteGuard {

    private var depth = 0

    /**
     * Call before muting. Returns true exactly when this is the first of
     * possibly several overlapping mute windows -- the only time the real
     * pre-mute volumes should be captured and the streams actually zeroed.
     * A nested/overlapping call still increments (so its matching endMute()
     * is accounted for) but returns false, since the streams are already
     * muted by the outer call.
     */
    fun beginMute(): Boolean {
        depth++
        return depth == 1
    }

    /**
     * Call when a mute window closes. Returns true exactly when this was
     * the last outstanding window -- the only time the real volumes should
     * be written back. Floor-guarded: a stray extra call (more endMute()
     * calls than beginMute() calls) can never push depth negative, which
     * would otherwise desync a future, unrelated mute/restore pair.
     */
    fun endMute(): Boolean {
        if (depth <= 0) {
            depth = 0
            return false
        }
        depth--
        return depth == 0
    }

    /**
     * Unconditionally clears every outstanding mute window and reports
     * whether any were actually outstanding. For a guaranteed restore at
     * shutdown only -- a normal endMute() call can close out at most one
     * window per call, and any other window's own scheduled restore
     * callback may already have been purged (see MainActivity's
     * handler.removeCallbacksAndMessages(null) in shutdownSystems()), so
     * relying on endMute() alone at shutdown could leave depth above zero
     * with nothing left to bring it back down.
     */
    fun forceReset(): Boolean {
        val hadOutstanding = depth > 0
        depth = 0
        return hadOutstanding
    }

    /** Test/diagnostic visibility only -- not used for any real decision. */
    fun currentDepth(): Int = depth
}
