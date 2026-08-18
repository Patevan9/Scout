package com.example.scoutface.brain

/** The Courtesy Phase 1 (+ acknowledgment) response categories -- see ScoutCourtesyMatcher. */
enum class CourtesyIntent { GREET, GOOD_MORNING, THANKS, GOOD_NIGHT, GOODBYE, ACKNOWLEDGE }

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
