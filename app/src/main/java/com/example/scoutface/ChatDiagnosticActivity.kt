package com.example.scoutface

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Developer-only chat-template diagnostic screen (Fold 7 Qwen investigation).
 *
 * Not reachable by ordinary users -- only launched from SettingsActivity after
 * the same hidden 7-tap developer unlock on "About Scout" that gates
 * LlamaBenchmarkActivity (see SettingsActivity.onAboutScoutTapped()).
 *
 * Shows the exact rendered prompt applyModelChatTemplate() produced for the
 * most recent real production chat generation -- the one thing
 * LlamaBenchmarkActivity's fixed prompts structurally cannot show, since
 * they bypass llama_chat_apply_template() entirely (see buildFixedPrompts()'s
 * own doc comment). Reads a snapshot captured natively as a side effect of
 * LlamaEngine.generateChat() -- see scout_llama_jni.cpp's g_chatDiag. This
 * screen only ever READS that snapshot (LlamaEngine.getLastChatDiagnostics*());
 * it never triggers a generation itself and has no way to influence one.
 *
 * Privacy: the rendered prompt can contain real conversation content and
 * personal facts. This screen is intentionally NOT part of Scout's ordinary
 * Diagnostic Report, is never written to DiagnosticDb or any file, and holds
 * nothing beyond this Activity's own lifecycle -- closing it and reopening
 * re-reads the (still in-memory-only, native-side) snapshot fresh. The
 * prompt text is shown in a selectable TextView so a developer can copy it
 * on-device if needed, rather than this screen writing it anywhere itself.
 */
class ChatDiagnosticActivity : AppCompatActivity() {

    private lateinit var summaryView: TextView
    private lateinit var promptView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "Chat Template Diagnostic (Dev)"
            textSize = 20f
            setTextColor(txt)
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "Developer diagnostic tool -- Fold 7 Qwen investigation. Shows the " +
                "exact rendered prompt applyModelChatTemplate() produced for the most " +
                "recent real production reply, from a snapshot kept in memory only. Not " +
                "part of Scout's Diagnostic Report, never written to disk. Tap Refresh " +
                "after Scout answers a real question to see that turn's data."
            textSize = 13f
            setTextColor(txtSec)
            setPadding(0, dp(8), 0, dp(16))
            setLineSpacing(0f, 1.3f)
        })

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }
        root.addView(refreshButton)

        summaryView = TextView(this).apply {
            textSize = 13f
            setTextColor(txt)
            setPadding(0, dp(16), 0, dp(8))
            setLineSpacing(0f, 1.3f)
            typeface = Typeface.MONOSPACE
        }
        root.addView(summaryView)

        root.addView(TextView(this).apply {
            text = "Rendered prompt (selectable -- long-press to copy):"
            textSize = 13f
            setTextColor(txtSec)
            setPadding(0, dp(12), 0, dp(4))
        })

        promptView = TextView(this).apply {
            textSize = 12f
            setTextColor(accent)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        root.addView(promptView)

        val scroll = ScrollView(this).apply {
            addView(root)
        }
        setContentView(scroll)

        refresh()
    }

    // Pure read -- LlamaEngine.getLastChatDiagnosticsSummary()/
    // getLastChatDiagnosticPrompt() only read the native-side snapshot,
    // never trigger generation, never touch LlamaEngine's nativeLock. Safe
    // to call any time, including while a real generation is in flight
    // elsewhere (it will simply show whichever turn completed most recently
    // as of this tap).
    private fun refresh() {
        val summary = LlamaEngine.getLastChatDiagnosticsSummary()
        val modelPath = LlamaEngine.loadedModelPath ?: "(not loaded)"

        summaryView.text = if (summary == null) {
            "Model path: $modelPath\n\nCould not read diagnostic summary (malformed native response)."
        } else if (!summary.valid) {
            "Model path: $modelPath\n\nNo production chat generation has completed yet " +
                "this session -- ask Scout something, then tap Refresh."
        } else {
            "Model path: $modelPath\n" +
                "Messages sent: ${summary.nMessages}\n" +
                "Rendered prompt length: ${summary.promptLength} chars\n" +
                "Prompt tokens: ${summary.nPromptTokens}\n" +
                "Generated tokens: ${summary.nGeneratedTokens}\n" +
                "Stopped by: " + if (summary.stoppedByEog) "EOG (model's own stop token)"
                    else "nPredict cap reached (did not stop on its own)"
        }

        promptView.text = LlamaEngine.getLastChatDiagnosticPrompt()
            .ifBlank { "(none captured yet)" }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
