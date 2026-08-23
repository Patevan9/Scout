package com.example.scoutface.brain

/**
 * Small, pure helpers extracted from MainActivity's Companion Moments wiring
 * layer so they're unit-testable without Robolectric/Android. None of these
 * make the "should Scout speak" decision -- that's ScoutCompanionMomentsEngine's
 * job -- they only handle the mechanical bits around it: latching a one-frame
 * camera signal until it's actually consumed, recognizing a stale background
 * result, tracking the continuous-presence streak that defines a "session" for
 * curiosity's own bookkeeping, phrasing a Memory fact honestly for whichever
 * entity it actually belongs to, deciding which fact keys are even eligible
 * to be offered as a Memory candidate in the first place, and giving a
 * stabilizing return greeting first opportunity over a Companion Moment.
 */

/**
 * A camera-frame event (e.g. "a second face just appeared") is latched as a
 * pending timestamp rather than a one-frame boolean, since the throttled
 * companion-moment check runs far less often than every frame and would
 * otherwise almost always see the flag already overwritten back to false by
 * the very next frame. consume() decides whether a just-cleared pending event
 * was still fresh enough to act on -- the caller is responsible for actually
 * clearing its own pending field once this returns, so one latched event is
 * only ever consumed (and evaluated) once.
 */
object ScoutArrivalLatch {
    fun consume(pendingSinceMs: Long, nowMs: Long, maxAgeMs: Long): Boolean {
        if (pendingSinceMs == 0L) return false
        val age = nowMs - pendingSinceMs
        return age in 0..maxAgeMs
    }
}

/**
 * Real-device finding: maybeMakeReturnGreeting() and maybeMakeCompanionMoment()
 * are both called on the same frame once a face reappears, but only the return
 * greeting is gated behind a stabilization window (RETURN_STABILIZATION_MS) --
 * Companion Moments has no such wait, so its background evaluation can
 * complete and speak before the return greeting is even attempted. Once it
 * does, it stamps the same shared proactive-remark clock the return greeting's
 * own cooldown reads (ScoutPresenceDecider.onExternalProactiveRemark() /
 * canMakeReturnGreeting()), which can suppress "welcome back" for a further
 * 20 minutes -- not just delay it, but replace it outright for that visit.
 *
 * This gate closes that window: while a genuine return is still stabilizing,
 * Companion Moments must not produce or speak a candidate, so the return
 * greeting always gets first opportunity once stabilization completes. Scoped
 * to exactly this one condition -- does not touch Companion Moment scoring,
 * cooldowns, PR #43's Memory-eligibility filters, or the return greeting's own
 * wording/thresholds, all of which stay exactly as they were.
 */
object ScoutReturnGreetingGate {
    fun isStabilizing(genuineAbsenceMarked: Boolean, returnStabilizingSinceMs: Long): Boolean =
        genuineAbsenceMarked && returnStabilizingSinceMs != 0L
}

/**
 * Real-device finding (both a Galaxy A32 and a Galaxy Fold 7, different
 * memory content on each): the instant Scout's face-triggered startup
 * greeting finishes, every other Companion Moment gate is already satisfied
 * -- isSpeaking clears, the 30-second poll throttle starts at 0L so it never
 * blocks a first check, and the shared 45-minute proactive cooldown reads as
 * "never fired" (Long.MAX_VALUE) on a fresh session -- so the very next
 * camera-analysis frame can immediately follow Scout's own greeting with an
 * unrelated spontaneous "I remember..." Companion Moment. This gate adds a
 * short, dedicated quiet period measured only from that startup greeting
 * finishing, scoped to exactly this one condition -- does not touch
 * Companion Moment scoring, category cooldowns, the daily budget,
 * Memory-eligibility filtering, the shared proactive cooldown, or presence
 * idle-silence/return-greeting timing, all of which stay exactly as they
 * were.
 *
 * startupGreetingFinishedAtMs == 0L (greeting not yet spoken) is deliberately
 * never "quiet" here. This is intentionally NOT keyed off MainActivity's
 * generic bootFinishedSpeaking latch (which flips on whichever TTS utterance
 * happens to finish first -- often an earlier boot-status announcement, not
 * the startup greeting itself, on a real boot) -- see
 * MainActivity.startupGreetingFinishedAtMs's own doc comment for the full
 * real-device reasoning. That distinct not-yet-booted state stays owned
 * entirely by the existing, separate bootFinishedSpeaking check.
 */
object ScoutPostBootQuietGate {
    fun isQuiet(startupGreetingFinishedAtMs: Long, nowMs: Long, quietMs: Long): Boolean =
        startupGreetingFinishedAtMs != 0L && nowMs - startupGreetingFinishedAtMs < quietMs
}

/**
 * Recognizes a background result computed under a since-superseded generation
 * (e.g. the Activity was destroyed, or a new one took over, while the result
 * was still in flight) -- mirrors the generation/owner-token pattern already
 * used by ScoutLlamaController for the same reason.
 */
object ScoutStaleResultGuard {
    fun isStale(capturedGeneration: Int, currentGeneration: Int): Boolean =
        capturedGeneration != currentGeneration
}

/**
 * Tracks the same tolerant, gap-forgiving continuous-presence streak that
 * currentTolerantPresenceMs()/CURIOSITY_MIN_PRESENCE_MS are measured against.
 * streakRestarted is true exactly when a brand new streak begins -- either the
 * very first sighting, or a sighting after a gap longer than gapGraceMs -- and
 * is what MainActivity uses to define "a new session began" for the purpose of
 * resetting hasHadConversationThisSession: that flag means "no conversation
 * yet this session," so it must be false again once the streak it's scoped to
 * has itself started over, not stay stuck true for the Activity's whole
 * process lifetime.
 */
object ScoutPresenceStreakTracker {
    data class StreakUpdate(val newPresentSinceMs: Long, val streakRestarted: Boolean)

    fun update(presentSinceMs: Long, lastSeenMs: Long, nowMs: Long, gapGraceMs: Long): StreakUpdate =
        when {
            presentSinceMs == 0L -> StreakUpdate(nowMs, streakRestarted = true)
            nowMs - lastSeenMs > gapGraceMs -> StreakUpdate(nowMs, streakRestarted = true)
            else -> StreakUpdate(presentSinceMs, streakRestarted = false)
        }
}

/**
 * Turns a Memory candidate's entity slug into honest possessive wording so a
 * fact about someone else can never be spoken as if it were the user's own
 * (or vice versa) -- e.g. a fact stored under "diana" must never come out as
 * "your birthday is November 27." Returns null -- caller must abort rather
 * than guess -- for a blank entity.
 */
object ScoutMemoryPhraser {
    fun possessive(
        entity: String,
        userEntity: String,
        scoutEntity: String,
        displayName: (String) -> String
    ): String? {
        val trimmed = entity.trim().lowercase()
        if (trimmed.isEmpty()) return null
        return when (trimmed) {
            userEntity -> "your"
            scoutEntity -> "my"
            else -> {
                val name = displayName(trimmed).trim()
                if (name.isEmpty()) null else "$name's"
            }
        }
    }

    fun buildSentence(
        intro: String,
        entity: String,
        userEntity: String,
        scoutEntity: String,
        humanFactKey: String,
        value: String,
        displayName: (String) -> String
    ): String? {
        val possessive = possessive(entity, userEntity, scoutEntity, displayName) ?: return null
        return "$intro $possessive $humanFactKey is $value."
    }
}

/**
 * Companion Moments Memory-category eligibility filter -- decides which
 * TruthDb fact *keys* are appropriate to resurface as "I've been thinking
 * about something you told me..." (see MainActivity.buildCompanionSignals(),
 * the only caller -- ENTITY_SCOUT itself is excluded there, the same way
 * getAllKnownFacts() already excludes it, so this only needs to reason about
 * fact keys, not entities). Two independent, real-device-confirmed problems:
 *
 * 1. Bootstrap/identity facts ("name", "aliases") are functional data Scout
 *    needs to operate, not a shared personal memory -- "Your name is
 *    Patrick" is a poor fit for this feature's intended tone, and tends to
 *    dominate the Memory category since it's usually the oldest (and
 *    therefore stalest) fact in the whole database.
 *
 * 2. A confirmed, separate real-device finding: TeachExtractor's generic
 *    "my X is Y" fallback auto-prefixes ANY unrecognized label with
 *    "favorite_" -- including plain relationship statements that never
 *    matched a more specific pattern, e.g. "my son is Elijah" (no "'s name
 *    is") silently becomes favorite_son = "Elijah" rather than son_name =
 *    "Elijah" (confirmed by tracing TeachExtractor.extract()'s regex chain).
 *    Rendered through ScoutMemoryPhraser's honest template above, that
 *    becomes "...your favorite son is Elijah" -- a person-ranking statement
 *    Scout must never make, especially in a household with more than one
 *    child. This is a write-path mislabeling bug in TeachExtractor,
 *    intentionally NOT fixed here (out of scope for this change) -- see the
 *    PR this shipped in. Filtering here instead is a read-side safety net
 *    that also covers any other route that could produce the same shape of
 *    key (e.g. a legitimately-phrased "Diana's favorite family member is
 *    Elijah"), not just this one write-path bug.
 *
 * MEMORY_PERSON_RELATION_WORDS mirrors the union of TeachExtractor's own
 * RELATION_WORDS and ScoutFactExtractor's PERSON_RELATION_HINTS in this same
 * package. Duplicated here rather than referenced directly since
 * TeachExtractor is explicitly out of scope for this change -- widening its
 * visibility as a side effect of an eligibility-only fix would blur that
 * boundary. Flagged for a future consolidation pass instead.
 */
object ScoutCompanionMemoryEligibility {

    private val MEMORY_BOOTSTRAP_FACT_KEYS = setOf("name", "aliases")

    private val MEMORY_PERSON_RELATION_WORDS = setOf(
        // TeachExtractor.RELATION_WORDS
        "wife", "husband", "spouse", "son", "daughter", "kid", "child", "dog", "cat", "pet",
        // ScoutFactExtractor.PERSON_RELATION_HINTS
        "friend", "neighbor", "neighbour", "coworker", "colleague", "boss",
        "roommate", "brother", "sister", "sibling", "cousin", "uncle", "aunt",
        "grandma", "grandmother", "grandpa", "grandfather", "nephew", "niece",
        "teacher", "boyfriend", "girlfriend", "fiance", "fiancee", "partner",
        "babysitter", "nanny", "doctor"
    )

    /**
     * True if [factKey] is safe to resurface as a Companion Moment memory --
     * false for bootstrap/identity keys, and false for any
     * favorite_<relation-word> key that would imply ranking one person above
     * others in the same relationship category. Ordinary preferences
     * ("favorite_color", "favorite_food", ...) stay eligible -- only the
     * person-ranking shape (the suffix after "favorite_" being a known
     * relation word) is excluded.
     */
    fun isCompanionMemoryEligible(factKey: String): Boolean {
        val key = factKey.trim().lowercase()
        if (key in MEMORY_BOOTSTRAP_FACT_KEYS) return false
        val relationSuffix = key.removePrefix("favorite_")
        if (relationSuffix != key && relationSuffix in MEMORY_PERSON_RELATION_WORDS) return false
        return true
    }
}
