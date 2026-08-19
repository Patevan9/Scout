package com.example.scoutface.brain

/** The Courtesy Phase 1 (+ acknowledgment) response categories -- see ScoutCourtesyMatcher. */
enum class CourtesyIntent { GREET, GOOD_MORNING, THANKS, GOOD_NIGHT, GOODBYE, ACKNOWLEDGE, WELCOME_BACK, CONFIRM }

/**
 * Deterministic, wake-name-free matching for a small, fixed set of everyday
 * courtesy phrases -- Phase 1 of the staged plan to reduce how often Scout's
 * name must be said for ordinary household speech, without loosening the
 * wake-name requirement for anything else. Exact equality only, checked
 * against the same normalized text the rest of the speech pipeline already
 * computes (TextNormalizer.normalizeUtterance) -- deliberately no fuzzy or
 * partial matching, so a real question that happens to start with "hi" is
 * never swallowed by this.
 *
 * Scope, decided explicitly rather than by omission:
 * - "hi"/"hello"/"hey" and "goodbye"/"bye" are matched bare only. Their
 *   name-included forms ("hi Scout", "bye Scout") are deliberately NOT
 *   matched here -- those already reach Scout today through the existing
 *   wake-name gate and ScoutIntentRouter's GREET/GOODBYE intents, so
 *   matching them here too would just be a second path to the same place.
 * - "good morning"/"good night"/"thank you"/"thanks" have no router intent
 *   at all today, bare or name-included -- confirmed by tracing
 *   ScoutIntentRouter before writing this. So their name-included forms
 *   ("good morning Scout", "thank you Scout") are matched here as well;
 *   otherwise saying Scout's name would ironically send these to the LLM
 *   instead of answering them deterministically.
 *
 * Real-device finding (Galaxy A32): plain conversational closers -- bare
 * "okay"/"alright"/"sounds good"/"you're welcome"/"got it", and any of the
 * phrases above led in with "okay,"/"alright,"/"got it," -- had no
 * deterministic handling anywhere (not here, not in ScoutIntentRouter), so
 * they fell through to Gemini/TinyLlama exactly like a real open-ended
 * question. On a slow device that's long enough to trigger a Busy-Brain
 * filler phrase ("Let me think about that...") in reply to a "thank you".
 * ACKNOWLEDGE and the lead-in stripping below close that gap.
 *
 * Real-device finding (Fold 7): "Welcome back!" said in reply to Scout's own
 * boot greeting had the identical gap -- no match here, no ScoutIntentRouter
 * pattern either, so it reached Gemini/TinyLlama like an open question. Worse
 * than a silent miss: the presence-reply window Scout's own boot/return
 * greeting opens (see MainActivity's isPresenceInitiated plumbing) lets this
 * through without the wake name at all, so it's a common phrase to actually
 * hit this gap on. WELCOME_BACK is its own CourtesyIntent rather than folded
 * into ACKNOWLEDGE -- it gets its own phrase pool (Phrases.COURTESY_WELCOME_BACK)
 * addressed specifically to "welcoming Scout back", not a generic acknowledgment.
 *
 * Real-device finding (Fold 7): a short conversational validation ("You are
 * right." / "Correct." / "Exactly.") said after Scout's own answer had the
 * same gap -- no match here, no ScoutIntentRouter pattern -- so it reached
 * Gemini/TinyLlama like an open question and triggered a Busy-Brain filler
 * ("Let me think about that...") in reply to being told Scout was right.
 * CONFIRM is its own CourtesyIntent, not folded into ACKNOWLEDGE: the two are
 * opposite directions (ACKNOWLEDGE is the user closing out something SCOUT
 * just said; CONFIRM is the user validating that Scout's own answer was
 * correct), so they warrant separate, differently-worded phrase pools rather
 * than reusing ACKNOWLEDGE's "Okay!"/"Sounds good." set, which doesn't fit.
 * Deliberately does NOT include bare "right" -- too overloaded (a literal
 * turn-right direction is plausible future Builder's Workbench vocabulary)
 * to safely claim as an acknowledgment on its own; only paired with an
 * unambiguous subject ("you are right"/"that is right") is it matched.
 * "that's right"/"that's correct" are matched as their own literal entries
 * rather than relying on contraction expansion -- TextNormalizer expands
 * "you're"/"youre" to "you are" (so "you're right" already reaches this
 * table as "you are right", no separate entry needed) but has no "that's"
 * rule at all, so "that's right" arrives here as literally "that's right",
 * distinct from "that is right".
 *
 * Lead-in stripping: at most ONE recognized filler lead-in ("okay"/"ok"/
 * "alright"/"got it") is stripped from the START of the utterance only
 * (never searched for mid-string), and only ever tried once -- no
 * recursion. The remainder is then matched via the exact same table below,
 * nothing looser. If the remainder isn't an exact hit, match() returns
 * null for the ORIGINAL, untouched string -- the lead-in strip has no
 * effect outside this function, so a real sentence like "Okay, what is the
 * weather?" still reaches ScoutIntentRouter's ordinary WEATHER routing on
 * the full original text, completely unchanged. LEAD_INS is deliberately
 * narrow: only words with no independent semantic payload of their own
 * (never a real yes/no answer to something Scout might have asked) are
 * safe to discard this way -- "sure"/"yeah"/"yes"/"no" are intentionally
 * NOT included for that reason.
 *
 * No Android imports, no internal state -- pure function, unit-testable the
 * same way as ScoutIntentRouter.
 */
object ScoutCourtesyMatcher {

    private val LEAD_INS = listOf("okay", "ok", "alright", "got it")

    fun match(normalized: String, scoutName: String): CourtesyIntent? {
        // Run the configured name through the exact same normalization the
        // incoming speech already went through -- a bare trim()/lowercase()
        // isn't enough, since a configured name containing punctuation or
        // unusual spacing (e.g. "R2-D2", "Scout Jr.") would normalize
        // differently than this, and the name-included forms below would
        // then never match.
        val name = TextNormalizer.normalizeUtterance(scoutName)

        exactMatch(normalized, name)?.let { return it }

        for (leadIn in LEAD_INS) {
            val remainder = stripLeadIn(normalized, leadIn) ?: continue
            if (remainder.isBlank()) continue
            exactMatch(remainder, name)?.let { return it }
        }

        return null
    }

    private fun exactMatch(s: String, name: String): CourtesyIntent? = when (s) {
        "hi", "hello", "hey" -> CourtesyIntent.GREET
        "good morning", "good morning $name" -> CourtesyIntent.GOOD_MORNING
        "thank you", "thanks", "thank you $name", "thanks $name" -> CourtesyIntent.THANKS
        "good night", "good night $name" -> CourtesyIntent.GOOD_NIGHT
        "goodbye", "bye" -> CourtesyIntent.GOODBYE
        // "you're welcome" arrives here as "you are welcome" -- TextNormalizer
        // already expands the "you're" contraction before this is ever called.
        "okay", "ok", "alright", "got it", "sounds good", "you are welcome" -> CourtesyIntent.ACKNOWLEDGE
        // "glad you are back" is "glad you're back" post-contraction-expansion,
        // same as "you are welcome" above. Deliberately exact-match only, not
        // a "welcome back" substring check -- see the adversarial tests in
        // ScoutCourtesyMatcherTest for phrases this must NOT swallow (e.g.
        // "welcome back to the show", "welcome back everyone to the meeting").
        "welcome back", "welcome back $name",
        "glad you are back", "good to have you back", "nice to have you back" -> CourtesyIntent.WELCOME_BACK
        // "you're right"/"you're correct" arrive here as "you are right"/
        // "you are correct" (TextNormalizer's existing "you're"->"you are"
        // expansion) -- no separate entry needed. "that's right"/"that's
        // correct" do NOT get expanded by TextNormalizer, so they're listed
        // here as their own literal strings alongside "that is right"/
        // "that is correct". Deliberately no bare "right" -- see the class
        // doc comment for why.
        "you are right", "that is right", "that's right",
        "you are correct", "that is correct", "that's correct",
        "correct", "exactly" -> CourtesyIntent.CONFIRM
        else -> null
    }

    // Strips [leadIn] from the start of [s] only if it appears there as a
    // whole word/phrase -- never searched for mid-string, so it can never
    // remove anything from inside a real sentence. Returns null (no strip
    // attempted) if [s] doesn't start with [leadIn] as a whole word.
    private fun stripLeadIn(s: String, leadIn: String): String? {
        val words = leadIn.split(" ").filter { it.isNotBlank() }
        val pattern = Regex("^" + words.joinToString("""\s+""") { Regex.escape(it) } + """\b\s*""")
        val found = pattern.find(s) ?: return null
        return s.substring(found.value.length)
    }
}
