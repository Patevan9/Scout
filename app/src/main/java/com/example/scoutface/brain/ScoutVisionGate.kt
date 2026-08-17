package com.example.scoutface.brain

/**
 * Decides whether a query that didn't match any specific intent in
 * ScoutIntentRouter might still be about Scout's camera/vision capability or
 * current view. If so, it must be answered by Scout's real vision handler
 * (deterministic, grounded in live ML Kit face/scene data -- see
 * MainActivity.handleVisionIntent()/VisionAnswerBuilder, which never denies
 * having a camera, only that current data is stale or unclear) instead of
 * being sent to fact-blind, vision-blind Gemini.
 *
 * Deliberately a separate object from ScoutMemoryGate, not a widening of it --
 * ScoutMemoryGate is specifically about personal-memory protection (family,
 * preferences, taught facts); vision is a distinct capability with its own
 * real subsystem to defer to, not a memory topic. See the real-device finding
 * this exists to close: "Do you see anything?" / "Who's in front of you?"
 * have no ScoutMemoryGate topic word at all, so with no vision-specific gate
 * they reached Gemini with zero grounding and it falsely claimed Scout has no
 * camera.
 *
 * Deliberately biased toward over-triggering, not precision, same tradeoff as
 * ScoutMemoryGate: a false positive here just costs a redundant, always-honest
 * deterministic vision check; a false negative means a vision question
 * reaches Gemini ungrounded, which is the thing this exists to prevent.
 * "look"/"looking"/"watching" are deliberately excluded from the topic
 * vocabulary -- too common in unrelated conversational filler ("look, here's
 * the thing," "let's look into that") to be a safe recall/precision tradeoff.
 */
object ScoutVisionGate {

    private val TOPIC_WORDS = listOf(
        "see", "seeing", "camera", "vision", "visible", "surroundings"
    )

    private val TOPIC_PHRASES = listOf("in front of you", "what's around you")

    private val SELF_WORDS = listOf("you", "your")

    fun isPossibleVisionQuery(clean: String): Boolean {
        if (TOPIC_PHRASES.any { clean.contains(it) }) return true // already implies "you"

        val hasTopicWord = TOPIC_WORDS.any { containsWord(clean, it) }
        val hasSelfWord = SELF_WORDS.any { containsWord(clean, it) }
        return hasTopicWord && hasSelfWord
    }

    // Whole-word/whole-phrase match, not a bare substring check -- matches
    // ScoutMemoryGate's own containsWord() exactly (kept as a separate copy
    // here rather than shared, since this stays a standalone gate).
    private fun containsWord(clean: String, phrase: String): Boolean {
        val escaped = phrase.trim().lowercase().split(" ").filter { it.isNotBlank() }
            .joinToString("""\s+""") { Regex.escape(it) }
        if (escaped.isBlank()) return false
        return Regex("""\b$escaped\b""").containsMatchIn(clean)
    }
}
