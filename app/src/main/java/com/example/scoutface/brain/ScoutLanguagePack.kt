package com.example.scoutface.brain

/**
 * Scout Local Language Pack -- v1.
 *
 * A small, bundled-only vocabulary extension for Scout's existing deterministic
 * courtesy handling (see ScoutCourtesyMatcher/CourtesyIntent/MainActivity's
 * handleCourtesy()) -- NOT a new decision engine. Its entire job is translating a
 * surface phrase Scout doesn't already recognize into the name of a category that
 * already has real, safe, deterministic handling -- e.g. "wassup" -> "GREETING" ->
 * the same CourtesyIntent.GREET behavior "hi"/"hello"/"hey" already trigger. It
 * never generates a reply itself, never writes TruthDb/HabitLayer, never touches
 * memory, and never introduces a new CourtesyIntent/IntentType of its own -- see
 * MainActivity's integration site (onResults(), right after ScoutCourtesyMatcher
 * misses) for how a returned category is mapped into existing behavior, and where
 * a miss (null) falls through to exactly what Scout does today.
 *
 * Purity: this class has NO android.* imports, NO org.json imports, and NO
 * Context/AssetManager dependency of any kind, unlike every other place in this
 * codebase that has ever touched JSON (ScoutExportManager, TeachExtractor, and the
 * long-deleted OfflineLexicon all import org.json alongside android.*). Decoding
 * language_pack.json's wire format (reading the bundled asset, parsing it with
 * org.json) is deliberately kept entirely on the Android-aware side
 * (MainActivity.loadLanguagePackSource()) -- this class receives only the already-
 * decoded result, a plain List<Pair<String, List<String>>> of (category name,
 * variant list) pairs, and is fully unit-testable with nothing but kotlin-stdlib +
 * JUnit, the same as every other pure class in this package.
 *
 * Input contract: [categoryFor] expects [normalized] to ALREADY be the output of
 * TextNormalizer.normalizeUtterance() -- this class performs no lowercasing,
 * trimming, contraction-expansion, or fuzzy matching of its own. language_pack.json
 * is authored entirely in already-normalized form for exactly this reason (e.g.
 * "what is up", never "what's up"/"whats up" -- TextNormalizer already collapses
 * both of those into "what is up" before this class ever sees them, so storing the
 * un-normalized forms would just be dead data). v1 is exact lookup only -- no
 * Levenshtein/fuzzy tolerance (see FuzzyNameMatcher for where that primitive
 * already exists, deliberately not used here yet).
 *
 * One-word behavior (intentional, not an accidental bypass): MainActivity's
 * onResults() consults this class BEFORE its one-word allowedOneWord filter, so an
 * exact hit like "yo" or "sup" is accepted even though it was never in that
 * hand-written allowlist. This is safe specifically because (1) ScoutCourtesyMatcher
 * has already had first refusal and missed, (2) the ENTIRE normalized utterance --
 * not a substring, not a fuzzy match -- must equal one of the small number of
 * deliberately curated variants in language_pack.json, and (3) v1 has zero fuzzy
 * tolerance, so there is no way for an arbitrary short utterance to slip through
 * this path by accident.
 *
 * Duplicate safety: if the same surface variant string appears more than once
 * across [source] -- whether under two different categories, or repeated (a data
 * typo) within the same one -- it is rejected from the usable lookup entirely
 * rather than resolved by first-wins or last-wins. Scout must never arbitrarily
 * pick a meaning for an ambiguous entry; see [rejectedDuplicates], exposed purely
 * for tests to verify this without this pure class ever touching DiagLog/Log
 * itself (that stays an Android-side concern, if wanted at all).
 */
class ScoutLanguagePack(source: List<Pair<String, List<String>>>) {

    /**
     * Every surface variant that appeared more than once across [source] (in any
     * combination of categories) and was therefore excluded from [categoryFor]'s
     * lookup entirely. Empty for a well-formed, non-ambiguous pack -- exposed only
     * so tests can assert a duplicate was actually caught, not silently resolved.
     */
    val rejectedDuplicates: Set<String>

    private val lookup: Map<String, String>

    init {
        val occurrenceCount = HashMap<String, Int>()
        val firstCategoryFor = LinkedHashMap<String, String>()

        for ((category, variants) in source) {
            for (variant in variants) {
                occurrenceCount[variant] = (occurrenceCount[variant] ?: 0) + 1
                firstCategoryFor.putIfAbsent(variant, category)
            }
        }

        rejectedDuplicates = occurrenceCount.filterValues { it > 1 }.keys

        lookup = firstCategoryFor.filterKeys { it !in rejectedDuplicates }
    }

    /**
     * Returns the category name (e.g. "GREETING") [normalized] exactly matches, or
     * null on any miss -- including a miss caused by [normalized] being a rejected
     * duplicate. [normalized] MUST already be normalized by the caller (see the
     * class doc comment's input contract) -- this is a plain, O(1) Map lookup,
     * nothing more.
     */
    fun categoryFor(normalized: String): String? = lookup[normalized]
}
