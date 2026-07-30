package com.example.scoutface.brain

/**
 * Generic wake-word tolerance for any configured Scout name -- not just the
 * literal default "Scout". A bare exact/whole-word check gives no mishearing
 * tolerance at all to a renamed Scout, and a hand-written list of alternate
 * spellings (the previous approach, kept only for "Scout" itself -- see
 * MainActivity's hearsHisName) can't scale to arbitrary names.
 *
 * Matches on bounded Levenshtein (edit) distance between each word in the heard
 * utterance and the configured name, with the allowed distance scaled to the
 * name's length -- a short name gets zero tolerance (an edit-distance-1 match on
 * a 2-3 letter name would sweep up too many unrelated real words), a mid-length
 * name gets one, a long name gets two. This is deliberately a spelling-closeness
 * check on already-transcribed text, not a phonetic/acoustic one: it's operating
 * on the same signal Scout actually has (SpeechRecognizer's text output), and
 * genuinely phonetic mishearings some STT engines produce (a real one seen on
 * this project: "Scout" heard as "Gal") aren't spelling-close enough for this or
 * any generic spelling-distance approach to catch -- that one narrow, empirically
 * observed exception is kept as its own explicit check at the call site in
 * MainActivity, not folded in here, so this matcher stays name-agnostic.
 */
object FuzzyNameMatcher {

    // Named and tunable -- initial values, expected to be retuned after real
    // A32 testing, same as the presence-layer and listening-reminder thresholds
    // elsewhere in this project.
    private const val EXACT_ONLY_MAX_LEN = 3       // name length <= this: exact match only
    private const val SHORT_NAME_MAX_LEN = 6       // name length <= this (and > EXACT_ONLY_MAX_LEN): distance 1
    private const val SHORT_NAME_MAX_DISTANCE = 1
    private const val LONG_NAME_MAX_DISTANCE = 2   // name length > SHORT_NAME_MAX_LEN: distance 2

    /**
     * True if any whole word in [utteranceNormalized] is within the allowed edit
     * distance of [configuredName]. [utteranceNormalized] is expected to already be
     * lowercased/punctuation-stripped (see TextNormalizer.normalizeUtterance()) --
     * [configuredName] is normalized here internally (trimmed, lowercased,
     * punctuation stripped) so callers can pass the raw stored/taught value as-is.
     *
     * A blank, single-character, or otherwise degenerate configured name can never
     * become a fuzzy wake word -- it falls through to requiring an exact (distance
     * 0) whole-word match instead, same as any other very short name, and a fully
     * blank name never matches anything at all.
     */
    fun matchesName(utteranceNormalized: String, configuredName: String): Boolean {
        val name = normalizeName(configuredName)
        if (name.isEmpty()) return false

        val maxDistance = maxDistanceFor(name.length)
        val words = utteranceNormalized.split(Regex("\\s+")).filter { it.isNotBlank() }

        return words.any { word ->
            // Edit distance is always >= the difference in length between the two
            // strings -- an exact lower bound, not a heuristic -- so a word whose
            // length differs from the name's by more than maxDistance can never be
            // within maxDistance regardless of its actual content. Cheap to check
            // before running the full comparison.
            if (kotlin.math.abs(word.length - name.length) > maxDistance) return@any false
            levenshtein(word, name) <= maxDistance
        }
    }

    private fun maxDistanceFor(nameLen: Int): Int = when {
        nameLen <= EXACT_ONLY_MAX_LEN -> 0
        nameLen <= SHORT_NAME_MAX_LEN -> SHORT_NAME_MAX_DISTANCE
        else -> LONG_NAME_MAX_DISTANCE
    }

    // Lowercase, strip anything that isn't a letter/digit, trim -- mirrors how
    // TextNormalizer.normalizeUtterance() already prepares heard speech, so the
    // configured name is compared on equal footing regardless of stray
    // capitalization/punctuation from however it was typed or taught.
    private fun normalizeName(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    // Standard iterative Levenshtein distance, O(a.length * b.length) time and
    // O(b.length) space -- these are single words, a handful of characters each,
    // so this is negligible cost per recognized utterance.
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // insertion
                    prev[j] + 1,          // deletion
                    prev[j - 1] + cost    // substitution
                )
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }

        return prev[b.length]
    }
}
