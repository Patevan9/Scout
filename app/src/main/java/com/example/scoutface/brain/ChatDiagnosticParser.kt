package com.example.scoutface.brain

/**
 * One dev-only snapshot of the most recent production chat-template
 * generation, as captured natively (see scout_llama_jni.cpp's g_chatDiag /
 * nativeGetLastChatDiagnosticsSummary()). Numeric/boolean fields only -- the
 * rendered prompt text itself comes from a separate native call
 * (LlamaEngine.getLastChatDiagnosticPrompt() / nativeGetLastChatDiagnosticPrompt()),
 * deliberately kept out of this delimited format since real prompt text can
 * contain '=' or ';' and would corrupt a "key=value;..." line.
 *
 * Dev-only, in-memory, reached from the hidden developer diagnostic screen --
 * never persisted to DiagnosticDb or any file.
 */
data class ChatDiagnosticSummary(
    val valid: Boolean,
    val nMessages: Int,
    val promptLength: Int,
    val nPromptTokens: Int,
    val nGeneratedTokens: Int,
    val stoppedByEog: Boolean
)

/**
 * Pure parser for nativeGetLastChatDiagnosticsSummary()'s
 * "key=value;key=value;..." line -- kept separate from LlamaEngine (which
 * imports android.util.Log and other Android APIs, so it cannot be compiled
 * or tested by this repo's local no-Android-SDK pure-Kotlin harness) purely
 * so this parsing logic has real, locally-runnable test coverage, the same
 * reasoning ScoutChatMessageBuilder was kept pure for.
 *
 * Mirrors LlamaEngine.parseBenchmarkResult()'s own field-splitting approach.
 * Returns null on any malformed/missing/non-numeric field rather than
 * guessing a default or throwing -- a silently-wrong diagnostic number would
 * be worse than no number at all, and this is reached from UI code that
 * should never crash a hidden developer screen on a parse issue.
 */
object ChatDiagnosticParser {

    fun parse(raw: String): ChatDiagnosticSummary? {
        val fields = raw.split(";").associate { part ->
            val idx = part.indexOf('=')
            if (idx < 0) part to "" else part.substring(0, idx) to part.substring(idx + 1)
        }
        return try {
            ChatDiagnosticSummary(
                valid = fields.getValue("valid") == "1",
                nMessages = fields.getValue("n_messages").toInt(),
                promptLength = fields.getValue("prompt_len").toInt(),
                nPromptTokens = fields.getValue("n_prompt_tokens").toInt(),
                nGeneratedTokens = fields.getValue("n_generated").toInt(),
                stoppedByEog = fields.getValue("stopped_eog") == "1"
            )
        } catch (e: Exception) {
            null
        }
    }
}
