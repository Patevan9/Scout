package com.example.scoutface.brain

import com.example.scoutface.GeminiClient
import com.example.scoutface.HabitLayer
import com.example.scoutface.TruthDb

/**
 * Builds the text Scout sends to Gemini and the text Scout speaks when
 * the online connection is unavailable.
 *
 * Pure string assembly only. Does NOT touch speech, camera, or downloads.
 *
 * Updated May 19, 2026 — buildOnlineUnavailableMessage() now checks
 * isDailyQuotaExhausted() first so Scout gives an honest "daily limit"
 * message instead of "back in a minute" when the whole day's quota is gone.
 */
object ScoutPromptBuilder {

    private const val ENTITY_SCOUT = "scout"

    fun buildSystemInstruction(
        truthDb: TruthDb,
        habitLayer: HabitLayer
    ): String {
        val scoutName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"
        val habitContext = habitLayer.getSummaryForGemini()

        return """
You are $scoutName, a calm, friendly family companion robot.

Be concise, safe, and helpful. If you don't know, say you don't know.

Do not claim abilities you don't have.

Keep responses short (1–3 sentences) unless asked for more.

$habitContext

""".trim()
    }

    /**
     * Three states, three messages:
     *
     *   1. Daily quota exhausted  → honest "daily limit" message
     *   2. Short cooldown active  → "back in about X minutes"
     *   3. Generic failure        → "having trouble reaching online"
     */
    fun buildOnlineUnavailableMessage(geminiClient: GeminiClient): String {
        if (geminiClient.isDailyQuotaExhausted()) {
            return "Gemini says you've reached your daily limit, but " +
                    "regardless of that, I can do my best locally to help " +
                    "any way I can."
        }
        if (geminiClient.isInCooldown()) {
            val remainMs = geminiClient.cooldownRemainingMs()
            val mins = ((remainMs + 59_999L) / 60_000L).toInt()
            return if (mins <= 1) {
                "I've reached my online limit for now. I'll be back in about a minute."
            } else {
                "I've reached my online limit for now. I'll be back in about $mins minutes."
            }
        }
        return "I'm having trouble reaching online at the moment."
    }
}