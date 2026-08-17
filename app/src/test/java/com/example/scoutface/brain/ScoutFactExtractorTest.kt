package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutFactExtractorTest {

    private val known = setOf("diana", "nicolas", "nick")

    // --- Word-order independence for date facts (Patrick's exact examples) ---

    @Test fun `subject possessive fact is value`() {
        val facts = ScoutFactExtractor.extract("Diana's birthday is November 27", known)
        assertEquals(1, facts.size)
        assertEquals(ScoutFactExtractor.Fact("diana", "birthday", "November 27"), facts[0])
    }

    @Test fun `value first ordering still extracts the same fact`() {
        val facts = ScoutFactExtractor.extract("November 27 is Diana's birthday", known)
        assertEquals(1, facts.size)
        assertEquals("diana", facts[0].subject)
        assertEquals("birthday", facts[0].property)
        assertEquals("November 27", facts[0].value)
    }

    @Test fun `born-on phrasing extracts the same fact as birthday-is phrasing`() {
        val facts = ScoutFactExtractor.extract("Diana was born on November 27", known)
        assertEquals(1, facts.size)
        assertEquals("diana", facts[0].subject)
        assertEquals("birthday", facts[0].property)
        assertEquals("November 27", facts[0].value)
    }

    @Test fun `remember prefix does not block extraction`() {
        val facts = ScoutFactExtractor.extract("Remember Diana's birthday is November 27", known)
        assertEquals(1, facts.size)
        assertEquals("November 27", facts[0].value)
    }

    @Test fun `a date with a day suffix and no year still parses`() {
        val facts = ScoutFactExtractor.extract("Diana's birthday is November 27th", known)
        assertEquals("November 27th", facts[0].value)
    }

    // --- Relation-word subject (no bare name involved yet) ---

    @Test fun `my wife's birthday is extracted with a relation subject`() {
        val facts = ScoutFactExtractor.extract("my wife's birthday is November 27", emptySet())
        assertEquals(1, facts.size)
        assertEquals("my wife", facts[0].subject)
        assertEquals("birthday", facts[0].property)
    }

    // --- Nickname clause, including the real on-device STT mishearing ---

    @Test fun `call him extracts a nickname`() {
        assertEquals("Nick", ScoutFactExtractor.extractNicknameClause("we call him Nick"))
    }

    @Test fun `can him is treated as a mishearing of call him`() {
        // Confirmed from an actual on-device transcript: "but we can him Nick" was
        // what speech-to-text produced for a spoken "call him Nick."
        assertEquals("Nick", ScoutFactExtractor.extractNicknameClause(
            "my dog's name is Nicolas, but we can him Nick"))
    }

    @Test fun `nickname clause combines with a birthday fact in the same sentence`() {
        val facts = ScoutFactExtractor.extract(
            "Diana's birthday is November 27, and we call her Di", known + "di")
        assertTrue(facts.any { it.property == "birthday" && it.value == "November 27" })
        assertTrue(facts.any { it.property == "nickname" && it.value == "Di" })
        assertTrue(facts.all { it.subject == "diana" })
    }

    // --- Generic possessive fallback for properties other than birthday/nickname ---

    @Test fun `generic possessive fact extracts an arbitrary property`() {
        val facts = ScoutFactExtractor.extract("Diana's favorite food is sushi", known)
        assertEquals(1, facts.size)
        assertEquals("favorite_food", facts[0].property)
        assertEquals("Sushi", facts[0].value)
    }

    @Test fun `birthday is not double-extracted via the generic fallback`() {
        val facts = ScoutFactExtractor.extract("Diana's birthday is November 27", known)
        assertEquals(1, facts.count { it.property == "birthday" })
    }

    // --- Questions must never be treated as teaching ---

    @Test fun `a direct question extracts nothing`() {
        assertTrue(ScoutFactExtractor.extract("What was Diana's birthday?", known).isEmpty())
        assertTrue(ScoutFactExtractor.extract("Is Diana's birthday November 27?", known).isEmpty())
    }

    @Test fun `unknown subject with no relation word extracts nothing`() {
        assertTrue(ScoutFactExtractor.extract("Steve called earlier", emptySet()).isEmpty())
    }

    // --- Safety net: unparsed-but-clearly-a-statement about a known entity ---

    @Test fun `unrecognized teaching about a known entity is flagged`() {
        assertTrue(ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "Don't forget Diana's birthday", known))
    }

    @Test fun `questions are never flagged as unrecognized teaching`() {
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "What was Diana's birthday?", known))
    }

    @Test fun `ordinary chit-chat with no entity or hint word is not flagged`() {
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "it is a beautiful day outside", known))
    }

    // --- Layer 1: first-introduction safety net for relation words findSubject()
    // doesn't recognize (friend, neighbor, coworker, ...) -- these have no
    // structured write path, so without this they'd silently reach Gemini/
    // TinyLlama with nothing written and no clarification asked. ---

    @Test fun `an appositive introduction with an unsupported relation word is flagged`() {
        assertTrue(ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "This is my friend, Janice.", emptySet()))
    }

    @Test fun `a that-is-my-neighbor introduction is flagged`() {
        assertTrue(ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "That's my neighbor, Bob.", emptySet()))
    }

    @Test fun `a name-first introduction is flagged`() {
        assertTrue(ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "Janice is my friend.", emptySet()))
    }

    @Test fun `an appositive introduction still works without a comma, matching real normalized input`() {
        // TextNormalizer strips commas to plain whitespace before this ever runs
        // in production -- confirm the comma-free form matches too, not just the
        // comma-containing form a person would naturally type.
        assertTrue(ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "this is my coworker sarah", emptySet()))
    }

    @Test fun `my coworker is Sarah is unaffected -- already extracted by TeachExtractor's generic pattern`() {
        // TeachExtractor.extract() already matches "my X is Y" generically and
        // writes a real fact for this phrasing (mislabeled as favorite_coworker
        // rather than coworker_name, a separate pre-existing quirk, out of scope
        // here) -- so it never reaches looksLikeUnrecognizedTeaching() in
        // production. Documented here to confirm the new patterns don't
        // separately (mis)flag it either, since "my coworker is sarah" contains
        // no "is my <relation>" substring at all.
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "My coworker is Sarah.", emptySet()))
    }

    @Test fun `a pronoun is never mistaken for a name in a name-first introduction`() {
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "He is my friend.", emptySet()))
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "This is my friend.", emptySet()))
    }

    @Test fun `an ordinary possessive statement about an object is never flagged as an introduction`() {
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "This is my favorite show.", emptySet()))
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "This is my house.", emptySet()))
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "This is my new car.", emptySet()))
    }

    @Test fun `an unsupported relation word alone with no name is not flagged`() {
        // "friend" is a PERSON_RELATION_HINTS word, but with no plausible name
        // token following it there's nothing to ask a clarifying question about.
        assertTrue(!ScoutFactExtractor.looksLikeUnrecognizedTeaching(
            "I have a friend", emptySet()))
    }

    // --- Layer 2: looksTeachingShaped() -- broader trigger for the output
    // backstop below, no known-entity/relation anchor required. ---

    @Test fun `a hint word in a statement is teaching-shaped even with no known entity`() {
        assertTrue(ScoutFactExtractor.looksTeachingShaped("my favorite team lost yesterday"))
    }

    @Test fun `a question is never teaching-shaped`() {
        assertTrue(!ScoutFactExtractor.looksTeachingShaped("what is my favorite color"))
    }

    @Test fun `ordinary small talk with no hint word is not teaching-shaped`() {
        assertTrue(!ScoutFactExtractor.looksTeachingShaped("how is the weather today"))
    }

    // --- Layer 2: containsRetentionClaim() -- narrow, explicit phrases only,
    // never a bare acknowledgment. ---

    @Test fun `explicit retention-claim phrasing is recognized`() {
        assertTrue(ScoutFactExtractor.containsRetentionClaim("Got it, I'll remember that about Janice!"))
        assertTrue(ScoutFactExtractor.containsRetentionClaim("Okay, I'll keep that in mind."))
        assertTrue(ScoutFactExtractor.containsRetentionClaim("I've saved that for later."))
        assertTrue(ScoutFactExtractor.containsRetentionClaim("I'll make a note of that."))
        assertTrue(ScoutFactExtractor.containsRetentionClaim("I won't forget that."))
        assertTrue(ScoutFactExtractor.containsRetentionClaim("I’ll remember that too.")) // curly apostrophe
    }

    @Test fun `an ordinary acknowledgment alone is never treated as a retention claim`() {
        assertTrue(!ScoutFactExtractor.containsRetentionClaim("Got it."))
        assertTrue(!ScoutFactExtractor.containsRetentionClaim("Noted. Sounds good."))
        assertTrue(!ScoutFactExtractor.containsRetentionClaim("Okay!"))
        assertTrue(!ScoutFactExtractor.containsRetentionClaim("That's nice, thanks for sharing."))
    }

    // --- Layer 2: applyRetentionClaimGuard() -- both conditions required
    // together, never a blanket filter on its own. ---

    @Test fun `a teaching-shaped input with a retention-claim reply is replaced with the honest clarification`() {
        val out = ScoutFactExtractor.applyRetentionClaimGuard(
            "this is my friend janice",
            "That's nice! I'll remember Janice."
        )
        assertEquals(ScoutFactExtractor.UNRECOGNIZED_TEACHING_CLARIFICATION, out)
    }

    @Test fun `a retention-claim reply to ordinary small talk is left untouched -- input was not teaching-shaped`() {
        val out = ScoutFactExtractor.applyRetentionClaimGuard(
            "how is the weather today",
            "I'll remember to check back on that for you!"
        )
        assertEquals("I'll remember to check back on that for you!", out)
    }

    @Test fun `a plain Got it reply to teaching-shaped input is left untouched -- no retention claim made`() {
        val out = ScoutFactExtractor.applyRetentionClaimGuard(
            "my favorite team lost yesterday",
            "Got it, sorry to hear that."
        )
        assertEquals("Got it, sorry to hear that.", out)
    }

    @Test fun `a plain Got it reply to ordinary small talk is left untouched`() {
        val out = ScoutFactExtractor.applyRetentionClaimGuard(
            "how is the weather today",
            "Got it! Looks sunny."
        )
        assertEquals("Got it! Looks sunny.", out)
    }

    // --- looksLikeMemoryCapabilityQuestion() -- self-referential "are you even
    // capable of learning/remembering" questions, gated so a specific referent
    // right after the verb is excluded (a real recall question or task
    // request, not a capability question). ---

    @Test fun `genuine capability questions are recognized`() {
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Can you learn?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Can you remember things?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you have a memory?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Are you able to learn?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Are you capable of learning?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you have the ability to learn?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Can you learn or remember from others?"))
        assertTrue(ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you even have a memory?"))
    }

    @Test fun `a specific referent after remember is a recall question, never a capability question`() {
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you remember the movie?"))
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Did you learn how that works?"))
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you remember my birthday?"))
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you remember Diana?"))
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Do you remember what I said?"))
    }

    @Test fun `a future-task request is not a capability question`() {
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Can you remember to grab milk?"))
    }

    @Test fun `an unrelated learn-a-skill question is not a memory capability question`() {
        assertTrue(!ScoutFactExtractor.looksLikeMemoryCapabilityQuestion("Can you learn a new language?"))
    }

    // --- containsCapabilityDenial() -- global denial only, never a truthful
    // specific-fact-absence answer. ---

    @Test fun `global capability denial phrases are recognized`() {
        assertTrue(ScoutFactExtractor.containsCapabilityDenial("I cannot learn."))
        assertTrue(ScoutFactExtractor.containsCapabilityDenial("I do not have the capability to learn."))
        assertTrue(ScoutFactExtractor.containsCapabilityDenial("I am unable to remember anything."))
        assertTrue(ScoutFactExtractor.containsCapabilityDenial("I don't retain information."))
        assertTrue(ScoutFactExtractor.containsCapabilityDenial("I don't have a memory."))
        assertTrue(ScoutFactExtractor.containsCapabilityDenial("I don't have a memory at all."))
    }

    @Test fun `a truthful specific-fact-absence answer is never treated as capability denial`() {
        assertTrue(!ScoutFactExtractor.containsCapabilityDenial("I don't have a memory of that."))
        assertTrue(!ScoutFactExtractor.containsCapabilityDenial("I don't have a memory of your birthday."))
        assertTrue(!ScoutFactExtractor.containsCapabilityDenial("I'm unable to remember your dog's name right now."))
        assertTrue(!ScoutFactExtractor.containsCapabilityDenial("I don't have a memory of what you said yesterday."))
    }

    // --- containsReminderPromise() -- future scheduling offers/completion
    // claims only, never ordinary recollection language. ---

    @Test fun `reminder scheduling promises are recognized`() {
        assertTrue(ScoutFactExtractor.containsReminderPromise("I'll remind you on January 27th."))
        assertTrue(ScoutFactExtractor.containsReminderPromise("Would you like me to remind you tomorrow?"))
        assertTrue(ScoutFactExtractor.containsReminderPromise("I've set a reminder."))
        assertTrue(ScoutFactExtractor.containsReminderPromise("I'll set an alarm."))
        assertTrue(ScoutFactExtractor.containsReminderPromise("I'll remind you at 3 PM."))
        assertTrue(ScoutFactExtractor.containsReminderPromise("I'll remind you."))
    }

    @Test fun `ordinary recollection language is never treated as a reminder promise`() {
        assertTrue(!ScoutFactExtractor.containsReminderPromise("I can remind you what you told me earlier."))
        assertTrue(!ScoutFactExtractor.containsReminderPromise("I can remind you of the facts I have about Diana."))
        assertTrue(!ScoutFactExtractor.containsReminderPromise("I'll remind you what you already told me."))
    }

    // --- containsVisionCapabilityDenial() -- global camera/vision denial
    // only, never a truthful current-state report. ---

    @Test fun `global vision-capability denial phrases are recognized`() {
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I don't have a camera."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I have no visual capability."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I don't have the ability to see."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("As an AI, I don't have eyes or a camera."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I'm just a text-based assistant, so I can't see."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I have no way to see anything."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I can't see."))
        assertTrue(ScoutFactExtractor.containsVisionCapabilityDenial("I cannot see."))
    }

    @Test fun `truthful current-state vision reports are never treated as capability denial`() {
        assertTrue(!ScoutFactExtractor.containsVisionCapabilityDenial("I don't see anything I recognize right now."))
        assertTrue(!ScoutFactExtractor.containsVisionCapabilityDenial("I'm not confident about what I'm seeing."))
        assertTrue(!ScoutFactExtractor.containsVisionCapabilityDenial("I can't see clearly at the moment."))
        assertTrue(!ScoutFactExtractor.containsVisionCapabilityDenial("I don't have a clear view right now."))
        assertTrue(!ScoutFactExtractor.containsVisionCapabilityDenial("I can't see you very well from this angle."))
    }

    // --- applyScoutCapabilityIntegrityGuards() -- combines all four checks
    // (memory-capability, reminder-promise, vision-capability, and the
    // existing teaching-shaped retention-claim guard); each of the first
    // three substitutions is independent of whether the input looked
    // teaching-shaped (unlike the retention-claim guard, which still
    // requires that). Renamed from applyMemoryIntegrityGuards() now that it
    // also covers vision. ---

    @Test fun `a global capability denial reply is replaced regardless of the question shape`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "what have you learned today",
            "I do not have the capability to learn or learn from others."
        )
        assertEquals(ScoutFactExtractor.MEMORY_CAPABILITY_CLARIFICATION, out)
    }

    @Test fun `a reminder scheduling promise reply is replaced regardless of the question shape`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "remind me about january 27th",
            "Sure, I'll remind you on January 27th!"
        )
        assertEquals(ScoutFactExtractor.REMINDER_NOT_AVAILABLE, out)
    }

    @Test fun `a specific-fact-absence reply is left untouched by the combined guard`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "do you remember my dog's name",
            "I don't have a memory of that yet."
        )
        assertEquals("I don't have a memory of that yet.", out)
    }

    @Test fun `ordinary recollection language is left untouched by the combined guard`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "can you remind me what diana likes",
            "I can remind you of the facts I have about Diana."
        )
        assertEquals("I can remind you of the facts I have about Diana.", out)
    }

    @Test fun `the combined guard still applies the existing teaching-shaped retention-claim check`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "this is my friend janice",
            "That's nice! I'll remember Janice."
        )
        assertEquals(ScoutFactExtractor.UNRECOGNIZED_TEACHING_CLARIFICATION, out)
    }

    @Test fun `a global vision-capability denial reply is replaced regardless of the question shape`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "what do you see",
            "Good morning! I don't have a camera to see my surroundings."
        )
        assertEquals(ScoutFactExtractor.VISION_CAPABILITY_CLARIFICATION, out)
    }

    @Test fun `a truthful current-state vision reply is left untouched by the combined guard`() {
        val out = ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(
            "what do you see",
            "I don't see anything I recognize right now."
        )
        assertEquals("I don't see anything I recognize right now.", out)
    }
}
