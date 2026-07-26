package com.example.scoutface.brain

/**
 * Extracts facts about someone other than the user themselves -- "Diana's
 * birthday is November 27," "November 27 is Diana's birthday," "Diana was born
 * on November 27," "my dog's name is Nicolas, but we call him Nick." Anchored on
 * property keywords and known entity names rather than one fixed sentence shape,
 * so word order can vary without needing a new regex per phrasing Patrick happens
 * to use. Self-facts ("my birthday is X") and the identity/relation-name teaching
 * that's tied to face recognition (TeachExtractor.extract()) are untouched --
 * this only handles the "about someone else" side.
 *
 * Deliberately regex/keyword-based, not model-based: this writes to TruthDb, the
 * authoritative store, so extraction has to stay deterministic and inspectable.
 * Letting an LLM decide what gets written here would reintroduce exactly the
 * hallucination risk the personal-memory gate exists to prevent on the read side.
 * (TinyLlama-assisted extraction, with Scout validating before writing, is a
 * reasonable later step -- not this one.)
 */
object ScoutFactExtractor {

    data class Fact(val subject: String, val property: String, val value: String)

    private val DATE_PROPERTY_WORDS = setOf("birthday", "born", "anniversary")

    // "can" alongside "call"/"called" -- confirmed from an actual on-device
    // transcript ("we can him Nick") that speech-to-text mishears "call" as "can"
    // in this construction often enough to be worth handling explicitly.
    private val NICKNAME_VERBS = setOf("call", "called", "can", "nicknamed")

    private val RELATION_WORDS = setOf(
        "wife", "husband", "spouse", "son", "daughter", "kid", "child", "dog", "cat", "pet"
    )

    // Hint words for the "probably teaching something, even though structured
    // extraction failed" safety net -- confidence signals, never required, per
    // Patrick's spec. A superset of the property words above plus general
    // teaching-intent words.
    private val HINT_WORDS = setOf(
        "remember", "forget", "named", "name", "nickname", "call", "called",
        "birthday", "born", "anniversary", "favorite", "likes", "loves", "hates",
        "prefers", "allergic", "works", "job", "school", "lives", "address",
        "phone", "email"
    )

    private val QUESTION_LEAD_WORDS = setOf(
        "what", "who", "when", "where", "why", "how",
        "do", "does", "did", "is", "are", "can", "could", "will", "would"
    )

    private val DATE_VALUE = Regex(
        """\b((?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun[e]?|jul[y]?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s*\d{4})?|\d{1,2}/\d{1,2}(?:/\d{2,4})?)\b"""
    )

    // knownNames: every name/alias Scout currently recognizes (lowercase), e.g.
    // from ScoutEntityResolver.buildAliasMap(...).keys.
    fun extract(input: String, knownNames: Set<String>): List<Fact> {
        val s = normalize(input)
        if (looksLikeQuestion(s)) return emptyList()

        val subject = findSubject(s, knownNames) ?: return emptyList()
        val facts = mutableListOf<Fact>()

        if (DATE_PROPERTY_WORDS.any { containsWord(s, it) }) {
            DATE_VALUE.find(s)?.let { m ->
                facts.add(Fact(subject, "birthday", cleanDate(m.value)))
            }
        }

        extractNicknameClause(s)?.let { nick ->
            facts.add(Fact(subject, "nickname", nick))
        }

        // Generic possessive fallback -- "SUBJECT's PROPERTY is VALUE" -- for
        // anything not already covered above. Unlike the date/nickname paths,
        // this one still needs the possessive "'s ... is ..." shape; making
        // arbitrary free-text properties word-order-independent isn't safe to do
        // with regex alone (too easy to grab the wrong span as the value).
        val escapedSubject = subject.split(" ").filter { it.isNotBlank() }
            .joinToString("""\s+""") { Regex.escape(it) }
        if (escapedSubject.isNotBlank()) {
            Regex("""\b$escapedSubject'?s\s+([a-z ]+?)\s+is\s+([a-z0-9,'\-/\s]+)""").find(s)?.let { m ->
                val factLabel = m.groupValues[1].trim()
                if (factLabel !in setOf("name", "birthday", "anniversary", "nickname")) {
                    facts.add(Fact(subject, FactKey.custom(factLabel), cleanDate(m.groupValues[2])))
                }
            }
        }

        return facts
    }

    // Standalone nickname-clause check -- "but we call him Nick" -- for callers
    // that already know the subject from elsewhere in the same turn (e.g.
    // TeachExtractor.extract() just matched "my dog's name is Nicolas," so the
    // subject is already "Nicolas," not something this needs to re-discover).
    fun extractNicknameClause(input: String): String? {
        val s = normalize(input)
        val verbs = NICKNAME_VERBS.joinToString("|") { Regex.escape(it) }
        Regex("""\b(?:we|i|you|everyone|they)\s+(?:$verbs)\s+(?:him|her|it)\s+([a-z]+)\b""")
            .find(s)?.let { return cleanName(it.groupValues[1]) }
        return null
    }

    // Even when structured extraction above finds nothing, a statement (not a
    // question) that mentions a known name or relation alongside a teaching-hint
    // word is very likely Scout being taught something in a phrasing this
    // extractor doesn't yet parse. Callers use this to give an honest "say that
    // differently" instead of letting the sentence fall through to TinyLlama,
    // which would improvise a reply that sounds like confirmation without
    // anything actually having been learned.
    fun looksLikeUnrecognizedTeaching(input: String, knownNames: Set<String>): Boolean {
        val s = normalize(input)
        if (looksLikeQuestion(s)) return false
        val mentionsEntity = findSubject(s, knownNames) != null
        val hasHint = HINT_WORDS.any { containsWord(s, it) }
        return mentionsEntity && hasHint
    }

    private fun looksLikeQuestion(s: String): Boolean {
        if (s.trim().endsWith("?")) return true
        val firstWord = s.trim().substringBefore(' ')
        return firstWord in QUESTION_LEAD_WORDS
    }

    // Returns the known name/alias itself if one is mentioned, else "my <relation>"
    // if a relation word is present alongside "my", else null. Picks whichever
    // known name occurs earliest in the sentence (not just whichever the caller's
    // Set happens to iterate first) so the result doesn't depend on Set ordering.
    private fun findSubject(s: String, knownNames: Set<String>): String? {
        var bestName: String? = null
        var bestIndex = Int.MAX_VALUE
        for (name in knownNames) {
            val match = Regex("""\b${Regex.escape(name)}\b""").find(s) ?: continue
            if (match.range.first < bestIndex) {
                bestIndex = match.range.first
                bestName = name
            }
        }
        if (bestName != null) return bestName

        if (containsWord(s, "my")) {
            for (rel in RELATION_WORDS) {
                if (containsWord(s, rel)) return "my $rel"
            }
        }
        return null
    }

    private fun normalize(input: String): String =
        input.lowercase().trim().replace("that's", "that is").replace("it's", "it is")

    private fun containsWord(s: String, phrase: String): Boolean {
        val escaped = phrase.trim().lowercase().split(" ").filter { it.isNotBlank() }
            .joinToString("""\s+""") { Regex.escape(it) }
        if (escaped.isBlank()) return false
        return Regex("""\b$escaped\b""").containsMatchIn(s)
    }

    private fun cleanName(raw: String): String = raw.trim().replaceFirstChar { it.uppercase() }

    private fun cleanDate(raw: String): String =
        raw.trim().split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
}
