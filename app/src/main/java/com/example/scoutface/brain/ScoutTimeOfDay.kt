package com.example.scoutface.brain

import com.example.scoutface.HabitLayer

/**
 * Deterministic "what part of the day is it" answer for "is it morning or
 * night" / "what time of day is it".
 *
 * Reuses HabitLayer.TIME_SLOTS' existing hour boundaries -- the app's one
 * hour-of-day classification, already used for habit/topic tracking --
 * instead of adding a second, independent hour-bucketing scheme. Only the
 * boundaries are reused; TIME_SLOTS' own `label` field ("quiet hours",
 * "late") is internal vocabulary written for habit-tracking context, not for
 * a spoken answer, so it is deliberately not spoken verbatim here (see the
 * two mappings below).
 *
 * ScoutPresenceDecider.PresenceMode was considered and rejected as the
 * boundary source. Its four modes (ACTIVE / CALM / QUIET / SLEEP) are named
 * for Scout's own social-engagement posture, not for the time of day a
 * person would say out loud: SLEEP spans midnight-6am, which a family
 * member would still call "night" for the first couple hours and "early
 * morning" for the rest, and ACTIVE alone spans 9am-7pm, covering what a
 * person would separately call morning, midday, and afternoon. Reusing
 * PresenceMode would make Scout's spoken answer wrong on its own terms even
 * though the mode names sound time-related.
 *
 * Two separate spoken vocabularies, same underlying boundaries:
 *   - spokenCategory()   -- "is it morning or night": always exactly one of
 *                           morning / afternoon / evening / night, the four
 *                           words the question itself is posed in terms of.
 *   - descriptiveLabel() -- "what time of day is it": finer-grained and
 *                           allowed to be more descriptive ("early morning",
 *                           "midday", "late night").
 */
object ScoutTimeOfDay {

    // HabitLayer.TIME_SLOTS' `key` -> the word a person means by "morning,
    // afternoon, evening, or night". Midday and late-night are folded into
    // the nearest of the four named categories rather than left as a fifth
    // or sixth option the question didn't offer.
    private val SPOKEN_CATEGORY_BY_KEY = mapOf(
        "night" to "night",
        "morning" to "morning",
        "midday" to "afternoon",
        "afternoon" to "afternoon",
        "evening" to "evening",
        "late" to "night"
    )

    // HabitLayer.TIME_SLOTS' `key` -> a more descriptive spoken phrase for
    // the same slot. Distinct from TIME_SLOTS' own `label` field ("quiet
    // hours" for the night slot) -- that wording fits a habit-tracking
    // summary, not a spoken answer to a direct question.
    private val DESCRIPTIVE_LABEL_BY_KEY = mapOf(
        "night" to "early morning",
        "morning" to "morning",
        "midday" to "midday",
        "afternoon" to "afternoon",
        "evening" to "evening",
        "late" to "late night"
    )

    /** [hour] is 0-23 (Calendar.HOUR_OF_DAY). */
    private fun slotKeyFor(hour: Int): String? =
        HabitLayer.TIME_SLOTS.firstOrNull { hour >= it.startHour && hour < it.endHour }?.key

    /** "is it morning or night" -- always morning / afternoon / evening / night. */
    fun spokenCategory(hour: Int): String =
        SPOKEN_CATEGORY_BY_KEY[slotKeyFor(hour)] ?: "day"

    /** "what time of day is it" -- more descriptive, e.g. "early morning" / "late night". */
    fun descriptiveLabel(hour: Int): String =
        DESCRIPTIVE_LABEL_BY_KEY[slotKeyFor(hour)] ?: "day"
}
