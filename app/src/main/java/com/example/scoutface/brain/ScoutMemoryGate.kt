package com.example.scoutface.brain

/**
 * Decides whether a query that didn't match any specific intent in
 * ScoutIntentRouter might still be a personal-memory question -- something
 * about the owner, family, pets, preferences, or anything ever taught to
 * Scout. If so, it must be answered from TruthDb (directly, or via TinyLlama
 * grounded in TruthDb) instead of being sent to a fact-blind Gemini.
 *
 * Deliberately biased toward over-triggering, not precision. A false
 * positive here just means TinyLlama gets asked to check facts it didn't
 * need to -- safe, and it can still say "I don't know that yet." A false
 * negative means a personal question reaches Gemini with zero grounding,
 * which is the thing this exists to prevent. So recall matters more than
 * precision.
 */
object ScoutMemoryGate {

    private val TOPIC_WORDS = listOf(
        "family", "wife", "husband", "spouse", "son", "daughter", "kid", "child",
        "dog", "cat", "pet", "name", "birthday", "favorite", "remember",
        "learned", "learn", "taught", "told", "household", "lives with", "live with"
    )

    private val SELF_WORDS = listOf("my", "me", "i", "i'm", "mine", "us", "we")

    fun isPossiblePersonalMemoryQuery(clean: String, knownFacts: List<Pair<String, String>>): Boolean {

        val hasSelfWord = SELF_WORDS.any { containsWord(clean, it) }
        val hasTopicWord = TOPIC_WORDS.any { containsWord(clean, it) }
        if (hasSelfWord && hasTopicWord) return true

        // Mentioning a name Scout already knows -- "Tell me about Diana," "What
        // was Nick's birthday" -- is just as much a memory question as one with
        // "my" in it. No phrase list needed: any *_name fact's stored value is a
        // known name, matched as a whole word so "Nick" doesn't match "mechanic."
        return mentionsKnownName(clean, knownFacts)
    }

    private fun mentionsKnownName(clean: String, knownFacts: List<Pair<String, String>>): Boolean {
        for ((key, value) in knownFacts) {
            if (value.isBlank()) continue
            if (key != "name" && !key.endsWith("_name")) continue
            if (containsWord(clean, value.trim())) return true
        }
        return false
    }

    // Whole-word/whole-phrase match, not a bare substring check -- "me" must not
    // match inside "time" or "remember," "i" must not match inside "wifi." Words
    // in [phrase] are joined so multi-word phrases like "lives with" still work.
    private fun containsWord(clean: String, phrase: String): Boolean {
        val escaped = phrase.trim().lowercase().split(" ").filter { it.isNotBlank() }
            .joinToString("""\s+""") { Regex.escape(it) }
        if (escaped.isBlank()) return false
        return Regex("""\b$escaped\b""").containsMatchIn(clean)
    }
}
