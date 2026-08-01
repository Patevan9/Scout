package com.example.scoutface

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Developer-only TinyLlama performance benchmark.
 *
 * Not reachable by ordinary users -- only launched from SettingsActivity after
 * the hidden 7-tap developer unlock on "About Scout." Runs four fixed prompts
 * across a fixed set of thread-count combinations using
 * LlamaEngine.generateBenchmark(), and writes each result to Scout's existing
 * diagnostic log (DiagLog.logLlamaBenchmark()) so results can be reviewed or
 * shared via the existing "Share Diagnostic Report" flow in Settings.
 *
 * Instrumentation only: this does not change LlamaEngine's normal generate()
 * path, TinyLlama's default thread configuration, or any of Scout's normal
 * behavior. Every real chat generation still runs through nativeGenerate(),
 * which always rebuilds its own context with production's fixed values
 * regardless of what a benchmark run leaves behind.
 *
 * The four prompts are fixed, synthetic strings -- not pulled from live
 * TruthDb facts or real conversation history -- so every thread combination
 * is measured against literally the same input, which is what makes the
 * comparison across combinations meaningful. They're modeled on production's
 * prompt shape (system preamble + few-shot pairs + facts/history + question)
 * but are NOT textually identical to MainActivity's real prompt builder --
 * this harness tells you which thread settings perform best on these
 * controlled prompt shapes, not how much of Scout's real-world delay comes
 * from the actual production prompt. Both the on-screen text and the final
 * summary say this explicitly so the numbers aren't over-read.
 *
 * Run order is a deterministic rotation (see buildRunPlan()), not grouped by
 * thread combo -- every combo gets an even mix of early/late (cooler/warmer)
 * positions across the session instead of low-thread combos always running
 * first while the device is coolest and high-thread combos always running
 * last after sustained load, which would bias the comparison toward
 * low-thread combos looking artificially fast.
 */
class LlamaBenchmarkActivity : AppCompatActivity() {

    private lateinit var resultsView: TextView
    private lateinit var runButton: Button
    private lateinit var diagLog: DiagLog
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    private data class ThreadCombo(val nThreads: Int, val nThreadsBatch: Int)

    private val threadCombos = listOf(
        ThreadCombo(2, 2),
        ThreadCombo(2, 4),
        ThreadCombo(3, 3),
        ThreadCombo(4, 4),
        ThreadCombo(5, 5),
        ThreadCombo(6, 6)
    )

    private data class BenchPrompt(val id: DiagLog.BenchPromptId, val label: String, val text: String)

    private val fixedPrompts: List<BenchPrompt> by lazy { buildFixedPrompts() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagLog = DiagLog(DiagnosticDb(this))

        val bg = Color.parseColor("#0D1728")
        val txt = Color.WHITE
        val txtSec = Color.parseColor("#8AAFC8")
        val accent = Color.parseColor("#4A8EFF")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "TinyLlama Performance Benchmark"
            textSize = 20f
            setTextColor(txt)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Developer diagnostic tool. Runs 4 fixed test prompts across " +
                "${threadCombos.size} thread-count combinations (${fixedPromptCountLabel()} " +
                "runs total, in a rotating order so no combo is unfairly favored by " +
                "device temperature) and logs measured performance to Scout's diagnostic " +
                "log. Camera and voice keep running normally during the test -- nothing " +
                "about Scout's normal settings is changed. When finished, use Settings > " +
                "Advanced & Support > Diagnostic Report to send the results."
            textSize = 13f
            setTextColor(txtSec)
            setPadding(0, dp(8), 0, dp(16))
            setLineSpacing(0f, 1.3f)
        })
        root.addView(TextView(this).apply {
            text = "Note: these 4 prompts are fixed test strings modeled on Scout's real " +
                "prompt shape, not copies of the actual production prompt. This tool can " +
                "tell you which thread settings perform best on these controlled prompts -- " +
                "it cannot by itself prove how much of Scout's real-world delay comes from " +
                "the full production prompt."
            textSize = 12f
            setTextColor(Color.parseColor("#F0B84A"))
            setPadding(0, 0, 0, dp(16))
            setLineSpacing(0f, 1.3f)
        })

        runButton = Button(this).apply {
            text = "Run All Benchmarks"
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            setOnClickListener { startBenchmark() }
        }
        root.addView(runButton)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = dp(16) }
        }
        resultsView = TextView(this).apply {
            textSize = 12f
            setTextColor(txt)
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.3f)
            text = "Not run yet."
        }
        scroll.addView(resultsView)
        root.addView(scroll)

        setContentView(root)
    }

    private fun fixedPromptCountLabel(): Int = threadCombos.size * fixedPrompts.size

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Short pause between runs so the device gets a brief chance to cool between calls
    // rather than every run stacking directly on the last one's heat -- deliberately
    // kept short (not a real cooldown-to-baseline wait) so the session doesn't drag on
    // for no measurable benefit. TEST value.
    private val RUN_COOLDOWN_MS = 1_500L

    // Deterministic rotating (Latin-square) run order: for round r (0 until
    // threadCombos.size) and prompt index p, the combo used is
    // threadCombos[(r + p) % threadCombos.size]. Every (prompt, combo) pair still runs
    // exactly once -- this only changes *when* each pair runs, so low-thread combos are
    // no longer always grouped at the start (coolest device) and high-thread combos
    // always grouped at the end (after sustained load, likely throttled). Round 0 alone
    // already starts each prompt on a different combo (prompt 0 -> combo 0, prompt 1 ->
    // combo 1, ...), and every combo appears across the full spread of early/late
    // positions by the time all rounds finish. Fully deterministic, so a rerun with the
    // same prompts/combos reproduces the identical sequence.
    private fun buildRunPlan(): List<Pair<BenchPrompt, ThreadCombo>> {
        val n = threadCombos.size
        val plan = mutableListOf<Pair<BenchPrompt, ThreadCombo>>()
        for (round in 0 until n) {
            for ((p, prompt) in fixedPrompts.withIndex()) {
                plan.add(prompt to threadCombos[(round + p) % n])
            }
        }
        return plan
    }

    private fun startBenchmark() {
        if (running) return
        if (!LlamaEngine.isReady) {
            Toast.makeText(this, "TinyLlama isn't loaded yet -- go back and wait for the offline brain to finish loading.", Toast.LENGTH_LONG).show()
            return
        }

        running = true
        runButton.isEnabled = false
        val plan = buildRunPlan()
        val lines = mutableListOf<String>()
        lines.add("Starting ${plan.size} run(s) in rotating order (see run= index in each line)...")
        resultsView.text = lines.joinToString("\n")

        Thread {
            for ((index, step) in plan.withIndex()) {
                val (prompt, combo) = step
                val runIndex = index + 1

                val result = LlamaEngine.generateBenchmark(
                    prompt = prompt.text,
                    nPredict = 100,
                    nThreads = combo.nThreads,
                    nThreadsBatch = combo.nThreadsBatch
                )

                val line = if (result == null) {
                    "[run=$runIndex/${plan.size}] ${prompt.label} threads=${combo.nThreads}/${combo.nThreadsBatch}: FAILED"
                } else {
                    diagLog.logLlamaBenchmark(
                        promptId = prompt.id,
                        nThreads = result.nThreads,
                        nThreadsBatch = result.nThreadsBatch,
                        nCtx = result.nCtx,
                        ctxReused = result.ctxReused,
                        nPromptTokens = result.nPromptTokens,
                        prefillMs = result.prefillMs,
                        prefillTokensPerSec = result.prefillTokensPerSec,
                        ttftMs = result.ttftMs,
                        nGeneratedTokens = result.nGeneratedTokens,
                        genMs = result.genMs,
                        genTokensPerSec = result.genTokensPerSec,
                        totalMs = result.totalMs,
                        runIndex = runIndex
                    )
                    "[run=$runIndex/${plan.size}] ${prompt.label} threads=${result.nThreads}/${result.nThreadsBatch} ctx=${result.nCtx}: " +
                        "prompt=${result.nPromptTokens}tok prefill=${result.prefillMs}ms(${"%.1f".format(result.prefillTokensPerSec)}tok/s) " +
                        "ttft=${result.ttftMs}ms gen=${result.nGeneratedTokens}tok/${result.genMs}ms(${"%.1f".format(result.genTokensPerSec)}tok/s) " +
                        "total=${result.totalMs}ms"
                }

                lines.add(line)
                mainHandler.post { resultsView.text = lines.joinToString("\n") }

                if (index < plan.size - 1) {
                    try { Thread.sleep(RUN_COOLDOWN_MS) } catch (_: InterruptedException) {}
                }
            }

            mainHandler.post {
                lines.add("")
                lines.add("Done. Reminder: these are 4 fixed test prompts, not the actual production " +
                    "prompt -- use these results to compare thread settings against each other, not " +
                    "as an absolute measure of Scout's real reply latency.")
                lines.add("Share via Settings > Advanced & Support > Diagnostic Report.")
                resultsView.text = lines.joinToString("\n")
                running = false
                runButton.isEnabled = true
            }
        }.start()
    }

    // ── Fixed benchmark prompts ─────────────────────────────────────────────

    private fun buildFixedPrompts(): List<BenchPrompt> {
        val system = """
You are Scout, a friendly AI companion robot who lives with a family.
Speak naturally and warmly, like a companion, not like a search engine.
Do not use numbered lists or bullet points. Speak in natural sentences only.
Respond only with Scout's next reply.
""".trimIndent()

        val fewShot = StringBuilder()
            .append("<|system|>\n$system</s>\n")
            .append("<|user|>\nCan you hear me?</s>\n")
            .append("<|assistant|>\nI hear you. I'm right here.</s>\n")
            .append("<|user|>\nAre you my friend?</s>\n")
            .append("<|assistant|>\nI'm happy when you're around.</s>\n")

        val shortFactual = fewShot.toString() +
            "<|user|>\nWhat is two plus two?</s>\n<|assistant|>\n"

        val personalMemory = fewShot.toString() +
            "<|user|>\nHere is what I know: The user's birthday is March 4. " +
            "The user's favorite color is blue. The user's dog is named Nicolas.</s>\n" +
            "<|assistant|>\nGot it, I'll keep that in mind.</s>\n" +
            "<|user|>\nWhat's my favorite color?</s>\n<|assistant|>\n"

        val conversational = fewShot.toString() +
            "<|user|>\nHow was your day?</s>\n" +
            "<|assistant|>\nPretty good, thanks for asking. Quiet, but nice.</s>\n" +
            "<|user|>\nWhat's your favorite thing about being a companion?</s>\n<|assistant|>\n"

        val longHistory = fewShot.toString() +
            "<|user|>\nGood morning.</s>\n<|assistant|>\nGood morning! Hope you slept well.</s>\n" +
            "<|user|>\nI did, thanks. Busy day ahead though.</s>\n<|assistant|>\nSounds like a lot. I'm here if you need anything.</s>\n" +
            "<|user|>\nWe're heading out for a bit.</s>\n<|assistant|>\nOkay, I'll be here when you get back.</s>\n" +
            "<|user|>\nWe're back now.</s>\n<|assistant|>\nWelcome back. Good to have you around again.</s>\n" +
            "<|user|>\nHere is what I know: The user's birthday is March 4. " +
            "The user's favorite color is blue. The user's dog is named Nicolas. " +
            "The user's wife is named Diana.</s>\n<|assistant|>\nGot it, I'll keep that in mind.</s>\n" +
            "<|user|>\nWhat do you remember about my family?</s>\n<|assistant|>\n"

        return listOf(
            BenchPrompt(DiagLog.BenchPromptId.SHORT_FACTUAL,   "short_factual",   shortFactual),
            BenchPrompt(DiagLog.BenchPromptId.PERSONAL_MEMORY, "personal_memory", personalMemory),
            BenchPrompt(DiagLog.BenchPromptId.CONVERSATIONAL,  "conversational",  conversational),
            BenchPrompt(DiagLog.BenchPromptId.LONG_HISTORY,    "long_history",    longHistory)
        )
    }
}
