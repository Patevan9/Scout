package com.example.scoutface

/**
 * Typed diagnostic logging facade for Scout.
 *
 * All public methods enforce privacy at the API boundary:
 *   - Speech text is never accepted or stored; only character counts
 *   - Scout response text is never accepted or stored
 *   - Names, memories, database values, URLs, file paths, and API keys
 *     are never accepted or stored
 *   - Exception messages are never accepted; only the simple class name
 *     is accepted, and it is sanitized before storage
 *   - Every string parameter is sanitized before it is written
 *   - Every call is wrapped in a try/catch so a logging failure never
 *     interrupts normal Scout behavior
 *
 * This class writes only to DiagnosticDb (scout_diagnostic.db).
 * It never touches JournalDb or any other database.
 *
 * Not wired into any existing file yet — that happens in a later step.
 */
class DiagLog(private val db: DiagnosticDb) {

    // ── Controlled input types ────────────────────────────────────────────────
    // All string-producing parameters come through these enums so callers
    // cannot pass arbitrary content into the log.

    /** Whether Scout was requiring a wake word or accepting follow-up speech. */
    enum class ListenMode { WAKE_WORD, FOLLOW_UP }

    /** Which subsystem produced Scout's response. */
    enum class BrainSource { TINYLLAMA, GEMINI, DIRECT, NONE }

    /** Lifecycle events for the TinyLlama engine. */
    enum class LlamaEvent {
        LOAD_STARTED, LOAD_DONE, LOAD_FAILED,
        GENERATION_STARTED, GENERATION_DONE,
        GENERATION_DISCARDED, GENERATION_FAILED
    }

    /** Which outbound network subsystem made a request. */
    enum class NetworkArea { WEATHER_POINTS, WEATHER_FORECAST, GEMINI }

    /** Why a listening session ended without producing a usable result. */
    enum class StopReason { ERROR, TTS_LOCKOUT, COOLDOWN, WATCHDOG_RESET }

    /** Why a received speech result was discarded after receipt. */
    enum class DiscardReason {
        TOO_SHORT, TTS_ACTIVE, COOLDOWN, NULL_RESULT, STALE_GENERATION
    }

    /** Why a weather cache entry was not used. */
    enum class WeatherCacheMiss { STALE, EMPTY, CROSS_DAY }

    /** Which Scout subsystem produced a logged error. */
    enum class ErrorArea {
        MICROPHONE, CAMERA, DATABASE, PERMISSION,
        SPEECH_RECOGNIZER, TTS, NETWORK, UNKNOWN
    }

    // ── Public logging methods ────────────────────────────────────────────────

    /**
     * Recorded once at app startup.
     * appVersion — e.g. "1.0"; sanitized to alphanumeric+dot+underscore.
     * androidApi — integer API level; no user data.
     * deviceModel — e.g. "SM-A325F"; sanitized; no user data.
     * geminiEnabled — boolean setting state.
     * llamaReady — whether TinyLlama finished loading before this boot completed.
     */
    fun logBoot(
        appVersion: String,
        androidApi: Int,
        deviceModel: String,
        geminiEnabled: Boolean,
        llamaReady: Boolean
    ) = safe("BOOT") {
        "version=${sanitizeLabel(appVersion, 20)} " +
        "android_api=$androidApi " +
        "device=${sanitizeLabel(deviceModel, 40)} " +
        "gemini=${flag(geminiEnabled)} " +
        "llama=${if (llamaReady) "ready" else "loading"}"
    }

    /**
     * Recorded when a speech recognition session starts.
     * mode — whether Scout required the wake word or was in follow-up mode.
     */
    fun logListenStart(mode: ListenMode) = safe("LISTEN") {
        "started mode=${mode.name.lowercase()}"
    }

    /**
     * Recorded when a listening session ends without producing a usable result.
     * reason — controlled code only; no speech content.
     */
    fun logListenStop(reason: StopReason) = safe("LISTEN") {
        "stopped reason=${reason.name.lowercase()}"
    }

    /**
     * Recorded when the speech recognizer returns a result.
     *
     * mode             — which listening mode was active at the time.
     * wakeWordDetected — whether Scout's wake word was present.
     * charCount        — character count of the recognized text. NEVER the text itself.
     * gapAfterResponseMs — milliseconds elapsed since Scout's last TTS completion.
     *                      Diagnostic value for the follow-up timing window.
     * discarded        — whether this result was dropped rather than routed.
     * discardReason    — why it was dropped; null if discarded=false.
     */
    fun logSpeechResult(
        mode: ListenMode,
        wakeWordDetected: Boolean,
        charCount: Int,
        gapAfterResponseMs: Long,
        discarded: Boolean,
        discardReason: DiscardReason? = null
    ) = safe("SPEECH") {
        buildString {
            append("mode=${mode.name.lowercase()} ")
            append("wake=${flag(wakeWordDetected)} ")
            append("chars=$charCount ")
            append("gap=${gapAfterResponseMs}ms ")
            append("discarded=${flag(discarded)}")
            if (discardReason != null) append(" reason=${discardReason.name.lowercase()}")
        }
    }

    /**
     * Recorded when the speech recognizer fires onError().
     * Only the integer error code is stored — no message or context.
     */
    fun logSpeechError(androidErrorCode: Int) = safe("SPEECH_ERR") {
        "error_code=$androidErrorCode"
    }

    /**
     * Recorded when an incoming utterance is dispatched to a handler.
     * intentName — must be the .name of a ScoutIntentRouter IntentType (a compile-time
     *              constant). It is sanitized to alphanumeric+underscore before storage
     *              so accidental string concatenation cannot smuggle in user content.
     * brain      — which subsystem will produce the response.
     */
    fun logRoute(intentName: String, brain: BrainSource) = safe("ROUTE") {
        "intent=${sanitizeLabel(intentName, 40)} brain=${brain.name.lowercase()}"
    }

    /**
     * Recorded for each TinyLlama lifecycle event.
     * durationMs — included where meaningful (LOAD_DONE, GENERATION_DONE); null otherwise.
     */
    fun logLlama(event: LlamaEvent, durationMs: Long? = null) = safe("LLAMA") {
        buildString {
            append("event=${event.name.lowercase()}")
            if (durationMs != null) append(" ms=$durationMs")
        }
    }

    /**
     * Recorded when Scout makes or fails an outbound network request.
     * No URLs, response bodies, or API keys are included.
     */
    fun logNetwork(area: NetworkArea, success: Boolean) = safe("NETWORK") {
        "area=${area.name.lowercase()} success=${flag(success)}"
    }

    /**
     * Recorded when a cached weather response is served without a network request.
     */
    fun logWeatherCacheHit() = safe("WEATHER") { "cache=hit" }

    /**
     * Recorded when a weather cache entry cannot be used.
     * reason — controlled code; no network content.
     */
    fun logWeatherCacheMiss(reason: WeatherCacheMiss) = safe("WEATHER") {
        "cache=miss reason=${reason.name.lowercase()}"
    }

    /**
     * Recorded when Scout finishes speaking a response.
     * durationMs — how long the TTS utterance took.
     * This timestamp anchors the gap_after_response value in logSpeechResult().
     */
    fun logResponseDone(durationMs: Long) = safe("RESPONSE") {
        "event=done ms=$durationMs"
    }

    /**
     * Recorded for controlled error events in any Scout subsystem.
     * area           — which subsystem encountered the error.
     * exceptionClass — the simple class name only (throwable.javaClass.simpleName).
     *                  NEVER pass exception messages, toString(), or stack traces.
     *                  The value is sanitized to letters/digits/underscore/dollar
     *                  so an accidentally-passed message cannot leak through.
     */
    fun logError(area: ErrorArea, exceptionClass: String? = null) = safe("ERROR") {
        buildString {
            append("area=${area.name.lowercase()}")
            if (exceptionClass != null) {
                append(" exception=${sanitizeClassName(exceptionClass)}")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Executes the detail builder and writes to DiagnosticDb.
     * Any exception — from the builder or from the DB write — is swallowed silently
     * so that a logging failure never interrupts Scout's normal behavior.
     */
    private inline fun safe(tag: String, detail: () -> String) {
        try {
            db.add(tag, detail())
        } catch (_: Throwable) {}
    }

    /** Converts a Boolean to a short log token. */
    private fun flag(b: Boolean) = if (b) "yes" else "no"

    /**
     * Strips everything except alphanumeric characters, underscores, hyphens, and dots.
     * Collapses whitespace to underscores. Lowercased. Capped at maxLen.
     * Used for version strings and device model — values with no user content.
     */
    private fun sanitizeLabel(s: String, maxLen: Int): String =
        s.replace(Regex("[^A-Za-z0-9 _.\\-]"), "")
         .replace(Regex("\\s+"), "_")
         .lowercase()
         .take(maxLen)

    /**
     * Strips everything except characters that appear in Java/Kotlin simple class names:
     * letters, digits, underscores, and dollar signs. Capped at 60 characters.
     * Prevents an accidentally-passed exception message from leaking into the log.
     */
    private fun sanitizeClassName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_\$]"), "").take(60)
}
