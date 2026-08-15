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

    // Relation words for the person-introduction safety net below -- deliberately
    // a *finite vocabulary*, not "any noun after my," so an ordinary possessive
    // statement ("this is my favorite show," "this is my house") never matches.
    // Broader than RELATION_WORDS above (which TeachExtractor/findSubject() use
    // for actual extraction) since this list only ever triggers a clarifying
    // question, never a write -- see looksLikeUnknownPersonIntroduction().
    private val PERSON_RELATION_HINTS = setOf(
        "friend", "neighbor", "neighbour", "coworker", "colleague", "boss",
        "roommate", "brother", "sister", "sibling", "cousin", "uncle", "aunt",
        "grandma", "grandmother", "grandpa", "grandfather", "nephew", "niece",
        "teacher", "boyfriend", "girlfriend", "fiance", "fiancee", "partner",
        "babysitter", "nanny", "doctor"
    )

    // Words that can follow "is my <relation>" or precede "is my <relation>"
    // without being an actual name -- pronouns and question/filler words.
    // Narrower than TeachExtractor's own NON_NAME_WORDS (that list guards a much
    // broader set of patterns); this only needs to cover the two shapes below.
    private val NAME_TOKEN_BLOCKLIST = setOf(
        "it", "this", "that", "he", "she", "they", "we", "i", "you",
        "who", "what", "here", "there"
    )

    private val QUESTION_LEAD_WORDS = setOf(
        "what", "who", "when", "where", "why", "how",
        "do", "does", "did", "is", "are", "can", "could", "will", "would"
    )

    // Explicit claims of durable retention -- "I'll remember," "I've saved
    // that," etc. Deliberately narrow and literal, matching the substring-list
    // style cleanOfflineReply() already uses for identity-leak phrases below in
    // MainActivity.kt. Ordinary conversational acknowledgment ("Got it,"
    // "Noted," "Okay") is intentionally NOT in this list -- see
    // containsRetentionClaim()'s doc comment.
    private val RETENTION_CLAIM_PHRASES = listOf(
        "i'll remember", "i will remember", "ill remember",
        "i won't forget", "i will not forget", "i wont forget",
        "keep that in mind", "keep this in mind", "keeping that in mind",
        "i've saved that", "i have saved that", "i'll save that", "ive saved that",
        "i'll make a note", "i've made a note", "i have made a note",
        "i'll note that", "i've noted that down", "noted that down", "ive noted that down",
        "i'll write that down", "i've written that down", "ill write that down",
        "committing that to memory", "committed that to memory",
        "i'll hold onto that", "i'll retain that", "ill hold onto that"
    )

    // Shared with MainActivity's Layer-1 clarification response (handleTeaching())
    // and the Layer-2 output backstop (applyRetentionClaimGuard()) so both speak
    // identically -- single source of truth, no risk of the two drifting apart.
    const val UNRECOGNIZED_TEACHING_CLARIFICATION =
        "I want to make sure I get that right -- can you say that a different way?"

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
    //
    // Also catches a first-time introduction using a relation word outside
    // RELATION_WORDS/findSubject()'s narrow vocabulary ("This is my friend,
    // Janice," "Janice is my friend") -- see looksLikeUnknownPersonIntroduction().
    // Those never had a subject findSubject() could anchor on, so without this
    // second check they'd silently fall all the way through to Gemini/TinyLlama
    // with nothing written and no clarification asked -- exactly the gap this
    // function exists to close.
    fun looksLikeUnrecognizedTeaching(input: String, knownNames: Set<String>): Boolean {
        val s = normalize(input)
        if (looksLikeQuestion(s)) return false
        val mentionsEntity = findSubject(s, knownNames) != null
        val hasHint = HINT_WORDS.any { containsWord(s, it) }
        if (mentionsEntity && hasHint) return true
        return looksLikeUnknownPersonIntroduction(s)
    }

    // "This is my friend, Janice." / "That's my neighbor, Bob." / "Janice is my
    // friend." -- a person introduction using a relation word findSubject()
    // doesn't recognize (so structured extraction can't safely write anything),
    // but is still unmistakably an attempt to tell Scout about someone. Gated on
    // PERSON_RELATION_HINTS' finite vocabulary specifically so an ordinary
    // possessive statement about an object ("this is my favorite show," "this
    // is my house" -- only one word after "my," or a word not in the list)
    // never matches. [s] is expected to already be normalize()'d by the caller.
    // Deliberately detection-only: never writes anything, never invents a
    // relation type TruthDb/ScoutEntityResolver don't already model.
    private fun looksLikeUnknownPersonIntroduction(s: String): Boolean {
        val relHints = PERSON_RELATION_HINTS.joinToString("|") { Regex.escape(it) }

        // "this is/that is my <relation>[,] <name>" -- [,\s]+ tolerates either a
        // raw comma (a natural appositive pause, e.g. in a test string typed the
        // way a person would write it) or the plain whitespace TextNormalizer
        // already reduces a comma to in the real runtime pipeline.
        Regex("""\b(?:this is|that is) my (?:$relHints)[,\s]+([a-z]+)\b""").find(s)?.let { m ->
            if (m.groupValues[1] !in NAME_TOKEN_BLOCKLIST) return true
        }

        // "<name> is my <relation>" -- name-first introduction.
        Regex("""\b([a-z]+) is my (?:$relHints)\b""").find(s)?.let { m ->
            if (m.groupValues[1] !in NAME_TOKEN_BLOCKLIST) return true
        }

        return false
    }

    // Broader than looksLikeUnrecognizedTeaching() above -- no known-entity or
    // relation-word anchor required, just a teaching-hint word in a statement
    // (not a question), or a person-introduction shape. Used only to gate the
    // retention-claim backstop below (applyRetentionClaimGuard()), never to
    // decide whether to write or ask a clarifying question -- deliberately
    // over-triggering here is safe because the only thing it enables is a
    // *check*, not an action, matching the same recall-over-precision
    // philosophy ScoutMemoryGate already documents.
    //
    // Also checks looksLikeUnknownPersonIntroduction(): in the real call graph
    // that's provably redundant (any input matching those shapes was already
    // fully handled inside MainActivity.handleTeaching(), which returns before
    // Gemini/TinyLlama is ever reached for that same turn) -- but this function
    // is a safety backstop, and a backstop shouldn't depend on an unenforced
    // assumption about what its caller already checked. Kept in for that
    // reason: cheap, and it's what makes this function correct when called on
    // its own, not just correct given today's one call graph.
    fun looksTeachingShaped(input: String): Boolean {
        val s = normalize(input)
        if (looksLikeQuestion(s)) return false
        return HINT_WORDS.any { containsWord(s, it) } || looksLikeUnknownPersonIntroduction(s)
    }

    // Explicit, literal retention-claim phrases only -- "Got it," "Noted," and
    // "Okay" alone never match, on purpose (see RETENTION_CLAIM_PHRASES' doc
    // comment). [reply] is a model's raw free-text output, not qNorm, so it is
    // not assumed to be pre-normalized -- lowercased and curly quotes folded to
    // straight ones here, matching TextNormalizer's own apostrophe handling,
    // rather than requiring the caller to normalize first.
    fun containsRetentionClaim(reply: String): Boolean {
        val lower = reply.lowercase().replace("’", "'")
        return RETENTION_CLAIM_PHRASES.any { lower.contains(it) }
    }

    // The Layer-2 backstop: only ever substitutes when BOTH the original
    // utterance looked teaching-shaped AND the model's own reply explicitly
    // claims durable retention. Never applied to the deterministic teaching or
    // calendar-clarification paths (see MainActivity), which already only speak
    // a confirmation after a real TruthDb write -- this exists solely for the
    // generative (Gemini/TinyLlama) fallback paths, as a contextual backstop for
    // whatever looksLikeUnrecognizedTeaching() above didn't already intercept
    // before either model was ever called.
    fun applyRetentionClaimGuard(originalInput: String, reply: String): String =
        if (looksTeachingShaped(originalInput) && containsRetentionClaim(reply)) {
            UNRECOGNIZED_TEACHING_CLARIFICATION
        } else {
            reply
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
