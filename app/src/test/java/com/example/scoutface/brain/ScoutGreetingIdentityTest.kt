package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoutGreetingIdentityTest {

    private val threshold = 0.72f

    @Test fun `null name returns null`() {
        assertNull(ScoutGreetingIdentity.resolveSpeakableName(null, 0.9f, threshold))
    }

    @Test fun `blank name returns null`() {
        assertNull(ScoutGreetingIdentity.resolveSpeakableName("", 0.9f, threshold))
    }

    @Test fun `whitespace-only name returns null`() {
        assertNull(ScoutGreetingIdentity.resolveSpeakableName("   ", 0.9f, threshold))
    }

    @Test fun `null score returns null`() {
        assertNull(ScoutGreetingIdentity.resolveSpeakableName("Patrick", null, threshold))
    }

    @Test fun `score below threshold returns null`() {
        assertNull(ScoutGreetingIdentity.resolveSpeakableName("Patrick", 0.71f, threshold))
    }

    @Test fun `score exactly at threshold returns the name -- boundary inclusive`() {
        assertEquals("Patrick", ScoutGreetingIdentity.resolveSpeakableName("Patrick", 0.72f, threshold))
    }

    @Test fun `score above threshold returns the name`() {
        assertEquals("Patrick", ScoutGreetingIdentity.resolveSpeakableName("Patrick", 0.95f, threshold))
    }

    @Test fun `a name that PeopleDb already accepted (0_65) but below the stricter spoken bar still returns null`() {
        // Demonstrates the whole point of this helper: PeopleDb's own base
        // threshold (0.65) is looser than the stricter bar a SPOKEN claim
        // requires -- a match PeopleDb itself would accept for silent
        // internal use is not automatically safe to speak out loud.
        assertNull(ScoutGreetingIdentity.resolveSpeakableName("Patrick", 0.68f, threshold))
    }

    @Test fun `a different confidenceThreshold is respected, not a hardcoded 0_72`() {
        assertEquals("Diana", ScoutGreetingIdentity.resolveSpeakableName("Diana", 0.5f, confidenceThreshold = 0.4f))
        assertNull(ScoutGreetingIdentity.resolveSpeakableName("Diana", 0.5f, confidenceThreshold = 0.6f))
    }
}
