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
        "dog", "cat", "pet", "name", "birthday", "anniversary", "favorite", "remember",
        "learned", "learn", "taught", "told", "household", "lives with", "live with"
    )

    // A personal-memory question can be about the speaker ("my wife's name")
    // or addressed directly at Scout ("what did you learn today") -- both
    // involve a personal entity holding/receiving memory, so both belong
    // here. "you"/"your" are extremely common words, but that's safe: they
    // only ever matter paired with a genuine TOPIC_WORD below, and that list
    // is deliberately narrow, so "can you set a timer" or "do you know the
    // weather" (no topic word present) never trips this regardless.
    private val SELF_WORDS = listOf("my", "me", "i", "i'm", "mine", "us", "we", "you", "your")

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

            // "aliases" (plural) is how TruthDb actually stores nicknames -- one
            // comma-joined fact value, e.g. "Nick, Nicky" (see TruthDb.addAlias()).
            // Matched as a set of individual names, not "alias == the whole joined
            // string," since the joined string as one phrase would never appear
            // verbatim in a real query.
            if (key == "aliases") {
                val hasAliasMatch = value.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .any { containsWord(clean, it) }
                if (hasAliasMatch) return true
                continue
            }

            val isNameLike = key == "name" || key == "alias" || key == "nickname" ||
                key.endsWith("_name") || key.endsWith("_nickname")
            if (!isNameLike) continue
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
