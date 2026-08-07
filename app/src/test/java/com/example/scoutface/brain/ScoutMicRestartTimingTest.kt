package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers ScoutMicRestartTiming.computeRestartDelayMs() -- the pure
 * replacement for the flat 150ms poll that used to reschedule
 * maybeStartListening()'s three post-TTS cooldown branches. Uses the same
 * real threshold values MainActivity actually configures today
 * (BOOT_LISTEN_EXTRA_DELAY_MS=250, TTS_LOCKOUT_MS=600,
 * MIC_RESUME_COOLDOWN_MS=650) as fixtures, to also serve as a lock on those
 * thresholds staying exactly what they were -- this change targets them
 * precisely, it doesn't touch what they are.
 */
class ScoutMicRestartTimingTest {

    // Real MainActivity constants, mirrored here as fixtures.
    private val bootDelayMs = 250L
    private val ttsLockoutMs = 600L
    private val micResumeCooldownMs = 650L

    // --- At the exact moment TTS completion is recorded (now == T0) ---

    @Test fun `at TTS completion, delay targets the largest deadline -- 650ms, the mic-resume cooldown`() {
        val t0 = 1_000_000L
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = t0,
            bootListenDeadlineMs = t0 + bootDelayMs,
            ttsLockoutDeadlineMs = t0 + ttsLockoutMs,
            micResumeDeadlineMs = t0 + micResumeCooldownMs
        )
        assertEquals(650L, delay)
    }

    @Test fun `computed delay does not overshoot the required deadline`() {
        val t0 = 500_000L
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = t0,
            bootListenDeadlineMs = t0 + bootDelayMs,
            ttsLockoutDeadlineMs = t0 + ttsLockoutMs,
            micResumeDeadlineMs = t0 + micResumeCooldownMs
        )
        // The whole point: now + delay lands exactly on the latest deadline,
        // not past it (the old flat-150ms poll could overshoot by up to 150ms).
        assertEquals(t0 + micResumeCooldownMs, t0 + delay)
    }

    // --- Partway through the cooldown period ---

    @Test fun `400ms after TTS completion, the mic-resume deadline is the binding one`() {
        val t0 = 1_000_000L
        val now = t0 + 400L
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = now,
            bootListenDeadlineMs = t0 + bootDelayMs,   // 250ms -- already passed
            ttsLockoutDeadlineMs = t0 + ttsLockoutMs,   // 600ms -- still 200ms out
            micResumeDeadlineMs = t0 + micResumeCooldownMs // 650ms -- still 250ms out, the latest of the three
        )
        assertEquals(250L, delay) // 650 - 400
    }

    @Test fun `599ms after TTS completion, TTS lockout has not quite cleared yet`() {
        val t0 = 2_000_000L
        val now = t0 + 599L
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = now,
            bootListenDeadlineMs = t0 + bootDelayMs,
            ttsLockoutDeadlineMs = t0 + ttsLockoutMs,
            micResumeDeadlineMs = t0 + micResumeCooldownMs
        )
        assertEquals(51L, delay) // 650 - 599
    }

    // --- All deadlines already passed ---

    @Test fun `once every deadline has passed, delay is zero, never negative`() {
        val t0 = 1_000_000L
        val now = t0 + 10_000L // long after all three
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = now,
            bootListenDeadlineMs = t0 + bootDelayMs,
            ttsLockoutDeadlineMs = t0 + ttsLockoutMs,
            micResumeDeadlineMs = t0 + micResumeCooldownMs
        )
        assertEquals(0L, delay)
    }

    // --- Deadlines don't have to be in their usual relative order ---

    @Test fun `whichever deadline is latest wins, regardless of which parameter it came from`() {
        val now = 0L
        // Deliberately unusual ordering -- ttsLockout given as the largest here.
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = now,
            bootListenDeadlineMs = 100L,
            ttsLockoutDeadlineMs = 900L,
            micResumeDeadlineMs = 300L
        )
        assertEquals(900L, delay)
    }

    @Test fun `a single still-active deadline among two already-passed ones is targeted exactly`() {
        val now = 1_000L
        val delay = ScoutMicRestartTiming.computeRestartDelayMs(
            now = now,
            bootListenDeadlineMs = 500L,   // passed
            ttsLockoutDeadlineMs = 800L,   // passed
            micResumeDeadlineMs = 1_200L   // still 200ms out
        )
        assertEquals(200L, delay)
    }
}
