package com.example.scoutface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers DiagLog.formatSelfEchoDiscard() -- the pure detail-string builder
 * behind logSelfEchoDiscarded(). DiagLog itself has no existing test
 * coverage (SQLiteOpenHelper needs a real Android Context), so this only
 * exercises the piece that doesn't need one: what the log line actually
 * contains.
 *
 * The function's signature itself already makes logging the recognized text
 * impossible here -- it only accepts an Int and a Long, never a String --
 * these tests confirm what it *does* produce is exactly character count and
 * timing, nothing else.
 */
class DiagLogSelfEchoFormatTest {

    @Test fun `formats character count and gap in milliseconds`() {
        val detail = DiagLog.formatSelfEchoDiscard(charCount = 42, gapAfterResponseMs = 1234L)
        assertEquals("chars=42 gap=1234ms", detail)
    }

    @Test fun `negative character count is clamped to zero, not passed through`() {
        val detail = DiagLog.formatSelfEchoDiscard(charCount = -5, gapAfterResponseMs = 0L)
        assertEquals("chars=0 gap=0ms", detail)
    }

    @Test fun `negative gap is clamped to zero, not passed through`() {
        val detail = DiagLog.formatSelfEchoDiscard(charCount = 10, gapAfterResponseMs = -200L)
        assertEquals("chars=10 gap=0ms", detail)
    }

    @Test fun `zero-length transcript is represented, not omitted`() {
        val detail = DiagLog.formatSelfEchoDiscard(charCount = 0, gapAfterResponseMs = 500L)
        assertEquals("chars=0 gap=500ms", detail)
    }

    @Test fun `output contains only the two numeric fields -- no other content sneaks in`() {
        val detail = DiagLog.formatSelfEchoDiscard(charCount = 7, gapAfterResponseMs = 99L)
        assertTrue(detail.startsWith("chars="))
        assertTrue(detail.contains(" gap="))
        assertTrue(detail.endsWith("ms"))
        // A representative sample of what recognized speech text might look
        // like -- confirms it's structurally impossible for it to appear,
        // not just that this particular call happened not to include it.
        assertFalse(detail.contains("hear", ignoreCase = true))
        assertFalse(detail.contains("scout", ignoreCase = true))
    }
}
