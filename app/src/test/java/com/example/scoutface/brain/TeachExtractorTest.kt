package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TeachExtractor had no dedicated unit tests before this file. Added
 * alongside the fix for a real-device finding: the FLEXIBLE fallback's
 * generic "my <label> is <value>" pattern used to auto-prepend "favorite_"
 * to any label that didn't already start with the literal word "favorite" --
 * so "my son is Elijah" silently became favorite_son = "Elijah" instead of
 * son_name = "Elijah", and "my mentor is Sam" became favorite_mentor = "Sam"
 * even though Scout has no such concept and the user never said "favorite".
 *
 * Fix, two parts:
 *   1. The FLEXIBLE fallback no longer invents a "favorite" meaning at all --
 *      it stores rawLabel exactly as spoken via FactKey.custom(). A
 *      favorite_<label> key is created ONLY when the spoken label itself
 *      literally begins with "favorite" (e.g. "my favorite color is teal").
 *      No preference-category allowlist needed: handleRecallIntent() (see
 *      MainActivity) already tries a plain, unprefixed key as a read-side
 *      fallback, so a plain key is already fully supported.
 *   2. Bare "my wife/son/dog is X" (no "'s name is", no "this is") now match
 *      the existing dedicated WIFE_NAME/SON_NAME/DOG_NAME blocks directly,
 *      guarded by the same NON_NAME_WORDS check the other bare "is X"
 *      patterns already use, so "my wife is happy" doesn't register "Happy"
 *      as her name.
 */
class TeachExtractorTest {

    // --- Tier 2: bare wife/son/dog introductions use their dedicated name keys ---

    @Test fun `bare my wife is X extracts WIFE_NAME`() {
        assertEquals(FactKey.WIFE_NAME to "Diana", TeachExtractor.extract("My wife is Diana."))
    }

    @Test fun `bare my son is X extracts SON_NAME`() {
        assertEquals(FactKey.SON_NAME to "Elijah", TeachExtractor.extract("My son is Elijah."))
    }

    @Test fun `bare my dog is X extracts DOG_NAME`() {
        assertEquals(FactKey.DOG_NAME to "Nicolas", TeachExtractor.extract("My dog is Nicolas."))
    }

    // --- Tier 2 guard: a mood/trait word after "is" is never mistaken for a name ---

    @Test fun `my wife is happy does not register Happy as her name`() {
        val result = TeachExtractor.extract("My wife is happy.")
        assertEquals(false, result?.first == FactKey.WIFE_NAME)
    }

    @Test fun `my son is tired does not register Tired as his name`() {
        val result = TeachExtractor.extract("My son is tired.")
        assertEquals(false, result?.first == FactKey.SON_NAME)
    }

    @Test fun `my dog is hungry does not register Hungry as its name`() {
        val result = TeachExtractor.extract("My dog is hungry.")
        assertEquals(false, result?.first == FactKey.DOG_NAME)
    }

    // --- Tier 1: relationship/person introductions never create favorite_<relation> ---

    @Test fun `my daughter is Sarah stores a plain daughter key, not favorite_daughter`() {
        assertEquals("daughter" to "Sarah", TeachExtractor.extract("My daughter is Sarah."))
    }

    @Test fun `my friend is Janice stores a plain friend key, not favorite_friend`() {
        assertEquals("friend" to "Janice", TeachExtractor.extract("My friend is Janice."))
    }

    @Test fun `my coworker is Bob stores a plain coworker key, not favorite_coworker`() {
        assertEquals("coworker" to "Bob", TeachExtractor.extract("My coworker is Bob."))
    }

    @Test fun `adversarial -- my mentor is Sam stores a plain mentor key, not favorite_mentor`() {
        // The exact case a category allowlist would have missed: "mentor" is
        // not a modeled relationship word at all, so a denylist approach
        // would have let this slip through as favorite_mentor. Dropping the
        // auto-prefix entirely (rather than gating it on a vocabulary)
        // closes this regardless of what word follows "my".
        assertEquals("mentor" to "Sam", TeachExtractor.extract("My mentor is Sam."))
    }

    // --- Explicit favorite statements still create favorite_* ---

    @Test fun `my favorite color is blue still creates favorite_color`() {
        assertEquals("favorite_color" to "Blue", TeachExtractor.extract("My favorite color is blue."))
    }

    @Test fun `my favorite food is pizza still creates favorite_food`() {
        assertEquals("favorite_food" to "Pizza", TeachExtractor.extract("My favorite food is pizza."))
    }

    @Test fun `my favorite team is the Dolphins still creates favorite_team`() {
        assertEquals("favorite_team" to "The Dolphins", TeachExtractor.extract("My favorite team is the Dolphins."))
    }

    // --- Saying "favorite" is now the ONLY way to create a favorite_ key --
    // no category allowlist, so the same category word without "favorite"
    // stores a plain key instead. ---

    @Test fun `my color is blue (no favorite) stores a plain color key`() {
        assertEquals("color" to "Blue", TeachExtractor.extract("My color is blue."))
    }

    @Test fun `my food is pizza (no favorite) stores a plain food key`() {
        assertEquals("food" to "Pizza", TeachExtractor.extract("My food is pizza."))
    }

    // --- Regression: existing dedicated teaching forms are unchanged ---

    @Test fun `wife's-name-is phrasing is unchanged`() {
        assertEquals(FactKey.WIFE_NAME to "Diana", TeachExtractor.extract("My wife's name is Diana."))
    }

    @Test fun `son's-name-is phrasing is unchanged`() {
        assertEquals(FactKey.SON_NAME to "Elijah", TeachExtractor.extract("My son's name is Elijah."))
    }

    @Test fun `dog's-name-is phrasing is unchanged`() {
        assertEquals(FactKey.DOG_NAME to "Nicolas", TeachExtractor.extract("My dog's name is Nicolas."))
    }

    @Test fun `dog-is-named phrasing is unchanged`() {
        assertEquals(FactKey.DOG_NAME to "Nicolas", TeachExtractor.extract("My dog is named Nicolas."))
    }

    @Test fun `this-is-my-wife phrasing is unchanged`() {
        assertEquals(FactKey.WIFE_NAME to "Diana", TeachExtractor.extract("This is my wife Diana."))
    }

    @Test fun `this-is-my-son phrasing is unchanged`() {
        assertEquals(FactKey.SON_NAME to "Elijah", TeachExtractor.extract("This is my son Elijah."))
    }

    @Test fun `this-is-my-dog phrasing is unchanged`() {
        assertEquals(FactKey.DOG_NAME to "Nicolas", TeachExtractor.extract("This is my dog Nicolas."))
    }

    @Test fun `my name is phrasing is unchanged`() {
        assertEquals(FactKey.NAME to "Patrick", TeachExtractor.extract("My name is Patrick."))
    }

    @Test fun `my birthday is phrasing is unchanged`() {
        assertEquals(FactKey.custom("birthday") to "January 27th", TeachExtractor.extract("My birthday is January 27th."))
    }

    @Test fun `generic X-'s-name-is phrasing for an unmodeled relation is unchanged`() {
        assertEquals(FactKey.custom("daughter_name") to "Sarah", TeachExtractor.extract("My daughter's name is Sarah."))
    }

    @Test fun `a question extracts nothing`() {
        assertNull(TeachExtractor.extract("What is my dog's name?"))
    }
}
