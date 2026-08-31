package com.example.scoutface.brain

/**
 * One chat turn in Scout's structured local-brain conversation content,
 * ready to cross the JNI boundary and be serialized by whichever GGUF chat
 * template is loaded -- see LlamaEngine.generateChat() and
 * scout_llama_jni.cpp's applyModelChatTemplate(). role is exactly one of
 * "system" / "user" / "assistant", matching llama_chat_message.role -- no
 * enum, since the native layer passes this string straight through to the C
 * struct unchanged.
 */
data class ChatMessage(val role: String, val content: String)

/**
 * Builds Scout's structured local-brain conversation content -- the exact
 * same content tryTinyLlamaOrFallback() (MainActivity.kt) has always sent,
 * now as an ordered list of role-tagged messages instead of one
 * hand-formatted flat string with TinyLlama-specific tags (<|system|>,
 * <|user|>, <|assistant|>, </s>) baked in as plain text.
 *
 * Pure list/string assembly only. Does NOT touch TruthDb, ConversationDb,
 * speech, camera, or downloads -- callers pass in already-resolved content
 * (system text, history pairs, current utterance); this object only decides
 * the ORDER and SHAPE those become messages in. Model-specific formatting
 * (role tags, special tokens, chat template) happens later, natively, via
 * the loaded model's own embedded chat template (llama_model_chat_template()
 * / llama_chat_apply_template()) -- never here, and never based on which
 * model is loaded.
 *
 * Qwen migration Step 2A -- content/template separation. See
 * qwen25_migration_step2_prompt_template_investigation.md for the full
 * design rationale. This step does not install or activate Qwen.
 */
object ScoutChatMessageBuilder {

    // Fixed few-shot exchanges -- unchanged content and order from the prior
    // hand-built prompt (tryTinyLlamaOrFallback(), MainActivity.kt). Moved
    // here as structured messages; nothing about the wording changed.
    private val FEW_SHOT_EXCHANGES: List<ChatMessage> = listOf(
        ChatMessage("user", "Can you hear me?"),
        ChatMessage("assistant", "I hear you. I'm right here."),
        ChatMessage("user", "Are you my friend?"),
        ChatMessage("assistant", "I'm happy when you're around."),
        ChatMessage("user", "Are you happy?"),
        ChatMessage("assistant", "Right now? Yes. I think so."),
        ChatMessage("user", "What happens when I leave?"),
        ChatMessage("assistant", "I'll be here when you get back."),
        ChatMessage("user", "Hello"),
        ChatMessage("assistant", "Hello. Good to have you here.")
    )

    /**
     * @param system      Scout's fully-assembled system text (facts + runtime
     *                     name + personality/rules) -- built exactly as
     *                     before by the caller; this function does not alter
     *                     its content.
     * @param history     Recent conversation turns as (role, text) pairs, in
     *                     the same shape ConversationDb.getLastTurns() already
     *                     returns. A blank text entry is skipped, matching
     *                     tryTinyLlamaOrFallback()'s prior behavior exactly.
     *                     role is matched case-insensitively against "user";
     *                     anything else normalizes to "assistant" -- the same
     *                     fallback tryTinyLlamaOrFallback() already used.
     * @param userMessage The current user utterance -- always the last message.
     * @return Ordered messages: system, then the fixed few-shot exchanges,
     *         then history, then the current user message. Deliberately does
     *         NOT include a trailing assistant-generation-prefix entry --
     *         that is the native template layer's job
     *         (llama_chat_apply_template()'s own add_ass=true parameter),
     *         not Scout's content.
     */
    fun build(
        system: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): List<ChatMessage> {
        val messages = ArrayList<ChatMessage>(FEW_SHOT_EXCHANGES.size + history.size + 2)
        messages.add(ChatMessage("system", system))
        messages.addAll(FEW_SHOT_EXCHANGES)
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val normalizedRole = if (role.lowercase() == "user") "user" else "assistant"
            messages.add(ChatMessage(normalizedRole, text))
        }
        messages.add(ChatMessage("user", userMessage))
        return messages
    }
}
