package com.example.scoutface.brain

import com.example.scoutface.IntentType

/**
 * Busy-Brain -- PR 2. Pure classification of which deterministic intents are
 * safe to answer while a Gemini/TinyLlama generation is still pending, and
 * which must wait for it to finish.
 *
 * Phase 1 is deliberately conservative: only read-only/conversational
 * intents are approved. Anything that changes state, navigates to another
 * screen, or writes memory stays blocked until the pending generation
 * resolves -- widen this list later, after real-device testing, not now.
 *
 * IntentType.UNKNOWN is deliberately not classified safe or unsafe here --
 * a second AI-style question is handled by its own, more specific arbitration
 * (see MainActivity's busyBrainState.isPending checks in handleUnknownIntent()
 * / handlePersonalMemoryQuery(), which already existed before this class was
 * added in PR 1), not by this generic policy.
 */
object ScoutBusyBrainPolicy {

    private val SAFE_WHILE_PENDING = setOf(
        // Explicitly approved read-only/conversational intents.
        IntentType.TIME, IntentType.DATE, IntentType.LANGUAGE, IntentType.TIME_OF_DAY,
        IntentType.CONNECTIVITY, IntentType.WEATHER, IntentType.CALENDAR, IntentType.VISION,
        IntentType.IDENTITY, IntentType.ASK_SCOUT_NAME, IntentType.ASK_MY_NAME,
        IntentType.ASK_WIFE_NAME, IntentType.ASK_SON_NAME, IntentType.ASK_DOG_NAME,
        IntentType.FAMILY_NAMES, IntentType.RECALL_FACT,
        // "Normal conversational courtesy where appropriate" -- canned-phrase
        // replies, no state change, no memory write.
        IntentType.GREET, IntentType.HOW_ARE_YOU, IntentType.PRAISE, IntentType.AFFECTION,
        // Explicit conversation-ending must stay available regardless of a
        // pending generation -- it's the control mechanism the explicit-close
        // discard behavior (PR 1's ScoutBusyBrainState.discard()) depends on
        // ever being reachable through a normal conversation, not just the
        // stuck-generation watchdog's edge case.
        IntentType.GOODBYE, IntentType.STOP_LISTENING
    )

    /**
     * True if [intent] may be answered immediately even while a generation is
     * pending. False for state-changing/navigation/memory-write intents
     * (GO_ONLINE, GO_OFFLINE, EXPORT_BRAIN, OPEN_CALENDAR_SETTINGS, and any
     * future intent not explicitly approved above) and for UNKNOWN, which the
     * caller is expected to route to its own dedicated handling instead of
     * consulting this function at all.
     */
    fun isSafeWhilePending(intent: IntentType): Boolean = intent in SAFE_WHILE_PENDING
}
