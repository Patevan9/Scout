package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarFollowupMatcherTest {

    // --- matchTopic(): exact-match only for Birthday/Anniversary ---

    @Test fun `exact Birthday and Anniversary titles match`() {
        assertEquals(CalendarFollowupTopic.BIRTHDAY, CalendarFollowupMatcher.matchTopic("Birthday"))
        assertEquals(CalendarFollowupTopic.ANNIVERSARY, CalendarFollowupMatcher.matchTopic("Anniversary"))
    }

    @Test fun `a title that already names someone does not match -- not generic anymore`() {
        assertNull(CalendarFollowupMatcher.matchTopic("Diana's Birthday"))
        assertNull(CalendarFollowupMatcher.matchTopic("Our Anniversary"))
    }

    @Test fun `doctor titles match on the whole word, vet and dentist do not`() {
        assertEquals(CalendarFollowupTopic.DOCTOR, CalendarFollowupMatcher.matchTopic("Doctor Appointment"))
        assertEquals(CalendarFollowupTopic.DOCTOR, CalendarFollowupMatcher.matchTopic("Dr. Smith"))
        assertNull(CalendarFollowupMatcher.matchTopic("Vet Appointment"))
        assertNull(CalendarFollowupMatcher.matchTopic("Dentist"))
    }

    @Test fun `an unrelated title matches nothing`() {
        assertNull(CalendarFollowupMatcher.matchTopic("Grocery Run"))
    }

    // --- canonicalMonthDay(): all-day events must read as UTC, not local ---

    @Test fun `an all-day event's UTC-midnight timestamp resolves to the correct calendar date`() {
        val utcMidnightAug13 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026, Calendar.AUGUST, 13)
        }.timeInMillis
        val (month, day) = CalendarFollowupMatcher.canonicalMonthDay(utcMidnightAug13, allDay = true)
        assertEquals(Calendar.AUGUST, month)
        assertEquals(13, day)
    }

    @Test fun `all-day date resolution is correct even when the host default timezone has a negative UTC offset`() {
        // This is the exact bug class being guarded against: formatting a
        // UTC-midnight all-day timestamp in a negative-offset local timezone
        // rolls the date back a day. canonicalMonthDay() must not be affected
        // by whatever the host's default timezone happens to be for allDay=true.
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")) // UTC-7/UTC-8
            val utcMidnightAug13 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear(); set(2026, Calendar.AUGUST, 13)
            }.timeInMillis
            val (month, day) = CalendarFollowupMatcher.canonicalMonthDay(utcMidnightAug13, allDay = true)
            assertEquals(Calendar.AUGUST, month)
            assertEquals(13, day)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun `a timed (non all-day) event is read in local time, unchanged from existing display behavior`() {
        val localNoonAug13 = Calendar.getInstance().apply {
            clear(); set(2026, Calendar.AUGUST, 13, 12, 0, 0)
        }.timeInMillis
        val (month, day) = CalendarFollowupMatcher.canonicalMonthDay(localNoonAug13, allDay = false)
        assertEquals(Calendar.AUGUST, month)
        assertEquals(13, day)
    }

    // --- resolveClarificationReply(): known entities/relations only, never guesses ---

    // Mirrors the shape ScoutEntityResolver.buildAliasMap() actually returns --
    // each entity's own slug as an identity entry, plus any taught aliases
    // mapped to that same entity slug (e.g. "nick" -> "nicolas").
    private val knownNames = mapOf("diana" to "diana", "elijah" to "elijah")
    private val relations = mapOf("wife" to "diana", "son" to "elijah")

    @Test fun `a known name mentioned directly resolves`() {
        assertEquals(
            "elijah",
            CalendarFollowupMatcher.resolveClarificationReply("It's Elijah's.", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `my wife's resolves through the precomputed relation map, not a literal name`() {
        assertEquals(
            "diana",
            CalendarFollowupMatcher.resolveClarificationReply("It's my wife's.", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `that's my son's birthday resolves through the relation map`() {
        assertEquals(
            "elijah",
            CalendarFollowupMatcher.resolveClarificationReply("That's my son's birthday.", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `an unconfirmed relation word is never guessed`() {
        // "mom" isn't in the precomputed relations map (not a modeled/known
        // relation) -- must not be treated as resolvable.
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("That's my mom's.", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `mine and Diana's resolves to Diana for anniversary -- self is implicit, not a separate candidate`() {
        assertEquals(
            "diana",
            CalendarFollowupMatcher.resolveClarificationReply("That's mine and Diana's.", CalendarFollowupTopic.ANNIVERSARY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `bare mine alone is not enough for anniversary -- must not default to the current wife`() {
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("It's mine.", CalendarFollowupTopic.ANNIVERSARY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `bare mine alone resolves to self for birthday`() {
        assertEquals(
            "user_primary",
            CalendarFollowupMatcher.resolveClarificationReply("Mine.", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `a question-shaped reply is never treated as an answer`() {
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("Whose is it?", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `two distinct known names in one reply is ambiguous, not guessed`() {
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("Diana and Elijah", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `self plus a named person is self-contradictory for a single birthday and is rejected`() {
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("Mine and Diana's", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    @Test fun `an unrecognized reply with no known name or relation is unresolved`() {
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("I don't remember.", CalendarFollowupTopic.BIRTHDAY, knownNames, relations, "user_primary")
        )
    }

    // A taught alias/nickname ("nick" -> "nicolas") must resolve through its
    // entity, the same way the canonical slug would -- regression test for a
    // bug where the caller collapsed the alias map down to just its distinct
    // entity-slug values before calling in here, silently losing every alias
    // key ("nick", "nicky") and leaving only "nicolas" recognizable.
    private val aliasedNames = mapOf("nicolas" to "nicolas", "nick" to "nicolas", "nicky" to "nicolas")

    @Test fun `a taught nickname resolves to the entity it's an alias for, not just the canonical name`() {
        assertEquals(
            "nicolas",
            CalendarFollowupMatcher.resolveClarificationReply("That's Nick's birthday.", CalendarFollowupTopic.BIRTHDAY, aliasedNames, relations, "user_primary")
        )
    }

    @Test fun `a second taught alias for the same entity also resolves`() {
        assertEquals(
            "nicolas",
            CalendarFollowupMatcher.resolveClarificationReply("It's Nicky's.", CalendarFollowupTopic.BIRTHDAY, aliasedNames, relations, "user_primary")
        )
    }

    @Test fun `two distinct aliases for two different entities in one reply is still ambiguous`() {
        val twoEntityAliases = mapOf("nicolas" to "nicolas", "nick" to "nicolas", "diana" to "diana")
        assertNull(
            CalendarFollowupMatcher.resolveClarificationReply("Nick and Diana", CalendarFollowupTopic.BIRTHDAY, twoEntityAliases, relations, "user_primary")
        )
    }

    // --- describeKnownOwner(): speak already-known ownership, never guess ---

    @Test fun `a single known birthday owner is spoken by name`() {
        val owners = listOf(DateOwnerMatch("elijah", "birthday", null))
        assertEquals(
            "That's Elijah's birthday.",
            CalendarFollowupMatcher.describeKnownOwner(owners, CalendarFollowupTopic.BIRTHDAY, "user_primary")
        )
    }

    @Test fun `a single known birthday owner who is the primary user is spoken as your birthday`() {
        val owners = listOf(DateOwnerMatch("user_primary", "birthday", null))
        assertEquals(
            "That's your birthday.",
            CalendarFollowupMatcher.describeKnownOwner(owners, CalendarFollowupTopic.BIRTHDAY, "user_primary")
        )
    }

    @Test fun `two people sharing a birthday on the same date is ambiguous -- no name is guessed`() {
        val owners = listOf(
            DateOwnerMatch("elijah", "birthday", null),
            DateOwnerMatch("diana", "birthday", null)
        )
        assertNull(CalendarFollowupMatcher.describeKnownOwner(owners, CalendarFollowupTopic.BIRTHDAY, "user_primary"))
    }

    @Test fun `a known anniversary participant is spoken by name`() {
        val owners = listOf(DateOwnerMatch("user_primary", "anniversary_with_diana", "diana"))
        assertEquals(
            "That's your anniversary with Diana.",
            CalendarFollowupMatcher.describeKnownOwner(owners, CalendarFollowupTopic.ANNIVERSARY, "user_primary")
        )
    }

    @Test fun `a bare legacy anniversary key with no recorded participant names no one`() {
        val owners = listOf(DateOwnerMatch("user_primary", "anniversary", null))
        assertNull(CalendarFollowupMatcher.describeKnownOwner(owners, CalendarFollowupTopic.ANNIVERSARY, "user_primary"))
    }

    @Test fun `two different anniversary participants on the same date is ambiguous -- no name is guessed`() {
        val owners = listOf(
            DateOwnerMatch("user_primary", "anniversary_with_diana", "diana"),
            DateOwnerMatch("user_primary", "anniversary_with_susan", "susan")
        )
        assertNull(CalendarFollowupMatcher.describeKnownOwner(owners, CalendarFollowupTopic.ANNIVERSARY, "user_primary"))
    }

    // --- findDateOwners(): compare-time canonicalization, format-agnostic ---

    @Test fun `matches a value stored in either November 27th or Nov 27 style`() {
        val facts = listOf(
            Triple("diana", "birthday", "November 27th"),
            Triple("elijah", "birthday", "Nov 27")
        )
        val matches = CalendarFollowupMatcher.findDateOwners(facts, "birthday", Calendar.NOVEMBER, 27)
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.entity == "diana" })
        assertTrue(matches.any { it.entity == "elijah" })
    }

    @Test fun `a slash-date value is a disclosed gap -- CalendarDateParser does not parse it, so it is not matched`() {
        val facts = listOf(Triple("diana", "birthday", "11/27"))
        val matches = CalendarFollowupMatcher.findDateOwners(facts, "birthday", Calendar.NOVEMBER, 27)
        assertTrue(matches.isEmpty())
    }

    @Test fun `matches the bare legacy anniversary key with no participant`() {
        val facts = listOf(Triple("user_primary", "anniversary", "August 13"))
        val matches = CalendarFollowupMatcher.findDateOwners(facts, "anniversary", Calendar.AUGUST, 13)
        assertEquals(1, matches.size)
        assertEquals("user_primary", matches[0].entity)
        assertNull(matches[0].participantSlug)
    }

    @Test fun `matches a compound anniversary_with_diana key and extracts the participant`() {
        val facts = listOf(Triple("user_primary", "anniversary_with_diana", "August 13"))
        val matches = CalendarFollowupMatcher.findDateOwners(facts, "anniversary", Calendar.AUGUST, 13)
        assertEquals(1, matches.size)
        assertEquals("diana", matches[0].participantSlug)
    }

    @Test fun `no match on a different date returns empty`() {
        val facts = listOf(Triple("diana", "birthday", "November 27th"))
        assertTrue(CalendarFollowupMatcher.findDateOwners(facts, "birthday", Calendar.DECEMBER, 1).isEmpty())
    }

    @Test fun `an unrelated fact key is never mistaken for a birthday or anniversary`() {
        val facts = listOf(Triple("diana", "favorite_color", "August 13")) // coincidentally date-shaped value
        assertTrue(CalendarFollowupMatcher.findDateOwners(facts, "birthday", Calendar.AUGUST, 13).isEmpty())
    }
}
