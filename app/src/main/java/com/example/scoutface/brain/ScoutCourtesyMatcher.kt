package com.example.scoutface.brain

/** The five Courtesy Phase 1 response categories -- see ScoutCourtesyMatcher. */
enum class CourtesyIntent { GREET, GOOD_MORNING, THANKS, GOOD_NIGHT, GOODBYE }

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
 * No Android imports, no internal state -- pure function, unit-testable the
 * same way as ScoutIntentRouter.
 */
object ScoutCourtesyMatcher {

    fun match(normalized: String, scoutName: String): CourtesyIntent? {
        // Run the configured name through the exact same normalization the
        // incoming speech already went through -- a bare trim()/lowercase()
        // isn't enough, since a configured name containing punctuation or
        // unusual spacing (e.g. "R2-D2", "Scout Jr.") would normalize
        // differently than this, and the name-included forms below would
        // then never match.
        val name = TextNormalizer.normalizeUtterance(scoutName)

        return when (normalized) {
            "hi", "hello", "hey" -> CourtesyIntent.GREET
            "good morning", "good morning $name" -> CourtesyIntent.GOOD_MORNING
            "thank you", "thanks", "thank you $name", "thanks $name" -> CourtesyIntent.THANKS
            "good night", "good night $name" -> CourtesyIntent.GOOD_NIGHT
            "goodbye", "bye" -> CourtesyIntent.GOODBYE
            else -> null
        }
    }
}
