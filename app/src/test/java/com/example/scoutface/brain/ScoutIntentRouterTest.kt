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

    // --- New FAMILY_NAMES routing: natural family-roster phrasings that
    // don't match any single relation word. Real-device finding: these fell
    // through to UNKNOWN, reached TinyLlama with only a flat fact dump, and
    // produced a hallucinated generic answer ("some popular names for
    // families include..."). handleFamilyNamesQuery() is fully deterministic. ---

    @Test fun `what names do you know in my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("What names do you know in my family?"))
    }

    @Test fun `who in my family do you know routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("Who in my family do you know?"))
    }

    @Test fun `tell me the names of my family routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("Tell me the names of my family"))
    }

    @Test fun `which family members do you know routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("Which family members do you know?"))
    }

    // False positives: ordinary mentions of "family" that aren't a roster
    // request must not be swept into FAMILY_NAMES.

    @Test fun `what is my family doing this weekend does not route to FAMILY_NAMES`() {
        assertNotEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("What is my family doing this weekend?"))
    }

    @Test fun `what's a good family name for a dog does not route to FAMILY_NAMES`() {
        assertNotEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("What's a good family name for a dog?"))
    }

    @Test fun `do you know if my family is coming over does not route to FAMILY_NAMES`() {
        assertNotEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("Do you know if my family is coming over?"))
    }

    @Test fun `is my family okay does not route to FAMILY_NAMES`() {
        assertNotEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("Is my family okay?"))
    }

    // --- New VISION routing: natural phrasings that don't match the existing
    // "what/can you see"/"look around"/"describe" checks. Real-device
    // finding: "Do you see anything?" and "Who's/What's in front of you?"
    // fell through to UNKNOWN, with no vision signal in ScoutMemoryGate's
    // vocabulary, reached fact-blind Gemini directly, and produced a false
    // "I don't have a camera" reply. ---

    @Test fun `do you see anything routes to VISION`() {
        assertEquals(IntentType.VISION, ScoutIntentRouter.route("Do you see anything?"))
    }

    @Test fun `who's in front of you routes to VISION`() {
        assertEquals(IntentType.VISION, ScoutIntentRouter.route("Who's in front of you?"))
    }

    @Test fun `can you see anyone routes to VISION`() {
        assertEquals(IntentType.VISION, ScoutIntentRouter.route("Can you see anyone?"))
    }

    @Test fun `what's in front of you routes to VISION`() {
        assertEquals(IntentType.VISION, ScoutIntentRouter.route("What's in front of you?"))
    }

    // --- VISION regression: existing phrasing routes exactly as before ---

    @Test fun `what do you see still routes to VISION`() {
        assertEquals(IntentType.VISION, ScoutIntentRouter.route("What do you see?"))
    }

    @Test fun `look around still routes to VISION`() {
        assertEquals(IntentType.VISION, ScoutIntentRouter.route("Look around"))
    }

    // --- New self/family-belonging routing: real-device finding that
    // self-referential statements/questions like "Scout is part of the
    // family" and "You're one of us" matched no router intent and no
    // ScoutMemoryGate self+topic pair, reaching a fact-blind Gemini or an
    // empty-TruthDb TinyLlama fallback with nothing grounding Scout's own
    // identity. Passed here already contraction-expanded ("you are," not
    // "you're"), matching the real pipeline: ScoutIntentRouter.route()
    // always receives TextNormalizer's output, which already expands
    // "you're" -> "you are" before routing ever sees it -- see
    // ScoutFactExtractor.looksLikeSelfFamilyBelongingStatement()'s doc
    // comment. ---

    @Test fun `scout is part of the family routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("scout is part of the family"))
    }

    @Test fun `you are part of our family routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("you are part of our family"))
    }

    @Test fun `you are one of us routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("you are one of us"))
    }

    @Test fun `dont forget you are family too routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("don't forget you are family too"))
    }

    // --- Adversarial: a real FAMILY_NAMES question, including with the wake
    // word, must keep routing to FAMILY_NAMES rather than being swept into
    // the new self-belonging IDENTITY check. ---

    @Test fun `scout who is a part of my family still routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("scout who is a part of my family"))
    }

    @Test fun `who is a part of my family still routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("who is a part of my family"))
    }

    // --- Real-device regression: Patrick's exact wording, "Scout is also
    // part of the family," fell through to UNKNOWN (and from there into
    // ungrounded generation, which hallucinated a false Scout-as-pet
    // relationship) because the original pattern required "scout is part
    // of" as a contiguous phrase. The "also" insertion must now route
    // deterministically to IDENTITY, same as the un-inserted phrasing. ---

    @Test fun `scout is also part of the family routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("scout is also part of the family"))
    }

    @Test fun `you are also part of the family routes to IDENTITY`() {
        assertEquals(IntentType.IDENTITY, ScoutIntentRouter.route("you are also part of the family"))
    }

    // --- Adversarial: confirms the "also" widening didn't loosen the
    // subject anchor -- this still names no self-subject directly next to
    // "is" (optionally "also") "part of," so it must keep routing to
    // FAMILY_NAMES exactly as before this change. ("who is part of my
    // family" itself is already covered above.) ---

    @Test fun `scout knows who is part of my family still routes to FAMILY_NAMES`() {
        assertEquals(IntentType.FAMILY_NAMES, ScoutIntentRouter.route("scout knows who is part of my family"))
    }
}
