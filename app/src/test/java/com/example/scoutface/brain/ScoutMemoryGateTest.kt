package com.example.scoutface.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutMemoryGateTest {

    private val facts = listOf(
        "name" to "Patrick",
        "wife_name" to "Diana",
        "son_name" to "Elijah",
        "dog_name" to "Nick"
    )

    private fun gate(q: String, knownFacts: List<Pair<String, String>> = facts) =
        ScoutMemoryGate.isPossiblePersonalMemoryQuery(q.lowercase().trim(), knownFacts)

    // --- Self word + topic word combination (the original, narrower design) ---

    @Test fun `what is my wife's name -- self plus topic`() {
        assertTrue(gate("what is my wife's name"))
    }

    @Test fun `what did you learn today -- self plus topic`() {
        assertTrue(gate("what did you learn today"))
    }

    @Test fun `unrelated question with no self or topic word is not personal`() {
        assertFalse(gate("what time is it"))
        assertFalse(gate("what is the weather like"))
    }

    // --- "me"/"i"/"us" must be whole words, not substrings (regression) ---

    @Test fun `time and remember must not falsely trigger self word via substring`() {
        // "time" contains "me", "remember" contains "me" -- neither should count
        // as a self-reference on its own without a real self word present.
        assertFalse(gate("what time is it"))
    }

    @Test fun `wifi and hi must not falsely trigger the bare i self word`() {
        assertFalse(gate("is the wifi working"))
        assertFalse(gate("hi scout"))
    }

    // --- Patrick's own natural-phrasing examples ---

    @Test fun `tell me about Diana -- known name mention, no self word needed`() {
        assertTrue(gate("Tell me about Diana"))
    }

    @Test fun `what do you know about Elijah -- known name mention`() {
        assertTrue(gate("What do you know about Elijah?"))
    }

    @Test fun `what was Nick's birthday -- known name plus possessive`() {
        assertTrue(gate("What was Nick's birthday?"))
    }

    @Test fun `who lives with us -- household phrasing without a topic keyword`() {
        assertTrue(gate("Who lives with us?"))
    }

    @Test fun `who lives with me -- singular household phrasing`() {
        assertTrue(gate("Who lives with me?"))
    }

    // --- A name that isn't known shouldn't false-positive on its own ---

    @Test fun `mentioning an unknown name alone is not personal`() {
        assertFalse(gate("did you see the news about Steve"))
    }

    // --- Known name matched as a whole word, not a substring ---

    @Test fun `a known name must match as a whole word, not inside another word`() {
        val withNick = listOf("dog_name" to "Nick")
        assertFalse(gate("I need a mechanic", withNick))
        assertTrue(gate("where is Nick", withNick))
    }

    @Test fun `empty known facts still allows the self plus topic path`() {
        assertTrue(gate("what is my favorite color", knownFacts = emptyList()))
        assertFalse(gate("tell me about Diana", knownFacts = emptyList()))
    }
}
