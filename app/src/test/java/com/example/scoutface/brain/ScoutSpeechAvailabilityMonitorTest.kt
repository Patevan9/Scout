package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutSpeechAvailabilityMonitorTest {

    private val NETWORK = ScoutSpeechAvailabilityMonitor.ERROR_NETWORK
    private val NETWORK_TIMEOUT = ScoutSpeechAvailabilityMonitor.ERROR_NETWORK_TIMEOUT
    private val UNRELATED = 6 // SpeechRecognizer.ERROR_SPEECH_TIMEOUT -- just needs to not be 1 or 2

    // --- Unrelated error codes never count and never trigger ---

    @Test fun `unrelated error codes are never counted and never trigger`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        repeat(10) { i ->
            assertFalse(monitor.onRecognizerError(UNRELATED, nowMs = 1000L * i))
        }
        assertEquals(0, monitor.currentPatternSize())
    }

    // --- Three qualifying errors within the window trigger; fewer do not ---

    @Test fun `two qualifying errors do not trigger, a third within the window does`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 1_000L))
        assertFalse(monitor.onRecognizerError(NETWORK_TIMEOUT, nowMs = 2_000L))
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_000L))
        assertEquals(3, monitor.currentPatternSize())
    }

    // --- Old errors age out of the rolling window rather than accumulating forever ---

    @Test fun `errors older than the window are aged out and do not contribute to the pattern`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        val windowMs = ScoutSpeechAvailabilityMonitor.ERROR_WINDOW_MS

        // One error long before the window, then only two recent ones -- should
        // NOT trigger, since the old one no longer counts by the time the third
        // (chronologically) error lands.
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 0L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = windowMs + 10_000L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = windowMs + 20_000L))
        // Only the last two are still inside the window relative to the most
        // recent call -- the very first (at time 0) has aged out.
        assertEquals(2, monitor.currentPatternSize())
    }

    @Test fun `three errors spread just inside the window still trigger`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        val windowMs = ScoutSpeechAvailabilityMonitor.ERROR_WINDOW_MS
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 0L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = windowMs / 2))
        // Still within windowMs of the first error.
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = windowMs - 1L))
    }

    // --- Announce cooldown: only once per cooldown window ---

    @Test fun `does not re-announce again immediately after a successful warning`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 1_000L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 2_000L))
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_000L))
        monitor.onWarned(nowMs = 3_000L)

        // Even though a fresh pattern of 3 more qualifying errors forms shortly
        // after, the cooldown blocks a second announcement.
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 4_000L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 5_000L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 6_000L))
    }

    @Test fun `announces again once the cooldown has elapsed`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 1_000L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 2_000L))
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_000L))
        monitor.onWarned(nowMs = 3_000L)

        val afterCooldown = 3_000L + ScoutSpeechAvailabilityMonitor.ANNOUNCE_COOLDOWN_MS + 1_000L
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = afterCooldown))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = afterCooldown + 1_000L))
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = afterCooldown + 2_000L))
    }

    // --- Skipping onWarned() (e.g. caller was already speaking) leaves the
    // cooldown untouched, so the very next qualifying error can try again ---

    @Test fun `not calling onWarned leaves the cooldown untouched for the next attempt`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 1_000L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 2_000L))
        // Pattern crossed the threshold, but the caller (simulating "Scout was
        // already speaking") never calls onWarned().
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_000L))

        // The next qualifying error still reports "should warn" -- nothing was
        // consumed since onWarned() was never called.
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_500L))
    }

    // --- A mix of ERROR_NETWORK and ERROR_NETWORK_TIMEOUT both count toward the same pattern ---

    @Test fun `ERROR_NETWORK and ERROR_NETWORK_TIMEOUT both count toward the same pattern`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 1_000L))
        assertFalse(monitor.onRecognizerError(NETWORK_TIMEOUT, nowMs = 2_000L))
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_000L))
    }

    // --- Unrelated errors interleaved with qualifying ones do not interfere ---

    @Test fun `unrelated errors interleaved with qualifying ones are simply ignored`() {
        val monitor = ScoutSpeechAvailabilityMonitor()
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 1_000L))
        assertFalse(monitor.onRecognizerError(UNRELATED, nowMs = 1_500L))
        assertFalse(monitor.onRecognizerError(NETWORK, nowMs = 2_000L))
        assertFalse(monitor.onRecognizerError(UNRELATED, nowMs = 2_500L))
        assertTrue(monitor.onRecognizerError(NETWORK, nowMs = 3_000L))
        assertEquals(3, monitor.currentPatternSize())
    }
}
