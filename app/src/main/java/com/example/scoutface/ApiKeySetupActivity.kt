package com.example.scoutface

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// ============================================================
//  ApiKeySetupActivity.kt
//  Scout — Optional AI Provider Setup
//
//  HOW TO ADD THIS LATER:
//  1. Drop this file into your project (same package as MainActivity)
//  2. Add to AndroidManifest.xml inside <application>:
//       <activity android:name=".ApiKeySetupActivity"
//                 android:theme="@style/Theme.AppCompat.DayNight" />
//  3. To launch from Settings or first-run, call:
//       startActivity(Intent(this, ApiKeySetupActivity::class.java))
//  4. Keys are saved to SharedPreferences under:
//       "scout_prefs" → "gemini_api_key" / "openai_api_key" / "claude_api_key"
//  5. In your AI brain layer, read the key with:
//       ScoutApiKeyHelper.getKey(context, Provider.GEMINI)
//
//  STABILITY NOTE: This activity is fully self-contained.
//  It does not touch MainActivity, TruthDb, JournalDb, or
//  any existing Scout architecture. Safe to add at any time.
// ============================================================

// ── Phase enum ──────────────────────────────────────────────
private enum class Phase { INTRO, PICK, STEPS, DONE }

// ── Provider data ───────────────────────────────────────────
private enum class Provider(
    val displayName: String,
    val prefKey: String,
    val badge: String,
    val steps: List<SetupStep>,
    // Whether this provider is actually wired up end-to-end. MainActivity's
    // GeminiClient/ScoutGeminiManager currently only ever read the Gemini key
    // (ScoutApiKeyHelper.Provider.GEMINI) -- OpenAI/Claude keys can be saved here
    // but nothing in Scout uses them yet, so a user who set one up would see
    // "We're connected!" and then have Scout keep talking to Gemini or TinyLlama
    // regardless. Hidden from the picker (see renderPick()) until real clients
    // and routing exist for them -- not deleted, so re-enabling later is just
    // flipping this flag once that work is done.
    val isAvailable: Boolean = true
) {
    GEMINI(
        displayName = "Google Gemini",
        prefKey = "gemini_api_key",
        badge = "Free tier — may change at any time",
        steps = listOf(
            SetupStep(
                title = "Open Google AI Studio",
                body = "Tap the button below. Sign in with any Google account — Gmail, YouTube, the same one you already use.\n\n" +
                       "When you get there, a box will pop up. Here is what to do:\n\n" +
                       "• \"Name your key\" — just type Scout, or leave it as-is\n" +
                       "• \"Choose an imported project\" — leave this alone, skip it\n\n" +
                       "Then tap \"Create key\".",
                actionLabel = "Open Google AI Studio →",
                isLink = true,
                link = "https://aistudio.google.com/app/apikey"
            ),
            SetupStep(
                title = "Create your free key",
                body = "When the long code appears, tap \"Copy\" next to it.",
                hint = "⛔ Example only — do not paste here:\nAIzaSyB3xR7...",
                actionLabel = "I've copied my key ✓"
            ),
            SetupStep(
                title = "Paste your key",
                body = "Paste it below. Scout saves it safely on your device only.",
                hasInput = true,
                actionLabel = "Connect Gemini!"
            )
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        prefKey = "openai_api_key",
        badge = "Paid — requires billing",
        steps = listOf(
            SetupStep(
                title = "Open OpenAI Platform",
                body = "Tap below to open OpenAI's developer platform. This is different from ChatGPT — it's the API side. Sign in or create a free account.",
                actionLabel = "Open OpenAI Platform →",
                isLink = true,
                link = "https://platform.openai.com/api-keys"
            ),
            SetupStep(
                title = "Add billing first",
                body = "OpenAI requires a small amount of credit before using their API. Go to Settings → Billing and add at least \$5.",
                warning = "⚠ ChatGPT Plus/Pro does NOT include API access — billing is separate.",
                actionLabel = "I've added billing ✓"
            ),
            SetupStep(
                title = "Create your key",
                body = "In the API Keys section, tap \"Create new secret key\". Give it a name like \"Scout\" then tap Copy — you won't be able to see it again!",
                hint = "Looks like: sk-proj-abc123...",
                actionLabel = "I've copied my key ✓"
            ),
            SetupStep(
                title = "Paste your key",
                body = "Paste it below. Scout saves it safely on your device only.",
                hasInput = true,
                actionLabel = "Connect OpenAI!"
            )
        ),
        isAvailable = false
    ),
    CLAUDE(
        displayName = "Claude (Anthropic)",
        prefKey = "claude_api_key",
        badge = "Paid — separate from Claude Pro",
        steps = listOf(
            SetupStep(
                title = "Important heads-up first",
                body = "If you already pay for Claude Pro on claude.ai — that's for the chat website only.\n\nThe API that Scout needs is completely separate and lives at console.anthropic.com. You'll need to set up a separate account and billing there.",
                warning = "⚠ Claude Pro (claude.ai) does NOT include API access for Scout.",
                actionLabel = "Open Anthropic Console →",
                isLink = true,
                link = "https://console.anthropic.com/settings/keys"
            ),
            SetupStep(
                title = "Sign in or create an account",
                body = "Sign in at the Anthropic Console. You may need to create a new account even if you already have claude.ai. Once in, go to Settings → API Keys.",
                actionLabel = "I'm signed in ✓"
            ),
            SetupStep(
                title = "Add billing credits",
                body = "Go to Settings → Billing and add some credits. Even \$5 goes a very long way for normal Scout conversations.",
                hint = "💡 Most families spend under \$1/month using Claude via Scout.",
                actionLabel = "I've added billing ✓"
            ),
            SetupStep(
                title = "Create your key",
                body = "Tap \"Create Key\", give it a name like \"Scout\", then copy it. Store it somewhere safe — you can't view it again after closing.",
                hint = "Looks like: sk-ant-api03-abc123...",
                actionLabel = "I've copied my key ✓"
            ),
            SetupStep(
                title = "Paste your key",
                body = "Paste it below. Scout saves it safely on your device only.",
                hasInput = true,
                actionLabel = "Connect Claude!"
            )
        ),
        isAvailable = false
    )
}

private data class SetupStep(
    val title: String,
    val body: String,
    val actionLabel: String,
    val isLink: Boolean = false,
    val link: String? = null,
    val warning: String? = null,
    val hint: String? = null,
    val hasInput: Boolean = false
)

// ── Main Activity ────────────────────────────────────────────
class ApiKeySetupActivity : AppCompatActivity() {

    private var phase = Phase.INTRO
    private var selectedProvider: Provider? = null
    private var stepIndex = 0

    // Views
    private lateinit var rootLayout: LinearLayout
    private lateinit var phaseTitle: TextView
    private lateinit var phaseSubtitle: TextView
    private lateinit var phaseBody: TextView
    private lateinit var warningBox: TextView
    private lateinit var hintBox: TextView
    private lateinit var inputField: EditText
    private lateinit var primaryButton: Button
    private lateinit var secondaryButton: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var providerContainer: LinearLayout
    private lateinit var tinyllamaBadge: TextView
    private lateinit var responsibilityNote: TextView
    private lateinit var finePrint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootLayout = buildLayout()

        // The picker phase stacks a title, subtitle, a responsibility note, and three
        // provider cards (Gemini/OpenAI/Claude) in a plain vertical layout with no scroll
        // capability -- on this screen's locked landscape orientation (short height), that
        // content is taller than the visible screen, silently cutting off the last card
        // (Claude) below the fold rather than showing it scrolled. Wrapping in a ScrollView
        // fixes that without changing anything about how the content itself is built.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                rootLayout,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        setContentView(scroll)

        renderPhase()
    }

    // ── Render current phase ─────────────────────────────────
    private fun renderPhase() {
        when (phase) {
            Phase.INTRO -> renderIntro()
            Phase.PICK  -> renderPick()
            Phase.STEPS -> renderSteps()
            Phase.DONE  -> renderDone()
        }
    }

    private fun renderIntro() {
        phaseTitle.text = "Hi! I'm Scout."
        phaseSubtitle.text = "I'm already ready to talk!"
        phaseBody.text =
            "I have my own built-in brain called TinyLlama. You can chat with me right now — " +
            "completely free, no accounts or sign-ups needed.\n\n" +
            "Want smarter, deeper conversations? You can optionally connect one of these AI " +
            "services. It's totally your choice, and you can always do it later in Settings."

        tinyllamaBadge.visibility = View.VISIBLE
        providerContainer.visibility = View.GONE
        responsibilityNote.visibility = View.GONE
        warningBox.visibility = View.GONE
        hintBox.visibility = View.GONE
        inputField.visibility = View.GONE
        progressBar.visibility = View.GONE

        primaryButton.text = "Connect an AI service (optional)"
        primaryButton.setOnClickListener { phase = Phase.PICK; renderPhase() }

        secondaryButton.text = "No thanks — let's just chat with TinyLlama!"
        secondaryButton.visibility = View.VISIBLE
        secondaryButton.setOnClickListener { phase = Phase.DONE; renderPhase() }
    }

    private fun renderPick() {
        phaseTitle.text = "Which AI service?"
        phaseSubtitle.text = "Pick whichever one you have — or want to try."
        phaseBody.text = ""

        tinyllamaBadge.visibility = View.GONE
        progressBar.visibility = View.GONE
        warningBox.visibility = View.GONE
        hintBox.visibility = View.GONE
        inputField.visibility = View.GONE
        primaryButton.visibility = View.GONE
        responsibilityNote.visibility = View.VISIBLE
        providerContainer.visibility = View.VISIBLE

        providerContainer.removeAllViews()
        Provider.values().filter { it.isAvailable }.forEach { p ->
            val card = buildProviderCard(p)
            providerContainer.addView(card)
        }

        secondaryButton.text = "← Back"
        secondaryButton.visibility = View.VISIBLE
        secondaryButton.setOnClickListener { phase = Phase.INTRO; renderPhase() }
    }

    private fun renderSteps() {
        val provider = selectedProvider ?: return
        val step = provider.steps.getOrNull(stepIndex) ?: return
        val total = provider.steps.size

        tinyllamaBadge.visibility = View.GONE
        providerContainer.visibility = View.GONE
        responsibilityNote.visibility = View.GONE
        primaryButton.visibility = View.VISIBLE

        // Progress
        progressBar.visibility = View.VISIBLE
        progressBar.max = total
        progressBar.progress = stepIndex + 1

        phaseTitle.text = step.title
        phaseSubtitle.text = "${provider.displayName} · Step ${stepIndex + 1} of $total"
        phaseBody.text = step.body

        // Warning
        if (step.warning != null) {
            warningBox.text = step.warning
            warningBox.visibility = View.VISIBLE
        } else {
            warningBox.visibility = View.GONE
        }

        // Hint
        if (step.hint != null) {
            hintBox.text = step.hint
            hintBox.visibility = View.VISIBLE
        } else {
            hintBox.visibility = View.GONE
        }

        // Input
        if (step.hasInput) {
            inputField.visibility = View.VISIBLE
            inputField.text.clear()
            inputField.hint = "Paste your key here..."
        } else {
            inputField.visibility = View.GONE
        }

        primaryButton.text = step.actionLabel
        primaryButton.setOnClickListener {
            when {
                step.isLink -> {
                    step.link?.let { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    advanceStep()
                }
                step.hasInput -> {
                    val key = inputField.text.toString().trim()
                    if (key.isEmpty()) {
                        inputField.error = "Please paste your key first."
                        return@setOnClickListener
                    }
                    saveKey(provider.prefKey, key)
                    phase = Phase.DONE
                    renderPhase()
                }
                else -> advanceStep()
            }
        }

        secondaryButton.text = "← Choose a different service"
        secondaryButton.visibility = View.VISIBLE
        secondaryButton.setOnClickListener {
            stepIndex = 0
            selectedProvider = null
            phase = Phase.PICK
            renderPhase()
        }
    }

    private fun renderDone() {
        val provider = selectedProvider

        tinyllamaBadge.visibility = View.GONE
        providerContainer.visibility = View.GONE
        responsibilityNote.visibility = View.GONE
        progressBar.visibility = View.GONE
        warningBox.visibility = View.GONE
        hintBox.visibility = View.GONE
        inputField.visibility = View.GONE

        phaseTitle.text = if (provider != null) "We're connected!" else "All set!"
        phaseSubtitle.text = if (provider != null) "${provider.displayName} is ready." else "TinyLlama is ready to chat."
        phaseBody.text = if (provider != null)
            "Scout will now use ${provider.displayName} for richer conversations. You can always change this in Settings."
        else
            "You can always connect an AI service later in Settings if you change your mind."

        primaryButton.text = "Start chatting with Scout"
        primaryButton.setOnClickListener { finish() }
        primaryButton.visibility = View.VISIBLE

        secondaryButton.visibility = if (provider == null) View.VISIBLE else View.GONE
        secondaryButton.text = "Connect an AI service instead"
        secondaryButton.setOnClickListener { phase = Phase.PICK; renderPhase() }
    }

    // ── Helpers ──────────────────────────────────────────────
    private fun advanceStep() {
        val provider = selectedProvider ?: return
        if (stepIndex < provider.steps.size - 1) {
            stepIndex++
            renderPhase()
        }
    }

    private fun saveKey(prefKey: String, value: String) {
        getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey, value)
            .apply()
    }

    private fun buildProviderCard(provider: Provider): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val nameLabel = TextView(this).apply {
            text = provider.displayName
            textSize = 16f
            setTextColor(0xFFE2E8F0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameRow.addView(nameLabel)
        card.addView(nameRow)

        val badge = TextView(this).apply {
            text = provider.badge
            textSize = 11f
            setTextColor(0xFFFBD38D.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(badge)

        card.setOnClickListener {
            selectedProvider = provider
            stepIndex = 0
            phase = Phase.STEPS
            renderPhase()
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(10)) }
        card.layoutParams = params

        return card
    }

    // ── Build layout programmatically ────────────────────────
    private fun buildLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1117.toInt())
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
            ).apply { setMargins(0, 0, 0, dp(20)) }
            visibility = View.GONE
        }
        root.addView(progressBar)

        tinyllamaBadge = TextView(this).apply {
            text = "✅ TinyLlama built-in — works right now, no setup needed"
            textSize = 11f
            setTextColor(0xFF68D391.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
            visibility = View.GONE
        }
        root.addView(tinyllamaBadge)

        phaseTitle = TextView(this).apply {
            textSize = 22f
            setTextColor(0xFFE2E8F0.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(4)) }
        }
        root.addView(phaseTitle)

        phaseSubtitle = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF63B3ED.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
        }
        root.addView(phaseSubtitle)

        phaseBody = TextView(this).apply {
            textSize = 15f
            setTextColor(0xBFE2E8F0.toInt())
            lineHeight = (15 * 1.7).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(16)) }
        }
        root.addView(phaseBody)

        responsibilityNote = TextView(this).apply {
            text = "You are responsible for managing your own account and any costs with the provider you choose."
            textSize = 12f
            setTextColor(0x80E2E8F0.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
            visibility = View.GONE
        }
        root.addView(responsibilityNote)

        providerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            visibility = View.GONE
        }
        root.addView(providerContainer)

        warningBox = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFBD38D.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
            visibility = View.GONE
        }
        root.addView(warningBox)

        hintBox = TextView(this).apply {
            textSize = 12f
            setTextColor(0xA063B3ED.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
            visibility = View.GONE
        }
        root.addView(hintBox)

        inputField = EditText(this).apply {
            textSize = 13f
            setTextColor(0xFFE2E8F0.toInt())
            setHintTextColor(0x60E2E8F0.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 3
            maxLines = 5
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
            visibility = View.GONE
        }
        root.addView(inputField)

        primaryButton = Button(this).apply {
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(4)) }
        }
        root.addView(primaryButton)

        secondaryButton = TextView(this).apply {
            textSize = 13f
            setTextColor(0x60E2E8F0.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(secondaryButton)

        // Fine print
        finePrint = TextView(this).apply {
            text =
                "* Google, OpenAI, and Anthropic are independent third-party services.\n" +
                "Usage costs depend entirely on the provider you choose —\n" +
                "Scout and its developer are not responsible for any charges.\n" +
                "Free tiers, including Google Gemini's, may be reduced,\n" +
                "restricted, or removed entirely at any time without notice."
            textSize = 10f
            setTextColor(0x66E2E8F0.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(20), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(finePrint)

        return root
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

// ── Static helper to read saved keys elsewhere in Scout ─────
object ScoutApiKeyHelper {

    enum class Provider(val prefKey: String) {
        GEMINI("gemini_api_key"),
        OPENAI("openai_api_key"),
        CLAUDE("claude_api_key")
    }

    fun getKey(context: Context, provider: Provider): String? {
        return context
            .getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
            .getString(provider.prefKey, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun hasAnyKey(context: Context): Boolean {
        return Provider.values().any { getKey(context, it) != null }
    }

    fun clearKey(context: Context, provider: Provider) {
        context.getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove(provider.prefKey)
            .apply()
    }
}
