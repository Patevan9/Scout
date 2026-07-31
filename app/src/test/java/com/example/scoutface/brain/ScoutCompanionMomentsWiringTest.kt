package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the small pure helpers extracted from MainActivity's Companion
 * Moments wiring layer -- ScoutCompanionMomentsWiring.kt -- so the mechanics
 * around the decision engine (arrival-event latching, stale-result discarding,
 * session-boundary tracking, entity-aware Memory phrasing) are verified the
 * same way the engine's own scoring already is.
 */
class ScoutCompanionMomentsWiringTest {

    // ---- ScoutArrivalLatch ----

    @Test fun `consume reports nothing pending when the latch was never set`() {
        assertFalse(ScoutArrivalLatch.consume(pendingSinceMs = 0L, nowMs = 10_000L, maxAgeMs = 5_000L))
    }

    @Test fun `consume reports fresh for an event latched moments ago`() {
        assertTrue(ScoutArrivalLatch.consume(pendingSinceMs = 1_000L, nowMs = 1_500L, maxAgeMs = 5_000L))
    }

    @Test fun `consume treats the exact max age as still fresh`() {
        assertTrue(ScoutArrivalLatch.consume(pendingSinceMs = 1_000L, nowMs = 6_000L, maxAgeMs = 5_000L))
    }

    @Test fun `consume expires an event older than max age`() {
        assertFalse(ScoutArrivalLatch.consume(pendingSinceMs = 1_000L, nowMs = 6_001L, maxAgeMs = 5_000L))
    }

    // ---- ScoutStaleResultGuard ----

    @Test fun `isStale is false when the generation hasn't changed`() {
        assertFalse(ScoutStaleResultGuard.isStale(capturedGeneration = 3, currentGeneration = 3))
    }

    @Test fun `isStale is true once the generation has moved on`() {
        assertTrue(ScoutStaleResultGuard.isStale(capturedGeneration = 3, currentGeneration = 4))
    }

    // ---- ScoutPresenceStreakTracker ----

    @Test fun `a first-ever sighting starts a new streak`() {
        val update = ScoutPresenceStreakTracker.update(
            presentSinceMs = 0L, lastSeenMs = 0L, nowMs = 1_000L, gapGraceMs = 120_000L
        )
        assertTrue(update.streakRestarted)
        assertEquals(1_000L, update.newPresentSinceMs)
    }

    @Test fun `a gap beyond the grace period restarts the streak`() {
        val update = ScoutPresenceStreakTracker.update(
            presentSinceMs = 1_000L, lastSeenMs = 1_000L, nowMs = 200_000L, gapGraceMs = 120_000L
        )
        assertTrue(update.streakRestarted)
        assertEquals(200_000L, update.newPresentSinceMs)
    }

    @Test fun `a gap within the grace period continues the same streak`() {
        val update = ScoutPresenceStreakTracker.update(
            presentSinceMs = 1_000L, lastSeenMs = 50_000L, nowMs = 100_000L, gapGraceMs = 120_000L
        )
        assertFalse(update.streakRestarted)
        assertEquals(1_000L, update.newPresentSinceMs)
    }

    // ---- ScoutMemoryPhraser ----

    private val userEntity = "user_primary"
    private val scoutEntity = "scout"
    private val displayName: (String) -> String = { it.replaceFirstChar { c -> c.uppercase() } }

    @Test fun `a user_primary fact is phrased as your`() {
        assertEquals("your", ScoutMemoryPhraser.possessive("user_primary", userEntity, scoutEntity, displayName))
    }

    @Test fun `a scout fact is phrased as my`() {
        assertEquals("my", ScoutMemoryPhraser.possessive("scout", userEntity, scoutEntity, displayName))
    }

    @Test fun `a named person's fact is phrased with their own possessive name`() {
        assertEquals("Diana's", ScoutMemoryPhraser.possessive("diana", userEntity, scoutEntity, displayName))
    }

    @Test fun `a pet's fact is phrased with its own possessive name`() {
        assertEquals("Nicolas's", ScoutMemoryPhraser.possessive("nicolas", userEntity, scoutEntity, displayName))
    }

    @Test fun `a blank entity aborts rather than guessing`() {
        assertNull(ScoutMemoryPhraser.possessive("   ", userEntity, scoutEntity, displayName))
    }

    @Test fun `an entity that resolves to a blank display name aborts rather than guessing`() {
        assertNull(ScoutMemoryPhraser.possessive("ghost", userEntity, scoutEntity, displayName = { "" }))
    }

    @Test fun `buildSentence never states another person's fact as the user's own`() {
        val sentence = ScoutMemoryPhraser.buildSentence(
            intro = "I still remember you told me",
            entity = "diana",
            userEntity = userEntity,
            scoutEntity = scoutEntity,
            humanFactKey = "birthday",
            value = "November 27",
            displayName = displayName
        )
        assertEquals("I still remember you told me Diana's birthday is November 27.", sentence)
        assertFalse(sentence!!.contains("your birthday"))
    }

    @Test fun `buildSentence phrases the user's own fact as your`() {
        val sentence = ScoutMemoryPhraser.buildSentence(
            intro = "I still remember you told me",
            entity = userEntity,
            userEntity = userEntity,
            scoutEntity = scoutEntity,
            humanFactKey = "favorite color",
            value = "teal",
            displayName = displayName
        )
        assertEquals("I still remember you told me your favorite color is teal.", sentence)
    }

    @Test fun `buildSentence phrases Scout's own fact as my`() {
        val sentence = ScoutMemoryPhraser.buildSentence(
            intro = "I still remember you told me",
            entity = scoutEntity,
            userEntity = userEntity,
            scoutEntity = scoutEntity,
            humanFactKey = "name",
            value = "Scout",
            displayName = displayName
        )
        assertEquals("I still remember you told me my name is Scout.", sentence)
    }

    @Test fun `buildSentence aborts for an unresolved entity`() {
        val sentence = ScoutMemoryPhraser.buildSentence(
            intro = "I still remember you told me",
            entity = "",
            userEntity = userEntity,
            scoutEntity = scoutEntity,
            humanFactKey = "name",
            value = "Rex",
            displayName = displayName
        )
        assertNull(sentence)
    }
}
