package com.example.scoutface.brain

object TeachExtractor {

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