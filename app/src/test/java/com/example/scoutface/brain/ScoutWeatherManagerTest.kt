package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * ScoutWeatherManager itself needs a real Context/SharedPreferences/network
 * stack (no Robolectric in this project), so its dispatch/caching logic isn't
 * unit-testable in isolation. What *is* testable, and what this regression
 * actually turned on, is the exact wording of the two fallback messages used
 * whenever fresh weather cannot currently be obtained -- this locks them in
 * place and guards against a cached-data caveat ("as of...", "last weather I
 * have...") ever being reintroduced into either one.
 */
class ScoutWeatherManagerTest {

    private val allFallbackMessages = listOf(
        ScoutWeatherManager.MSG_NEED_INTERNET,
        ScoutWeatherManager.MSG_SERVICE_UNREACHABLE
    )

    // --- Exact wording ---

    @Test fun `offline message matches the desired honest wording`() {
        assertEquals("I need to be online to check the weather.", ScoutWeatherManager.MSG_NEED_INTERNET)
    }

    @Test fun `service-unreachable message matches the desired honest wording`() {
        assertEquals("I wasn't able to reach the weather service right now.", ScoutWeatherManager.MSG_SERVICE_UNREACHABLE)
    }

    // --- Neither fallback message may ever reference cached/old conditions ---

    @Test fun `no fallback message says 'as of'`() {
        allFallbackMessages.forEach { assertFalse(it, it.lowercase().contains("as of")) }
    }

    @Test fun `no fallback message references 'last weather'`() {
        allFallbackMessages.forEach { assertFalse(it, it.lowercase().contains("last weather")) }
    }

    @Test fun `no fallback message references an 'earlier day' or 'earlier check'`() {
        allFallbackMessages.forEach {
            assertFalse(it, it.lowercase().contains("earlier day"))
            assertFalse(it, it.lowercase().contains("earlier check"))
        }
    }

    @Test fun `no fallback message claims a fresh reading was obtained`() {
        // Guards against ever silently reintroducing "I couldn't get a fresh
        // reading, but..." followed by stale cached conditions.
        allFallbackMessages.forEach { assertFalse(it, it.lowercase().contains("but")) }
    }

    @Test fun `offline and service-unreachable messages are distinct`() {
        // Sanity check -- the two failure modes (no internet vs. online but
        // NWS unreachable) must not collapse into one indistinguishable message.
        org.junit.Assert.assertNotEquals(
            ScoutWeatherManager.MSG_NEED_INTERNET,
            ScoutWeatherManager.MSG_SERVICE_UNREACHABLE
        )
    }
}
