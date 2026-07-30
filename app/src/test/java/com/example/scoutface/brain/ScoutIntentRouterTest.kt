package com.example.scoutface.brain

import com.example.scoutface.IntentType
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoutIntentRouterTest {

    // --- Existing wording, unchanged behavior (regression) ---

    @Test fun `single-relation wife question still routes directly`() {
        assertEquals(IntentType.ASK_WIFE_NAME, ScoutIntentRouter.route("what is my wife's name"))
    }

    @Test fun `single-relation son question still routes directly`() {
        assertEquals(IntentType.ASK_SON_NAME, ScoutIntentRouter.route("who is my son"))
    }

    @Test fun `single-relation dog question still routes directly`() {
        assertEquals(IntentType.ASK_DOG_NAME, ScoutIntentRouter.route("what's my dog's name"))
    }

    @Test fun `family names summary phrasing still routes directly`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("who's in my family"))
    }

    @Test fun `turn on the calendar still matches with the optional the`() {
        assertEquals(IntentType.OPEN_CALENDAR_SETTINGS, ScoutIntentRouter.route("turn on the calendar"))
    }

    @Test fun `bare calendar mention still falls through to the calendar catch-all`() {
        assertEquals(IntentType.CALENDAR, ScoutIntentRouter.route("do I need my calendar today"))
    }

    // --- Compound relation questions no longer short-circuit on the first match ---

    @Test fun `wife and son mentioned together no longer resolves to just the wife`() {
        // Before the compound guard, "wife" matched first and this returned
        // ASK_WIFE_NAME, silently dropping the son half of the question.
        assertEquals(IntentType.UNKNOWN, ScoutIntentRouter.route("who is my wife and son"))
    }

    @Test fun `wife and dog mentioned together falls through instead of picking one`() {
        assertEquals(IntentType.UNKNOWN, ScoutIntentRouter.route("what are my wife and dog's names"))
    }

    @Test fun `son and dog mentioned together falls through instead of picking one`() {
        assertEquals(IntentType.UNKNOWN, ScoutIntentRouter.route("tell me about my son and my dog"))
    }

    @Test fun `all three relations mentioned together falls through`() {
        assertEquals(IntentType.UNKNOWN, ScoutIntentRouter.route("what are my wife, son, and dog's names"))
    }

    // --- Natural variations that were never in the fixed phrase list ---

    @Test fun `tell me about my household is not a recognized fixed phrase`() {
        // No router pattern matches this -- confirms it falls to UNKNOWN, where
        // ScoutMemoryGate (not ScoutIntentRouter) is responsible for catching it.
        assertEquals(IntentType.UNKNOWN, ScoutIntentRouter.route("tell me about my household"))
    }

    @Test fun `mentioning a name with no relation keyword is not routed here either`() {
        assertEquals(IntentType.UNKNOWN, ScoutIntentRouter.route("tell me about Diana"))
    }
}
