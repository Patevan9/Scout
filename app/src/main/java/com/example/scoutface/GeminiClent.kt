package com.example.scoutface

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thin wrapper around Google's generateContent endpoint.
 *
 * Safety mechanisms on top of plain HTTP:
 *
 *   1. Single-flight guard — Scout can never have two Gemini requests
 *      open at the same time.
 *
 *   2. Cooldown clock — honors Google's 429 retryDelay. When the API
 *      says "retry in 9s", we refuse to call again for 9 seconds plus
 *      a small buffer.
 *
 *   3. Daily-quota detection — when a 429 specifically indicates a
 *      per-day quota exhaustion (quotaId contains "PerDay"), Scout sets
 *      a much longer cooldown (6 hours) and exposes isDailyQuotaExhausted()
 *      so the caller can give a smarter, more honest message.
 *
 * generateReply() is synchronous. Call it from a worker thread (MainActivity
 * already does this inside Thread { }).
 */
class GeminiClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String
) {

    // =======================
    // PUBLIC STATE
    // =======================

    /** True when a per-minute or per-request cooldown is active. */
    fun isInCooldown(): Boolean {
        return System.currentTimeMillis() < cooldownUntilMs.get()
    }

    /** How many milliseconds remain on the current cooldown. 0 if none. */
    fun cooldownRemainingMs(): Long {
        val remaining = cooldownUntilMs.get() - System.currentTimeMillis()
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * True when the daily quota specifically has been exhausted.
     * This is a distinct state from a short rate-limit cooldown.
     * ScoutPromptBuilder checks this to choose the right message.
     */
    fun isDailyQuotaExhausted(): Boolean {
        return System.currentTimeMillis() < dailyQuotaExhaustedUntilMs.get()
    }

    /** True when a request is mid-flight on another thread. */
    fun isRequestInFlight(): Boolean = requestInFlight.get()


    // =======================
    // INTERNAL STATE
    // =======================

    private val requestInFlight            = AtomicBoolean(false)
    private val cooldownUntilMs            = AtomicLong(0L)
    private val dailyQuotaExhaustedUntilMs = AtomicLong(0L)

    // Buffer added on top of Google's retryDelay so we do not hit
    // the API the same instant the window opens.
    private val COOLDOWN_BUFFER_MS = 5_000L

    // Fallback short cooldown when retryDelay cannot be parsed.
    private val FALLBACK_COOLDOWN_MS = 60_000L

    // How long to wait after a daily quota exhaustion.
    // The daily quota resets at midnight Pacific. 6 hours is a
    // reasonable retry window — if the quota is still gone when we
    // try again, we set another 6-hour cooldown automatically.
    private val DAILY_QUOTA_COOLDOWN_MS = 60L * 60L * 1000L  // 1 hour — retry sooner


    // =======================
    // MAIN ENTRY POINT
    // =======================

    fun generateReply(
        systemInstruction: String,
        conversation: List<Pair<String, String>>
    ): String? {

        // 1) Refuse fast if any cooldown is active.
        val nowAtEntry = System.currentTimeMillis()
        val cooldownAt = cooldownUntilMs.get()
        if (nowAtEntry < cooldownAt) {
            val remainSec = (cooldownAt - nowAtEntry) / 1000L
            Log.e("ScoutGemini", "Blocked: cooldown active, ${remainSec}s remaining")
            return null
        }

        // 2) Single-flight guard.
        if (!requestInFlight.compareAndSet(false, true)) {
            Log.e("ScoutGemini", "Blocked: request already in flight")
            return null
        }

        try {
            val key = apiKeyProvider().trim()
            if (key.isBlank()) {
                Log.e("ScoutGemini", "Blocked: API key blank")
                return null
            }

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "${modelProvider().trim()}:generateContent"
            )

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", key)
            }

            val contents = JSONArray()
            for ((role, text) in conversation) {
                val cleanText = text.trim()
                if (cleanText.isBlank()) continue
                val apiRole = if (role.lowercase() == "user") "user" else "model"
                contents.put(
                    JSONObject()
                        .put("role", apiRole)
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", cleanText))
                        )
                )
            }

            val payload = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                .put("contents", contents)
                .put(
                    "generationConfig",
                    JSONObject().put("maxOutputTokens", 400)
                )

            OutputStreamWriter(conn.outputStream).use {
                it.write(payload.toString())
            }

            val code = conn.responseCode
            val raw = if (code in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            }

            // 3) Quota exhausted. Check whether this is a daily limit or
            //    a short rate-limit and set the right cooldown duration.
            if (code == 429) {
                if (isDailyQuotaInError(raw)) {
                    // Daily quota — long cooldown, plus the separate flag
                    // so ScoutPromptBuilder can show a more honest message.
                    val until = System.currentTimeMillis() + DAILY_QUOTA_COOLDOWN_MS
                    dailyQuotaExhaustedUntilMs.set(until)
                    cooldownUntilMs.set(until)
                    Log.e("ScoutGemini", "HTTP 429 (daily quota). Cooling down 6h.")
                } else {
                    // Regular rate-limit — short cooldown based on Google's hint.
                    val parsed = parseRetryDelayMs(raw)
                    val waitMs = (parsed ?: FALLBACK_COOLDOWN_MS) + COOLDOWN_BUFFER_MS
                    cooldownUntilMs.set(System.currentTimeMillis() + waitMs)
                    Log.e("ScoutGemini", "HTTP 429 (rate limit). Cooling down ${waitMs}ms.")
                }
                return null
            }

            if (code !in 200..299) {
                Log.e("ScoutGemini", "HTTP $code: $raw")
                return null
            }

            val json = JSONObject(raw)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)

            val finishReason = candidate?.optString("finishReason").orEmpty()
            if (finishReason.isNotBlank()) {
                Log.e("ScoutGemini", "finishReason=$finishReason")
            }

            val rawText = candidate
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()

            // If Gemini hit the token limit mid-sentence, trim to the last
            // complete sentence so Scout never speaks a dangling half-sentence.
            val text = if (!rawText.isNullOrBlank() && finishReason == "MAX_TOKENS") {
                val lastPunct = rawText.lastIndexOfAny(charArrayOf('.', '!', '?'))
                if (lastPunct > 0) {
                    Log.e("ScoutGemini", "MAX_TOKENS: trimming to last sentence end at $lastPunct")
                    rawText.substring(0, lastPunct + 1)
                } else rawText
            } else rawText

            return if (text.isNullOrBlank()) {
                Log.e("ScoutGemini", "Blank Gemini text. Raw=$raw")
                null
            } else {
                text
            }

        } catch (e: Exception) {
            Log.e("ScoutGemini", "Gemini request failed", e)
            return null
        } finally {
            // Always release the in-flight lock, no matter how we exit.
            requestInFlight.set(false)
        }
    }


    // =======================
    // DAILY QUOTA DETECTION
    // =======================

    /**
     * Returns true when the 429 error body specifically indicates a
     * per-day quota exhaustion. Looks inside the QuotaFailure violation
     * list for a quotaId containing "PerDay".
     *
     * Distinct from a short per-minute rate limit — lets Scout give a
     * more honest "daily limit" message rather than "back in a minute."
     */
    private fun isDailyQuotaInError(rawErrorJson: String): Boolean {
        if (rawErrorJson.isBlank()) return false
        return try {
            val root = JSONObject(rawErrorJson)
            val details = root.optJSONObject("error")?.optJSONArray("details")
                ?: return false
            for (i in 0 until details.length()) {
                val item = details.optJSONObject(i) ?: continue
                val type = item.optString("@type", "")
                if (!type.endsWith("QuotaFailure")) continue
                val violations = item.optJSONArray("violations") ?: continue
                for (j in 0 until violations.length()) {
                    val v = violations.optJSONObject(j) ?: continue
                    val quotaId = v.optString("quotaId", "")
                    if (quotaId.contains("PerDay", ignoreCase = true)) return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }


    // =======================
    // RETRY DELAY PARSER
    // =======================

    /**
     * Pulls the retry delay (in ms) out of a Google rpc error envelope.
     * Looks for "retryDelay": "9s" or "9.019s" inside the details array.
     * Returns null if no retry delay can be found.
     */
    private fun parseRetryDelayMs(rawErrorJson: String): Long? {
        if (rawErrorJson.isBlank()) return null
        return try {
            val root = JSONObject(rawErrorJson)
            val details = root.optJSONObject("error")?.optJSONArray("details")
                ?: return null
            for (i in 0 until details.length()) {
                val item = details.optJSONObject(i) ?: continue
                val type = item.optString("@type", "")
                if (!type.endsWith("RetryInfo")) continue
                val raw = item.optString("retryDelay", "")
                if (raw.isBlank()) continue
                val numStr = raw.trim().removeSuffix("s").trim()
                val seconds = numStr.toDoubleOrNull() ?: continue
                return (seconds * 1000.0).toLong()
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
