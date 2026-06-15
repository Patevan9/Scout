package com.example.scoutface.brain

/**
 * Builds formatted prompts for TinyLlama 1.1B Chat v1.0.
 *
 * TinyLlama uses the ChatML template:
 *
 *   <|system|>
 *   {system}</s>
 *   <|user|>
 *   {user}</s>
 *   <|assistant|>
 *
 * The trailing <|assistant|> with no closing token is the generation
 * prefix — the model continues from there.
 *
 * Does NOT touch speech, camera, downloads, Gemini, or memory layers.
 */
object OfflinePromptBuilder {

    /**
     * Builds a fully formatted inference prompt for TinyLlama.
     *
     * @param system       System instruction string
     * @param conversation Recent conversation turns as (role, text) pairs.
     *                     role is "user" or "scout"
     * @param userMessage  The current user message to respond to
     * @param maxTurns     How many recent turns to include — keeps prompt short
     * @return             Complete formatted prompt ready for LlamaEngine.generate()
     */
    fun build(
        system: String,
        conversation: List<Pair<String, String>>,
        userMessage: String,
        maxTurns: Int = 4
    ): String {
        val sb = StringBuilder()

        // System block
        sb.append("<|system|>\n")
        sb.append(system.trim())
        sb.append("</s>\n")

        // Recent conversation turns
        val recentTurns = conversation.takeLast(maxTurns)
        for ((role, text) in recentTurns) {
            val trimmed = text.trim()
            if (trimmed.isBlank()) continue
            if (role.lowercase() == "user") {
                sb.append("<|user|>\n")
                sb.append(trimmed)
                sb.append("</s>\n")
            } else {
                sb.append("<|assistant|>\n")
                sb.append(trimmed)
                sb.append("</s>\n")
            }
        }

        // Current user message
        sb.append("<|user|>\n")
        sb.append(userMessage.trim())
        sb.append("</s>\n")

        // Assistant prefix — generation starts here
        sb.append("<|assistant|>\n")

        return sb.toString()
    }

    /**
     * Short system instruction tuned for TinyLlama's small size.
     * TinyLlama works best with brief, clear instructions.
     *
     * @param userName  The user's name if known, or null
     * @param robotName Scout's name
     */
    fun buildSystemInstruction(
        userName: String? = null,
        robotName: String = "Scout"
    ): String {
        val nameContext = if (!userName.isNullOrBlank()) {
            "The user's name is $userName. "
        } else {
            ""
        }
        return "${nameContext}You are $robotName, a calm and helpful " +
               "family companion. Give short, friendly answers. " +
               "Be honest when you do not know something."
    }
}