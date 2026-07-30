package com.example.scoutface.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyNameMatcherTest {

    private fun matches(utterance: String, name: String) =
        FuzzyNameMatcher.matchesName(utterance, name)

    // --- Exact match is always preserved, regardless of name length ---

    @Test fun `exact match always matches at every length tier`() {
        assertTrue(matches("hey scout are you there", "Scout"))   // 5 letters
        assertTrue(matches("hey cat", "Cat"))                     // 3 letters
        assertTrue(matches("hey nick", "Nick"))                   // 4 letters
        assertTrue(matches("hey robbie", "Robbie"))                // 6 letters
        assertTrue(matches("hey charlie", "Charlie"))              // 7 letters
    }

    // --- 3-letter name: exact-only tier, zero fuzzy tolerance ---

    @Test fun `3-letter name gets no fuzzy tolerance`() {
        assertTrue(matches("hey cat", "Cat"))
        assertFalse(matches("hey cot", "Cat"))   // distance 1 -- must NOT match
        assertFalse(matches("hey cats", "Cat"))  // distance 1 (insertion) -- must NOT match
    }

    // --- 4-letter name: distance-1 tier ---

    @Test fun `4-letter name allows distance 1 but not 2`() {
        assertTrue(matches("hey nick", "Nick"))
        assertTrue(matches("hey nic", "Nick"))     // distance 1 (deletion)
        assertTrue(matches("hey nack", "Nick"))    // distance 1 (substitution)
        // "duck" is a real, common word -- exactly distance 2 from "nick"
        // (n->d, i->u) -- must NOT match, this is the false-positive case that
        // matters most for a short name.
        assertFalse(matches("watch out for the duck", "Nick"))
    }

    // --- 6-letter name: upper edge of the distance-1 tier ---

    @Test fun `6-letter name allows distance 1 but not 2`() {
        assertTrue(matches("hey robbie", "Robbie"))
        assertTrue(matches("hey robbi", "Robbie"))    // distance 1 (deletion)
        // "cabbie" is a real word, distance 2 from "robbie" (r->c, o->a) --
        // must NOT match.
        assertFalse(matches("call me a cabbie", "Robbie"))
    }

    // --- 7-letter name: distance-2 tier ---

    @Test fun `7-letter name allows distance 2 but not more`() {
        assertTrue(matches("hey charlie", "Charlie"))
        assertTrue(matches("hey charley", "Charlie"))   // distance 2 (i->e, e->y)
        assertFalse(matches("that was a total banana", "Charlie")) // wildly different word
    }

    // --- Whole-word only -- a substring inside a longer word must not match ---

    @Test fun `substring inside a longer word does not match`() {
        assertFalse(matches("please concatenate these files", "Cat"))
        assertFalse(matches("I'll figure it out eventually", "Scout"))
        assertFalse(matches("watch outside for the mailman", "Scout"))
    }

    // --- Blank / unsafe configured names never become fuzzy (or any) wake word ---

    @Test fun `blank configured name never matches anything`() {
        assertFalse(matches("hello are you there", ""))
        assertFalse(matches("hello are you there", "   "))
    }

    @Test fun `one-character name gets exact-only matching, not fuzzy`() {
        assertTrue(matches("hey a are you there", "A"))
        assertFalse(matches("hey at are you there", "A")) // distance 1 -- must NOT match
    }

    // --- Punctuation and capitalization normalization ---

    @Test fun `configured name is normalized regardless of stray casing or punctuation`() {
        assertTrue(matches("hey scout", " Scout! "))
        assertTrue(matches("hey scout", "SCOUT"))
        assertTrue(matches("hey scout", "Sc-out"))
    }

    // --- Names taught with trailing/leading whitespace ---

    @Test fun `configured name is trimmed before use`() {
        assertTrue(matches("hey nick", "  Nick  "))
    }

    // --- Real project history: "Scott" no longer needs a separate hardcoded entry ---

    @Test fun `Scott is caught generically for the default name Scout`() {
        assertTrue(matches("hey scott are you there", "Scout"))
    }

    // --- "Gal" is intentionally NOT covered here -- see MainActivity's own
    // explicit exception for that specific, non-generalizable mishearing.
    @Test fun `gal is not close enough to match generically`() {
        assertFalse(matches("hey gal are you there", "Scout"))
    }
}
