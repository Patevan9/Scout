package com.example.scoutface.brain

/**
 * Computes exactly how long maybeStartListening() should wait before its
 * next attempt, given the currently active post-TTS cooldown deadlines --
 * instead of the flat LISTEN_RESTART_DELAY_MS (150ms) poll it used to
 * reschedule at on every failed gate check, which could overshoot the true
 * binding deadline by up to a full poll interval.
 *
 * Pure arithmetic only. Does NOT change, lower, or otherwise touch any of
 * MainActivity's actual threshold values (TTS_LOCKOUT_MS,
 * MIC_RESUME_COOLDOWN_MS, BOOT_LISTEN_EXTRA_DELAY_MS) -- those are still
 * computed by the caller exactly as before and simply passed in here as
 * deadlines to target precisely.
 */
object ScoutMicRestartTiming {

    /**
     * [now] and every deadline parameter are epoch-millis
     * (System.currentTimeMillis()). Returns the number of milliseconds to
     * wait before the next restart attempt -- zero if every deadline has
     * already passed, never negative.
     *
     * Takes the latest (maximum) of the three deadlines, since
     * maybeStartListening() only actually proceeds once all three of its
     * gates are simultaneously satisfied -- waiting for anything less would
     * just mean an earlier, still-failing attempt.
     */
    fun computeRestartDelayMs(
        now: Long,
        bootListenDeadlineMs: Long,
        ttsLockoutDeadlineMs: Long,
        micResumeDeadlineMs: Long
    ): Long {
        val latestDeadline = maxOf(bootListenDeadlineMs, ttsLockoutDeadlineMs, micResumeDeadlineMs)
        return (latestDeadline - now).coerceAtLeast(0L)
    }
}
