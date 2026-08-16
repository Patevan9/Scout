package com.example.scoutface.brain

import com.example.scoutface.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // --- Real-device regression: "who is a part of my family" and close
    // relatives fell through to TinyLlama, which hallucinated family members
    // TruthDb never had. FAMILY_NAMES's phrasing was widened to cover these. ---

    @Test fun `who is a part of my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("who is a part of my family"))
    }

    @Test fun `who is part of my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("who is part of my family"))
    }

    @Test fun `who are the members of my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("who are the members of my family"))
    }

    @Test fun `tell me about my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("tell me about my family"))
    }

    @Test fun `who do you know in my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("who do you know in my family"))
    }

    @Test fun `what do you know about my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("what do you know about my family"))
    }

    @Test fun `family summary phrasings never fall through to UNKNOWN`() {
        val phrasings = listOf(
            "who is a part of my family", "who is part of my family",
            "who are the members of my family", "tell me about my family",
            "who do you know in my family", "what do you know about my family"
        )
        phrasings.forEach {
            assertNotEquals("UNKNOWN for: $it", IntentType.UNKNOWN, ScoutIntentRouter.route(it))
        }
    }

    @Test fun `wife name question still routes to ASK_WIFE_NAME, not swept into the widened FAMILY_NAMES check`() {
        assertEquals(IntentType.ASK_WIFE_NAME, ScoutIntentRouter.route("what is my wife's name"))
    }

    @Test fun `turn on the calendar still matches with the optional the`() {
        assertEquals(IntentType.OPEN_CALENDAR_SETTINGS, ScoutIntentRouter.route("turn on the calendar"))
    }

    @Test fun `bare calendar mention still falls through to the calendar catch-all`() {
        assertEquals(IntentType.CALENDAR, ScoutIntentRouter.route("do I need my calendar today"))
    }

    // --- Calendar Follow-up: deterministic "whose birthday/anniversary is
    // [date]" recall, checked before RECALL_FACT even though it also names a
    // date -- must never fall through to CALENDAR or the generative fallback. ---

    @Test fun `whose anniversary is on a date routes to WHOSE_DATE_EVENT`() {
        assertEquals(IntentType.WHOSE_DATE_EVENT, ScoutIntentRouter.route("whose anniversary is on august 13th"))
    }

    @Test fun `whose birthday is on a date routes to WHOSE_DATE_EVENT`() {
        assertEquals(IntentType.WHOSE_DATE_EVENT, ScoutIntentRouter.route("whose birthday is august 13th"))
    }

    @Test fun `whose birthday with no parseable date does not route to WHOSE_DATE_EVENT`() {
        assertNotEquals(IntentType.WHOSE_DATE_EVENT, ScoutIntentRouter.route("whose birthday is it"))
    }

    @Test fun `existing calendar and recall-fact phrasings are unaffected by the new WHOSE_DATE_EVENT branch`() {
        assertEquals(IntentType.CALENDAR, ScoutIntentRouter.route("do I need my calendar today"))
        assertEquals(IntentType.RECALL_FACT, ScoutIntentRouter.route("what is my birthday"))
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

    // --- New DATE phrasing (router gap fix) ---

    @Test fun `what day is today now routes to DATE`() {
        assertEquals(IntentType.DATE, ScoutIntentRouter.route("what day is today"))
    }

    @Test fun `what day is it now routes to DATE`() {
        assertEquals(IntentType.DATE, ScoutIntentRouter.route("what day is it"))
    }

    // --- DATE regression: existing phrasing routes exactly as before ---

    @Test fun `what date is it still routes to DATE`() {
        assertEquals(IntentType.DATE, ScoutIntentRouter.route("what date is it"))
    }

    @Test fun `what is today still routes to DATE`() {
        assertEquals(IntentType.DATE, ScoutIntentRouter.route("what is today"))
    }

    @Test fun `bare date still routes to DATE`() {
        assertEquals(IntentType.DATE, ScoutIntentRouter.route("date"))
    }

    // --- New ASK_SCOUT_NAME phrasing (router gap fix) ---

    @Test fun `what should i call you now routes to ASK_SCOUT_NAME`() {
        assertEquals(IntentType.ASK_SCOUT_NAME, ScoutIntentRouter.route("what should i call you"))
    }

    @Test fun `what do i call you now routes to ASK_SCOUT_NAME`() {
        assertEquals(IntentType.ASK_SCOUT_NAME, ScoutIntentRouter.route("what do i call you"))
    }

    // --- ASK_SCOUT_NAME regression: existing phrasing routes exactly as before ---

    @Test fun `what is your name still routes to ASK_SCOUT_NAME`() {
        assertEquals(IntentType.ASK_SCOUT_NAME, ScoutIntentRouter.route("what is your name"))
    }

    @Test fun `who are you still routes to ASK_SCOUT_NAME`() {
        assertEquals(IntentType.ASK_SCOUT_NAME, ScoutIntentRouter.route("who are you"))
    }

    // --- New LANGUAGE intent ---

    @Test fun `what language are we speaking routes to LANGUAGE`() {
        assertEquals(IntentType.LANGUAGE, ScoutIntentRouter.route("what language are we speaking"))
    }

    @Test fun `what language do you speak routes to LANGUAGE`() {
        assertEquals(IntentType.LANGUAGE, ScoutIntentRouter.route("what language do you speak"))
    }

    @Test fun `what language are you speaking routes to LANGUAGE`() {
        assertEquals(IntentType.LANGUAGE, ScoutIntentRouter.route("what language are you speaking"))
    }

    // --- New TIME_OF_DAY intent ---

    @Test fun `is it morning or night routes to TIME_OF_DAY`() {
        assertEquals(IntentType.TIME_OF_DAY, ScoutIntentRouter.route("is it morning or night"))
    }

    @Test fun `what time of day is it routes to TIME_OF_DAY`() {
        assertEquals(IntentType.TIME_OF_DAY, ScoutIntentRouter.route("what time of day is it"))
    }

    // --- TIME_OF_DAY does not collide with the existing clock-time TIME intent ---

    @Test fun `what time is it still routes to TIME, not TIME_OF_DAY`() {
        assertEquals(IntentType.TIME, ScoutIntentRouter.route("what time is it"))
    }

    // --- Better Conversation State Phase 1: new GOODBYE-family phrasing ---

    @Test fun `that's all routes to GOODBYE`() {
        assertEquals(IntentType.GOODBYE, ScoutIntentRouter.route("that's all"))
    }

    @Test fun `that will be all routes to GOODBYE`() {
        assertEquals(IntentType.GOODBYE, ScoutIntentRouter.route("that will be all"))
    }

    // --- GOODBYE regression: existing phrasing routes exactly as before ---

    @Test fun `goodbye still routes to GOODBYE`() {
        assertEquals(IntentType.GOODBYE, ScoutIntentRouter.route("goodbye"))
    }

    @Test fun `bare bye still routes to GOODBYE`() {
        assertEquals(IntentType.GOODBYE, ScoutIntentRouter.route("bye"))
    }

    @Test fun `see you later still routes to GOODBYE`() {
        assertEquals(IntentType.GOODBYE, ScoutIntentRouter.route("see you later"))
    }

    // --- New STOP_LISTENING intent ---

    @Test fun `stop listening routes to STOP_LISTENING`() {
        assertEquals(IntentType.STOP_LISTENING, ScoutIntentRouter.route("stop listening"))
    }

    @Test fun `you can stop listening routes to STOP_LISTENING`() {
        assertEquals(IntentType.STOP_LISTENING, ScoutIntentRouter.route("you can stop listening"))
    }

    @Test fun `stop listening does not route to GOODBYE`() {
        assertEquals(IntentType.STOP_LISTENING, ScoutIntentRouter.route("scout stop listening"))
    }

    // --- New memory-capability routing: "can you learn?" etc. now route to
    // IDENTITY for a deterministic, TruthDb-grounded answer instead of falling
    // through to UNKNOWN and reaching Gemini/TinyLlama. See
    // ScoutFactExtractor.looksLikeMemoryCapabilityQuestion() for the detection
    // logic and its false-positive guards. ---

    @Test fun `can you learn routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("Can you learn?"))
    }

    @Test fun `do you have a memory routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("Do you have a memory?"))
    }

    @Test fun `are you capable of remembering routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("Are you capable of remembering?"))
    }

    // A specific referent right after "remember" is a real recall/task
    // question, not a capability question, and must keep routing normally.

    @Test fun `do you remember my birthday still routes to RECALL_FACT, not IDENTITY`() {
        assertEquals(IntentType.RECALL_FACT, ScoutIntentRouter.route("do you remember my birthday"))
    }

    @Test fun `can you remember to grab milk does not route to IDENTITY`() {
        assertNotEquals(IntentType.IDENTITY, ScoutIntentRouter.route("Can you remember to grab milk?"))
    }

    @Test fun `do you remember the movie does not route to IDENTITY`() {
        assertNotEquals(IntentType.IDENTITY, ScoutIntentRouter.route("Do you remember the movie?"))
    }
}
