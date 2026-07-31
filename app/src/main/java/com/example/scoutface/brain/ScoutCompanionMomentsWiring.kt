package com.example.scoutface.brain

/**
 * Small, pure helpers extracted from MainActivity's Companion Moments wiring
 * layer so they're unit-testable without Robolectric/Android. None of these
 * make the "should Scout speak" decision -- that's ScoutCompanionMomentsEngine's
 * job -- they only handle the mechanical bits around it: latching a one-frame
 * camera signal until it's actually consumed, recognizing a stale background
 * result, tracking the continuous-presence streak that defines a "session" for
 * curiosity's own bookkeeping, and phrasing a Memory fact honestly for whichever
 * entity it actually belongs to.
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
