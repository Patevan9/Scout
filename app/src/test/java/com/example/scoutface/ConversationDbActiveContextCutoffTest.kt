package com.example.scoutface

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers ConversationDb.activeContextCutoff() -- the pure boundary
 * calculation behind getLastTurns()'s previous-session/long-idle leak fix.
 * ConversationDb itself has no existing test coverage (SQLiteOpenHelper
 * needs a real Android Context, same reasoning as DiagLogLlamaBenchmarkFormatTest
 * and DiagLogSelfEchoFormatTest), so this only exercises the piece that
 * doesn't need one: given a row's created_at, the current session's start
 * time, and "now," would that row still count as active context?
 *
 * A row passes the cutoff (and so would be returned by getLastTurns()) when
 * its created_at is at or after activeContextCutoff(...). These tests model
 * the three scenarios the fix is meant to cover directly, without touching
 * SQLite at all.
 */
class ConversationDbActiveContextCutoffTest {

    private val maxAgeMs = ConversationDb.MAX_ACTIVE_CONTEXT_AGE_MS

    @Test
    fun previousSessionRow_isExcluded() {
        // Previous session ran and ended well before this one started; the
        // row is recent by the wall-clock (well under maxAgeMs old), but it
        // predates sessionStartedAtMs, so it must not count as active.
        val sessionStartedAtMs = 1_000_000L
        val now = sessionStartedAtMs + 5_000L
        val rowCreatedAt = sessionStartedAtMs - 60_000L

        val cutoff = ConversationDb.activeContextCutoff(sessionStartedAtMs, now, maxAgeMs)

        assertTrue("previous-session row should be excluded", rowCreatedAt < cutoff)
    }

    @Test
    fun currentSessionRecentRow_isIncluded() {
        // Logged a moment ago, well within the current session and well
        // under the max-age window -- must still count as active context.
        val sessionStartedAtMs = 1_000_000L
        val now = sessionStartedAtMs + 60_000L
        val rowCreatedAt = now - 2_000L

        val cutoff = ConversationDb.activeContextCutoff(sessionStartedAtMs, now, maxAgeMs)

        assertTrue("recent current-session row should be included", rowCreatedAt >= cutoff)
    }

    @Test
    fun currentSessionRowOlderThanMaxAge_isExcluded() {
        // Same running session (row is after sessionStartedAtMs), but logged
        // long enough ago -- more than MAX_ACTIVE_CONTEXT_AGE_MS before "now"
        // -- that it must no longer count as active context.
        val sessionStartedAtMs = 1_000_000L
        val rowCreatedAt = sessionStartedAtMs + 60_000L
        val now = rowCreatedAt + maxAgeMs + 1_000L

        val cutoff = ConversationDb.activeContextCutoff(sessionStartedAtMs, now, maxAgeMs)

        assertTrue("row is still within the current session", rowCreatedAt > sessionStartedAtMs)
        assertTrue("row older than the max-age window should be excluded", rowCreatedAt < cutoff)
    }
}
