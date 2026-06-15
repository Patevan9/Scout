package com.example.scoutface.brain

import java.util.Locale

object TextNormalizer {

    fun normalizeUtterance(raw: String): String {
        var s = raw.lowercase(Locale.US).trim()
        s = s.replace("’", "'")
        s = s.replace(Regex("[^a-z0-9'\\s]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()

        s = s.replace("what's", "what is")
        s = s.replace("whats", "what is")
        s = s.replace("who's", "who is")
        s = s.replace("whos", "who is")
        s = s.replace("it's", "it is")
        s = s.replace(Regex("\\bim\\b"), "i am")
        s = s.replace("i'm", "i am")
        s = s.replace("you're", "you are")
        s = s.replace("youre", "you are")

        s = s.replace("wife's", "wife")
        s = s.replace("wifes", "wife")
        s = s.replace("wives", "wife")

        s = s.replace("sons", "son")
        s = s.replace("son's", "son")
        s = s.replace("kids", "kid")
        s = s.replace("child's", "child")

        s = s.replace("wi fi", "wifi")
        s = s.replace("wi-fi", "wifi")

        return s
    }
}