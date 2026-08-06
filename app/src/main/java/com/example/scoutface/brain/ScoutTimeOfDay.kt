package com.example.scoutface.brain

import com.example.scoutface.HabitLayer

/**
 * Deterministic "what part of the day is it" answer for "is it morning or
 * night" / "what time of day is it".
 *
 * Reuses HabitLayer.TIME_SLOTS -- the app's one existing hour-of-day-to-label
 * classification, already used for habit/topic tracking -- instead of adding
 * a second, independent hour-bucketing scheme.
 *
 * ScoutPresenceDecider.PresenceMode was considered and rejected as the source
 * here. Its four modes (ACTIVE / CALM / QUIET / SLEEP) are named for Scout's
 * own social-engagement posture, not for the time of day a person would say
 * out loud: SLEEP spans midnight-6am, which a family member would still call
 * "night" for the first couple hours and "early morning" for the rest, and
 * ACTIVE alone spans 9am-7pm, covering what a person would separately call
 * morning, midday, and afternoon. Reusing PresenceMode would make Scout's
 * spoken answer wrong on its own terms even though the mode names sound
 * time-related. TIME_SLOTS' `label` field, by contrast, is already written
 * as natural spoken wording ("morning", "quiet hours", "late night", ...),
 * so it's used verbatim rather than reinterpreted.
 */
object ScoutTimeOfDay {

    /** [hour] is 0-23 (Calendar.HOUR_OF_DAY). Falls back to a neutral phrase
     *  only if TIME_SLOTS somehow doesn't cover the given hour -- it always
     *  does today, since its six slots span the full day with no gaps. */
    fun currentLabel(hour: Int): String =
        HabitLayer.TIME_SLOTS.firstOrNull { hour >= it.startHour && hour < it.endHour }
            ?.label
            ?: "right now"
}
