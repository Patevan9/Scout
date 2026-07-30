package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutFactExtractorTest {

    private val known = setOf("diana", "nicolas", "nick")

    // --- Word-order independence for date facts (Patrick's exact examples) ---

    @Test fun `subject possessive fact is value`() {
        val facts = ScoutFactExtractor.extract("Diana's birthday is November 27", known)
        assertEquals(1, facts.size)
        assertEquals(ScoutFactExtractor.Fact("diana", "birthday", "November 27"), facts[0])
    }

    @Test fun `value first ordering still extracts the same fact`() {
        val facts = ScoutFactExtractor.extract("November 27 is Diana's birthday", known)
        assertEquals(1, facts.size)
        assertEquals("diana", facts[0].subject)
        assertEquals("birthday", facts[0].property)
        assertEquals("November 27", facts[0].value)
    }

    @Test fun `born-on phrasing extracts the same fact as birthday-is phrasing`() {
        val facts = ScoutFactExtractor.extract("Diana was born on November 27", known)
        assertEquals(1, facts.size)
        assertEquals("diana", facts[0].subject)
        assertEquals("birthday", facts[0].property)
        assertEquals("November 27", facts[0].value)
    }

    @Test fun `remember prefix does not block extraction`() {
        val facts = ScoutFactExtractor.extract("Remember Diana's birthday is November 27", known)
        assertEquals(1, facts.size)
        assertEquals("November 27", facts[0].value)
    }

    @Test fun `a date with a day suffix and no year still parses`() {
        val facts = ScoutFactExtractor.extract("Diana's birthday is November 27th", known)
        assertEquals("November 27th", facts[0].value)
    }

    // --- Relation-word subject (no bare name involved yet) ---

    @Test fun `my wife's birthday is extracted with a relation subject`() {
        val facts = ScoutFactExtractor.extract("my wife's birthday is November 27", emptySet())
        assertEquals(1, facts.size)
        assertEquals("my wife", facts[0].subject)
        assertEquals("birthday", facts[0].property)
    }

    // --- Nickname clause, including the real on-device STT mishearing ---

    @Test fun `call him extracts a nickname`() {
        assertEquals("Nick", ScoutFactExtractor.extractNicknameClause("we call him Nick"))
    }

    @Test fun `can him is treated as a mishearing of call him`() {
        // Confirmed from an actual on-device transcript: "but we can him Nick" was
        // what speech-to-text produced for a spoken "call him Nick."
        assertEquals("Nick", ScoutFactExtractor.extractNicknameClause(
            "my dog's name is Nicolas, but we can him Nick"))
    }

    @Test fun `nickname clause combines with a birthday fact in the same sentence`() {
        val facts = ScoutFactExtractor.extract(
            "Diana's birthday is November 27, and we call her Di", known + "di")
        assertTrue(facts.any { it.property == "birthday" && it.value == "November 27" })
        assertTrue(facts.any { it.property == "nickname" && it.value == "Di" })
        assertTrue(facts.all { it.subject == "diana" })
    }

    // --- Generic possessive fallback for properties other than birthday/nickname ---

    @Test fun `generic possessive fact extracts an arbitrary property`() {
        val facts = ScoutFactExtractor.extract("Diana's favorite food is sushi", known)
        assertEquals(1, facts.size)
        assertEquals("favorite_food", facts[0].property)
        assertEquals("Sushi", facts[0].value)
    }

    @Test fun `birthday is not double-extracted via the generic fallback`() {
        val facts = ScoutFactExtractor.extract("Diana's birthday is November 27", known)
        assertEquals(1, facts.count { it.property == "birthday" })
    }

    // --- Questions must never be treated as teaching ---

    @Test fun `a direct question extracts nothing`() {
        assertTrue(ScoutFactExtractor.extract("What was Diana's birthday?", known).isEmpty())
        assertTrue(ScoutFactExtractor.extract("Is Diana's birthday November 27?", known).isEmpty())
    }

    @Test fun `unknown subject with no relation word extracts nothing`() {
        assertTrue(ScoutFactExtractor.extract("Steve called earlier", emptySet()).isEmpty())
    }

    // --- Safety net: unparsed-but-clearly-a-statement about a known entity ---

    @Test fun `unrecognized teaching about a known entity is flagged`() {
        assertTrue(ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "Don't forget Diana's birthday", known))
    }

    @Test fun `questions are never flagged as unrecognized teaching`() {
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "What was Diana's birthday?", known))
    }

    @Test fun `ordinary chit-chat with no entity or hint word is not flagged`() {
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "it is a beautiful day outside", known))
    }
}
