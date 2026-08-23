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

    // ---- ScoutReturnGreetingGate ----
    // Real-device finding: Companion Moments could win the race to speak
    // before a stabilizing return greeting, then suppress it outright via the
    // shared proactive-remark cooldown. This gate blocks Companion Moments for
    // exactly the stabilization window, no more, no less.

    @Test fun `not stabilizing when no absence was ever marked`() {
        assertFalse(ScoutReturnGreetingGate.isStabilizing(
            genuineAbsenceMarked = false, returnStabilizingSinceMs = 0L))
    }

    @Test fun `not stabilizing when an absence was marked but no return has been detected yet`() {
        // genuineAbsenceMarked can be true while the person is still away --
        // returnStabilizingSinceMs only starts once a face reappears.
        assertFalse(ScoutReturnGreetingGate.isStabilizing(
            genuineAbsenceMarked = true, returnStabilizingSinceMs = 0L))
    }

    @Test fun `stabilizing once a genuine return is detected and the stabilization timer has started`() {
        assertTrue(ScoutReturnGreetingGate.isStabilizing(
            genuineAbsenceMarked = true, returnStabilizingSinceMs = 12_345L))
    }

    @Test fun `not stabilizing once the return greeting has fired and reset both fields`() {
        // Mirrors maybeMakeReturnGreeting()'s own reset of genuineAbsenceMarked
        // and returnStabilizingSinceMs after it actually speaks.
        assertFalse(ScoutReturnGreetingGate.isStabilizing(
            genuineAbsenceMarked = false, returnStabilizingSinceMs = 0L))
    }

    @Test fun `defensive -- a nonzero timestamp alone without a marked absence is not treated as stabilizing`() {
        // Shouldn't be reachable in practice (returnStabilizingSinceMs is only
        // ever set inside the genuineAbsenceMarked branch), but the gate
        // requires both explicitly rather than inferring one from the other.
        assertFalse(ScoutReturnGreetingGate.isStabilizing(
            genuineAbsenceMarked = false, returnStabilizingSinceMs = 12_345L))
    }

    // ---- ScoutPostBootQuietGate ----
    // Real-device finding (Galaxy A32 and Galaxy Fold 7, different memory
    // content on each): the instant the boot/greeting TTS finishes, every
    // other Companion Moment gate is already satisfied, so the very next
    // camera-analysis frame could immediately follow Scout's own greeting with
    // an unrelated spontaneous "I remember..." Companion Moment. This gate
    // adds a short quiet period measured only from the boot greeting finishing.

    @Test fun `not quiet when boot hasn't finished speaking yet`() {
        // bootFinishedSpeakingAtMs == 0L is the not-yet-booted state -- that
        // stays owned entirely by the existing, separate bootFinishedSpeaking
        // check, so this gate must never independently report it as quiet.
        assertFalse(ScoutPostBootQuietGate.isQuiet(
            bootFinishedSpeakingAtMs = 0L, nowMs = 10_000L, quietMs = 300_000L))
    }

    @Test fun `quiet immediately after the boot greeting finishes`() {
        assertTrue(ScoutPostBootQuietGate.isQuiet(
            bootFinishedSpeakingAtMs = 1_000L, nowMs = 1_050L, quietMs = 300_000L))
    }

    @Test fun `still quiet just before the quiet period elapses`() {
        assertTrue(ScoutPostBootQuietGate.isQuiet(
            bootFinishedSpeakingAtMs = 1_000L, nowMs = 1_000L + 300_000L - 1L, quietMs = 300_000L))
    }

    @Test fun `eligible again exactly at the quiet-period boundary`() {
        assertFalse(ScoutPostBootQuietGate.isQuiet(
            bootFinishedSpeakingAtMs = 1_000L, nowMs = 1_000L + 300_000L, quietMs = 300_000L))
    }

    @Test fun `eligible well after the quiet period has elapsed`() {
        assertFalse(ScoutPostBootQuietGate.isQuiet(
            bootFinishedSpeakingAtMs = 1_000L, nowMs = 1_000L + 600_000L, quietMs = 300_000L))
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

    // ---- ScoutCompanionMemoryEligibility ----
    // Real-device findings: bootstrap/identity facts and person-ranking
    // favorite_<relation-word> facts must never be offered as a Companion
    // Moment memory. (ENTITY_SCOUT exclusion itself is a separate,
    // entity-level filter applied by the caller -- MainActivity -- before any
    // fact key reaches this eligibility check, so it isn't re-tested here.)

    @Test fun `bootstrap identity keys are not eligible`() {
        assertFalse(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("name"))
        assertFalse(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("aliases"))
    }

    @Test fun `favorite son is not eligible -- the confirmed real-device finding`() {
        // Traced root cause: TeachExtractor's generic fallback mislabels "my son
        // is Elijah" as favorite_son = "Elijah" instead of son_name = "Elijah".
        // Rendered honestly by ScoutMemoryPhraser, that becomes "...your
        // favorite son is Elijah" -- a person-ranking statement this filter
        // exists to stop, independent of how the key was created.
        assertFalse(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("favorite_son"))
    }

    @Test fun `favorite relation-word keys are not eligible across the whole vocabulary`() {
        val riskyKeys = listOf(
            "favorite_son", "favorite_daughter", "favorite_child", "favorite_kid",
            "favorite_wife", "favorite_husband", "favorite_dog", "favorite_cat", "favorite_pet",
            "favorite_friend", "favorite_coworker", "favorite_neighbor", "favorite_sister",
            "favorite_brother", "favorite_cousin", "favorite_teacher", "favorite_boss"
        )
        riskyKeys.forEach {
            assertFalse("expected $it to be ineligible", ScoutCompanionMemoryEligibility.isCompanionMemoryEligible(it))
        }
    }

    @Test fun `ordinary object and activity preferences remain eligible`() {
        val safeKeys = listOf(
            "favorite_color", "favorite_food", "favorite_movie", "favorite_show",
            "favorite_place", "favorite_team", "favorite_animal", "favorite_hobby"
        )
        safeKeys.forEach {
            assertTrue("expected $it to remain eligible", ScoutCompanionMemoryEligibility.isCompanionMemoryEligible(it))
        }
    }

    @Test fun `dedicated relationship-name keys remain eligible -- only the favorite_ prefix shape is excluded`() {
        // "Nicolas is your dog" (dog_name) and "your anniversary with Diana is
        // August 13th" (anniversary) are exactly the kind of meaningful memory
        // this feature should still be able to surface.
        assertTrue(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("dog_name"))
        assertTrue(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("wife_name"))
        assertTrue(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("son_name"))
        assertTrue(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("birthday"))
        assertTrue(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("anniversary"))
    }

    @Test fun `a bare relation word with no favorite_ prefix is unaffected`() {
        // "son_name" (above) is the real key TeachExtractor should have used;
        // a literal bare "son" key isn't one this codebase actually produces,
        // but confirms the filter only excludes the favorite_ shape, not the
        // word "son" appearing anywhere in a key.
        assertTrue(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("son"))
    }

    @Test fun `matching is case-insensitive`() {
        assertFalse(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("Favorite_Son"))
        assertFalse(ScoutCompanionMemoryEligibility.isCompanionMemoryEligible("NAME"))
    }
}
