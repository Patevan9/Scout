package com.example.scoutface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers DiagLog.formatLlamaBenchmark() -- the pure detail-string builder
 * behind logLlamaBenchmark(). DiagLog itself has no existing test coverage
 * (SQLiteOpenHelper needs a real Android Context), so this only exercises the
 * piece that doesn't need one, same reasoning as DiagLogSelfEchoFormatTest.
 *
 * Focus here is the isProductionDefault field added by the TinyLlama
 * benchmark instrumentation review: confirms it's rendered distinctly for the
 * PRODUCTION_DEFAULT configuration vs. every explicit thread-count
 * configuration, independent of whatever nThreads/nThreadsBatch numbers
 * llama.cpp actually granted -- see formatLlamaBenchmark()'s own doc comment
 * for why that independence matters (an explicit combo and the true default
 * could otherwise report identical numbers with no way to tell them apart).
 * Note: like DiagLogSelfEchoFormatTest, this file can only be compiled/run
 * under the real Gradle/Android unit-test task (testDebugUnitTest) -- DiagLog's
 * constructor references DiagnosticDb, which needs android.database.sqlite.*,
 * unavailable to this repo's local pure-Kotlin (no-Android-SDK) test harness.
 */
class DiagLogLlamaBenchmarkFormatTest {

    private fun format(isProductionDefault: Boolean, nThreads: Int = 2, nThreadsBatch: Int = 2) =
        DiagLog.formatLlamaBenchmark(
            promptId = DiagLog.BenchPromptId.SHORT_FACTUAL,
            nThreads = nThreads,
            nThreadsBatch = nThreadsBatch,
            nCtx = 2048,
            ctxReused = false,
            isProductionDefault = isProductionDefault,
            nPromptTokens = 42,
            prefillMs = 100L,
            prefillTokensPerSec = 420f,
            ttftMs = 120L,
            nGeneratedTokens = 50,
            genMs = 800L,
            genTokensPerSec = 62.5f,
            totalMs = 900L,
            runIndex = 1
        )

    @Test fun `an explicit thread-count run is marked production_default=no`() {
        val detail = format(isProductionDefault = false)
        assertTrue(detail.contains("production_default=no"))
    }

    @Test fun `the PRODUCTION_DEFAULT configuration is marked production_default=yes`() {
        val detail = format(isProductionDefault = true)
        assertTrue(detail.contains("production_default=yes"))
    }

    @Test fun `production_default is independent of the granted thread numbers`() {
        // If llama.cpp's actual n_threads_batch default happened to equal an
        // explicit combo's value (e.g. 2), the granted numbers alone would be
        // identical between the two runs -- this field is what still tells
        // them apart, exactly the scenario formatLlamaBenchmark()'s doc
        // comment describes.
        val explicitRun = format(isProductionDefault = false, nThreads = 2, nThreadsBatch = 2)
        val defaultRun = format(isProductionDefault = true, nThreads = 2, nThreadsBatch = 2)
        assertTrue(explicitRun.contains("threads=2 threads_batch=2"))
        assertTrue(defaultRun.contains("threads=2 threads_batch=2"))
        assertTrue(explicitRun.contains("production_default=no"))
        assertTrue(defaultRun.contains("production_default=yes"))
    }

    @Test fun `full line format places production_default right after ctx_reused`() {
        val detail = format(isProductionDefault = true)
        assertEquals(
            "run=1 prompt=short_factual threads=2 threads_batch=2 ctx=2048 " +
                "ctx_reused=no production_default=yes prompt_tokens=42 " +
                "prefill_ms=100 prefill_tps=420.0 ttft_ms=120 gen_tokens=50 " +
                "gen_ms=800 gen_tps=62.5 total_ms=900",
            detail
        )
    }
}
