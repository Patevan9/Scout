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

    // Layer-2 replacement for a global capability-denial reply (see
    // containsCapabilityDenial() below) -- truthful about what Scout actually
    // does: writes real facts to TruthDb and never claims retention beyond that.
    const val MEMORY_CAPABILITY_CLARIFICATION =
        "Actually, I do remember things -- I can learn facts you teach me and store them locally. I only say I remember something once it's actually been saved."

    // Layer-2 replacement for a reminder-scheduling promise (see
    // containsReminderPromise() below) -- Scout has no reminder system yet, so
    // this is the honest answer until one exists (see MainActivity's future
    // ReminderDb/AlarmManager design notes).
    const val REMINDER_NOT_AVAILABLE = "I can't set reminders yet."

    // Layer-2 replacement for a global vision-capability-denial reply (see
    // containsVisionCapabilityDenial() below) -- truthful about what Scout
    // actually has: a real camera, even when current data is stale or unclear.
    const val VISION_CAPABILITY_CLARIFICATION =
        "Actually, I do have a real camera -- I might not always recognize what I'm seeing, but I can see."

    // Self-referential "are you even capable of learning/remembering" questions
    // -- "Can you learn?", "Do you have a memory?", "Are you capable of
    // remembering?". Deliberately narrow modal/capability phrasing, gated so a
    // specific referent right after the verb ("Can you remember my birthday?",
    // "Can you remember to grab milk?") is excluded -- those are real recall
    // questions or task requests, not questions about Scout's capability, and
    // must keep routing normally (RECALL_FACT, or an ordinary conversational
    // reply). Verified against both real capability phrasings and adversarial
    // specific-referent phrasings before this was written.
    private val CAPABILITY_SAFE_TRAILERS = setOf(
        "", "anything", "something", "things", "stuff", "at all", "in general",
        "information", "facts", "memories", "from others", "or remember",
        "or remember from others", "or remember things"
    )
    private val CAPABILITY_REFERENT_LEAD_WORDS = setOf(
        "my", "the", "this", "that", "a", "an", "our", "his", "her", "their", "to"
    )
    private val CAPABILITY_QUESTION_PATTERNS = listOf(
        Regex("""\b(?:can|could) you (?:learn|remember)\b(.*)$"""),
        Regex("""\bare you (?:able|capable) (?:to|of) (?:learn|learning|remember|remembering)\b(.*)$"""),
        Regex("""\bdo you (?:even |really |actually )?have (?:a )?(?:memory|the ability to learn|the capacity to learn|the ability to remember)\b(.*)$"""),
        Regex("""\bdo you (?:have the capability|have the ability|have the capacity) to (?:learn|remember)\b(.*)$"""),
        Regex("""\b(?:can|could) you (?:retain|store|hold onto|keep) (?:information|memories|things|facts)\b(.*)$"""),
        Regex("""\bdo you remember\b(.*)$""")
    )

    // Global capability-denial phrases only -- "I cannot learn," "I don't have
    // the capability to learn," etc. Deliberately excludes a truthful,
    // specific-fact-absence answer like "I don't have a memory of that" or
    // "I'm unable to remember your dog's name right now" -- those are correct
    // things for Scout to say and must never be rewritten. See
    // containsCapabilityDenial()'s trailer-gated patterns below for the two
    // constructions ambiguous enough to need that distinction.
    private val GLOBAL_DENIAL_LITERALS = listOf(
        "i cannot learn", "i can't learn", "i can not learn",
        "i do not have the capability to learn", "i don't have the capability to learn",
        "i do not have the ability to learn", "i don't have the ability to learn",
        "i am unable to learn", "i'm unable to learn",
        "i do not retain information", "i don't retain information",
        "i cannot remember anything", "i can't remember anything", "i can not remember anything",
        "i am unable to remember anything", "i'm unable to remember anything",
        "i have no memory of my own", "i have no ability to learn", "i have no capacity to learn",
        "i do not have a memory of my own", "i don't have a memory of my own"
    )
    private val GLOBAL_DENIAL_SAFE_TRAILERS = setOf("", "at all", "of my own", "in general", "period")
    private val GLOBAL_DENIAL_PATTERNS = listOf(
        Regex("""\bi (?:do not|don't) have (?:a |any )?memory\b(.*)$"""),
        Regex("""\bi'?m unable to remember\b(.*)$"""),
        Regex("""\bi am unable to remember\b(.*)$""")
    )

    // Future-scheduling / completion-claim reminder phrases only -- "I'll
    // remind you on January 27th," "I've set a reminder," "Would you like me
    // to remind you tomorrow?" Deliberately excludes ordinary recollection
    // language like "I can remind you what you told me earlier" -- that's
    // Scout offering to restate something already known, not a promise to
    // schedule a future nudge Scout has no way to deliver. See
    // containsReminderPromise()'s trailer-gated patterns below.
    private val REMINDER_ALWAYS_BLOCK_LITERALS = listOf(
        "would you like me to remind you", "do you want me to remind you",
        "want me to remind you", "should i remind you", "shall i remind you",
        "i've set a reminder", "i have set a reminder", "i'll set a reminder", "i will set a reminder",
        "i've set an alarm", "i have set an alarm", "i'll set an alarm", "i will set an alarm",
        "i've scheduled a reminder", "i have scheduled a reminder",
        "i'll schedule a reminder", "i will schedule a reminder",
        "i've created a reminder", "i have created a reminder"
    )
    private val REMINDER_PROMISE_PATTERNS = listOf(
        Regex("""\bi'?ll remind you\b(.*)$"""),
        Regex("""\bi will remind you\b(.*)$"""),
        Regex("""\bi can remind you\b(.*)$"""),
        Regex("""\bi could remind you\b(.*)$""")
    )
    // A future/time cue anywhere in the trailer means "remind you ___" is a
    // scheduling promise. A starter set for this pass, not exhaustive -- Layer
    // 1 (deterministic routing) and the system-prompt line are the other two
    // legs of defense, not just this list.
    private val REMINDER_FUTURE_TIME_CUES = Regex(
        """\b(tomorrow|tonight|later|soon|next\s+\w+|in an?\s+\w+|in\s+\d|at\s+\d|""" +
        """on\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*|""" +
        """on\s+(?:mon|tues|wednes|thurs|fri|satur|sun)day|this\s+(?:morning|afternoon|evening|weekend))\b"""
    )
    // A recollection/past-information cue means "remind you ___" is about
    // re-stating something already known, not scheduling a future nudge.
    private val REMINDER_RECOLLECTION_CUES = Regex(
        """\b(what you (?:told|said)|what i (?:told|said)|of (?:that|what|the)|about (?:that|what|the)|earlier|before|last time|already (?:told|said))\b"""
    )

    // Global vision/camera-denial phrases only -- "I don't have a camera,"
    // "I have no visual capability," etc. Deliberately excludes a truthful
    // current-state report like "I don't see anything I recognize right now"
    // or "I'm not confident about what I'm seeing" -- those are correct
    // things for Scout to say (see VisionAnswerBuilder/VISION_STALE/
    // VISION_UNCLEAR, which never deny having a camera, only that current
    // data is stale or unclear) and must never be rewritten. See
    // containsVisionCapabilityDenial()'s trailer-gated pattern below for the
    // one construction ambiguous enough to need that distinction.
    private val VISION_GLOBAL_DENIAL_LITERALS = listOf(
        "i don't have a camera", "i do not have a camera", "i have no camera",
        "i don't have the ability to see", "i do not have the ability to see",
        "i don't have visual capability", "i do not have visual capability", "i have no visual capability",
        "i have no ability to see", "i have no capacity to see",
        "i cannot see at all", "i can't see at all",
        "i have no way to see", "i have no way of seeing",
        "i'm not equipped with a camera", "i am not equipped with a camera",
        "i don't have eyes", "i do not have eyes",
        "i'm just a text-based", "i am just a text-based",
        "as an ai, i don't have eyes", "as an ai, i do not have eyes"
    )
    // Trailer cues for the bare "i can('t|not) see" / "i('m| am) unable to
    // see" construction -- the genuinely ambiguous one. A temporal/
    // uncertainty cue means this is a truthful current-state report
    // (preserve); an AI-identity cue or nothing at all means a global denial
    // (block).
    private val VISION_CURRENT_STATE_CUES = Regex(
        """\b(right now|currently|at the moment|clearly|well|much|exactly|quite|for sure)\b"""
    )
    private val VISION_GLOBAL_CUES = Regex(
        """\b(at all|because i'm|because i am|i'm just an? ai|i am just an? ai|as an ai|anything at all)\b"""
    )
    private val VISION_DENIAL_PATTERNS = listOf(
        Regex("""\bi (?:cannot|can't|can not) see\b(.*)$"""),
        Regex("""\bi'?m unable to see\b(.*)$"""),
        Regex("""\bi am unable to see\b(.*)$""")
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

    // Layer 1 (before either model): is this a self-referential question about
    // whether Scout is even capable of learning/remembering, as opposed to a
    // question about a specific fact ("do you remember my birthday") or a
    // comprehension check ("did you learn how that works")? Callers route a
    // match to IntentType.IDENTITY for a deterministic, TruthDb-grounded
    // answer -- see MainActivity.handleIdentityIntent().
    fun looksLikeMemoryCapabilityQuestion(input: String): Boolean {
        val s = input.lowercase().trim().trimEnd('?', '.', '!')
        for (re in CAPABILITY_QUESTION_PATTERNS) {
            val m = re.find(s) ?: continue
            val rest = if (m.groupValues.size > 1) m.groupValues[1] else ""
            if (capabilityTrailerIsSafe(rest)) return true
        }
        return false
    }

    private fun capabilityTrailerIsSafe(rest: String): Boolean {
        val trimmed = rest.trim()
        if (trimmed in CAPABILITY_SAFE_TRAILERS) return true
        val firstWord = trimmed.substringBefore(' ')
        if (firstWord in CAPABILITY_REFERENT_LEAD_WORDS) return false
        return trimmed.isBlank() || firstWord in CAPABILITY_SAFE_TRAILERS
    }

    // Layer 2: does [reply] contain a GLOBAL claim that Scout cannot learn or
    // remember at all -- "I cannot learn," "I don't have the capability to
    // learn," "I don't retain information"? Deliberately excludes a truthful,
    // specific-fact-absence answer ("I don't have a memory of that," "I'm
    // unable to remember your dog's name right now") via the trailer check on
    // the two ambiguous constructions -- see GLOBAL_DENIAL_PATTERNS' doc
    // comment above. [reply] is a model's raw free-text output, so it is not
    // assumed pre-normalized here, matching containsRetentionClaim().
    fun containsCapabilityDenial(reply: String): Boolean {
        val lower = reply.lowercase().replace("’", "'")
        if (GLOBAL_DENIAL_LITERALS.any { lower.contains(it) }) return true
        for (re in GLOBAL_DENIAL_PATTERNS) {
            val m = re.find(lower) ?: continue
            val rest = if (m.groupValues.size > 1) m.groupValues[1] else ""
            if (globalDenialTrailerIsGlobal(rest)) return true
        }
        return false
    }

    private fun globalDenialTrailerIsGlobal(rest: String): Boolean {
        val trimmed = rest.trim().trimEnd('.', '!')
        if (trimmed in GLOBAL_DENIAL_SAFE_TRAILERS) return true
        val firstWord = trimmed.substringBefore(' ')
        // "of"/"about"/"that"/"this"/"right"/"for" all introduce a specific
        // referent ("of that," "about your trip," "right now") -- never global.
        if (firstWord in setOf("of", "about", "that", "this", "right", "for")) return false
        return false
    }

    // Layer 2: does [reply] contain a future reminder-scheduling promise or a
    // completion claim -- "I'll remind you on January 27th," "I've set a
    // reminder," "Would you like me to remind you tomorrow?" Deliberately
    // excludes ordinary recollection language ("I can remind you what you told
    // me earlier") via the future-time-cue/recollection-cue trailer check --
    // see REMINDER_PROMISE_PATTERNS' doc comment above.
    fun containsReminderPromise(reply: String): Boolean {
        val lower = reply.lowercase().replace("’", "'")
        if (REMINDER_ALWAYS_BLOCK_LITERALS.any { lower.contains(it) }) return true
        for (re in REMINDER_PROMISE_PATTERNS) {
            val m = re.find(lower) ?: continue
            val rest = if (m.groupValues.size > 1) m.groupValues[1] else ""
            if (reminderTrailerImpliesPromise(rest)) return true
        }
        return false
    }

    private fun reminderTrailerImpliesPromise(rest: String): Boolean {
        val trimmed = rest.trim()
        if (trimmed.isBlank()) return true  // bare "I'll remind you." -- a promise by default
        if (REMINDER_FUTURE_TIME_CUES.containsMatchIn(trimmed)) return true
        if (REMINDER_RECOLLECTION_CUES.containsMatchIn(trimmed)) return false
        // Neither cue present: conservatively treat short trailing filler
        // ("I'll remind you!") as a promise -- only a recognized recollection
        // cue earns the exemption.
        return true
    }

    // Layer 2: does [reply] contain a GLOBAL claim that Scout has no camera or
    // cannot see at all -- "I don't have a camera," "I have no visual
    // capability"? Deliberately excludes a truthful current-state report
    // ("I don't see anything I recognize right now," "I'm not confident
    // about what I'm seeing") via the trailer check on the one ambiguous
    // construction -- see VISION_DENIAL_PATTERNS' doc comment above. [reply]
    // is a model's raw free-text output, so it is not assumed pre-normalized
    // here, matching containsCapabilityDenial().
    fun containsVisionCapabilityDenial(reply: String): Boolean {
        val lower = reply.lowercase().replace("’", "'")
        if (VISION_GLOBAL_DENIAL_LITERALS.any { lower.contains(it) }) return true
        for (re in VISION_DENIAL_PATTERNS) {
            val m = re.find(lower) ?: continue
            val rest = if (m.groupValues.size > 1) m.groupValues[1] else ""
            if (visionDenialTrailerIsGlobal(rest)) return true
        }
        return false
    }

    private fun visionDenialTrailerIsGlobal(rest: String): Boolean {
        val trimmed = rest.trim().trimEnd('.', '!')
        if (trimmed.isBlank()) return true
        if (VISION_GLOBAL_CUES.containsMatchIn(trimmed)) return true
        if (VISION_CURRENT_STATE_CUES.containsMatchIn(trimmed)) return false
        // Neither cue present: lean toward NOT blocking -- a bare "I can't
        // see [object/person]" without further context is at least as likely
        // to be a legitimate specific-obstruction report as a capability
        // denial, and this construction is already the least reliable signal
        // in the set. The literal list above catches the unambiguous phrasings.
        return false
    }

    // Combined Layer-2 output guard for the generative (Gemini/TinyLlama)
    // fallback paths -- see MainActivity's two deliverAiResult() call sites.
    // Renamed from applyMemoryIntegrityGuards() now that it also covers
    // vision-capability denial, not just memory/reminders. Order: capability-
    // denial, reminder-promise, and vision-denial checks all run
    // unconditionally on every reply (none has a legitimate global reading
    // once matched, so no input-shape gate is needed, unlike the
    // retention-claim guard below). applyRetentionClaimGuard() keeps its own
    // existing teaching-shaped gate, unchanged from its original PR.
    fun applyScoutCapabilityIntegrityGuards(originalInput: String, reply: String): String {
        if (containsCapabilityDenial(reply)) return MEMORY_CAPABILITY_CLARIFICATION
        if (containsReminderPromise(reply)) return REMINDER_NOT_AVAILABLE
        if (containsVisionCapabilityDenial(reply)) return VISION_CAPABILITY_CLARIFICATION
        return applyRetentionClaimGuard(originalInput, reply)
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
