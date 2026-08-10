package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutBeepMuteGuardTest {

    // --- Single, non-overlapping window (today's start-only behavior) ---

    @Test fun `a single mute window applies on begin and restores on end`() {
        val guard = ScoutBeepMuteGuard()
        assertTrue(guard.beginMute())
        assertEquals(1, guard.currentDepth())
        assertTrue(guard.endMute())
        assertEquals(0, guard.currentDepth())
    }

    // --- Overlapping windows (start-side + stop-side) ---

    @Test fun `a second overlapping begin does not re-apply the mute`() {
        val guard = ScoutBeepMuteGuard()
        assertTrue(guard.beginMute())   // outer window -- real mute happens
        assertFalse(guard.beginMute())  // inner/overlapping window -- already muted
        assertEquals(2, guard.currentDepth())
    }

    @Test fun `restore only happens on the last matching end, not the first`() {
        val guard = ScoutBeepMuteGuard()
        guard.beginMute()
        guard.beginMute()
        assertFalse(guard.endMute())  // one window still outstanding -- must not restore yet
        assertEquals(1, guard.currentDepth())
        assertTrue(guard.endMute())   // now the last one -- real restore happens
        assertEquals(0, guard.currentDepth())
    }

    @Test fun `end order does not matter, only the count does`() {
        // Whichever of the two overlapping callers happens to fire its
        // scheduled restore first, the real restore must only happen once,
        // on whichever call turns out to be the second (last) one.
        val guard = ScoutBeepMuteGuard()
        guard.beginMute()
        guard.beginMute()
        val firstEnd = guard.endMute()
        val secondEnd = guard.endMute()
        assertFalse(firstEnd)
        assertTrue(secondEnd)
    }

    // --- Guarding against getting stuck muted or restoring twice ---

    @Test fun `an extra end call beyond the matching begins is a harmless no-op`() {
        val guard = ScoutBeepMuteGuard()
        guard.beginMute()
        assertTrue(guard.endMute())   // legitimate close -- depth back to 0
        assertFalse(guard.endMute())  // stray extra call -- must not go negative
        assertEquals(0, guard.currentDepth())
    }

    @Test fun `depth never goes negative from unmatched end calls`() {
        val guard = ScoutBeepMuteGuard()
        assertFalse(guard.endMute())
        assertFalse(guard.endMute())
        assertEquals(0, guard.currentDepth())
    }

    @Test fun `a stray end call does not desync a later legitimate mute-restore pair`() {
        val guard = ScoutBeepMuteGuard()
        guard.endMute() // stray call before any begin, e.g. a defensive/duplicate call site
        assertTrue(guard.beginMute())
        assertTrue(guard.endMute())
    }

    // --- Forced reset (shutdown) ---

    @Test fun `forceReset reports true and clears depth when windows were outstanding`() {
        val guard = ScoutBeepMuteGuard()
        guard.beginMute()
        guard.beginMute()
        val hadOutstanding = guard.forceReset()
        assertTrue(hadOutstanding)
        assertEquals(0, guard.currentDepth())
    }

    @Test fun `forceReset reports false when nothing was outstanding`() {
        val guard = ScoutBeepMuteGuard()
        val hadOutstanding = guard.forceReset()
        assertFalse(hadOutstanding)
        assertEquals(0, guard.currentDepth())
    }

    @Test fun `a fresh mute-restore cycle after forceReset behaves normally`() {
        val guard = ScoutBeepMuteGuard()
        guard.beginMute()
        guard.forceReset()
        // A stray scheduled restore from the reset window firing late must
        // not disturb a genuinely new cycle that starts afterward.
        assertTrue(guard.beginMute())
        assertTrue(guard.endMute())
    }
}
