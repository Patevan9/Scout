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
        "different", "new", "old", "big", "small", "broken", "working"
    )

    fun extract(input: String): Pair<String, String>? {
        val s = input.lowercase().trim()

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
Regex("""\bmy ([a-z ]+?) is ([a-z ]+)""").find(s)?.let {
            val label = FactKey.custom("favorite_" + it.groupValues[1].trim())
            val value = it.groupValues[2].trim()
                .replaceFirstChar { c -> c.uppercase() }
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
}