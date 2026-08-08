package com.example.scoutface.brain

import com.example.scoutface.IntentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutBusyBrainPolicyTest {

    // --- Approved read-only/conversational intents ---

    @Test fun `time date and language are safe while a generation is pending`() {
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.TIME))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.DATE))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.LANGUAGE))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.TIME_OF_DAY))
    }

    @Test fun `connectivity weather and calendar are safe while a generation is pending`() {
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.CONNECTIVITY))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.WEATHER))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.CALENDAR))
    }

    @Test fun `vision and identity questions are safe while a generation is pending`() {
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.VISION))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.IDENTITY))
    }

    @Test fun `personal-memory recall questions are safe while a generation is pending`() {
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.ASK_SCOUT_NAME))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.ASK_MY_NAME))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.ASK_WIFE_NAME))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.ASK_SON_NAME))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.ASK_DOG_NAME))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.FAMILY_NAMES))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.RECALL_FACT))
    }

    @Test fun `conversational courtesy is safe while a generation is pending`() {
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.GREET))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.HOW_ARE_YOU))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.PRAISE))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.AFFECTION))
    }

    @Test fun `explicit conversation-ending stays available while a generation is pending`() {
        // The control mechanism PR 1's explicit-close discard depends on
        // being reachable through a normal conversation, not just the
        // stuck-generation watchdog's edge case.
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.GOODBYE))
        assertTrue(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.STOP_LISTENING))
    }

    // --- Blocked: state-changing / navigation / not yet approved ---

    @Test fun `going online or offline is blocked while a generation is pending`() {
        assertFalse(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.GO_ONLINE))
        assertFalse(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.GO_OFFLINE))
    }

    @Test fun `exporting the brain is blocked while a generation is pending`() {
        assertFalse(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.EXPORT_BRAIN))
    }

    @Test fun `opening calendar settings is blocked while a generation is pending`() {
        assertFalse(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.OPEN_CALENDAR_SETTINGS))
    }

    // --- UNKNOWN is not this function's concern ---

    @Test fun `unknown is not classified safe -- routed to its own dedicated handling instead`() {
        assertFalse(ScoutBusyBrainPolicy.isSafeWhilePending(IntentType.UNKNOWN))
    }
}
