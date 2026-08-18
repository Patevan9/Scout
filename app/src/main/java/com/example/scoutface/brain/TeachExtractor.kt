package com.example.scoutface.brain

object TeachExtractor {

    // Common words that can follow "this is", "i am", or "you see" without
    // being a person's name (feelings, locations, pronouns, filler, etc.)
    private val NON_NAME_WORDS = setOf(
        "a", "an", "the", "this", "that", "these", "those", "it", "its",
        "my", "your", "his", "her", "our", "their", "here", "there",
        "what", "who", "which", "anything", "something", "nothing",
        "everything", "someone", "somebody", "everyone", "everybody",
        "no", "not", "all", "some",
        "in", "on", "at", "with", "to", "for", "from", "of", "about",
        "into", "onto", "over", "under", "near", "behind", "beside", "around",
        "going", "gonna", "doing", "trying", "looking", "talking", "kidding",
        "joking", "asking", "saying", "feeling", "getting", "being",
        "named", "called", "ready", "done", "finished", "leaving",
        "coming", "back", "home",
        "happy", "sad", "glad", "mad", "angry", "upset", "tired", "sleepy",
        "hungry", "thirsty", "cold", "hot", "warm", "sick", "fine", "okay",
        "ok", "good", "bad", "great", "well", "busy", "free", "lost",
        "confused", "nervous", "scared", "afraid", "excited", "bored",
        "alone", "single", "married", "right", "wrong", "late", "early",
        "sorry", "serious", "sure", "certain",
        "cool", "awesome", "amazing", "weird", "crazy", "funny",
        "interesting", "nice", "terrible", "annoying", "perfect", "stupid",
        "dumb", "silly", "important", "true", "false", "real", "fake",
        "different", "new", "old", "big", "small", "broken", "working",
        "now", "today", "then", "soon", "later", "again", "still", "next",
        "last", "already", "yet", "always", "never", "just", "only",
        "anymore", "sometimes", "often", "lately", "recently", "currently",
        "here", "there", "away", "out", "up", "down",
        // Intensifiers/adverbs — "I am very tired", "that is really nice" must
        // not register "very"/"really" as a name (root cause of a real bug:
        // a false "Very" profile accumulated a full set of face embeddings).
        "very", "really", "quite", "extremely", "totally", "pretty",
        "so", "too", "kinda", "sorta"
    )

    // Guard for the "you see X" pattern below -- see its own comment for why
    // this is scoped to "you see" only, not the other broad NAME patterns.
    private val YOU_SEE_QUESTION_LEAD = Regex(
        """\b(?:do|does|did|can|could|will|would|are|is)\s+you\s+see\b"""
    )

    fun extract(input: String): Pair<String, String>? {
        val s = input.lowercase().trim()
            .replace("that's", "that is")
            .replace("it's", "it is")

        // -------------------------
        // NAME
        // -------------------------
        Regex("""\bmy name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bi am named ([a-z]+)\b""").find(s)?.let {
            return FactKey.NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bi'm named ([a-z]+)\b""").find(s)?.let {
            return FactKey.NAME to cleanName(it.groupValues[1])
        }

        // Phrases used to introduce/identify the person Scout is looking
        // at: "this is Patrick", "I'm Patrick", "I am Patrick", "you see
        // Patrick". These are broad, so guard against common non-name
        // words (feelings, locations, filler) via NON_NAME_WORDS.
        Regex("""\bthis is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.NAME to cleanName(word)
        }
        Regex("""\bi am ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.NAME to cleanName(word)
        }
        // Real-device finding (Fold 7): "Do/Can you see colors?" is a
        // capability question about Scout's vision, not a statement
        // introducing someone the camera sees -- but it still contains "you
        // see X" as a contiguous, in-order substring, so the pattern above
        // matched it and mistaught "Colors"/"Color" as a person's name. This
        // is deliberately NOT fixed with a denylist of specific words --
        // "do/can/does/... you see WORD" is unsafe for ANY WORD, since
        // English question word order keeps "you see" intact here in a way
        // it doesn't for "this is"/"i am"/"that is" (those invert to "is
        // this"/"am i"/"is that" in a genuine question, so they never
        // collide with their own statement-order pattern the way this one
        // does -- confirmed before deciding to scope this fix to "you see"
        // only). Checked as a local match right next to this specific
        // pattern -- not a whole-utterance first-word check -- so a leading
        // wake-word prefix ("Scout, can you see colors?") can't defeat it.
        if (!YOU_SEE_QUESTION_LEAD.containsMatchIn(s)) {
            Regex("""\byou see ([a-z]+)\b""").find(s)?.let {
                val word = it.groupValues[1]
                if (word !in NON_NAME_WORDS) return FactKey.NAME to cleanName(word)
            }
        }

        // "his name is X" / "her name is X" — pointing at someone
        Regex("""\bhis name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bher name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.NAME to cleanName(it.groupValues[1])
        }

        // "that is X" / "that person is X" — broad pointing phrases (checked before son/wife specifics)
        Regex("""\bthat is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.NAME to cleanName(word)
        }
        Regex("""\bthat person is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.NAME to cleanName(word)
        }

        // -------------------------
        // WIFE
        // -------------------------
        Regex("""\bmy wife'?s name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.WIFE_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bmy wife name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.WIFE_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthis is my wife ([a-z]+)\b""").find(s)?.let {
            return FactKey.WIFE_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthat is my wife[,\s]+([a-z]+)\b""").find(s)?.let {
            return FactKey.WIFE_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthat person is my wife[,\s]+([a-z]+)\b""").find(s)?.let {
            return FactKey.WIFE_NAME to cleanName(it.groupValues[1])
        }
        // "my wife is Diana" -- the bare introduction, without "'s name is" or
        // "this is". Real-device finding: this natural phrasing fell all the
        // way through to the generic FLEXIBLE fallback below and got
        // mislabeled as favorite_wife = "Diana" instead of wife_name =
        // "Diana". Guarded by NON_NAME_WORDS like the other bare "is X"
        // patterns above/below, so "my wife is happy" doesn't register
        // "Happy" as her name.
        Regex("""\bmy wife is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.WIFE_NAME to cleanName(word)
        }

        // -------------------------
        // SON
        // -------------------------
        Regex("""\bmy son'?s name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.SON_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bmy son name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.SON_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthis is my son ([a-z]+)\b""").find(s)?.let {
            return FactKey.SON_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthat is my son[,\s]+([a-z]+)\b""").find(s)?.let {
            return FactKey.SON_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthat person is my son[,\s]+([a-z]+)\b""").find(s)?.let {
            return FactKey.SON_NAME to cleanName(it.groupValues[1])
        }
        // "my son is Elijah" -- the bare introduction. Same real-device
        // finding and NON_NAME_WORDS guard as the bare wife pattern above.
        Regex("""\bmy son is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.SON_NAME to cleanName(word)
        }

        // -------------------------
        // DOG
        // -------------------------
        Regex("""\bmy dog'?s name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bmy dog name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bmy dog is named ([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bmy dog is called ([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthis is my dog ([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        // "the dog is Nicolas" / "the dog's name is Nicolas"
        Regex("""\bthe dog'?s name is ([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        Regex("""\bthe dog is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.DOG_NAME to cleanName(word)
        }
        // "that is my dog Nicolas" (also catches "that's my dog Nicolas" via contraction expansion above)
        Regex("""\bthat is my dog[,\s]+([a-z]+)\b""").find(s)?.let {
            return FactKey.DOG_NAME to cleanName(it.groupValues[1])
        }
        // "my dog is Nicolas" -- the bare introduction. Same real-device
        // finding and NON_NAME_WORDS guard as the bare wife/son patterns above.
        Regex("""\bmy dog is ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.DOG_NAME to cleanName(word)
        }

        // -------------------------
        // BIRTHDAY / ANNIVERSARY
        // Dates need to be stored in full, including the day number — the generic
        // catch-all below only matches [a-z ], which silently truncates any date
        // ("January 27th" → "January"). These also skip the "favorite_" auto-prefix
        // since a birthday isn't a preference.
        // -------------------------
        Regex("""\bmy birthday is ([a-z0-9,'\-/\s]+)""").find(s)?.let {
            return FactKey.custom("birthday") to cleanDateValue(it.groupValues[1])
        }
        Regex("""\b(?:our|my) anniversary is ([a-z0-9,'\-/\s]+)""").find(s)?.let {
            return FactKey.custom("anniversary") to cleanDateValue(it.groupValues[1])
        }

        // -------------------------
        // FLEXIBLE — any relationship or fact
        // "my daughter's name is Sarah"
        // "my cat is named Whiskers"
        // "my favorite color is teal"
        // "my favorite place is Maine"
        // -------------------------
        Regex("""\bmy ([a-z ]+?)'?s? name is ([a-z]+)\b""").find(s)?.let {
            val label = FactKey.custom(it.groupValues[1].trim() + "_name")
            val value = cleanName(it.groupValues[2])
            return label to value
        }
        Regex("""\bmy ([a-z ]+?) is named ([a-z]+)\b""").find(s)?.let {
            val label = FactKey.custom(it.groupValues[1].trim() + "_name")
            val value = cleanName(it.groupValues[2])
            return label to value
        }
        Regex("""\bmy ([a-z ]+?) is called ([a-z]+)\b""").find(s)?.let {
            val label = FactKey.custom(it.groupValues[1].trim() + "_name")
            val value = cleanName(it.groupValues[2])
            return label to value
        }
        // Real-device finding: this used to auto-prepend "favorite_" to
        // *any* label here that didn't already start with the word
        // "favorite" -- so "my son is Elijah" silently became favorite_son
        // = "Elijah" instead of son_name = "Elijah" (now handled above by
        // the dedicated wife/son/dog blocks), and "my mentor is Sam" became
        // favorite_mentor = "Sam" even though the user never said
        // "favorite" and Scout has no such concept modeled. Recall
        // (handleRecallIntent()) already tries a plain, unprefixed key as a
        // fallback after favorite_/_name, so there's no need to invent a
        // "favorite" meaning here at all -- rawLabel is stored exactly as
        // spoken. This means favorite_<label> is now created ONLY when the
        // user's own words literally start with "favorite" (rawLabel itself
        // begins with "favorite", e.g. "my favorite color is teal" ->
        // rawLabel "favorite color" -> FactKey.custom() already turns that
        // into "favorite_color" with no special-casing needed).
        Regex("""\bmy ([a-z ]+?) is ([a-z0-9,'\-/ ]+)""").find(s)?.let {
            val rawLabel = it.groupValues[1].trim()
            val label = FactKey.custom(rawLabel)
            val value = cleanDateValue(it.groupValues[2])
            return label to value
        }
        Regex("""\bmy ([a-z ]+?) is ([a-z ]+)\b""").find(s)?.let {
            val label = FactKey.custom(it.groupValues[1].trim())
            val value = it.groupValues[2].trim()
                .replaceFirstChar { c -> c.uppercase() }
            return label to value
        }

        return null
    }

    private fun cleanName(raw: String): String {
        return raw.trim().replaceFirstChar { it.uppercase() }
    }

    // Capitalizes each word — "january 27th" → "January 27th", "new york" → "New York".
    private fun cleanDateValue(raw: String): String {
        return raw.trim().split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { c -> c.uppercase() }
        }
    }
}
