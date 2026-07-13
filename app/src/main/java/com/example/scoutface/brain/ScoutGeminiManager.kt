package com.example.scoutface.brain

import android.util.Log
import com.example.scoutface.GeminiClient
import com.example.scoutface.HabitLayer
import com.example.scoutface.TruthDb
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles Scout's Gemini orchestration.
 *
 * This file does NOT touch speech recognition, camera, downloads,
 * weather, presence, or memory storage.
 *
 * It only manages:
 *   - Gemini enabled checks
 *   - API key / internet checks
 *   - duplicate prompt protection
 *   - request spacing
 *   - worker thread call to GeminiClient
 *   - fallback message selection
 *
 * Quota message discipline (May 23, 2026):
 *   When Gemini is unavailable or in quota, Scout says so ONCE.
 *   After that he stays quiet for the rest of the cooldown window.
 *   This prevents the same "I'm limited" message repeating every
 *   time the user asks anything while Gemini is down.
 */
class ScoutGeminiManager(
    private val geminiClient: GeminiClient,
    private val truthDb: TruthDb,
    private val habitLayer: HabitLayer,
    private val isGeminiEnabled: () -> Boolean,
    private val hasApiKey: () -> Boolean,
    private val hasValidatedInternet: () -> Boolean,
    private val runOnMain: (() -> Unit) -> Unit,
    private val respond: (String) -> Unit,
    private val finishThinking: () -> Unit
) {

    // =======================
    // REQUEST DISCIPLINE
    // =======================

    private val requestInFlight = AtomicBoolean(false)

    private var lastRequestStartedMs  = 0L
    private var lastPrompt            = ""
    private var lastPromptMs          = 0L
    private var lastGeminiReply: String? = null
    private var lastGeminiReplyMs     = 0L

    private val minRequestGapMs          = 2_500L
    private val duplicatePromptWindowMs  = 45_000L
    private val REPLY_CACHE_TTL_MS       = 4L * 60L * 1_000L


    // =======================
    // QUOTA MESSAGE DISCIPLINE
    // =======================

    /**
     * Tracks when Scout last spoke the "Gemini unavailable" message.
     * Scout only says it once per cooldown window — not on every request.
     */
    private var unavailableLastSpokenMs = 0L

    /**
     * How long to stay quiet after saying the short-cooldown message.
     * 10 minutes — long enough to stop the repetition without
     * hiding the situation from the user all day.
     */
    private val shortCooldownRepeatGapMs = 10L * 60L * 1_000L

    /**
     * How long to stay quiet after saying the daily quota message.
     * Matches GeminiClient's 6-hour daily cooldown window.
     */
    private val dailyQuotaRepeatGapMs = 6L * 60L * 60L * 1_000L


    // =======================
    // MAIN ENTRY POINT
    // =======================

    fun tryGemini(
        qNorm: String,
        conversation: List<Pair<String, String>>,
        onStarted: (() -> Unit)? = null,
        onAnswered: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ): Boolean {

        val enabled   = isGeminiEnabled()
        val hasKey    = hasApiKey()
        val validated = hasValidatedInternet()

        if (!enabled) {
            Log.e("ScoutGemini", "Blocked: Gemini disabled")
            return false
        }

        if (!hasKey) {
            Log.e("ScoutGemini", "Blocked: API key blank")
            return false
        }

        if (!validated) {
            Log.e("ScoutGemini", "Blocked: internet not validated")
            return false
        }

        val now = System.currentTimeMillis()

        if (!requestInFlight.compareAndSet(false, true)) {
            Log.e("ScoutGemini", "Blocked: request already in flight")
            finishThinking()
            return true
        }

        if (qNorm == lastPrompt && now - lastPromptMs < duplicatePromptWindowMs) {
            val cached = lastGeminiReply
            if (cached != null && now - lastGeminiReplyMs < REPLY_CACHE_TTL_MS) {
                requestInFlight.set(false)
                Log.e("ScoutGemini", "Duplicate prompt — replaying cached answer")
                respond(cached)
                return true
            }
            // No cached answer — let it through so the user gets a response
            Log.e("ScoutGemini", "Duplicate prompt, no cache — allowing through")
            lastPromptMs = 0L
        }

        if (now - lastRequestStartedMs < minRequestGapMs) {
            requestInFlight.set(false)
            Log.e("ScoutGemini", "Blocked: cooldown active")
            finishThinking()
            return true
        }

        lastRequestStartedMs = now
        lastPrompt           = qNorm
        lastPromptMs         = now

        val system = ScoutPromptBuilder.buildSystemInstruction(
            truthDb    = truthDb,
            habitLayer = habitLayer
        )

        onStarted?.invoke()

        Thread {
            try {
                Log.e("ScoutGemini", "Gemini thread started. convoSize=${conversation.size}")

                val reply = geminiClient.generateReply(
                    systemInstruction = system,
                    conversation      = conversation
                )

                Log.e("ScoutGemini", "Gemini thread finished. replyBlank=${reply.isNullOrBlank()}")

                runOnMain {
                    requestInFlight.set(false)

                    val out = reply?.trim().takeUnless { it.isNullOrBlank() }

                    if (out != null) {
                        // Gemini answered — cache it, reset unavailable timer.
                        unavailableLastSpokenMs = 0L
                        lastGeminiReply   = out
                        lastGeminiReplyMs = System.currentTimeMillis()
                        onAnswered?.invoke()
                        respond(out)
                    } else {
                        // Gemini returned nothing — quota, timeout, or error.
                        // Let caller try TinyLlama first; only speak unavailable if
                        // no fallback was provided or the fallback didn't handle it.
                        if (onFailed != null) onFailed.invoke() else speakUnavailableIfNeeded()
                    }
                }

            } catch (e: Throwable) {
                Log.e("ScoutGemini", "Gemini thread crashed", e)
                runOnMain {
                    requestInFlight.set(false)
                    if (onFailed != null) onFailed.invoke() else speakUnavailableIfNeeded()
                }
            }
        }.start()

        return true
    }


    // =======================
    // QUOTA MESSAGE HELPER
    // =======================

    /**
     * Speaks the Gemini unavailable message ONCE per cooldown window.
     * After that, stays silent — the user already knows.
     *
     * Uses a longer repeat gap for daily quota exhaustion since
     * that won't clear for hours.
     */
    /** Returns true if Gemini is currently in any cooldown window. */
    fun isInCooldown(): Boolean = geminiClient.isInCooldown()

    /**
     * Speaks the Gemini unavailable message ONCE per cooldown window.
     * Returns true if the message was spoken, false if it was suppressed
     * (already announced recently). Callers use the return value to decide
     * whether to also try a local fallback on the same turn.
     */
    fun speakUnavailableIfNeeded(): Boolean {
        val now = System.currentTimeMillis()

        val repeatGap = if (geminiClient.isDailyQuotaExhausted()) {
            dailyQuotaRepeatGapMs
        } else {
            shortCooldownRepeatGapMs
        }

        return if (now - unavailableLastSpokenMs > repeatGap) {
            unavailableLastSpokenMs = now
            respond(ScoutPromptBuilder.buildOnlineUnavailableMessage(geminiClient))
            true
        } else {
            // Already told the user — stay quiet this time.
            Log.e("ScoutGemini", "Quota message suppressed (already announced recently)")
            finishThinking()
            false
        }
    }
}