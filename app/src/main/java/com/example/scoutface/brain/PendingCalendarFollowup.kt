package com.example.scoutface.brain

// Calendar Follow-up -- notice/ask/learn/remember. What a generic calendar
// event title (a bare "Birthday"/"Anniversary", or a "Doctor"/"Dr." event)
// might prompt Scout to ask about, once, right after describing it -- never
// from a background scan. See MainActivity.appendCalendarFollowupIfWarranted()
// for where this is set, and handleQuery() for where it's resolved/dropped.

enum class CalendarFollowupTopic { BIRTHDAY, ANNIVERSARY, DOCTOR }

// A single, mutually-exclusive, RAM-only pending state -- one nullable field
// on MainActivity can hold at most one of these at a time, by construction,
// so a Clarification and a DoctorCheckIn can never accidentally coexist.
// Single-turn lifetime: set when Scout asks, resolved or dropped on the very
// next user utterance regardless of outcome (see handleQuery()) -- no
// separate expiry timer needed.
sealed class PendingCalendarFollowup {

    // Birthday or Anniversary only -- Scout doesn't yet know whose it is and
    // is waiting for the next reply to say. eventDateMs/eventAllDay are
    // carried from the real CalendarEvent that triggered the question, so the
    // eventual TruthDb write never has to re-parse a date out of speech (see
    // CalendarFollowupMatcher.canonicalMonthDay()).
    data class Clarification(
        val topic: CalendarFollowupTopic,   // BIRTHDAY or ANNIVERSARY
        val eventTitle: String,
        val eventDateMs: Long,
        val eventAllDay: Boolean,
        val askedAtMs: Long
    ) : PendingCalendarFollowup()

    // A one-off caring question ("Is everything okay?") with no data path at
    // all -- the next reply is never parsed, extracted, or stored. See
    // handleQuery()'s DoctorCheckIn branch: it's a full early return to a
    // fixed, content-blind acknowledgment, never reaching handleTeaching(),
    // ScoutIntentRouter, Gemini, or TinyLlama.
    data class DoctorCheckIn(val askedAtMs: Long) : PendingCalendarFollowup()
}
