package com.example.scoutface

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.scoutface.brain.ChatMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-wide owner of LlamaEngine's single generation executor and of the
 * "which caller is still valid" token, so an Activity recreation (a configuration
 * change, multi-window resize, etc. -- NOT a real app close) can never leave
 * TinyLlama generation stuck, and can never let a stale generation started by a
 * now-superseded Activity instance touch that instance's (detached) UI once a new
 * instance has taken over.
 *
 * LlamaEngine.generate() is already safe against concurrent *native* access --
 * LlamaEngine's lock is an object-level, process-wide field, so it serializes
 * every call regardless of which executor/thread calls it; two overlapping
 * generate() calls can never execute inside the native context at the same time,
 * the second one simply waits for the first to finish. What that lock does NOT
 * protect against is a stale generation's *result* being delivered to and acted
 * on by a destroyed Activity instance's callback (touching detached UI, speaking
 * through a stale TTS reference, etc.) -- that's what the token below is for.
 *
 * The executor lives here, not on MainActivity, specifically so a configuration-
 * change recreation doesn't leave an old, permanently-shut-down executor behind
 * with no way for the new Activity instance to submit generation work except by
 * constructing an entirely separate one (which would also leak the old one's
 * background thread -- ExecutorService.shutdown() is required to reclaim it, and
 * skipping that shutdown to let an in-flight generation finish was exactly the
 * point). There is now only ever one executor for the whole process's lifetime.
 */
object ScoutLlamaController {

    private const val TAG = "ScoutLlamaController"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Lazily constructed from an application Context the first time an owner
    // registers, then reused for the process's lifetime. Deliberately NOT the
    // caller's own (Activity-scoped) DiagLog: a discard is detected and logged
    // from inside a queued executor task that can run after the Activity that
    // submitted the generation has been destroyed, so logging through that
    // Activity's diagLog would mean invoking an Activity-owned object (and
    // keeping it reachable) after the Activity is gone. This one is scoped to
    // the process instead, so it stays valid regardless of Activity lifecycle.
    private var appDiagLog: DiagLog? = null

    // Bumped once per new MainActivity instance (registerOwner(), called from
    // onCreate()) and once per new question (newGeneration()) -- either kind of
    // change invalidates every in-flight generation started under an older value.
    // One counter covers both "a newer question superseded this one" and "the
    // Activity instance that asked this question no longer exists," since both
    // cases need the identical response: discard the result, never touch UI.
    @Volatile var currentToken: Long = 0L
        private set

    /** Call once, at the very start of a new MainActivity instance's onCreate(). */
    fun registerOwner(context: Context): Long {
        if (appDiagLog == null) {
            try {
                appDiagLog = DiagLog(DiagnosticDb(context.applicationContext))
            } catch (e: Exception) {
                Log.e(TAG, "failed to initialize diagnostic logging", e)
            }
        }
        currentToken += 1
        return currentToken
    }

    /** Call once per new question, by the currently-registered owner. */
    fun newGeneration(): Long {
        currentToken += 1
        return currentToken
    }

    /**
     * Call from onDestroy(), for every kind of Activity destruction -- a real
     * close AND a configuration-change recreation alike. Unlike
     * shutdownForRealClose() (which only tears down the native engine on a
     * genuine close), invalidating the token here is correct regardless of why
     * the Activity is being destroyed: its UI/TTS are gone either way, so a
     * generation that finishes after this point must never be delivered to it.
     * A recreated instance's onCreate() calls registerOwner() moments later and
     * bumps the token again anyway -- this just closes the short window where a
     * generation could finish and be delivered between the old instance's
     * onDestroy() and the new instance's onCreate().
     */
    fun invalidateOwner(): Long {
        currentToken += 1
        return currentToken
    }

    /**
     * Runs LlamaEngine.generate() on the shared process-wide executor and delivers
     * the result on the main thread via [onResult] -- but only if [token] (captured
     * by the caller from newGeneration()/currentToken at submission time) is still
     * the current token once the generation finishes. If a newer question or a new
     * Activity instance has superseded it by then, [onResult] is never invoked --
     * the discard is logged internally (see [appDiagLog]) instead of calling back
     * into caller-supplied code, since that caller may be a since-destroyed
     * Activity and its callback would otherwise be invoked (and kept reachable)
     * after destruction purely to log a diagnostic event.
     */
    fun generateAsync(
        token: Long,
        prompt: String,
        nPredict: Int = 150,
        onResult: (String?) -> Unit
    ) {
        executor.execute {
            val reply = try {
                LlamaEngine.generate(prompt, nPredict = nPredict)
            } catch (e: Throwable) {
                Log.e(TAG, "generateAsync() threw", e)
                null
            }
            mainHandler.post {
                if (token != currentToken) {
                    try {
                        appDiagLog?.logLlama(DiagLog.LlamaEvent.GENERATION_DISCARDED)
                    } catch (e: Exception) {
                        Log.e(TAG, "failed to log GENERATION_DISCARDED", e)
                    }
                    return@post
                }
                onResult(reply)
            }
        }
    }

    /**
     * Structured-chat counterpart to generateAsync() (structured-chat-template-seam
     * / Qwen migration Step 2A). Identical executor/token/callback contract --
     * same process-wide single-thread executor, same token-staleness check
     * before delivering onResult, same discard logging via appDiagLog -- the
     * only difference is that [messages] (Scout's structured content) is
     * passed to LlamaEngine.generateChat() instead of a pre-formatted flat
     * [prompt] string being passed to LlamaEngine.generate(). See
     * generateAsync()'s own doc comment above for the full reasoning behind
     * this contract; kept as a separate function rather than an overload so
     * neither call site's signature/behavior is disturbed by this addition.
     */
    fun generateChatAsync(
        token: Long,
        messages: List<ChatMessage>,
        nPredict: Int = 150,
        onResult: (String?) -> Unit
    ) {
        executor.execute {
            val reply = try {
                LlamaEngine.generateChat(messages, nPredict = nPredict)
            } catch (e: Throwable) {
                Log.e(TAG, "generateChatAsync() threw", e)
                null
            }
            mainHandler.post {
                if (token != currentToken) {
                    try {
                        appDiagLog?.logLlama(DiagLog.LlamaEvent.GENERATION_DISCARDED)
                    } catch (e: Exception) {
                        Log.e(TAG, "failed to log GENERATION_DISCARDED", e)
                    }
                    return@post
                }
                onResult(reply)
            }
        }
    }

    /**
     * Call from onDestroy() only when the Activity is not being recreated for a
     * configuration change (see Activity.isChangingConfigurations()) -- i.e. only
     * on a genuine close. Frees the native engine only if nothing is actively
     * using it right now; if a generation is still in flight, the native engine is
     * intentionally left allocated rather than freed out from under it -- see
     * LlamaEngine.freeIfIdle(). Does NOT touch the executor -- it's a process-wide
     * singleton meant to keep working for as long as the process itself lives, not
     * torn down by any single Activity instance's teardown.
     */
    fun shutdownForRealClose() {
        try {
            val freed = LlamaEngine.freeIfIdle(maxWaitMs = 5_000L)
            if (!freed) {
                Log.w(TAG, "shutdownForRealClose(): engine still busy, left loaded until process death.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "shutdownForRealClose() failed", e)
        }
    }
}
