package com.example.scoutface.brain

import java.time.Instant
import java.time.ZoneId

/** The four kinds of moment this engine can propose. */
enum class MomentCategory { ENVIRONMENT, MEMORY, OBSERVATION, CURIOSITY }

/**
 * One taught fact eligible to be resurfaced as a Memory moment.
 * contentKey identifies the specific fact (e.g. "memory:user_primary:favorite_color") --
 * stable across calls so novelty/tie-break tracking can key on it.
 */
data class StaleFact(val contentKey: String, val daysSinceLastSurfaced: Int)

/**
 * A grounded, scored proposal to speak. Deliberately carries no literal spoken
 * text -- wording belongs to VoiceBank (the same phrase-pool + anti-repeat
 * pattern already used for presence remarks), not to this decision engine.
 * contentKey is what a caller resolves into actual words and what novelty
 * tracking keys on; groundedIn/contributions exist purely for diagnostics and
 * are restricted to signal/contribution *names*, never facts, names, or text,
 * so logging this is always privacy-safe by construction.
 */
data class MomentCandidate(
    val category: MomentCategory,
    val contentKey: String,
    val groundedIn: List<String>,
    val contributions: List<String>,
    val confidence: Float
)

/**
 * Plain-typed snapshot of everything the engine needs for one evaluation.
 * The caller (MainActivity) is responsible for assembling this from the
 * camera loop, TruthDb, ConversationDb, HabitLayer, and PeopleDb -- this
 * class touches none of them directly, matching ScoutPresenceDecider's own
 * no-android-imports convention so both stay unit-testable without Robolectric.
 */
data class CompanionSignals(
    val nowMs: Long,

    // --- Hard gates -- see ScoutCompanionMomentsEngine.evaluate() ---
    // situationalGuardPassed mirrors the existing blockReason check the
    // presence callers already do (not speaking/capturing/thinking, boot
    // finished, foregrounded, appropriate mode) -- computed by the caller,
    // not re-derived here.
    val situationalGuardPassed: Boolean,
    // Shared with ScoutPresenceDecider's own proactive-remark clock -- to the
    // user there is only one Scout, so a presence greeting and a companion
    // moment draw from the same cooldown, never independent ones.
    val msSinceLastProactiveRemark: Long,
    val proactiveMomentsFiredToday: Int,

    // --- Environment: scoped to the one case Presence doesn't already own --
    // a second face joining, not a return-from-absence or a quiet room (those
    // stay Presence's job; see the design discussion this class implements).
    val secondFaceJustAppeared: Boolean,

    // --- Memory ---
    val staleTaughtFacts: List<StaleFact>,

    // --- Observation -- deliberately no emotion/tone/energy signal exists or
    // is faked here; only what's actually measurable today.
    val habitObservationContentKey: String?,
    val conversationTurnRateElevated: Boolean,

    // --- Curiosity ---
    val continuousPresenceMs: Long,
    val hasHadConversationThisSession: Boolean,
    /** Null means curiosity has never fired before -- treated as maximally overdue. */
    val msSinceLastCuriosityMoment: Long?,
    val isFirstCuriosityOpportunityToday: Boolean,

    // --- First-meeting-today: a cross-cutting bonus, never its own candidate
    // type -- it can strengthen a Memory or Observation candidate but can
    // never by itself manufacture an arrival-shaped moment, since this engine
    // has no such candidate type to begin with (that stays Presence's job).
    /** Null means this person has never been seen before -- trivially "first meeting". */
    val presentPersonLastSeenMs: Long?,
    val zoneId: ZoneId = ZoneId.systemDefault(),

    // --- Tie-break: per-contentKey (not per-category) last-fired time, so two
    // candidates that happen to share a category can still be told apart. A
    // missing entry means "never fired" and sorts as the most overdue.
    val lastFiredMsByContentKey: Map<String, Long> = emptyMap(),

    // --- Per-category cooldown: a hard gate, distinct from the tie-break map
    // above. A category whose own cooldown hasn't elapsed is not even
    // considered for candidate generation this evaluation -- this is what
    // stops e.g. Memory from resurfacing a fact every time the shared
    // cooldown happens to allow *some* moment to fire. A missing entry means
    // "this category has never fired" and never blocks it.
    val lastFiredMsByCategory: Map<MomentCategory, Long> = emptyMap()
)

/**
 * Decides whether Scout should create a Companion Moment -- a small,
 * self-initiated remark that isn't a wake-word reply, a presence courtesy
 * greeting, memory recall, or an LLM response. Every code path in this file
 * exists to answer one question: "why would a thoughtful companion say this
 * right now?" If nothing clears that bar, evaluate() returns null -- which is
 * the expected, common outcome, not a fallback. Silence is not a failure
 * state here; it's the default.
 *
 * Stateless and pure by design (no android.* imports, no internal mutable
 * state) -- every piece of history this engine needs (cooldown clocks, daily
 * budget, last-fired timestamps) is supplied fresh via CompanionSignals on
 * each call, so it stays trivially unit-testable and never needs resetting
 * between calls or across a configuration-change recreation.
 *
 * Hard gates (situational safety, the shared proactive-speech cooldown, the
 * daily budget, and each category's own cooldown) are intentionally kept
 * separate from and unconditionally override confidence scoring -- a
 * high-scoring candidate must never be able to talk its way past a restraint
 * rule. Confidence scoring only decides *which* grounded candidate to use
 * once the hard gates already allow speaking at all.
 */
object ScoutCompanionMomentsEngine {

    // Hard gates -- intentionally conservative starting values for the
    // initial on-device test build. An intrusive Scout is worse than a quiet
    // one; these are meant to loosen only after real A32 testing confirms
    // it's warranted -- never shortened directly just to make testing more
    // convenient. If faster observation is ever needed during testing, add a
    // separate, explicitly labeled smoke-test configuration rather than
    // editing these values in place (see e.g. the temporary, clearly-flagged
    // smoke-test thresholds already used elsewhere in this project, such as
    // MainActivity's IDLE_SILENCE_PRESENCE_THRESHOLD_MS).
    const val SHARED_PROACTIVE_COOLDOWN_MS = 45L * 60L * 1_000L // 45 minutes
    const val DAILY_MOMENT_BUDGET = 3

    /** The single knob that tunes how talkative Scout is overall. */
    const val MOMENT_CONFIDENCE_THRESHOLD = 0.50f

    const val MAX_CONFIDENCE = 1.0f

    // Per-category cooldowns -- a second, independent hard gate on top of the
    // shared cooldown above. The shared cooldown limits how often Scout
    // speaks proactively at all; these limit how often the *same kind* of
    // moment repeats, so e.g. a Memory moment can't resurface again the very
    // next time the shared cooldown happens to allow some other moment to
    // fire. Deliberately rarer than ScoutPresenceDecider's own category
    // cooldowns (30-90 minutes) -- Companion Moments are meant to feel more
    // special than mechanical presence courtesy remarks, not equally routine.
    const val ENVIRONMENT_CATEGORY_COOLDOWN_MS = 2L * 60L * 60L * 1_000L // 2 hours
    const val MEMORY_CATEGORY_COOLDOWN_MS = 24L * 60L * 60L * 1_000L // 24 hours
    const val OBSERVATION_CATEGORY_COOLDOWN_MS = 3L * 60L * 60L * 1_000L // 3 hours
    const val CURIOSITY_CATEGORY_COOLDOWN_MS = 6L * 60L * 60L * 1_000L // 6 hours

    private val CATEGORY_COOLDOWN_MS: Map<MomentCategory, Long> = mapOf(
        MomentCategory.ENVIRONMENT to ENVIRONMENT_CATEGORY_COOLDOWN_MS,
        MomentCategory.MEMORY to MEMORY_CATEGORY_COOLDOWN_MS,
        MomentCategory.OBSERVATION to OBSERVATION_CATEGORY_COOLDOWN_MS,
        MomentCategory.CURIOSITY to CURIOSITY_CATEGORY_COOLDOWN_MS
    )

    // Environment
    const val ENVIRONMENT_NEW_ARRIVAL_SCORE = 0.60f

    // Memory
    const val MEMORY_FACT_BASE_SCORE = 0.50f
    const val MEMORY_MIN_DAYS_SINCE_SURFACE = 3
    const val MEMORY_STALENESS_BONUS_PER_DAY = 0.02f
    const val MEMORY_STALENESS_BONUS_CAP = 0.15f

    // Observation
    const val OBSERVATION_HABIT_BASE_SCORE = 0.45f
    const val OBSERVATION_ACTIVITY_ONLY_BASE_SCORE = 0.35f
    const val OBSERVATION_ELEVATED_ACTIVITY_BONUS = 0.10f

    // Curiosity -- base is deliberately below MOMENT_CONFIDENCE_THRESHOLD on
    // its own. Curiosity must earn its way across the bar through its own
    // grounded conditions below, never by lowering the global threshold.
    const val CURIOSITY_BASE_SCORE = 0.30f
    const val CURIOSITY_MIN_PRESENCE_MS = 20L * 60L * 1_000L // required just to be eligible at all
    const val CURIOSITY_NO_CONVERSATION_YET_BONUS = 0.15f
    const val CURIOSITY_LONG_GAP_BONUS_CAP = 0.15f
    const val CURIOSITY_LONG_GAP_FULL_BONUS_MS = 24L * 60L * 60L * 1_000L // gap at which the bonus maxes out
    const val CURIOSITY_FIRST_OF_DAY_BONUS = 0.10f

    // Cross-cutting: applied only inside Memory/Observation candidate scoring
    // (see CompanionSignals.presentPersonLastSeenMs doc) -- never its own candidate.
    const val FIRST_MEETING_TODAY_BONUS = 0.15f

    // Time-sensitivity rank used only as the first tie-break stage (lower
    // wins). Memory and Observation deliberately share a rank -- both are
    // equally non-urgent reflective remarks -- so the later tie-break stages
    // are real, reachable code paths, not dead branches that could only fire
    // for a comparison that stage 1 would have already resolved.
    private val TIME_SENSITIVITY_RANK: Map<MomentCategory, Int> = mapOf(
        MomentCategory.ENVIRONMENT to 0,
        MomentCategory.MEMORY to 1,
        MomentCategory.OBSERVATION to 1,
        MomentCategory.CURIOSITY to 2
    )

    // Final deterministic fallback -- independent from TIME_SENSITIVITY_RANK
    // above on purpose. They coincide in relative order today, but keeping
    // them as two separately named things means a future candidate type can
    // get its own time-sensitivity rank without also having to redefine the
    // fallback order, or vice versa.
    private val FIXED_CATEGORY_TIEBREAK_ORDER = listOf(
        MomentCategory.ENVIRONMENT, MomentCategory.MEMORY, MomentCategory.OBSERVATION, MomentCategory.CURIOSITY
    )

    fun evaluate(signals: CompanionSignals): MomentCandidate? {
        if (!signals.situationalGuardPassed) return null
        if (signals.msSinceLastProactiveRemark < SHARED_PROACTIVE_COOLDOWN_MS) return null
        if (signals.proactiveMomentsFiredToday >= DAILY_MOMENT_BUDGET) return null

        val candidates = listOfNotNull(
            generateIfCooldownElapsed(MomentCategory.ENVIRONMENT, signals) { generateEnvironmentCandidate(signals) },
            generateIfCooldownElapsed(MomentCategory.MEMORY, signals) { generateMemoryCandidate(signals) },
            generateIfCooldownElapsed(MomentCategory.OBSERVATION, signals) { generateObservationCandidate(signals) },
            generateIfCooldownElapsed(MomentCategory.CURIOSITY, signals) { generateCuriosityCandidate(signals) }
        ).filter { it.confidence >= MOMENT_CONFIDENCE_THRESHOLD }

        return selectWinner(candidates, signals.lastFiredMsByContentKey)
    }

    /**
     * A category still inside its own cooldown is never even considered --
     * the generator isn't called at all, so a category on cooldown can never
     * produce a candidate regardless of how strongly it would otherwise score.
     * This is a hard gate, exactly like the shared cooldown and daily budget
     * in evaluate(): confidence cannot buy a category out of its cooldown.
     */
    private inline fun generateIfCooldownElapsed(
        category: MomentCategory,
        signals: CompanionSignals,
        generate: () -> MomentCandidate?
    ): MomentCandidate? {
        val lastFired = signals.lastFiredMsByCategory[category] ?: return generate()
        val cooldown = CATEGORY_COOLDOWN_MS.getValue(category)
        if (signals.nowMs - lastFired < cooldown) return null
        return generate()
    }

    /**
     * Additive score composition with a hard ceiling at MAX_CONFIDENCE.
     * Deliberately additive, not multiplicative, per design: each
     * contributing factor is an independent, honestly-grounded reason to
     * speak, not a multiplier on the others. The cap exists so that future
     * bonuses stacking together can never produce a score outside the
     * documented 0..1 range, even before any individual bonus is itself capped.
     */
    internal fun composeScore(base: Float, vararg bonuses: Float): Float =
        (base + bonuses.sum()).coerceIn(0f, MAX_CONFIDENCE)

    /**
     * Calendar-day-boundary-safe "have I not seen this person yet today"
     * check -- deliberately compares LocalDate, not elapsed milliseconds, so
     * two visits eight hours apart on the same day never count as two "first
     * meetings", while a visit shortly after midnight correctly does, even
     * though only minutes may separate it from the previous visit.
     */
    internal fun isFirstMeetingToday(lastSeenMs: Long?, nowMs: Long, zoneId: ZoneId): Boolean {
        if (lastSeenMs == null) return true
        val lastSeenDate = Instant.ofEpochMilli(lastSeenMs).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return today.isAfter(lastSeenDate)
    }

    private fun generateEnvironmentCandidate(s: CompanionSignals): MomentCandidate? {
        if (!s.secondFaceJustAppeared) return null
        return MomentCandidate(
            category = MomentCategory.ENVIRONMENT,
            contentKey = "environment:new_arrival",
            groundedIn = listOf("second_face_appeared"),
            contributions = listOf("environment_new_arrival"),
            confidence = composeScore(ENVIRONMENT_NEW_ARRIVAL_SCORE)
        )
    }

    private fun generateMemoryCandidate(s: CompanionSignals): MomentCandidate? {
        val eligible = s.staleTaughtFacts.filter { it.daysSinceLastSurfaced >= MEMORY_MIN_DAYS_SINCE_SURFACE }
        if (eligible.isEmpty()) return null

        // Deterministic pick: stalest fact wins; contentKey breaks any further tie.
        val fact = eligible.sortedWith(
            compareByDescending<StaleFact> { it.daysSinceLastSurfaced }.thenBy { it.contentKey }
        ).first()

        val contributions = mutableListOf("memory_fact_base")
        val groundedIn = mutableListOf("taught_fact:${fact.contentKey}")

        val stalenessBonus = (fact.daysSinceLastSurfaced * MEMORY_STALENESS_BONUS_PER_DAY)
            .coerceAtMost(MEMORY_STALENESS_BONUS_CAP)
        if (stalenessBonus > 0f) contributions += "memory_staleness_bonus"

        val firstMeeting = isFirstMeetingToday(s.presentPersonLastSeenMs, s.nowMs, s.zoneId)
        val firstMeetingBonus = if (firstMeeting) FIRST_MEETING_TODAY_BONUS else 0f
        if (firstMeeting) {
            contributions += "first_meeting_today_bonus"
            groundedIn += "first_meeting_today"
        }

        return MomentCandidate(
            category = MomentCategory.MEMORY,
            contentKey = fact.contentKey,
            groundedIn = groundedIn,
            contributions = contributions,
            confidence = composeScore(MEMORY_FACT_BASE_SCORE, stalenessBonus, firstMeetingBonus)
        )
    }

    private fun generateObservationCandidate(s: CompanionSignals): MomentCandidate? {
        val habitKey = s.habitObservationContentKey
        if (habitKey == null && !s.conversationTurnRateElevated) return null

        val contributions = mutableListOf<String>()
        val groundedIn = mutableListOf<String>()
        val base: Float
        val contentKey: String
        val activityBonus: Float

        if (habitKey != null) {
            base = OBSERVATION_HABIT_BASE_SCORE
            contentKey = habitKey
            contributions += "observation_habit_base"
            groundedIn += "habit_observation:$habitKey"
            activityBonus = if (s.conversationTurnRateElevated) OBSERVATION_ELEVATED_ACTIVITY_BONUS else 0f
            if (s.conversationTurnRateElevated) {
                contributions += "observation_elevated_activity_bonus"
                groundedIn += "conversation_turn_rate_elevated"
            }
        } else {
            base = OBSERVATION_ACTIVITY_ONLY_BASE_SCORE
            contentKey = "observation:elevated_activity"
            contributions += "observation_activity_only_base"
            groundedIn += "conversation_turn_rate_elevated"
            activityBonus = 0f
        }

        val firstMeeting = isFirstMeetingToday(s.presentPersonLastSeenMs, s.nowMs, s.zoneId)
        val firstMeetingBonus = if (firstMeeting) FIRST_MEETING_TODAY_BONUS else 0f
        if (firstMeeting) {
            contributions += "first_meeting_today_bonus"
            groundedIn += "first_meeting_today"
        }

        return MomentCandidate(
            category = MomentCategory.OBSERVATION,
            contentKey = contentKey,
            groundedIn = groundedIn,
            contributions = contributions,
            confidence = composeScore(base, activityBonus, firstMeetingBonus)
        )
    }

    private fun generateCuriosityCandidate(s: CompanionSignals): MomentCandidate? {
        if (s.continuousPresenceMs < CURIOSITY_MIN_PRESENCE_MS) return null

        val contributions = mutableListOf("curiosity_base")
        val groundedIn = mutableListOf("continuous_presence_ms:${s.continuousPresenceMs}")

        val noConversationBonus = if (!s.hasHadConversationThisSession) CURIOSITY_NO_CONVERSATION_YET_BONUS else 0f
        if (!s.hasHadConversationThisSession) {
            contributions += "curiosity_no_conversation_yet_bonus"
            groundedIn += "no_conversation_this_session"
        }

        val gapMs = s.msSinceLastCuriosityMoment
        val gapBonus = if (gapMs == null) {
            CURIOSITY_LONG_GAP_BONUS_CAP
        } else {
            (gapMs.toFloat() / CURIOSITY_LONG_GAP_FULL_BONUS_MS.toFloat() * CURIOSITY_LONG_GAP_BONUS_CAP)
                .coerceIn(0f, CURIOSITY_LONG_GAP_BONUS_CAP)
        }
        if (gapBonus > 0f) {
            contributions += "curiosity_long_gap_bonus"
            groundedIn += "ms_since_last_curiosity:${gapMs ?: "never"}"
        }

        val firstOfDayBonus = if (s.isFirstCuriosityOpportunityToday) CURIOSITY_FIRST_OF_DAY_BONUS else 0f
        if (s.isFirstCuriosityOpportunityToday) {
            contributions += "curiosity_first_of_day_bonus"
            groundedIn += "first_curiosity_opportunity_today"
        }

        return MomentCandidate(
            category = MomentCategory.CURIOSITY,
            contentKey = "curiosity:light_question",
            groundedIn = groundedIn,
            contributions = contributions,
            confidence = composeScore(CURIOSITY_BASE_SCORE, noConversationBonus, gapBonus, firstOfDayBonus)
        )
    }

    /**
     * Deterministic winner selection: highest confidence first; ties broken
     * by time-sensitivity rank, then by which candidate's content has gone
     * longest without being said (missing history = never said = most
     * overdue = wins), then by a fixed category order as the final,
     * unconditional fallback. Exposed internally (not private) so the
     * tie-break contract itself is directly unit-testable independent of
     * which generators happen to produce colliding scores today.
     */
    internal fun selectWinner(
        candidates: List<MomentCandidate>,
        lastFiredMsByContentKey: Map<String, Long>
    ): MomentCandidate? {
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareByDescending<MomentCandidate> { it.confidence }
                .thenBy { TIME_SENSITIVITY_RANK.getValue(it.category) }
                .thenBy { lastFiredMsByContentKey[it.contentKey] ?: 0L }
                .thenBy { FIXED_CATEGORY_TIEBREAK_ORDER.indexOf(it.category) }
        ).first()
    }
}
