package com.example.scoutface.brain

import java.util.Calendar
import java.util.TimeZone

// (entity, factKey, value) triple -- same shape MainActivity.getAllKnownFacts()
// already returns, reused as-is rather than introducing a new TruthDb-facing
// type. participantSlug is non-null only for a compound "<prefix>_with_<slug>"
// key (see findDateOwners()); null for the bare legacy "birthday"/"anniversary"
// key, meaning the date is known but who it belongs to/is with is not.
data class DateOwnerMatch(val entity: String, val factKey: String, val participantSlug: String?)

// Pure calendar-follow-up logic -- title matching, reply resolution, date-
// safe UTC/local derivation, and the durable-fact reverse lookup. No Android,
// no TruthDb, no context: MainActivity supplies already-fetched facts/known
// entities as plain data, same pattern ScoutMemoryGate and ScoutFactExtractor
// already use, so this stays testable in the plain JVM unit test suite.
object CalendarFollowupMatcher {

    private val QUESTION_LEAD_WORDS = setOf(
        "what", "who", "when", "where", "why", "how",
        "do", "does", "did", "is", "are", "can", "could", "will", "would"
    )

    // Exact match only for Birthday/Anniversary -- a title that already names
    // someone ("Diana's Birthday") isn't generic anymore and asking "whose
    // birthday is that?" would be a redundant, dumber-feeling question. Doctor
    // is a whole-word match since real titles vary ("Doctor Appointment",
    // "Dr. Smith") -- deliberately not a broad "appointment" catch-all, since
    // "vet appointment"/"school appointment" have nothing medical about them.
    fun matchTopic(title: String): CalendarFollowupTopic? {
        val t = title.trim().lowercase()
        return when {
            t == "birthday" -> CalendarFollowupTopic.BIRTHDAY
            t == "anniversary" -> CalendarFollowupTopic.ANNIVERSARY
            Regex("""\b(doctor|dr)\b""").containsMatchIn(t) -> CalendarFollowupTopic.DOCTOR
            else -> null
        }
    }

    // Android's CalendarContract stores an all-day event's start as UTC
    // midnight of the intended date, meant to be read back in UTC -- not the
    // device's local timezone. Formatting that raw millis value in any
    // negative-UTC-offset timezone (all of the continental US) would roll the
    // date back into the previous day. Timed events are real instants and are
    // correctly read in local time, as the rest of this app's calendar
    // display code already does.
    fun canonicalMonthDay(startMs: Long, allDay: Boolean): Pair<Int, Int> {
        val cal = Calendar.getInstance(if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault())
        cal.timeInMillis = startMs
        return cal.get(Calendar.MONTH) to cal.get(Calendar.DAY_OF_MONTH)
    }

    // Resolves a reply to a pending Clarification against Scout's already-known
    // entities and relations only -- never invents a relationship or guesses a
    // date. [aliasMap] is every name/alias Scout currently recognizes, mapped
    // to the entity slug its facts live under (ScoutEntityResolver.buildAliasMap()
    // itself, not just its values) -- so a taught nickname ("nick") resolves to
    // its entity ("nicolas") the same way the canonical slug would, instead of
    // being silently dropped. [resolvedRelations] is a small precomputed map
    // (e.g. "wife" -> "diana") of only the relation words that resolved to a
    // genuinely known entity -- reusing ScoutEntityResolver's existing
    // wife/son/dog relationship resolution rather than a separate name-only
    // understanding system. A relation word with no confirmed entry is simply
    // absent, never guessed.
    //
    // ANNIVERSARY is inherently self-inclusive (it's always "my" anniversary),
    // so a bare "mine"/"my own" alone is deliberately NOT enough to resolve --
    // it doesn't say with whom, and defaulting to "whoever the current wife
    // is" would silently reintroduce exactly the unstated assumption this
    // feature is built to avoid. It needs exactly one other resolved
    // participant. BIRTHDAY has no such structural expectation: exactly one
    // resolved entity (self OR one known name/relation) is required; zero or
    // multiple distinct candidates (including a self+other mix, which is
    // self-contradictory for a single birthday) is treated as unresolved.
    fun resolveClarificationReply(
        reply: String,
        topic: CalendarFollowupTopic,
        aliasMap: Map<String, String>,
        resolvedRelations: Map<String, String>,
        selfEntity: String
    ): String? {
        val s = reply.trim().lowercase()
        if (looksLikeQuestion(s)) return null

        val candidates = mutableSetOf<String>()
        val mentionsSelf = Regex("""\b(mine|my own)\b""").containsMatchIn(s)

        for ((alias, entity) in aliasMap) {
            if (Regex("""\b${Regex.escape(alias)}\b""").containsMatchIn(s)) candidates.add(entity)
        }
        for ((rel, entity) in resolvedRelations) {
            if (Regex("""\bmy\s+${Regex.escape(rel)}\b""").containsMatchIn(s)) candidates.add(entity)
        }

        return when (topic) {
            CalendarFollowupTopic.ANNIVERSARY -> candidates.singleOrNull()
            CalendarFollowupTopic.BIRTHDAY -> when {
                candidates.size == 1 && !mentionsSelf -> candidates.single()
                candidates.isEmpty() && mentionsSelf -> selfEntity
                else -> null
            }
            CalendarFollowupTopic.DOCTOR -> null // unreachable -- never called for DOCTOR
        }
    }

    // When TruthDb already knows who a generic Birthday/Anniversary event
    // belongs to, Scout should say so instead of only suppressing the "whose
    // is that?" follow-up question. [owners] is findDateOwners()'s result for
    // this event's own date -- already date-filtered, never a broader scan.
    // Returns null (no clause added, caller falls back to the plain base
    // answer) whenever ownership isn't a single clear name: zero owners (the
    // "already known" caller never reaches here), a bare legacy key with no
    // recorded participant (the date is known but who it belongs to isn't),
    // or more than one distinct entity/participant (e.g. two people share a
    // birthday) -- never guessed.
    fun describeKnownOwner(owners: List<DateOwnerMatch>, topic: CalendarFollowupTopic, selfEntity: String): String? =
        when (topic) {
            CalendarFollowupTopic.BIRTHDAY -> {
                val entity = owners.map { it.entity }.toSet().singleOrNull()
                when {
                    entity == null -> null
                    entity == selfEntity -> "That's your birthday."
                    else -> "That's ${slugDisplayName(entity)}'s birthday."
                }
            }
            CalendarFollowupTopic.ANNIVERSARY -> {
                val participant = owners.mapNotNull { it.participantSlug }.toSet().singleOrNull()
                if (participant == null) null else "That's your anniversary with ${slugDisplayName(participant)}."
            }
            CalendarFollowupTopic.DOCTOR -> null // unreachable -- findDateOwners() is never called for DOCTOR
        }

    // Local copy of ScoutEntityResolver.displayName()'s capitalization, not a
    // call to it -- that file imports TruthDb, and this file is deliberately
    // Android/TruthDb-free (see class doc above) so it stays testable in the
    // plain JVM unit test suite without pulling Android in transitively.
    private fun slugDisplayName(entity: String): String =
        entity.trim().split(" ", "_").filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    private fun looksLikeQuestion(s: String): Boolean {
        if (s.endsWith("?")) return true
        return s.substringBefore(' ') in QUESTION_LEAD_WORDS
    }

    // Reverse lookup for "whose birthday/anniversary is [date]" -- matches the
    // bare legacy key (e.g. a fact written by TeachExtractor's "our
    // anniversary is X" path, with no participant recorded) or any compound
    // "<prefix>_with_<slug>" key (see MainActivity.tryResolveCalendarClarification()).
    // Compares month/day only, never a raw stored string -- both the stored
    // value and the query date are independently re-parsed through
    // CalendarDateParser, so this matches regardless of which free-text
    // format ("November 27th", "Nov 27", ...) the fact happened to be taught
    // in. One disclosed, narrow gap: CalendarDateParser deliberately doesn't
    // parse slash dates ("11/27"), so a fact stored in that rare form (only
    // ever possible via ScoutFactExtractor's older, separate date pattern)
    // won't be found here -- unlikely for a voice-only app, not silently
    // hidden.
    fun findDateOwners(
        facts: List<Triple<String, String, String>>,
        factKeyPrefix: String,
        targetMonth: Int,
        targetDay: Int
    ): List<DateOwnerMatch> =
        facts.filter { (_, key, _) -> key == factKeyPrefix || key.startsWith("${factKeyPrefix}_with_") }
            .mapNotNull { (entity, key, value) ->
                val parsed = CalendarDateParser.parseDate(value) ?: return@mapNotNull null
                if (parsed.get(Calendar.MONTH) == targetMonth && parsed.get(Calendar.DAY_OF_MONTH) == targetDay) {
                    DateOwnerMatch(entity, key, key.removePrefix("${factKeyPrefix}_with_").takeIf { it != key })
                } else null
            }
}
