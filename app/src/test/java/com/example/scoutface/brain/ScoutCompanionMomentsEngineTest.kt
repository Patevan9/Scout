package com.example.scoutface.brain

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutCompanionMomentsEngineTest {

    private val UTC = ZoneOffset.UTC

    private fun baseSignals(
        nowMs: Long = 10_000_000L,
        situationalGuardPassed: Boolean = true,
        msSinceLastProactiveRemark: Long = ScoutCompanionMomentsEngine.SHARED_PROACTIVE_COOLDOWN_MS + 1,
        proactiveMomentsFiredToday: Int = 0,
        secondFaceJustAppeared: Boolean = false,
        staleTaughtFacts: List<StaleFact> = emptyList(),
        habitObservationContentKey: String? = null,
        conversationTurnRateElevated: Boolean = false,
        continuousPresenceMs: Long = 0L,
        hasHadConversationThisSession: Boolean = true,
        msSinceLastCuriosityMoment: Long? = 0L,
        isFirstCuriosityOpportunityToday: Boolean = false,
        presentPersonLastSeenMs: Long? = nowMs, // seen "now" -- not a first meeting by default
        lastFiredMsByContentKey: Map<String, Long> = emptyMap(),
        lastFiredMsByCategory: Map<MomentCategory, Long> = emptyMap()
    ) = CompanionSignals(
        nowMs = nowMs,
        situationalGuardPassed = situationalGuardPassed,
        msSinceLastProactiveRemark = msSinceLastProactiveRemark,
        proactiveMomentsFiredToday = proactiveMomentsFiredToday,
        secondFaceJustAppeared = secondFaceJustAppeared,
        staleTaughtFacts = staleTaughtFacts,
        habitObservationContentKey = habitObservationContentKey,
        conversationTurnRateElevated = conversationTurnRateElevated,
        continuousPresenceMs = continuousPresenceMs,
        hasHadConversationThisSession = hasHadConversationThisSession,
        msSinceLastCuriosityMoment = msSinceLastCuriosityMoment,
        isFirstCuriosityOpportunityToday = isFirstCuriosityOpportunityToday,
        presentPersonLastSeenMs = presentPersonLastSeenMs,
        zoneId = UTC,
        lastFiredMsByContentKey = lastFiredMsByContentKey,
        lastFiredMsByCategory = lastFiredMsByCategory
    )

    // --- Hard gates always override confidence, no exceptions ---

    @Test fun `situational guard failing overrides an otherwise high-confidence candidate`() {
        val signals = baseSignals(situationalGuardPassed = false, secondFaceJustAppeared = true)
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `shared cooldown not yet elapsed overrides an otherwise high-confidence candidate`() {
        val signals = baseSignals(msSinceLastProactiveRemark = 0L, secondFaceJustAppeared = true)
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `daily budget exhausted overrides an otherwise high-confidence candidate`() {
        val signals = baseSignals(
            proactiveMomentsFiredToday = ScoutCompanionMomentsEngine.DAILY_MOMENT_BUDGET,
            secondFaceJustAppeared = true
        )
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    // --- Per-category cooldown: a second, independent hard gate ---

    @Test fun `a category still inside its own cooldown does not fire even at high confidence`() {
        val nowMs = 10_000_000L
        val stillOnCooldown = nowMs - (ScoutCompanionMomentsEngine.ENVIRONMENT_CATEGORY_COOLDOWN_MS - 1)
        val signals = baseSignals(
            nowMs = nowMs,
            secondFaceJustAppeared = true,
            lastFiredMsByCategory = mapOf(MomentCategory.ENVIRONMENT to stillOnCooldown)
        )
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `a category fires again once its own cooldown has elapsed`() {
        val nowMs = 10_000_000L
        val cooldownElapsed = nowMs - (ScoutCompanionMomentsEngine.ENVIRONMENT_CATEGORY_COOLDOWN_MS + 1)
        val signals = baseSignals(
            nowMs = nowMs,
            secondFaceJustAppeared = true,
            lastFiredMsByCategory = mapOf(MomentCategory.ENVIRONMENT to cooldownElapsed)
        )
        val winner = ScoutCompanionMomentsEngine.evaluate(signals)
        assertEquals(MomentCategory.ENVIRONMENT, winner?.category)
    }

    @Test fun `category cooldowns are independent -- one category on cooldown does not block another`() {
        val nowMs = 10_000_000L
        val environmentStillOnCooldown = nowMs - (ScoutCompanionMomentsEngine.ENVIRONMENT_CATEGORY_COOLDOWN_MS - 1)
        val signals = baseSignals(
            nowMs = nowMs,
            secondFaceJustAppeared = true, // would fire, but ENVIRONMENT is on cooldown
            staleTaughtFacts = listOf(StaleFact("memory:user_primary:favorite_color", ScoutCompanionMomentsEngine.MEMORY_MIN_DAYS_SINCE_SURFACE)),
            lastFiredMsByCategory = mapOf(MomentCategory.ENVIRONMENT to environmentStillOnCooldown)
        )
        val winner = ScoutCompanionMomentsEngine.evaluate(signals)
        assertEquals(MomentCategory.MEMORY, winner?.category)
    }

    // --- A presence greeting (the shared clock) suppresses an immediate companion moment ---

    @Test fun `a presence remark that just fired suppresses an immediate companion moment`() {
        // Presence stamps the same shared clock this engine reads -- "just spoke" means
        // msSinceLastProactiveRemark is small, regardless of which system spoke.
        val signals = baseSignals(msSinceLastProactiveRemark = 5_000L, secondFaceJustAppeared = true)
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    // --- No candidate clearing the threshold returns null ---

    @Test fun `an under-threshold candidate does not fire even though one was generated`() {
        // Observation's activity-only path scores 0.35, below the 0.50 threshold.
        val signals = baseSignals(conversationTurnRateElevated = true)
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `no triggering signals at all yields null`() {
        assertNull(ScoutCompanionMomentsEngine.evaluate(baseSignals()))
    }

    // --- Additive composition, capped at 1.0 ---

    @Test fun `composeScore adds bonuses rather than multiplying them`() {
        assertEquals(0.4f, ScoutCompanionMomentsEngine.composeScore(0.3f, 0.1f), 0.0001f)
    }

    @Test fun `composeScore caps the total at MAX_CONFIDENCE`() {
        val uncapped = ScoutCompanionMomentsEngine.composeScore(0.9f, 0.5f, 0.5f) // would be 1.9 uncapped
        assertEquals(ScoutCompanionMomentsEngine.MAX_CONFIDENCE, uncapped, 0.0001f)
    }

    @Test fun `composeScore never goes below zero`() {
        assertEquals(0f, ScoutCompanionMomentsEngine.composeScore(-0.5f), 0.0001f)
    }

    // --- Deterministic tie-breaking ---

    @Test fun `tie-break stage 1 -- more time-sensitive category wins on equal confidence`() {
        val environment = MomentCandidate(MomentCategory.ENVIRONMENT, "environment:new_arrival", emptyList(), emptyList(), 0.55f)
        val curiosity = MomentCandidate(MomentCategory.CURIOSITY, "curiosity:light_question", emptyList(), emptyList(), 0.55f)
        val winner = ScoutCompanionMomentsEngine.selectWinner(listOf(curiosity, environment), emptyMap())
        assertEquals(MomentCategory.ENVIRONMENT, winner?.category)
    }

    @Test fun `tie-break stage 2 -- least recently used content wins when time-sensitivity also ties`() {
        // MEMORY and OBSERVATION share a time-sensitivity rank, so this exercises stage 2.
        val memory = MomentCandidate(MomentCategory.MEMORY, "memory:user_primary:favorite_color", emptyList(), emptyList(), 0.55f)
        val observation = MomentCandidate(MomentCategory.OBSERVATION, "observation:habit:coffee", emptyList(), emptyList(), 0.55f)
        val lastFired = mapOf(
            "memory:user_primary:favorite_color" to 9_000_000L, // fired recently
            "observation:habit:coffee" to 1_000_000L            // fired long ago -- more overdue
        )
        val winner = ScoutCompanionMomentsEngine.selectWinner(listOf(memory, observation), lastFired)
        assertEquals(MomentCategory.OBSERVATION, winner?.category)
    }

    @Test fun `tie-break stage 2 -- content that has never fired is treated as most overdue`() {
        val memory = MomentCandidate(MomentCategory.MEMORY, "memory:user_primary:favorite_color", emptyList(), emptyList(), 0.55f)
        val observation = MomentCandidate(MomentCategory.OBSERVATION, "observation:habit:coffee", emptyList(), emptyList(), 0.55f)
        // Only observation has ever fired -- memory (missing from the map) should win.
        val lastFired = mapOf("observation:habit:coffee" to 1_000L)
        val winner = ScoutCompanionMomentsEngine.selectWinner(listOf(memory, observation), lastFired)
        assertEquals(MomentCategory.MEMORY, winner?.category)
    }

    @Test fun `tie-break stage 3 -- fixed category order is the final deterministic fallback`() {
        val memory = MomentCandidate(MomentCategory.MEMORY, "memory:user_primary:favorite_color", emptyList(), emptyList(), 0.55f)
        val observation = MomentCandidate(MomentCategory.OBSERVATION, "observation:habit:coffee", emptyList(), emptyList(), 0.55f)
        // Equal confidence, equal time-sensitivity rank, and identical last-fired times --
        // only the fixed fallback order can break this tie. Memory precedes Observation in it.
        val lastFired = mapOf(
            "memory:user_primary:favorite_color" to 5_000L,
            "observation:habit:coffee" to 5_000L
        )
        val winner = ScoutCompanionMomentsEngine.selectWinner(listOf(observation, memory), lastFired)
        assertEquals(MomentCategory.MEMORY, winner?.category)
    }

    @Test fun `selectWinner returns null for an empty candidate list`() {
        assertNull(ScoutCompanionMomentsEngine.selectWinner(emptyList(), emptyMap()))
    }

    // --- Curiosity cannot fire without its own required grounding ---

    @Test fun `curiosity generates no candidate at all below the minimum presence duration`() {
        val signals = baseSignals(
            continuousPresenceMs = ScoutCompanionMomentsEngine.CURIOSITY_MIN_PRESENCE_MS - 1,
            hasHadConversationThisSession = false,
            isFirstCuriosityOpportunityToday = true,
            msSinceLastCuriosityMoment = null
        )
        // Even with every bonus condition true, insufficient presence means no eligibility at all.
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `curiosity base score alone cannot clear the threshold`() {
        val signals = baseSignals(
            continuousPresenceMs = ScoutCompanionMomentsEngine.CURIOSITY_MIN_PRESENCE_MS,
            hasHadConversationThisSession = true,
            isFirstCuriosityOpportunityToday = false,
            msSinceLastCuriosityMoment = 0L
        )
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `curiosity clears the threshold through its own grounded bonuses, not a lowered bar`() {
        val signals = baseSignals(
            continuousPresenceMs = ScoutCompanionMomentsEngine.CURIOSITY_MIN_PRESENCE_MS,
            hasHadConversationThisSession = false, // +0.15
            isFirstCuriosityOpportunityToday = true, // +0.10
            msSinceLastCuriosityMoment = 0L
        )
        val winner = ScoutCompanionMomentsEngine.evaluate(signals)
        assertEquals(MomentCategory.CURIOSITY, winner?.category)
        assertTrue((winner?.confidence ?: 0f) >= ScoutCompanionMomentsEngine.MOMENT_CONFIDENCE_THRESHOLD)
    }

    // --- Memory eligibility floor ---

    @Test fun `a fact surfaced too recently is not eligible for a memory moment`() {
        val signals = baseSignals(
            staleTaughtFacts = listOf(StaleFact("memory:user_primary:favorite_color", ScoutCompanionMomentsEngine.MEMORY_MIN_DAYS_SINCE_SURFACE - 1))
        )
        assertNull(ScoutCompanionMomentsEngine.evaluate(signals))
    }

    @Test fun `a sufficiently stale fact produces a memory moment`() {
        val signals = baseSignals(
            staleTaughtFacts = listOf(StaleFact("memory:user_primary:favorite_color", ScoutCompanionMomentsEngine.MEMORY_MIN_DAYS_SINCE_SURFACE))
        )
        val winner = ScoutCompanionMomentsEngine.evaluate(signals)
        assertEquals(MomentCategory.MEMORY, winner?.category)
    }

    // --- Environment ---

    @Test fun `a new arrival while another person is present produces an environment moment`() {
        val signals = baseSignals(secondFaceJustAppeared = true)
        val winner = ScoutCompanionMomentsEngine.evaluate(signals)
        assertEquals(MomentCategory.ENVIRONMENT, winner?.category)
    }

    // --- First-meeting-today only contributes when the calendar date boundary truly changed ---

    @Test fun `isFirstMeetingToday is false for two timestamps on the same calendar day`() {
        // 2023-11-14T09:00:00Z and 2023-11-14T17:00:00Z -- 8 hours apart, same UTC date.
        val morning = 1_699_952_400_000L
        val sameDayLater = 1_699_981_200_000L
        assertFalse(ScoutCompanionMomentsEngine.isFirstMeetingToday(morning, sameDayLater, UTC))
    }

    @Test fun `isFirstMeetingToday is true once the calendar day has actually rolled over, even with little elapsed time`() {
        // 2023-11-14T23:00:00Z and 2023-11-15T01:00:00Z -- only 2 hours apart, but the
        // UTC date changed. This is the case that specifically proves the check is
        // calendar-date-based, not an elapsed-time threshold in disguise.
        val lateNight = 1_700_002_800_000L
        val justAfterMidnight = 1_700_010_000_000L
        assertTrue(ScoutCompanionMomentsEngine.isFirstMeetingToday(lateNight, justAfterMidnight, UTC))
    }

    @Test fun `isFirstMeetingToday is true when the person has never been seen before`() {
        assertTrue(ScoutCompanionMomentsEngine.isFirstMeetingToday(null, 1_700_000_000_000L, UTC))
    }

    @Test fun `first-meeting-today bonus is absent within the same calendar day`() {
        val nowMs = 1_700_000_000_000L
        val seenEarlierToday = nowMs - 60_000L
        val signals = baseSignals(
            nowMs = nowMs,
            staleTaughtFacts = listOf(StaleFact("memory:user_primary:favorite_color", ScoutCompanionMomentsEngine.MEMORY_MIN_DAYS_SINCE_SURFACE)),
            presentPersonLastSeenMs = seenEarlierToday
        )
        val withoutBonus = ScoutCompanionMomentsEngine.evaluate(signals)!!.confidence

        val signalsCrossedMidnight = signals.copy(presentPersonLastSeenMs = nowMs - 25L * 60L * 60L * 1_000L)
        val withBonus = ScoutCompanionMomentsEngine.evaluate(signalsCrossedMidnight)!!.confidence

        assertTrue(withBonus > withoutBonus)
        assertEquals(ScoutCompanionMomentsEngine.FIRST_MEETING_TODAY_BONUS, withBonus - withoutBonus, 0.0001f)
    }
}
