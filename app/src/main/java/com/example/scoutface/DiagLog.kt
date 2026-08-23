package com.example.scoutface

/**
 * Typed diagnostic logging facade for Scout.
 *
 * Privacy rules enforced at every public method:
 *   - Speech text is never accepted or stored; only character counts
 *   - Scout response text is never accepted or stored
 *   - Names, memories, database values, URLs, file paths, and API keys
 *     are never accepted or stored
 *   - Throwable parameters expose only javaClass.simpleName, sanitized;
 *     message, cause, toString(), localizedMessage, and stack traces
 *     are never inspected or stored
 *   - All string parameters are sanitized before storage
 *   - All numeric parameters are clamped to non-negative values
 *   - Every call is wrapped in try/catch(Exception) so a logging failure
 *     never interrupts normal Scout behavior; Error subclasses such as
 *     OutOfMemoryError are intentionally NOT caught here
 *
 * Writes only to DiagnosticDb (scout_diagnostic.db).
 * Never touches JournalDb or any other database.
 * Not wired into any existing file yet.
 */
class DiagLog(private val db: DiagnosticDb) {

    companion object {
        /**
         * Pure detail-string formatter for logSelfEchoDiscarded() below, split out
         * so it's unit-testable without a database/Context — DiagLog itself has no
         * existing test coverage (SQLiteOpenHelper needs Android), but this piece
         * of it doesn't need to. Character count and timing only, matching this
         * class's own no-speech-content rule (see the class doc comment) — never
         * the recognized text.
         */
        fun formatSelfEchoDiscard(charCount: Int, gapAfterResponseMs: Long): String =
            "chars=${charCount.coerceAtLeast(0)} gap=${gapAfterResponseMs.coerceAtLeast(0L)}ms"

        /** Converts a Boolean to a short log token. Moved here (from a private
         *  instance method) so formatLlamaBenchmark() below can use it too --
         *  purely a location change, same "yes"/"no" tokens as before. */
        private fun flag(b: Boolean) = if (b) "yes" else "no"

        /**
         * Pure detail-string formatter for logLlamaBenchmark() below, split out
         * for the same reason as formatSelfEchoDiscard() above -- unit-testable
         * without a database/Context.
         *
         * isProductionDefault (TinyLlama benchmark instrumentation review):
         * distinguishes the PRODUCTION_DEFAULT benchmark configuration (which
         * requests n_threads=2 and leaves n_threads_batch entirely at whatever
         * llama_context_default_params() fills in, exactly matching real
         * nativeGenerate() behavior) from every explicit thread-count
         * configuration (2/2, 2/4, 3/3, 4/4, 5/5, 6/6), which always overrides
         * n_threads_batch to a specific value. Without this flag, a
         * PRODUCTION_DEFAULT run and an explicit run could report identical
         * nThreads/nThreadsBatch numbers (if llama.cpp's actual default happens
         * to match one of the explicit values) with no way to tell them apart
         * in the log -- this field is the disambiguator, independent of
         * whatever the granted numbers turn out to be.
         */
        fun formatLlamaBenchmark(
            promptId: BenchPromptId,
            nThreads: Int,
            nThreadsBatch: Int,
            nCtx: Int,
            ctxReused: Boolean,
            isProductionDefault: Boolean,
            nPromptTokens: Int,
            prefillMs: Long,
            prefillTokensPerSec: Float,
            ttftMs: Long,
            nGeneratedTokens: Int,
            genMs: Long,
            genTokensPerSec: Float,
            totalMs: Long,
            runIndex: Int
        ): String =
            "run=${runIndex.coerceAtLeast(0)} " +
            "prompt=${promptId.name.lowercase()} " +
            "threads=${nThreads.coerceAtLeast(0)} " +
            "threads_batch=${nThreadsBatch.coerceAtLeast(0)} " +
            "ctx=${nCtx.coerceAtLeast(0)} " +
            "ctx_reused=${flag(ctxReused)} " +
            "production_default=${flag(isProductionDefault)} " +
            "prompt_tokens=${nPromptTokens.coerceAtLeast(0)} " +
            "prefill_ms=${prefillMs.coerceAtLeast(0L)} " +
            "prefill_tps=${"%.1f".format(prefillTokensPerSec.coerceAtLeast(0f))} " +
            "ttft_ms=${ttftMs.coerceAtLeast(0L)} " +
            "gen_tokens=${nGeneratedTokens.coerceAtLeast(0)} " +
            "gen_ms=${genMs.coerceAtLeast(0L)} " +
            "gen_tps=${"%.1f".format(genTokensPerSec.coerceAtLeast(0f))} " +
            "total_ms=${totalMs.coerceAtLeast(0L)}"
    }

    // ── Controlled input types ────────────────────────────────────────────────
    // Every string-producing parameter comes through one of these enums.
    // Callers cannot pass arbitrary text into any sensitive position.

    /** Whether Scout was requiring a wake word or accepting follow-up speech. */
    enum class ListenMode { WAKE_WORD, FOLLOW_UP }

    /** Which subsystem produced Scout's response. */
    enum class BrainSource { TINYLLAMA, GEMINI, DIRECT, NONE }

    /**
     * Which path dispatched a given TTS utterance -- NORMAL for an ordinary
     * respond()/speak() call, DRAINED_PENDING_ANSWER for the Busy-Brain PR 2
     * bridging path (a Gemini/TinyLlama answer held in pendingAiAnswer while
     * Scout was busy, spoken from inside a *different* utterance's own
     * onDone()/onError() callback -- see MainActivity.deliverAiResult() and
     * the TTS UtteranceProgressListener's drain check). Real-device finding
     * under investigation: exactly this drained path can display a reply
     * (captions) and never actually speak it, leaving isSpeaking stuck until
     * the speaking watchdog force-clears it -- see the logTts*() methods
     * below, added to distinguish that specific chain diagnostically before
     * any behavior change.
     *
     * STARTUP_GREETING tags the one face-triggered "Hello. I am Scout." / "I
     * see <name>." respond() call -- unlike the other values, this one is also
     * read for real (not just diagnostic) behavior: MainActivity's TTS
     * onDone()/onError() callbacks check for it to stamp
     * startupGreetingFinishedAtMs, the anchor for the Companion Moments
     * post-boot quiet period. See that field's doc comment for why the quiet
     * period must key off this specific utterance rather than whichever one
     * happens to finish first (e.g. ScoutBootStatus's own boot-status
     * announcement, which normally finishes earlier).
     */
    enum class TtsDispatchSource { NORMAL, DRAINED_PENDING_ANSWER, STARTUP_GREETING }

    /** Lifecycle events for the TinyLlama engine. */
    enum class LlamaEvent {
        LOAD_STARTED, LOAD_DONE, LOAD_FAILED,
        GENERATION_STARTED, GENERATION_DONE,
        GENERATION_DISCARDED, GENERATION_FAILED
    }

    /**
     * Identifies which fixed benchmark prompt a LLAMA_BENCH entry belongs to.
     * The prompt text itself is never logged -- only this label -- consistent
     * with every other DiagLog method never accepting free text.
     */
    enum class BenchPromptId { SHORT_FACTUAL, PERSONAL_MEMORY, CONVERSATIONAL, LONG_HISTORY }

    /** Which outbound network subsystem made a request. */
    enum class NetworkArea { WEATHER_POINTS, WEATHER_FORECAST, GEMINI }

    /** Why a listening session ended without producing a usable result. */
    enum class StopReason { ERROR, TTS_LOCKOUT, COOLDOWN, WATCHDOG_RESET }

    /**
     * Every controlled reason maybeStartListening() can stop at, in the order
     * they're checked, plus the two terminal outcomes (STARTLISTENING_CALLED /
     * STARTLISTENING_EXCEPTION). Lets a real-device logcat/diagnostic-report
     * capture answer "why didn't the mic start" precisely, without free text.
     */
    enum class ListenAttemptReason {
        ACTIVITY_NOT_RESUMED, LISTENING_DISABLED, CONVERSATION_GATE,
        SCOUT_SPEAKING, SCOUT_THINKING, ALREADY_LISTENING,
        PERMISSIONS_MISSING, STARTUP_NOT_SETTLED, BOOT_NOT_FINISHED,
        COOLDOWN, SPEECH_RECOGNIZER_NOT_READY,
        STARTLISTENING_CALLED, STARTLISTENING_EXCEPTION
    }

    /** Why a received speech result was discarded after receipt. */
    enum class DiscardReason {
        TOO_SHORT, TTS_ACTIVE, COOLDOWN, NULL_RESULT, STALE_GENERATION
    }

    /** Why a weather cache entry was not used. */
    enum class WeatherCacheMiss { STALE, EMPTY, CROSS_DAY }

    /**
     * The routing decision made inside ScoutGeminiManager.tryGemini().
     * Only REQUEST_STARTED means a network thread actually launched.
     * BLOCKED_* values are controlled routing decisions, not errors.
     */
    enum class GeminiDecision {
        REQUEST_STARTED,
        CACHE_HIT,
        BLOCKED_IN_FLIGHT,
        BLOCKED_COOLDOWN
    }

    /** Which Scout subsystem produced a logged error. */
    enum class ErrorArea {
        MICROPHONE, CAMERA, DATABASE, PERMISSION,
        SPEECH_RECOGNIZER, TTS, NETWORK, UNKNOWN
    }

    /**
     * Mirrors ScoutBusyBrainState's BusyBrainDiscardReason (defined in the
     * brain package) as a controlled diagnostic token, the same way
     * ConversationEndReason is mirrored below -- keeps this class independent
     * of the brain package.
     *
     * SUPERSEDED_BY_NEW_TURN (generation-ownership fix, PR 2) -- a genuinely
     * newer user turn was accepted and answered while this generation was
     * still pending, via MainActivity's supersedeAnyPendingGeneration().
     * Distinct from PendingAnswerDiscardReason.SUPERSEDED below, which
     * covers an already-resolved, queued pendingAiAnswer being superseded by
     * a brand new generative request -- a different lifecycle object.
     */
    enum class BusyBrainDiscardReason { CONVERSATION_CLOSED, TIMEOUT, SUPERSEDED_BY_NEW_TURN }

    /**
     * Why a queued pendingAiAnswer (MainActivity, Busy-Brain PR 2's holding
     * slot for an answer that resolved while Scout was mid-utterance) was
     * discarded without ever being spoken. Deliberately its own type rather
     * than reusing BusyBrainDiscardReason above -- that type is paired with
     * a BrainSource for a currently in-flight generation, which
     * pendingAiAnswer (an already-resolved answer, generation already
     * complete) doesn't track, and it models a different lifecycle object.
     *   EXPIRED             -- ScoutPendingAnswerGate.decide() returned
     *                          EXPIRED: too much time passed since it queued.
     *   SUPERSEDED          -- a genuinely new generative request began
     *                          (busyBrainState.tryBegin() succeeded) before
     *                          this older answer was ever delivered.
     *   CONVERSATION_CLOSED -- an explicit close (goodbye/stop listening/
     *                          good night) discarded it, mirroring
     *                          BusyBrainDiscardReason.CONVERSATION_CLOSED's
     *                          own reason for the still-in-flight case.
     */
    enum class PendingAnswerDiscardReason { EXPIRED, SUPERSEDED, CONVERSATION_CLOSED }

    /**
     * Mirrors every value in IntentType (defined in MainActivity.kt) as a
     * controlled diagnostic token. Callers map IntentType → DiagIntent at the
     * callsite; the compiler enforces that no arbitrary string can be passed.
     * UNKNOWN covers the else branch of the routing when block.
     */
    enum class DiagIntent {
        TIME, DATE, LANGUAGE, TIME_OF_DAY, CONNECTIVITY,
        GO_ONLINE, GO_OFFLINE,
        EXPORT_BRAIN,
        VISION,
        GREET, HOW_ARE_YOU, GOODBYE, STOP_LISTENING,
        PRAISE, AFFECTION,
        IDENTITY,
        RECALL_FACT,
        ASK_MY_NAME, ASK_SCOUT_NAME,
        ASK_WIFE_NAME, ASK_SON_NAME, ASK_DOG_NAME,
        TEACH_WIFE_NAME, TEACH_SON_NAME, TEACH_DOG_NAME, TEACH_MY_NAME,
        WEATHER,
        CALENDAR,
        UNKNOWN
    }

    /**
     * Mirrors brain.MomentCategory (ScoutCompanionMomentsEngine) as a controlled
     * diagnostic token, same convention as DiagIntent above -- kept as DiagLog's
     * own enum rather than importing the brain-package type directly, so this
     * file stays independently stable and doesn't need a cross-package import.
     */
    enum class DiagMomentCategory { ENVIRONMENT, MEMORY, OBSERVATION, CURIOSITY }

    /**
     * Mirrors brain.ConversationEndReason (ScoutConversationState) as a
     * controlled diagnostic token, same convention as DiagIntent/
     * DiagMomentCategory above -- kept as DiagLog's own enum rather than
     * importing the brain-package type directly.
     */
    enum class ConversationEndReason { SILENCE_TIMEOUT, EXPLICIT_END }

    // ── Public logging methods ────────────────────────────────────────────────

    /**
     * Recorded once at app startup.
     * appVersion  — e.g. "1.0"; sanitized; no user content.
     * androidApi  — integer API level; no user data.
     * deviceModel — e.g. "SM-A325F"; sanitized; no user data.
     * geminiEnabled — boolean setting state.
     * llamaReady  — whether TinyLlama was already loaded at boot time.
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
     * mode — WAKE_WORD or FOLLOW_UP; compiler-enforced.
     */
    fun logListenStart(mode: ListenMode) = safe("LISTEN") {
        "started mode=${mode.name.lowercase()}"
    }

    /**
     * Recorded when a listening session ends without a usable result.
     * reason — controlled enum; no speech content.
     */
    fun logListenStop(reason: StopReason) = safe("LISTEN") {
        "stopped reason=${reason.name.lowercase()}"
    }

    /**
     * Recorded from maybeStartListening() -- every controlled reason it
     * stopped at, or its two terminal outcomes. Callers dedupe consecutive
     * identical reasons before calling this (see MainActivity's
     * logListenAttemptOnce()) so a tight restart loop doesn't flood the
     * diagnostic report with repeats of the same reason; a real transition
     * (including two consecutive STARTLISTENING_CALLED restarts, since
     * something else always logs in between in practice) still gets recorded.
     */
    fun logListenAttempt(reason: ListenAttemptReason) = safe("LISTEN_ATTEMPT") {
        "reason=${reason.name.lowercase()}"
    }

    /**
     * Recorded when the speech recognizer returns a result.
     *
     * mode               — which listening mode was active.
     * wakeWordDetected   — whether Scout's wake word was present.
     * charCount          — character count of the recognized text. NEVER the text itself.
     *                      Clamped to ≥ 0.
     * gapAfterResponseMs — milliseconds since Scout's last TTS completion.
     *                      Clamped to ≥ 0. Key field for diagnosing follow-up window issues.
     * discarded          — whether the result was dropped rather than routed.
     * discardReason      — why it was dropped. Only written when discarded=true.
     *                      If discarded=true and no reason is supplied, "unknown" is written.
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
            append("chars=${charCount.coerceAtLeast(0)} ")
            append("gap=${gapAfterResponseMs.coerceAtLeast(0L)}ms ")
            append("discarded=${flag(discarded)}")
            if (discarded) {
                val reason = discardReason?.name?.lowercase() ?: "unknown"
                append(" reason=$reason")
            }
        }
    }

    /**
     * Recorded when a recognized transcript is discarded because it looks like
     * Scout hearing his own just-spoken TTS output bleed back through the mic
     * (the self-echo substring guard in onResults() — matching logic unchanged
     * by this method's addition). Character count and timing only; the
     * recognized text itself is never accepted as a parameter here, let alone
     * stored.
     */
    fun logSelfEchoDiscarded(charCount: Int, gapAfterResponseMs: Long) = safe("SELF_ECHO") {
        formatSelfEchoDiscard(charCount, gapAfterResponseMs)
    }

    /**
     * Recorded when ScoutConversationState (Better Conversation State Phase 1)
     * transitions from inactive to active -- either a real user turn (wake-word
     * or an opening courtesy phrase) or Scout speaking first (a presence-
     * initiated remark). No speech text, no phrase content -- only which side
     * started it.
     */
    fun logConversationStarted(startedByScout: Boolean) = safe("CONVERSATION") {
        "event=started started_by=${if (startedByScout) "scout" else "user"}"
    }

    /**
     * Recorded when ScoutConversationState transitions from active to
     * inactive. reason — controlled enum, never free text. durationMs —
     * endedAt - startedAt, clamped >= 0.
     */
    fun logConversationEnded(reason: ConversationEndReason, durationMs: Long) = safe("CONVERSATION") {
        "event=ended reason=${reason.name.lowercase()} duration_ms=${durationMs.coerceAtLeast(0L)}"
    }

    /**
     * Recorded when the speech recognizer fires onError().
     * Only the integer error code is stored — no message or context.
     */
    fun logSpeechError(androidErrorCode: Int) = safe("SPEECH_ERR") {
        "error_code=$androidErrorCode"
    }

    /**
     * Recorded when ScoutSpeechAvailabilityMonitor detects a sustained pattern of
     * network-dependent recognizer failures (ERROR_NETWORK/ERROR_NETWORK_TIMEOUT)
     * and the availability warning is actually spoken to the user.
     * errorCount — how many qualifying errors were in the rolling window at the
     * moment of warning; clamped to >= 0. No speech content, no error messages,
     * no stack traces.
     */
    fun logSpeechAvailabilityWarning(errorCount: Int) = safe("SPEECH_AVAILABILITY") {
        "warned=1 error_count=${errorCount.coerceAtLeast(0)}"
    }

    /**
     * Recorded when ScoutCompanionMomentsEngine's decision actually results in
     * Scout speaking a companion moment -- never when a candidate is merely
     * generated or scored. category — DiagMomentCategory enum; compiler-enforced.
     * confidence — the winning candidate's score, clamped to [0, 1]. contributions
     * — the named contribution keys the score was built from (e.g.
     * "curiosity_no_conversation_yet_bonus"); this is always a small, fixed
     * vocabulary of identifiers hardcoded in ScoutCompanionMomentsEngine's own
     * source, never user speech, a taught fact, or a name, so it's safe to record
     * as-is. No spoken text, no fact values, no names are ever passed here.
     */
    fun logCompanionMoment(category: DiagMomentCategory, confidence: Float, contributions: List<String>) =
        safe("COMPANION_MOMENT") {
            "category=${category.name.lowercase()} confidence=${confidence.coerceIn(0f, 1f)} contributions=${contributions.joinToString(",")}"
        }

    /**
     * Recorded when an incoming utterance is dispatched to a handler.
     * intent — DiagIntent enum value; compiler-enforced; no arbitrary string accepted.
     * Brain source is NOT recorded here — it is recorded by logBrainStarted() only
     * when a brain path is actually entered, not when it is merely eligible.
     */
    fun logRoute(intent: DiagIntent) = safe("ROUTE") {
        "intent=${intent.name.lowercase()}"
    }

    /**
     * Recorded only when a brain path is actually entered — not when it is
     * eligible, planned, or blocked by a guard.
     *   DIRECT    — a hardcoded intent handler ran synchronously
     *   GEMINI    — a Gemini network thread was actually launched
     *   TINYLLAMA — a TinyLlama generation thread was actually launched
     *   NONE      — no brain path was entered (should not appear in normal use)
     */
    fun logBrainStarted(source: BrainSource) = safe("BRAIN") {
        "started=${source.name.lowercase()}"
    }

    /**
     * Recorded when a pending Gemini/TinyLlama generation's own completion
     * callback finds it was marked discarded (ScoutBusyBrainState.discard())
     * before it got a chance to speak -- either the conversation was
     * explicitly closed while it was in flight, or the stuck-generation
     * watchdog gave up waiting. Never includes the generated reply text.
     */
    fun logBusyBrainDiscarded(reason: BusyBrainDiscardReason, source: BrainSource) = safe("BUSY_BRAIN") {
        "event=discarded reason=${reason.name.lowercase()} source=${source.name.lowercase()}"
    }

    /**
     * Recorded when a queued pendingAiAnswer is discarded without ever being
     * spoken -- see PendingAnswerDiscardReason above. Never includes the
     * discarded answer's text.
     */
    fun logPendingAnswerDiscarded(reason: PendingAnswerDiscardReason) = safe("PENDING_ANSWER") {
        "event=discarded reason=${reason.name.lowercase()}"
    }

    /**
     * Recorded for every internal Gemini routing decision.
     * Only REQUEST_STARTED indicates a real network launch.
     * BLOCKED_* values record controlled routing decisions.
     * No prompt text, response text, or error message is ever included.
     */
    fun logGeminiDecision(decision: GeminiDecision) = safe("GEMINI") {
        "decision=${decision.name.lowercase()}"
    }

    /**
     * Recorded for each TinyLlama lifecycle event.
     * durationMs — meaningful for LOAD_DONE and GENERATION_DONE; null otherwise.
     *              Clamped to ≥ 0 when present.
     */
    fun logLlama(event: LlamaEvent, durationMs: Long? = null) = safe("LLAMA") {
        buildString {
            append("event=${event.name.lowercase()}")
            if (durationMs != null) append(" ms=${durationMs.coerceAtLeast(0L)}")
        }
    }

    /**
     * Recorded once per dev-only performance benchmark run (see
     * LlamaBenchmarkActivity — not reachable by ordinary users). All fields
     * are numeric or controlled enums; the benchmark prompt text and the
     * generated reply are never accepted here or anywhere upstream of this
     * call (nativeGenerateBenchmark() never returns the reply text at all).
     *
     * promptId          — which of the four fixed benchmark prompts this run used.
     * nThreads          — generation thread count actually granted by llama.cpp.
     * nThreadsBatch     — prompt-processing thread count actually granted.
     * nCtx              — context size actually granted.
     * ctxReused         — whether the KV-cache context was reused rather than
     *                      recreated. Always false today; reuse isn't implemented.
     * isProductionDefault — true only for the PRODUCTION_DEFAULT configuration
     *                      (n_threads=2, n_threads_batch left at whatever
     *                      llama.cpp defaults to -- exactly nativeGenerate()'s
     *                      real behavior), false for every explicit thread-count
     *                      configuration. See formatLlamaBenchmark()'s doc
     *                      comment for why this can't be inferred from
     *                      nThreads/nThreadsBatch alone.
     * nPromptTokens     — prompt token count for this run.
     * prefillMs         — prompt-processing (prefill) duration.
     * prefillTokensPerSec — nPromptTokens / prefillMs, precomputed by the caller.
     * ttftMs            — wall-clock time to the first generated token.
     * nGeneratedTokens  — number of tokens generated.
     * genMs             — generation-loop duration.
     * genTokensPerSec   — nGeneratedTokens / genMs, precomputed by the caller.
     * totalMs           — total wall-clock duration of the whole call.
     * runIndex          — this run's 1-based position in the whole benchmark
     *                      session's chronological execution order (not grouped
     *                      by thread combo -- see LlamaBenchmarkActivity's
     *                      rotating run order). Lets a later reader reconstruct
     *                      how much sustained load preceded any given run, since
     *                      thermal state depends on execution order, not on
     *                      which combo/prompt a run happens to be.
     * All numeric fields are clamped to >= 0.
     */
    fun logLlamaBenchmark(
        promptId: BenchPromptId,
        nThreads: Int,
        nThreadsBatch: Int,
        nCtx: Int,
        ctxReused: Boolean,
        isProductionDefault: Boolean,
        nPromptTokens: Int,
        prefillMs: Long,
        prefillTokensPerSec: Float,
        ttftMs: Long,
        nGeneratedTokens: Int,
        genMs: Long,
        genTokensPerSec: Float,
        totalMs: Long,
        runIndex: Int
    ) = safe("LLAMA_BENCH") {
        formatLlamaBenchmark(
            promptId, nThreads, nThreadsBatch, nCtx, ctxReused, isProductionDefault,
            nPromptTokens, prefillMs, prefillTokensPerSec, ttftMs,
            nGeneratedTokens, genMs, genTokensPerSec, totalMs, runIndex
        )
    }

    /**
     * Recorded when Scout makes or fails an outbound network request.
     * No URLs, response bodies, or API keys are included.
     */
    fun logNetwork(area: NetworkArea, success: Boolean) = safe("NETWORK") {
        "area=${area.name.lowercase()} success=${flag(success)}"
    }

    /** Recorded when a cached weather response is served without a network request. */
    fun logWeatherCacheHit() = safe("WEATHER") { "cache=hit" }

    /**
     * Recorded when a weather cache entry cannot be used.
     * reason — controlled enum; no network content.
     */
    fun logWeatherCacheMiss(reason: WeatherCacheMiss) = safe("WEATHER") {
        "cache=miss reason=${reason.name.lowercase()}"
    }

    /**
     * Recorded when Scout finishes speaking a response.
     * durationMs — how long the TTS utterance took; clamped to ≥ 0.
     * Anchors the gapAfterResponseMs value in subsequent logSpeechResult() calls.
     */
    fun logResponseDone(durationMs: Long) = safe("RESPONSE") {
        "event=done ms=${durationMs.coerceAtLeast(0L)}"
    }

    // ── TTS lifecycle diagnostics (instrumentation only -- no behavior change) ──
    // Added to distinguish a normal speak() dispatch from the Busy-Brain PR 2
    // "drained pendingAiAnswer" bridging path (see TtsDispatchSource above),
    // and to tell "TTS requested" apart from "TTS engine actually started/
    // completed it" -- the exact gap a suspected real-device TTS lifecycle
    // lockup falls into. dispatchId is a small per-utterance counter (not the
    // Android SpeechRecognizer/TTS utteranceId text -- see MainActivity's own
    // counter) so a requested/speak_call/started/done/error sequence can be
    // correlated across the whole chain, including back-to-back or re-entrant
    // dispatches, without relying on Android's utteranceId ever being unique
    // (previously a single hardcoded constant for every utterance). No speech
    // text, no response text -- only the numeric id and controlled source.

    /** Recorded when speak() is invoked, before any delay or actual TTS engine call. */
    fun logTtsRequested(dispatchId: Int, source: TtsDispatchSource) = safe("TTS") {
        "event=requested id=${dispatchId.coerceAtLeast(0)} source=${source.name.lowercase()}"
    }

    /**
     * Recorded immediately after tts.speak() returns, before any engine
     * callback has necessarily fired. ok=false means TextToSpeech.ERROR was
     * returned synchronously (the existing manual-reset branch in speak()
     * already handles that case; this only records that it happened).
     * ok=true means the engine accepted the request -- NOT that it will ever
     * actually speak it, which is exactly the gap logTtsStarted()/
     * logTtsCompleted()/logTtsFailed() below exist to close.
     */
    fun logTtsSpeakCall(dispatchId: Int, source: TtsDispatchSource, ok: Boolean) = safe("TTS") {
        "event=speak_call id=${dispatchId.coerceAtLeast(0)} source=${source.name.lowercase()} ok=${flag(ok)}"
    }

    /** Recorded from the TTS engine's onStart() callback. */
    fun logTtsStarted(dispatchId: Int, source: TtsDispatchSource) = safe("TTS") {
        "event=started id=${dispatchId.coerceAtLeast(0)} source=${source.name.lowercase()}"
    }

    /**
     * Recorded from the TTS engine's onDone() callback. Separate from the
     * existing logResponseDone() above (which is unchanged) -- this one
     * carries the dispatch id/source needed to correlate with
     * logTtsRequested()/logTtsSpeakCall() for the same utterance.
     */
    fun logTtsCompleted(dispatchId: Int, source: TtsDispatchSource, durationMs: Long) = safe("TTS") {
        "event=done id=${dispatchId.coerceAtLeast(0)} source=${source.name.lowercase()} ms=${durationMs.coerceAtLeast(0L)}"
    }

    /** Recorded from the TTS engine's onError() callback. */
    fun logTtsFailed(dispatchId: Int, source: TtsDispatchSource) = safe("TTS") {
        "event=error id=${dispatchId.coerceAtLeast(0)} source=${source.name.lowercase()}"
    }

    /**
     * Recorded for controlled error events.
     * area      — which Scout subsystem encountered the error; compiler-enforced.
     * throwable — optional. Only javaClass.simpleName is extracted and stored.
     *             The message, cause, toString(), localizedMessage, and stack trace
     *             are never inspected or stored. The class name is sanitized before
     *             storage to prevent an accidentally-passed message from leaking.
     */
    fun logError(area: ErrorArea, throwable: Throwable? = null) = safe("ERROR") {
        buildString {
            append("area=${area.name.lowercase()}")
            if (throwable != null) {
                val className = throwable.javaClass.simpleName
                append(" exception=${sanitizeClassName(className)}")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Executes the detail builder and writes to DiagnosticDb.
     * Catches Exception only — Error subclasses (OutOfMemoryError, StackOverflowError,
     * ThreadDeath) are intentionally not caught, as they should not be hidden by a
     * diagnostic logging wrapper.
     */
    private inline fun safe(tag: String, detail: () -> String) {
        try {
            db.add(tag, detail())
        } catch (_: Exception) {}
    }

    /**
     * Keeps alphanumeric characters, underscores, hyphens, and dots only.
     * Collapses whitespace to underscores. Lowercased. Capped at maxLen.
     * Used for version strings and device model — fields with no user content.
     */
    private fun sanitizeLabel(s: String, maxLen: Int): String =
        s.replace(Regex("[^A-Za-z0-9 _.\\-]"), "")
         .replace(Regex("\\s+"), "_")
         .lowercase()
         .take(maxLen)

    /**
     * Keeps only characters that appear in Java/Kotlin simple class names:
     * letters, digits, underscores, and dollar signs. Capped at 60 characters.
     * Ensures that an accidentally-passed exception message cannot survive sanitization.
     */
    private fun sanitizeClassName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_\$]"), "").take(60)
}
