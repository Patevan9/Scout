package com.example.scoutface

import android.os.Handler
import android.os.Looper
import android.util.Log
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

    // Bumped once per new MainActivity instance (registerOwner(), called from
    // onCreate()) and once per new question (newGeneration()) -- either kind of
    // change invalidates every in-flight generation started under an older value.
    // One counter covers both "a newer question superseded this one" and "the
    // Activity instance that asked this question no longer exists," since both
    // cases need the identical response: discard the result, never touch UI.
    @Volatile var currentToken: Long = 0L
        private set

    /** Call once, at the very start of a new MainActivity instance's onCreate(). */
    fun registerOwner(): Long {
        currentToken += 1
        return currentToken
    }

    /** Call once per new question, by the currently-registered owner. */
    fun newGeneration(): Long {
        currentToken += 1
        return currentToken
    }

    /**
     * Runs LlamaEngine.generate() on the shared process-wide executor and delivers
     * the result on the main thread via [onResult] -- but only if [token] (captured
     * by the caller from newGeneration()/currentToken at submission time) is still
     * the current token once the generation finishes. If a newer question or a new
     * Activity instance has superseded it by then, [onResult] is never invoked;
     * [onDiscarded] fires instead (also on the main thread) so a caller that wants
     * to log the discard (e.g. a diagnostic event) still can, without needing its
     * own separate staleness check.
     */
    fun generateAsync(
        token: Long,
        prompt: String,
        nPredict: Int = 150,
        onDiscarded: () -> Unit = {},
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
                    onDiscarded()
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
