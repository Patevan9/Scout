package com.example.scoutface.brain

/**
 * Weather-query classification (v1) -- answers exactly one question: "which
 * NWS forecast period(s) is this query actually asking about, and, for a
 * named-day query, which day?" Owns nothing beyond that: no Android imports,
 * no SharedPreferences, no network, no caching, no parsing of NWS responses
 * -- see ScoutWeatherManager for all of that. Extracted out of
 * ScoutWeatherManager.fetchWeather() specifically so this piece is
 * unit-testable without a real Context/SharedPreferences/network stack (see
 * ScoutWeatherManagerTest's own doc comment on why ScoutWeatherManager
 * itself isn't testable in isolation).
 *
 * Same-day fix: NWS labels the current day's own periods "Today"/"Tonight"
 * -- it never uses the literal weekday name for the current day, only for
 * periods two or more days out. Earlier logic converted "today" + a
 * future-tense qualifier ("going to"/"will"/"later"/"rest of") into today's
 * own weekday name and searched for a SPECIFIC_DAY period matching it --
 * which could never match, always falling through to "I can only see about
 * a week ahead for weather. I'm sorry, I can't tell you about $day from
 * here." Every same-day "today" phrasing (with or without a future-tense
 * qualifier) now classifies as CURRENT instead, exactly like plain "what's
 * the weather" already did -- it's answered from the same first/current
 * period NWS already returns. A genuine named weekday (e.g. "Thursday")
 * is untouched: it still classifies as SPECIFIC_DAY with that day's name,
 * whether or not it happens to be today.
 */
object ScoutWeatherQueryClassifier {

    enum class QueryType { CURRENT, TONIGHT, TOMORROW, SPECIFIC_DAY, WEEK }

    data class Classification(val type: QueryType, val specificDay: String?)

    fun classify(query: String): Classification {
        val q = query.trim().lowercase()

        val specificDay = listOf(
            "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday"
        ).firstOrNull { q.contains(it) }
            ?.replaceFirstChar { it.uppercase() }

        val type = when {
            specificDay != null -> QueryType.SPECIFIC_DAY
            q.contains("tomorrow") -> QueryType.TOMORROW
            q.contains("tonight") -> QueryType.TONIGHT
            q.contains("week") || q.contains("days") ||
                    q.contains("forecast") -> QueryType.WEEK
            else -> QueryType.CURRENT
        }

        // A named weekday (specificDay != null) always wins -- "today" is
        // never considered once a genuine weekday word is present.
        val isTodayPhrasing = specificDay == null && q.contains("today") &&
            (q.contains("going to") || q.contains("will") ||
                    q.contains("later") || q.contains("rest of"))

        return if (isTodayPhrasing) Classification(QueryType.CURRENT, null)
               else Classification(type, specificDay)
    }
}
