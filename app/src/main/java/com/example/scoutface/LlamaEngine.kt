package com.example.scoutface

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object LlamaEngine {

    private const val TAG = "LlamaEngine"

    @Volatile private var nativeHandle: Long = 0L

    @Volatile var isReady: Boolean = false
        private set

    @Volatile var isLoading: Boolean = false
        private set

    @Volatile private var isGenerating: Boolean = false

    // ReentrantLock instead of a plain synchronized(Any()) monitor specifically so
    // freeIfIdle() can use tryLock(timeout) -- a bounded wait for "is anything
    // currently using the native engine," which a plain monitor can't express
    // without either blocking indefinitely or polling. Every other method here
    // still just needs ordinary mutual exclusion (withLock{} is the direct
    // equivalent of synchronized{} for a ReentrantLock).
    private val nativeLock = ReentrantLock()

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        nPredict: Int,
        temp: Float,
        repeatPenalty: Float
    ): String
    private external fun nativeGenerateBenchmark(
        handle: Long,
        prompt: String,
        nPredict: Int,
        temp: Float,
        nThreads: Int,
        nThreadsBatch: Int
    ): String
    private external fun nativeFree(handle: Long)

    /**
     * One benchmark run's measured performance -- never includes the generated
     * reply text, matching DiagnosticDb's existing invariant that response
     * content is never logged. Dev-only; produced only by generateBenchmark().
     */
    data class BenchmarkResult(
        val nPromptTokens: Int,
        val prefillMs: Long,
        val ttftMs: Long,
        val nGeneratedTokens: Int,
        val genMs: Long,
        val totalMs: Long,
        val nThreads: Int,
        val nThreadsBatch: Int,
        val nCtx: Int,
        val ctxReused: Boolean
    ) {
        val prefillTokensPerSec: Float
            get() = if (prefillMs > 0) nPromptTokens * 1000f / prefillMs else 0f
        val genTokensPerSec: Float
            get() = if (genMs > 0) nGeneratedTokens * 1000f / genMs else 0f
    }

    init {
        try {
            System.loadLibrary("scout_llama")
            Log.i(TAG, "scout_llama native library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load scout_llama — offline brain unavailable.", e)
        }
    }

    fun loadAsync(
        modelFile: File,
        nCtx: Int = 2048,
        nThreads: Int = 4,
        onReady: (success: Boolean) -> Unit = {}
    ) {
        if (isReady) {
            onReady(true)
            return
        }
        if (isLoading) {
            Log.w(TAG, "loadAsync called while already loading — ignored.")
            return
        }
        isLoading = true
        Thread {
            val success = load(modelFile, nCtx, nThreads)
            isLoading = false
            onReady(success)
        }.start()
    }

    fun load(modelFile: File, nCtx: Int = 2048, nThreads: Int = 4): Boolean {
        nativeLock.withLock {
            if (isReady) return true
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                return false
            }
            return try {
                Log.i(TAG, "Loading offline brain from: ${modelFile.name} (${modelFile.length() / 1_048_576}MB)")
                val handle = nativeLoad(modelFile.absolutePath, nCtx, nThreads)
                if (handle != 0L) {
                    nativeHandle = handle
                    isReady = true
                    Log.i(TAG, "Offline brain ready.")
                    true
                } else {
                    Log.e(TAG, "nativeLoad returned null handle — model rejected by llama.cpp.")
                    false
                }
            } catch (e: Throwable) {
                Log.e(TAG, "load() threw exception", e)
                false
            }
        }
    }

    fun generate(
        prompt: String,
        nPredict: Int = 150,
        temp: Float = 0.6f,
        repeatPenalty: Float = 1.12f
    ): String? {
        nativeLock.withLock {
            if (isGenerating) {
                Log.w(TAG, "generate() blocked because another generation is already running.")
                return null
            }
            if (!isReady || nativeHandle == 0L) {
                Log.w(TAG, "generate() called but engine not ready.")
                return null
            }

            isGenerating = true
            return try {
                val raw = nativeGenerate(nativeHandle, prompt, nPredict, temp, repeatPenalty)
                val cleaned = raw.trim()
                    .removePrefix("<|eot_id|>")
                    .removePrefix("<|end|>")
                    .trim()
                    .ifBlank { null }
                cleaned
            } catch (e: Throwable) {
                Log.e(TAG, "generate() threw exception", e)
                null
            } finally {
                isGenerating = false
            }
        }
    }

    /**
     * Dev-only performance benchmark. Runs the same generation core as
     * generate() with caller-supplied thread counts, and returns measured
     * timing only -- the reply text itself is discarded natively and never
     * crosses the JNI boundary, so it can never end up in a diagnostic log.
     *
     * Shares generate()'s lock/isGenerating guard so a benchmark run and a
     * real chat generation can never race against the same native context.
     * Callers should serialize their own benchmark runs on a single
     * background thread -- this only prevents concurrent native access, it
     * does not queue overlapping benchmark calls.
     */
    fun generateBenchmark(
        prompt: String,
        nPredict: Int = 100,
        temp: Float = 0.6f,
        nThreads: Int,
        nThreadsBatch: Int
    ): BenchmarkResult? {
        nativeLock.withLock {
            if (isGenerating) {
                Log.w(TAG, "generateBenchmark() blocked because a generation is already running.")
                return null
            }
            if (!isReady || nativeHandle == 0L) {
                Log.w(TAG, "generateBenchmark() called but engine not ready.")
                return null
            }

            isGenerating = true
            return try {
                val raw = nativeGenerateBenchmark(nativeHandle, prompt, nPredict, temp, nThreads, nThreadsBatch)
                parseBenchmarkResult(raw)
            } catch (e: Throwable) {
                Log.e(TAG, "generateBenchmark() threw exception", e)
                null
            } finally {
                isGenerating = false
            }
        }
    }

    // Parses the native "key=value;key=value" line -- see nativeGenerateBenchmark()
    // in scout_llama_jni.cpp for the exact fields written. Returns null on "ok=0"
    // or any malformed/missing field rather than guessing a default, since a
    // silently-wrong benchmark number would be worse than no number at all.
    private fun parseBenchmarkResult(raw: String): BenchmarkResult? {
        val fields = raw.split(";").associate { part ->
            val idx = part.indexOf('=')
            if (idx < 0) part to "" else part.substring(0, idx) to part.substring(idx + 1)
        }
        if (fields["ok"] != "1") return null
        return try {
            BenchmarkResult(
                nPromptTokens     = fields.getValue("n_prompt").toInt(),
                prefillMs         = fields.getValue("prefill_ms").toFloat().toLong(),
                ttftMs            = fields.getValue("ttft_ms").toFloat().toLong(),
                nGeneratedTokens  = fields.getValue("n_gen").toInt(),
                genMs             = fields.getValue("gen_ms").toFloat().toLong(),
                totalMs           = fields.getValue("total_ms").toFloat().toLong(),
                nThreads          = fields.getValue("n_threads").toInt(),
                nThreadsBatch     = fields.getValue("n_threads_batch").toInt(),
                nCtx              = fields.getValue("n_ctx").toInt(),
                ctxReused         = fields.getValue("ctx_reused") == "1"
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseBenchmarkResult() malformed native output", e)
            null
        }
    }

    /**
     * Frees the native engine, but only if the lock can be acquired within
     * [maxWaitMs] -- i.e., only if no load()/generate()/generateBenchmark() call is
     * currently in progress, or the in-progress one finishes within the wait
     * window. Bounded specifically so a caller on the main thread (e.g. Activity
     * teardown) can't be blocked indefinitely by a long-running generation; an
     * unbounded synchronized/lock() here would risk an ANR instead. Returns true
     * if actually freed, false if skipped because something was still using the
     * engine -- callers should treat false as "leave it loaded," not as an error.
     */
    fun freeIfIdle(maxWaitMs: Long): Boolean {
        val acquired = nativeLock.tryLock(maxWaitMs, TimeUnit.MILLISECONDS)
        if (!acquired) {
            Log.w(TAG, "freeIfIdle(): could not acquire lock within ${maxWaitMs}ms -- " +
                "an in-flight call is still using the engine, skipping free().")
            return false
        }
        try {
            val h = nativeHandle
            if (h != 0L) {
                try {
                    nativeFree(h)
                } catch (e: Throwable) {
                    Log.e(TAG, "nativeFree() threw exception", e)
                }
                nativeHandle = 0L
                isReady = false
                Log.i(TAG, "Offline brain freed.")
            }
            return true
        } finally {
            nativeLock.unlock()
        }
    }

    fun statusString(): String = when {
        isReady -> "Offline brain: ready"
        isLoading -> "Offline brain: loading"
        else -> "Offline brain: not loaded"
    }
}