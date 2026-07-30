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

    // --- "you"/"your" addressing Scout directly is a self-reference too --
    // a personal-memory question can be about the speaker or about what
    // Scout himself holds/remembers, and both need the gate to catch them.

    @Test fun `what do you remember about me -- self plus topic, both directions`() {
        assertTrue(gate("what do you remember about me"))
    }

    @Test fun `what have I taught you -- self plus topic, addressed at Scout`() {
        assertTrue(gate("what have I taught you"))
    }

    // --- "you" alone, without a genuine topic word, must not over-trigger --
    // "you" is an extremely common word, so ordinary Scout-directed commands
    // that happen to contain it must still fall through to their normal
    // handling rather than being swept into the memory gate.

    @Test fun `do you know the weather -- you present but no topic word`() {
        assertFalse(gate("do you know the weather"))
    }

    @Test fun `can you set a timer -- you present but no topic word`() {
        assertFalse(gate("can you set a timer"))
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

    // --- Aliases (nicknames stored under a named entity, not the user) ---

    @Test fun `an alias fact is recognized the same way a name fact is`() {
        val withAlias = listOf("alias" to "Nick")
        assertTrue(gate("where is Nick", withAlias))
        assertFalse(gate("I need a mechanic", withAlias))
    }

    // TruthDb actually stores nicknames under the plural "aliases" key, as one
    // comma-joined value (see TruthDb.addAlias()/getAliases()) -- this is the real
    // shape "an alias fact" above doesn't cover, and was the actual reported bug:
    // a nickname-only query like "when is Nick's birthday" fell through this gate
    // because "aliases" wasn't recognized as name-like at all.
    @Test fun `an aliases fact (plural, comma-joined) matches each nickname individually`() {
        val withAliases = listOf("aliases" to "Nick, Nicky")
        assertTrue(gate("where is Nick", withAliases))
        assertTrue(gate("where is Nicky", withAliases))
        assertFalse(gate("I need a mechanic", withAliases))
    }
}
