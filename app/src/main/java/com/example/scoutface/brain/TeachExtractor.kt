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
        Regex("""\byou see ([a-z]+)\b""").find(s)?.let {
            val word = it.groupValues[1]
            if (word !in NON_NAME_WORDS) return FactKey.NAME to cleanName(word)
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
Regex("""\bmy ([a-z ]+?) is ([a-z0-9,'\-/ ]+)""").find(s)?.let {
            val rawLabel = it.groupValues[1].trim()
            // Don't double-prefix: "my favorite color is X" → "favorite_color", not "favorite_favorite_color"
            val label = if (rawLabel.startsWith("favorite")) FactKey.custom(rawLabel)
                        else FactKey.custom("favorite_$rawLabel")
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
