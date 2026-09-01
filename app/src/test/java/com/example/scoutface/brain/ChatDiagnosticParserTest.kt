package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers ChatDiagnosticParser.parse() -- the pure parsing half of the Fold 7
 * Qwen chat-template diagnostic (see scout_llama_jni.cpp's
 * nativeGetLastChatDiagnosticsSummary()). Does not and cannot cover the
 * native capture itself (no NDK/device in this repo's test environment) --
 * that guarantee (the capture cannot alter what runGeneration() receives) is
 * established by construction: the capture blocks in nativeGenerateChat()
 * only ever copy FROM already-computed local values INTO a separate static
 * struct, never assign back into `prompt` or `messages`, and sit textually
 * before/after the unchanged runGeneration(sm, prompt, ...) call, not inside
 * it -- verifiable by direct diff review, not by a JVM test.
 */
class ChatDiagnosticParserTest {

    @Test
    fun parsesWellFormedValidLine() {
        val result = ChatDiagnosticParser.parse(
            "valid=1;n_messages=14;prompt_len=812;n_prompt_tokens=210;n_generated=87;stopped_eog=1"
        )
        assertEquals(
            ChatDiagnosticSummary(
                valid = true,
                nMessages = 14,
                promptLength = 812,
                nPromptTokens = 210,
                nGeneratedTokens = 87,
                stoppedByEog = true
            ),
            result
        )
    }

    @Test
    fun stoppedEogFalseWhenCapExhausted() {
        val result = ChatDiagnosticParser.parse(
            "valid=1;n_messages=14;prompt_len=812;n_prompt_tokens=210;n_generated=100;stopped_eog=0"
        )
        assertFalse(result!!.stoppedByEog)
        assertEquals(100, result.nGeneratedTokens)
    }

    @Test
    fun validFalseStillParsesRatherThanReturningNull() {
        // No production turn has completed yet (e.g. app just launched) --
        // this must be distinguishable from a genuinely malformed/unreadable
        // string, so it parses successfully with valid=false, not null.
        val result = ChatDiagnosticParser.parse(
            "valid=0;n_messages=0;prompt_len=0;n_prompt_tokens=0;n_generated=0;stopped_eog=0"
        )
        assertTrue(result != null)
        assertFalse(result!!.valid)
    }

    @Test
    fun missingFieldReturnsNull() {
        val result = ChatDiagnosticParser.parse("valid=1;n_messages=14;prompt_len=812")
        assertNull(result)
    }

    @Test
    fun nonNumericFieldReturnsNull() {
        val result = ChatDiagnosticParser.parse(
            "valid=1;n_messages=abc;prompt_len=812;n_prompt_tokens=210;n_generated=87;stopped_eog=1"
        )
        assertNull(result)
    }

    @Test
    fun emptyStringReturnsNull() {
        assertNull(ChatDiagnosticParser.parse(""))
    }
}
