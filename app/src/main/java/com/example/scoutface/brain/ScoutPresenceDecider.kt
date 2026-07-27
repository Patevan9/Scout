package com.example.scoutface.brain

import android.util.Log
import java.util.Calendar

/**
 * Scout's social timing and presence behavior layer.
 *
 * Decides:
 *   - When Scout should respond to input
 *   - Whether Scout can make a spontaneous comment
 *   - How Scout's presence level shifts through the day
 *   - When to acknowledge a long absence warmly
 *
 * Scout's presence philosophy:
 *   Calmly present. Not constantly talking. Not passive.
 *   Not hyperactive. Not randomly interruptive.
 *
 * Four presence modes driven by time of day:
 *   ACTIVE  — 9am to 7pm.  Fully engaged.
 *   CALM    — 6am–9am and 8pm–10pm.  Gentler, less spontaneous.
 *   QUIET   — 10pm to midnight.  Minimal spontaneous comments.
 *   SLEEP   — Midnight to 6am.  Only responds if directly addressed.
 *
 * Social battery (0–100):
 *   Depletes a little with each conversation turn.
 *   Recharges slowly during quiet periods.
 *   When low, Scout holds back spontaneous comments.
 *
 * Does NOT touch speech recognition, TTS, camera, downloads,
 * weather, Gemini, or any memory layer.
 */
class ScoutPresenceDecider(
    private val isSpontaneousCommentsEnabled: () -> Boolean,
    private val isPresenceModeEnabled: () -> Boolean
) {

    // =======================
    // PRESENCE MODE
    // =======================

    enum class PresenceMode {
        ACTIVE,  // Daytime — fully engaged
        CALM,    // Morning wake-up or evening wind-down
        QUIET,   // Late night — minimal, mostly reactive
        SLEEP    // Very late or very early — direct address only
    }


    // =======================
    // SOCIAL BATTERY
    // =======================

    /** 0–100. Starts full. Depletes with conversation, recharges during silence. */
    private var socialBattery = 100

    private val BATTERY_COST_PER_TURN  = 8
    private val BATTERY_LOW_THRESHOLD  = 25
    private val BATTERY_RECHARGE_PER_MINUTE = 5


    // =======================
    // TIMING STATE
    // =======================

    private var lastConversationTurnMs   = 0L
    private var lastSpontaneousCommentMs = 0L
    private var knownFaceNearby          = false

    /** 30 minutes of silence triggers a warm return greeting. */
    private val LONG_ABSENCE_THRESHOLD_MS = 30L * 60L * 1_000L

    /** Minimum gap between spontaneous comments — 8 minutes. */
    private val MIN_SPONTANEOUS_GAP_MS = 8L * 60L * 1_000L

    /** Set to true when a long absence is detected. Cleared after greeting is consumed. */
    private var pendingLongAbsenceGreeting = false


    // =======================
    // PUBLIC API
    // =======================

    /**
     * Should Scout respond to this input right now?
     *
     * In SLEEP mode Scout only responds if the input sounds like
     * a direct address — "hey Scout", "wake up", etc.
     * In all other modes Scout always responds.
     * If presence mode is off entirely, always respond.
     *
     * currentName is passed in by the caller (the same TruthDb-configured name
     * used by wake-word detection) rather than stored here, so there's only ever
     * one source of truth for Scout's name.
     */
    fun shouldRespondToInput(qNorm: String, currentName: String): Boolean {
        if (!isPresenceModeEnabled()) return true
        rechargeIfNeeded()
        return if (getCurrentMode() == PresenceMode.SLEEP) {
            looksLikeDirectAddress(qNorm, currentName)
        } else {
            true
        }
    }

    /**
     * Can Scout make a spontaneous comment right now?
     *
     * Checks in order:
     *   1. Spontaneous comments toggle in Settings
     *   2. Presence mode enabled
     *   3. Not in QUIET or SLEEP mode
     *   4. Social battery above low threshold
     *   5. Enough time since the last spontaneous comment
     */
    fun shouldMakeSpontaneousComment(): Boolean {
        if (!isSpontaneousCommentsEnabled()) return false
        if (!isPresenceModeEnabled())        return false

        rechargeIfNeeded()

        val mode = getCurrentMode()
        if (mode == PresenceMode.QUIET || mode == PresenceMode.SLEEP) return false
        if (socialBattery < BATTERY_LOW_THRESHOLD) return false

        val now = System.currentTimeMillis()
        if (now - lastSpontaneousCommentMs < MIN_SPONTANEOUS_GAP_MS) return false

        return true
    }

    /**
     * Call this after Scout makes a spontaneous comment so the
     * timer and battery are updated correctly.
     */
    fun onSpontaneousCommentMade() {
        lastSpontaneousCommentMs = System.currentTimeMillis()
        depleteBattery()
    }

    /**
     * Call this after every conversation exchange (in respond()).
     * Updates the social battery and checks for long absences.
     */
    fun onConversationTurn() {
        val now = System.currentTimeMillis()

        if (lastConversationTurnMs > 0L &&
            now - lastConversationTurnMs > LONG_ABSENCE_THRESHOLD_MS) {
            pendingLongAbsenceGreeting = true
            Log.e("ScoutPresence", "Long absence detected — return greeting queued")
        }

        lastConversationTurnMs = now
        depleteBattery()

        Log.e("ScoutPresence",
            "Turn recorded. battery=$socialBattery mode=${getCurrentMode()}")
    }

    /**
     * Call this when the camera detects a face.
     */
    fun onFaceDetected(isKnown: Boolean) {
        knownFaceNearby = isKnown
    }

    /**
     * Call this when no face has been visible for a while.
     */
    fun onFaceLost() {
        knownFaceNearby = false
    }

    /**
     * Returns a warm return greeting if Scout is coming back from
     * a long absence, then clears the flag so it only plays once.
     * Returns null if no greeting is pending.
     *
     * Call this at the start of handleQuery() before routing.
     */
    fun consumeLongAbsenceGreeting(): String? {
        if (!pendingLongAbsenceGreeting) return null
        pendingLongAbsenceGreeting = false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning. It is good to hear from you."
            hour < 17 -> "Welcome back."
            hour < 21 -> "Good evening."
            else      -> "You are up late."
        }
    }

    /**
     * Returns the current presence mode based on time of day.
     * Exposed so face animation can hint at Scout's current energy level.
     */
    fun getCurrentMode(): PresenceMode {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5   -> PresenceMode.SLEEP   // Midnight – 6am
            in 6..8   -> PresenceMode.CALM    // Waking up
            in 9..19  -> PresenceMode.ACTIVE  // 9am – 7pm
            in 20..21 -> PresenceMode.CALM    // 8pm – 9pm
            in 22..23 -> PresenceMode.QUIET   // 10pm – midnight
            else      -> PresenceMode.ACTIVE
        }
    }

    /**
     * Current battery level (0–100).
     * Can be read by MainActivity for face animation hints.
     */
    fun getSocialBattery(): Int = socialBattery


    // =======================
    // INTERNAL HELPERS
    // =======================

    private fun depleteBattery() {
        socialBattery = (socialBattery - BATTERY_COST_PER_TURN).coerceAtLeast(0)
    }

    /**
     * Passively recharges the battery based on how long Scout
     * has been quiet since the last conversation turn.
     * Called before any decision that checks battery level.
     */
    private fun rechargeIfNeeded() {
        if (lastConversationTurnMs == 0L) return
        val now = System.currentTimeMillis()
        val minutesSilent = ((now - lastConversationTurnMs) / 60_000L).toInt()
        if (minutesSilent <= 0) return
        val recharge = (minutesSilent * BATTERY_RECHARGE_PER_MINUTE)
            .coerceAtMost(100 - socialBattery)
        if (recharge > 0) {
            socialBattery = (socialBattery + recharge).coerceAtMost(100)
            Log.e("ScoutPresence",
                "Battery recharged +$recharge → $socialBattery ($minutesSilent min silent)")
        }
    }

    /**
     * Returns true if the input sounds like a direct address to Scout.
     * Used in SLEEP mode to decide whether to respond at all.
     */
    private fun looksLikeDirectAddress(qNorm: String, currentName: String): Boolean {
        val q = qNorm.trim().lowercase()
        val nameLower = currentName.trim().lowercase()
        return q.startsWith(nameLower) ||
                q.contains("hey $nameLower") ||
                q.contains("hello $nameLower") ||
                q.contains("wake up") ||
                q.contains("are you there") ||
                q.contains("are you awake")
    }


    // =======================
    // PRESENCE LAYER -- IDLE-SILENCE ACKNOWLEDGMENT
    //
    // First, narrowest "presence moment": a rare, quiet acknowledgment after
    // someone's been continuously present for a long stretch with no
    // conversation at all -- not a check-in, not a question, just proof Scout's
    // still there. Two cooldowns gate it: a global one shared by every presence
    // moment (so future moments, like a proactive return greeting, can't stack
    // close together with this one), and a longer category cooldown so this
    // specific moment itself doesn't repeat too often.
    // =======================

    /** How long someone must be continuously present, with no conversation, before
     *  the first idle-silence acknowledgment can fire. Deliberately conservative
     *  for this first version -- easy to shorten once the rhythm's been tested. */
    private val IDLE_SILENCE_PRESENCE_THRESHOLD_MS = 75L * 60L * 1_000L // ~75 min

    /** Minimum time since ANY Scout-initiated presence remark -- any category --
     *  before another one can fire. */
    private val PRESENCE_GLOBAL_COOLDOWN_MS = 20L * 60L * 1_000L // 20 min

    /** Minimum time between idle-silence acknowledgments specifically. */
    private val IDLE_SILENCE_CATEGORY_COOLDOWN_MS = 90L * 60L * 1_000L // 90 min

    private var lastPresenceRemarkMs    = 0L // any presence-moment category
    private var lastIdleSilenceRemarkMs = 0L // this category specifically

    /**
     * Can Scout make the idle-silence acknowledgment right now?
     *
     * continuousPresenceMs is supplied by the caller (MainActivity owns face
     * tracking; this class deliberately doesn't touch the camera) using its own
     * gap-tolerant presence measurement -- a brief missed frame shouldn't reset
     * this the way it would the arrival-greeting timer.
     */
    fun canMakeIdleSilenceRemark(continuousPresenceMs: Long): Boolean {
        if (!isPresenceModeEnabled()) return false

        val mode = getCurrentMode()
        if (mode == PresenceMode.QUIET || mode == PresenceMode.SLEEP) return false

        if (continuousPresenceMs < IDLE_SILENCE_PRESENCE_THRESHOLD_MS) return false

        val now = System.currentTimeMillis()
        val msSinceLastConversation =
            if (lastConversationTurnMs == 0L) Long.MAX_VALUE else now - lastConversationTurnMs
        if (msSinceLastConversation < IDLE_SILENCE_PRESENCE_THRESHOLD_MS) return false

        if (now - lastPresenceRemarkMs < PRESENCE_GLOBAL_COOLDOWN_MS) return false
        if (now - lastIdleSilenceRemarkMs < IDLE_SILENCE_CATEGORY_COOLDOWN_MS) return false

        return true
    }

    /** Call after Scout actually speaks the idle-silence acknowledgment. */
    fun onIdleSilenceRemarkMade() {
        val now = System.currentTimeMillis()
        lastPresenceRemarkMs = now
        lastIdleSilenceRemarkMs = now
    }
}