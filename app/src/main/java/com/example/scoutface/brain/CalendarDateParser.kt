package com.example.scoutface.brain

import java.util.Calendar
import java.util.Locale

// Parses a specific calendar date out of a natural-language question -- "am I free on
// July 10th", "what do I have on the 10th of July", "are we busy next Saturday" -- for
// MainActivity.handleCalendarIntent()'s date-lookup branch. Returns null when no
// recognizable date is present, so callers fall back to the existing today/tomorrow/
// this-week/title-search handling in that case. Deliberately bounded in scope: no
// numeric slash dates (7/10) since those are ambiguous (month/day vs day/month) when
// spoken or typed casually.
object CalendarDateParser {

    private val MONTHS = mapOf(
        "january" to 0, "jan" to 0,
        "february" to 1, "feb" to 1,
        "march" to 2, "mar" to 2,
        "april" to 3, "apr" to 3,
        "may" to 4,
        "june" to 5, "jun" to 5,
        "july" to 6, "jul" to 6,
        "august" to 7, "aug" to 7,
        "september" to 8, "sept" to 8, "sep" to 8,
        "october" to 9, "oct" to 9,
        "november" to 10, "nov" to 10,
        "december" to 11, "dec" to 11
    )

    private val WEEKDAYS = mapOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    // Longest names first so "september" matches before the shorter "sep" alternative wins.
    private val MONTH_ALT = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
    private val WEEKDAY_ALT = WEEKDAYS.keys.sortedByDescending { it.length }.joinToString("|")

    // "july 10th", "july 10", "july 10 2027"
    private val MONTH_DAY = Regex("""\b($MONTH_ALT)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""")

    // "the 10th of july", "10th of july 2027"
    private val DAY_OF_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+of\s+($MONTH_ALT)\.?(?:,?\s+(\d{4}))?\b""")

    // "next saturday", "this saturday", "saturday"
    private val WEEKDAY = Regex("""\b(next|this)?\s*($WEEKDAY_ALT)\b""")

    // "the 10th" alone -- no month named, checked last since it's the least specific.
    private val BARE_DAY = Regex("""\bthe\s+(\d{1,2})(?:st|nd|rd|th)\b""")

    fun parseDate(query: String, now: Calendar = Calendar.getInstance()): Calendar? {
        val clean = query.lowercase(Locale.US)

        MONTH_DAY.find(clean)?.let { m ->
            val month = MONTHS[m.groupValues[1]] ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            if (day !in 1..31) return@let
            return buildDate(now, month, day, m.groupValues[3].toIntOrNull())
        }

        DAY_OF_MONTH.find(clean)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            if (day !in 1..31) return@let
            val month = MONTHS[m.groupValues[2]] ?: return@let
            return buildDate(now, month, day, m.groupValues[3].toIntOrNull())
        }

        WEEKDAY.find(clean)?.let { m ->
            val targetDow = WEEKDAYS[m.groupValues[2]] ?: return@let
            return buildWeekday(now, targetDow, wantNext = m.groupValues[1] == "next")
        }

        BARE_DAY.find(clean)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            if (day !in 1..31) return@let
            return buildBareDay(now, day)
        }

        return null
    }

    private fun startOfDay(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    // No explicit year given -> nearest occurrence: this year, unless that date already
    // passed, in which case next year (e.g. asking about "July 10th" in late July means
    // next year's July 10th, not the one that already happened).
    private fun buildDate(now: Calendar, month: Int, day: Int, explicitYear: Int?): Calendar {
        val today = startOfDay(now)
        val year = explicitYear ?: today.get(Calendar.YEAR)
        val result = Calendar.getInstance().apply { clear(); set(year, month, day, 0, 0, 0) }
        if (explicitYear == null && result.timeInMillis < today.timeInMillis) {
            result.set(Calendar.YEAR, year + 1)
        }
        return result
    }

    // "the 10th" with no month -- this month, or next month if that day already passed.
    private fun buildBareDay(now: Calendar, day: Int): Calendar {
        val today = startOfDay(now)
        val result = Calendar.getInstance().apply {
            clear()
            set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), day, 0, 0, 0)
        }
        if (result.timeInMillis < today.timeInMillis) {
            result.add(Calendar.MONTH, 1)
        }
        return result
    }

    // Bare/"this" weekday -> the soonest occurrence, today included. "next" weekday ->
    // one week after that (so "next Saturday" never resolves to a Saturday that is today).
    private fun buildWeekday(now: Calendar, targetDow: Int, wantNext: Boolean): Calendar {
        val today = startOfDay(now)
        val result = today.clone() as Calendar
        var daysAhead = (targetDow - result.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (wantNext) daysAhead += 7
        result.add(Calendar.DAY_OF_YEAR, daysAhead)
        return result
    }
}
