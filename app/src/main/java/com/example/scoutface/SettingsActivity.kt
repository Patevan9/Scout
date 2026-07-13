package com.example.scoutface

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var scoutPrefs: SharedPreferences
    private lateinit var memPrefs: SharedPreferences

    private val screenStack = ArrayDeque<String>()

    private var tts: TextToSpeech? = null
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    private lateinit var swipeDetector: GestureDetector

    private val BG           = Color.parseColor("#0D1728")
    private val CARD         = Color.parseColor("#19293F")
    private val ACCENT       = Color.parseColor("#4A8EFF")
    private val DIM_BLUE     = Color.parseColor("#1E3D6E")
    private val TXT          = Color.WHITE
    private val TXT_SEC      = Color.parseColor("#8AAFC8")
    private val TXT_MUTE     = Color.parseColor("#4A6280")
    private val DIV          = Color.parseColor("#1A2D45")

    // Subtle icon badge colors per section
    private val IC_IDENTITY  = Color.parseColor("#1F3A70")
    private val IC_BRAIN     = Color.parseColor("#3A2060")
    private val IC_WORKBENCH = Color.parseColor("#153E3E")
    private val IC_PRIVACY   = Color.parseColor("#153E20")
    private val IC_EXTRAS    = Color.parseColor("#3E2E10")

    companion object {
        private const val S_MAIN      = "main"
        private const val S_IDENTITY  = "identity"
        private const val S_BRAIN     = "brain"
        private const val S_WORKBENCH = "workbench"
        private const val S_PRIVACY   = "privacy"
        private const val S_EXTRAS    = "extras"
        private const val S_ROBOT     = "robot_name"
        private const val S_APIKEY    = "api_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scoutPrefs = getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
        memPrefs   = getSharedPreferences("scout_memory", Context.MODE_PRIVATE)
        container  = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(container)
        tts = TextToSpeech(this) { /* init silent — ready by the time the user touches sliders */ }

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: return false)
                if (dx < -160f && vX < -400f && abs(vY) < abs(vX)) {
                    finish()
                    return true
                }
                return false
            }
        })

        push(S_MAIN)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.stay_still, R.anim.slide_out_to_left)
    }

    override fun onDestroy() {
        super.onDestroy()
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakPreview(full: Boolean) {
        val pitch = scoutPrefs.getFloat("voice_pitch", 1.0f)
        val speed = scoutPrefs.getFloat("voice_speed", 1.0f)
        val name  = scoutPrefs.getString("robot_name", "Scout") ?: "Scout"
        val phrase = if (full) "Hello. My name is $name." else "Hello."
        tts?.let {
            it.setPitch(pitch)
            it.setSpeechRate(speed)
            it.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (screenStack.size > 1) {
            screenStack.removeLast()
            show(screenStack.last())
        } else {
            finish()
        }
    }

    private fun push(screen: String) { screenStack.addLast(screen); show(screen) }
    private fun pop() { if (screenStack.size > 1) { screenStack.removeLast(); show(screenStack.last()) } else finish() }
    private fun show(s: String) { container.removeAllViews(); container.addView(build(s)) }

    private fun build(s: String): View = when (s) {
        S_MAIN      -> mainScreen()
        S_IDENTITY  -> identityScreen()
        S_BRAIN     -> brainScreen()
        S_WORKBENCH -> workbenchScreen()
        S_PRIVACY   -> privacyScreen()
        S_EXTRAS    -> extrasScreen()
        S_ROBOT     -> robotNameScreen()
        S_APIKEY    -> apiKeyScreen()
        else        -> mainScreen()
    }

    // ─── MAIN ───────────────────────────────────────────────────

    private fun mainScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(mainHeader())
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(8), dp(16), dp(32))

        body.addView(sectionCard("👤", "Identity & Voice",    "Name, voice pitch, and speed",   IC_IDENTITY)  { push(S_IDENTITY) })
        body.addView(cardSpacer())
        body.addView(sectionCard("🧠", "Brain & Behavior",    "How Scout thinks and responds",   IC_BRAIN)     { push(S_BRAIN) })
        body.addView(cardSpacer())
        body.addView(sectionCard("🔧", "Builder's Workbench", "Hardware, controls, and chassis", IC_WORKBENCH) { push(S_WORKBENCH) })
        body.addView(cardSpacer())
        body.addView(sectionCard("🛡️", "Privacy & Data",      "Memory and privacy controls",     IC_PRIVACY)   { push(S_PRIVACY) })
        body.addView(cardSpacer())
        body.addView(sectionCard("⭐", "Extras & Support",    "Cosmetics, licenses, and help",   IC_EXTRAS)    { push(S_EXTRAS) })
        body.addView(footerNote("Scout is built for families — safe, private, and always on your side."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    private fun mainHeader(): View {
        val v = vCol(BG).padded(dp(20), dp(52), dp(20), dp(12))
        v.addView(lbl("Settings", 26f, TXT, bold = true))
        v.addView(lbl("Customize Scout to fit your family.", 13f, TXT_SEC).padded(0, dp(4), 0, dp(12)))
        return v
    }

    // ─── IDENTITY & VOICE ───────────────────────────────────────

    private fun identityScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Identity & Voice", "Customize Scout's name and how he speaks."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        val name = scoutPrefs.getString("robot_name", "Scout") ?: "Scout"

        body.addView(sectionLabel("IDENTITY"))
        body.addView(cardGroup(
            navRow("Robot Name", name, "How Scout introduces himself") { push(S_ROBOT) }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("VOICE"))
        body.addView(cardGroup(
            sliderRow("🎵", "Voice Pitch", "Adjust how high or low Scout's voice sounds", "voice_pitch", scoutPrefs, 0.5f, 2.0f, 1.0f, preview = true),
            sliderRow("⚡", "Voice Speed", "Adjust how fast or slow Scout speaks", "voice_speed", scoutPrefs, 0.5f, 2.0f, 1.0f, preview = true),
            resetVoiceRow()
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("ACCESSIBILITY"))
        body.addView(cardGroup(
            toggleRow("Closed Captions", "Show Scout's words as text at the bottom of the screen",
                scoutPrefs.getBoolean("closed_captions", false)
            ) { on -> scoutPrefs.edit().putBoolean("closed_captions", on).apply() }
        ))

        body.addView(cardSpacer())
        body.addView(cardGroup(
            navRow("Voice Tone", "Warm  ✦ Future", "Choose a different tone for Scout") { toast("Voice Tone personalities coming in a future update!") }
        ))

        body.addView(footerNote("More voice tone options coming in a future update."))
        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── BRAIN & BEHAVIOR ───────────────────────────────────────

    private fun brainScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Brain & Behavior", "How Scout thinks and responds."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        body.addView(sectionLabel("CONNECTION"))
        body.addView(cardGroup(
            toggleRow("Online Features", "Use internet services when available",
                memPrefs.getBoolean("gemini_enabled", true)
            ) { on -> memPrefs.edit().putBoolean("gemini_enabled", on).apply() },
            navRow("Online Services", "", "Manage API keys and providers") { push(S_APIKEY) },
            navRow("Online Brain Helper", "Gemini / Llama", "Use an AI to make Scout smarter") { toast("Brain model selection coming in a future update!") }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("BEHAVIOR"))
        body.addView(cardGroup(
            toggleRow("Kid Safe Filter", "Keep conversations family-friendly",
                scoutPrefs.getBoolean("kid_safe_filter", true)
            ) { on -> scoutPrefs.edit().putBoolean("kid_safe_filter", on).apply() },
            toggleRow("Presence Mode", "Scout adapts when you're nearby",
                memPrefs.getBoolean("presence_mode_enabled", true)
            ) { on -> memPrefs.edit().putBoolean("presence_mode_enabled", on).apply() },
            toggleRow("Allow Spontaneous Comments", "Scout may share observations",
                memPrefs.getBoolean("spontaneous_enabled", true)
            ) { on -> memPrefs.edit().putBoolean("spontaneous_enabled", on).apply() }
        ))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── BUILDER'S WORKBENCH ────────────────────────────────────

    private fun workbenchScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Builder's Workbench", "Hardware, controls, and chassis."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        body.addView(sectionLabel("HARDWARE"))
        body.addView(cardGroup(
            toggleRow("Enable Hardware Mode", "Use motors, sensors, and chassis",
                scoutPrefs.getBoolean("hardware_mode", false)
            ) { on -> scoutPrefs.edit().putBoolean("hardware_mode", on).apply() },
            navRow("Motor Controls", "✦ Future", "Drive arms, lights, and more") { toast("Motor Controls coming in a future update!") },
            navRow("Bluetooth Pairing", "✦ Future", "Pair with Scout's hardware") { toast("Bluetooth Pairing coming in a future update!") }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("SAFETY"))
        body.addView(cardGroup(
            toggleRow("Pet Awareness", "Scout uses extra caution around pets",
                scoutPrefs.getBoolean("pet_safety", true)
            ) { on -> scoutPrefs.edit().putBoolean("pet_safety", on).apply() }
        ))

        body.addView(footerNote("More hardware controls coming in a future update."))
        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── PRIVACY & DATA ─────────────────────────────────────────

    private fun privacyScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Privacy & Data", "Manage Scout's memory and privacy."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        body.addView(sectionLabel("MEMORY"))
        body.addView(cardGroup(
            navRow("Memory Export", "", "Save Scout's memory to a file") { toast("Use the voice command 'export brain' for now — UI export coming soon!") },
            navRow("Import Memory", "", "Load memory from a file to restore or transfer") { toast("Memory Import coming in a future update!") },
            navRow("Reset Memory Layers", "", "Clear Scout's memory") { confirmReset() }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("CAMERA"))
        body.addView(cardGroup(
            navRow("Camera Controls", "✦ Future", "Manage access controls") { toast("Camera Controls coming in a future update!") },
            toggleRow("Voice Camera Commands", "Scout will look at you when you speak",
                scoutPrefs.getBoolean("voice_cam_cmds", true)
            ) { on -> scoutPrefs.edit().putBoolean("voice_cam_cmds", on).apply() }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("LEGAL"))
        body.addView(cardGroup(
            navRow("Privacy Policy", "", "How Scout handles your data") { showPrivacyPolicy() },
            navRow("Terms of Use", "", "Terms for using Scout") { showTermsOfUse() }
        ))

        body.addView(footerNote("Your data stays private. All memory files stay on your device unless you choose to share them."))
        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── EXTRAS & SUPPORT ───────────────────────────────────────

    private fun extrasScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Extras & Support", "Cosmetics, support, and more."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        body.addView(sectionLabel("COSMETICS"))
        body.addView(cardGroup(
            navRow("Cosmetics  ✦ Future", "", "Change Scout's backpack and look") { toast("Cosmetics coming in a future update!") }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("SUPPORT"))
        body.addView(cardGroup(
            navRow("Support", "", "Get help and connect to support") { showSupport() },
            navRow("About Scout", "", "Version and info") { showAbout() },
            navRow("Licenses", "", "Open source licenses") { showLicenses() }
        ))

        body.addView(cardSpacer())
        body.addView(sectionLabel("DIAGNOSTICS"))
        body.addView(cardGroup(
            navRow("View Diagnostic Report",  "", "Review the technical information in Scout's diagnostic report.") { startActivity(Intent(this, DiagReportActivity::class.java).putExtra(DiagReportActivity.EXTRA_SHOW_SHARE, false)) },
            navRow("Share Diagnostic Report", "", "Review the report, then choose where to send it")                  { startActivity(Intent(this, DiagReportActivity::class.java).putExtra(DiagReportActivity.EXTRA_SHOW_SHARE, true)) },
            navRow("Delete Diagnostic Logs",  "", "Remove all diagnostic events, crash log, and report") { confirmDeleteDiagLogs() }
        ))

        body.addView(footerNote("Thank you for supporting Scout!"))
        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── ROBOT NAME ─────────────────────────────────────────────

    private fun robotNameScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Robot Name", "This is how Scout introduces himself."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        body.addView(sectionLabel("NAME"))

        val cardWrap = vCol(Color.TRANSPARENT).apply {
            background = roundRect(CARD, 14)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val cur = scoutPrefs.getString("robot_name", "Scout") ?: "Scout"
        val edit = EditText(this).apply {
            setText(cur)
            textSize = 16f
            setTextColor(TXT)
            setHintTextColor(TXT_MUTE)
            hint = "Scout"
            background = roundRect(BG, 10)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSelection(cur.length)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        cardWrap.addView(lbl("Robot Name", 12f, TXT_SEC).padded(0, 0, 0, dp(8)))
        cardWrap.addView(edit)
        cardWrap.addView(lbl("Choose the name Scout will use when speaking.", 12f, TXT_MUTE).padded(0, dp(8), 0, 0))
        body.addView(cardWrap)

        body.addView(spacer(dp(16)))
        body.addView(actionBtn("Save Name") {
            val n = edit.text.toString().trim()
            if (n.isNotEmpty()) {
                scoutPrefs.edit().putString("robot_name", n).apply()
                // Also update TruthDb so the wake word and identity responses use the new name
                try {
                    val db = TruthDb(this)
                    db.upsertFact("scout", "name", n, 1.0f, "user_setting")
                    db.close()
                } catch (_: Exception) { }
                toast("Name saved as \"$n\"")
                pop()
            } else {
                toast("Name can't be empty")
            }
        })
        body.addView(tipCard("Your change takes effect immediately. Scout will use this name from now on."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── API KEY ────────────────────────────────────────────────

    private fun apiKeyScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("Online Services", "Manage API keys and providers."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(16), dp(16), dp(16), dp(32))

        val key = ScoutApiKeyHelper.getKey(this, ScoutApiKeyHelper.Provider.GEMINI)
        val hasKey = !key.isNullOrBlank()

        body.addView(sectionLabel("GEMINI"))
        body.addView(cardGroup(
            statusRow(
                "Google Gemini API Key",
                if (hasKey) "✓  Key saved and active" else "No key saved yet",
                if (hasKey) Color.parseColor("#4CAF50") else TXT_MUTE
            )
        ))

        body.addView(spacer(dp(16)))
        body.addView(actionBtn(if (hasKey) "Update API Key" else "Set Up API Key") {
            startActivity(Intent(this, ApiKeySetupActivity::class.java))
        })
        body.addView(tipCard("Your key is stored securely on this device only. Scout never sends your key anywhere else."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── CARD LAYOUT BUILDERS ───────────────────────────────────

    private fun sectionCard(icon: String, title: String, sub: String, iconBg: Int, onClick: () -> Unit): View {
        val card = hRow(Color.TRANSPARENT).apply {
            background = roundRect(CARD, 16)
            clipToOutline = true
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(16))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val iconBadge = FrameLayout(this).apply {
            background = roundRect(iconBg, 10)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) }
        }
        iconBadge.addView(TextView(this).apply {
            text = icon; textSize = 20f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })
        card.addView(iconBadge)
        val col = vCol(Color.TRANSPARENT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(lbl(title, 15f, TXT, bold = true))
        col.addView(lbl(sub, 12f, TXT_SEC).padded(0, dp(2), 0, 0))
        card.addView(col)
        card.addView(lbl("›", 22f, TXT_MUTE).padded(dp(8), 0, 0, 0))
        return card
    }

    private fun cardGroup(vararg rows: View): LinearLayout {
        val card = vCol(Color.TRANSPARENT).apply {
            background = roundRect(CARD, 14)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rows.forEachIndexed { i, row ->
            card.addView(row)
            if (i < rows.size - 1) card.addView(cardDivider())
        }
        return card
    }

    private fun cardDivider() = View(this).apply {
        setBackgroundColor(DIV)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(20)
        }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(TXT_MUTE)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.12f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8); topMargin = dp(4) }
        setPadding(dp(4), 0, 0, 0)
    }

    private fun cardSpacer() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16))
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun roundRect(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    // ─── SHARED HEADER ──────────────────────────────────────────

    private fun subHeader(title: String, sub: String): View {
        val v = vCol(BG).padded(0, dp(48), 0, 0)
        val backRow = hRow(BG).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(16), dp(4))
            isClickable = true; isFocusable = true
            setOnClickListener { pop() }
        }
        backRow.addView(lbl("←", 22f, ACCENT).padded(dp(8), dp(8), dp(16), dp(8)))
        backRow.addView(lbl(title, 16f, TXT, bold = true))
        v.addView(backRow)
        if (sub.isNotEmpty()) v.addView(lbl(sub, 13f, TXT_SEC).padded(dp(20), dp(4), dp(20), dp(12)))
        return v
    }

    // ─── ROW BUILDERS ───────────────────────────────────────────

    private fun navRow(title: String, value: String, sub: String, onClick: () -> Unit): View {
        val row = hRow(Color.TRANSPARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        val col = vCol(Color.TRANSPARENT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(lbl(title, 15f, TXT))
        if (sub.isNotEmpty()) col.addView(lbl(sub, 12f, TXT_SEC).padded(0, dp(2), 0, 0))
        row.addView(col)
        if (value.isNotEmpty()) row.addView(lbl(value, 13f, ACCENT).padded(dp(8), 0, dp(4), 0))
        row.addView(lbl("›", 22f, TXT_MUTE).padded(dp(4), 0, 0, 0))
        return row
    }

    private fun toggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): View {
        val row = hRow(Color.TRANSPARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(16), dp(14))
        }
        val col = vCol(Color.TRANSPARENT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(lbl(title, 15f, if (enabled) TXT else TXT_MUTE))
        if (sub.isNotEmpty()) col.addView(lbl(sub, 12f, TXT_SEC).padded(0, dp(2), 0, 0))
        row.addView(col)
        row.addView(Switch(this).apply {
            isChecked = checked
            isEnabled = enabled
            thumbTintList = csl(if (checked) ACCENT else TXT_MUTE)
            setOnCheckedChangeListener { _, on ->
                thumbTintList = csl(if (on) ACCENT else TXT_MUTE)
                onChange(on)
            }
        })
        return row
    }

    private fun resetVoiceRow(): View {
        val row = hRow(Color.TRANSPARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val col = vCol(Color.TRANSPARENT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(lbl("Reset Voice to Default", 15f, TXT))
        col.addView(lbl("Pitch 1.00  ·  Speed 1.00", 12f, TXT_SEC).padded(0, dp(2), 0, 0))
        row.addView(col)
        row.addView(Button(this).apply {
            text = "Reset"
            textSize = 13f; isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundRect(DIM_BLUE, 8)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                scoutPrefs.edit().putFloat("voice_pitch", 1.0f).putFloat("voice_speed", 1.0f).apply()
                speakPreview(true)
                show(S_IDENTITY)
            }
        })
        return row
    }

    private fun sliderRow(icon: String, title: String, sub: String, key: String, prefs: SharedPreferences, min: Float, max: Float, def: Float, preview: Boolean = false): View {
        val col = vCol(Color.TRANSPARENT).padded(dp(20), dp(14), dp(20), dp(14))
        val header = hRow(Color.TRANSPARENT).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(lbl("$icon  $title", 15f, TXT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val cur = prefs.getFloat(key, def)
        val valLabel = lbl(String.format("%.2f", cur), 14f, ACCENT, bold = true)
        header.addView(valLabel)
        col.addView(header)
        col.addView(lbl(sub, 12f, TXT_SEC).padded(dp(24), dp(2), 0, dp(8)))
        val steps = 30
        val rangeMin = min
        val rangeMax = max
        col.addView(SeekBar(this).apply {
            this.max = steps
            progress = ((cur - rangeMin) / (rangeMax - rangeMin) * steps).toInt().coerceIn(0, steps)
            progressTintList = csl(ACCENT)
            thumbTintList = csl(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = (Math.round((rangeMin + p.toFloat() / steps * (rangeMax - rangeMin)) * 100f)) / 100f
                    valLabel.text = String.format("%.2f", v)
                    if (fromUser) {
                        prefs.edit().putFloat(key, v).apply()
                        if (preview) {
                            previewRunnable?.let { previewHandler.removeCallbacks(it) }
                            previewRunnable = Runnable { speakPreview(false) }
                            previewHandler.postDelayed(previewRunnable!!, 350L)
                        }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    if (preview) {
                        previewRunnable?.let { previewHandler.removeCallbacks(it) }
                        previewRunnable = null
                        speakPreview(true)
                    }
                }
            })
        })
        return col
    }

    private fun statusRow(title: String, status: String, statusColor: Int): View {
        val row = vCol(Color.TRANSPARENT).apply {
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        row.addView(lbl(title, 15f, TXT))
        row.addView(lbl(status, 13f, statusColor).padded(0, dp(4), 0, 0))
        return row
    }

    // ─── SMALL HELPERS ──────────────────────────────────────────

    private fun footerNote(text: String): LinearLayout {
        val v = vCol(Color.TRANSPARENT).padded(dp(4), dp(16), dp(4), dp(8))
        v.addView(lbl(text, 12f, TXT_MUTE).apply { setLineSpacing(0f, 1.4f) })
        return v
    }

    private fun tipCard(text: String): View {
        val v = vCol(Color.TRANSPARENT).apply {
            background = roundRect(DIM_BLUE, 12)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        v.setPadding(dp(16), dp(14), dp(16), dp(14))
        v.addView(lbl(text, 13f, TXT_SEC).apply { setLineSpacing(0f, 1.4f) })
        return v
    }

    private fun actionBtn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f; isAllCaps = false
        setTextColor(Color.WHITE)
        background = roundRect(ACCENT, 10)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun lbl(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun vCol(bg: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
    }

    private fun hRow(bg: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(bg)
    }

    private fun LinearLayout.padded(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setPadding(l, t, r, b) }
    private fun TextView.padded(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setPadding(l, t, r, b) }

    private fun LinearLayout.fillParent() = apply {
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    private fun ScrollView.wrapWeight() = apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    private fun csl(c: Int) = ColorStateList.valueOf(c)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ─── DIALOGS ────────────────────────────────────────────────

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset Memory Layers")
            .setMessage("This will clear Scout's learned face and memory data. Scout's built-in knowledge stays intact.\n\nContinue?")
            .setPositiveButton("Reset") { _, _ ->
                try {
                    val db = PeopleDb(this)
                    db.writableDatabase.execSQL("DELETE FROM people")
                    db.close()
                    toast("Memory layers reset.")
                } catch (e: Exception) {
                    toast("Error: ${e.message}")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteDiagLogs() {
        AlertDialog.Builder(this)
            .setTitle("Delete Diagnostic Logs")
            .setMessage(
                "This will permanently delete:\n" +
                "  · All diagnostic events\n" +
                "  · The crash log\n" +
                "  · Any generated diagnostic report\n\n" +
                "It will not affect Scout's memories, personal facts, habits, " +
                "settings, conversations, model files, or any other data.\n\n" +
                "Continue?"
            )
            .setPositiveButton("Delete") { _, _ ->
                try {
                    DiagnosticDb(this).use { db -> db.deleteAll() }
                    toast("Diagnostic logs deleted.")
                } catch (_: Exception) {
                    toast("Could not delete diagnostic logs.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("About Scout")
            .setMessage("Scout is a local-first AI companion robot app.\n\nBuilt for families. Safe, private, and always on your side.\n\nVersion 1.0")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSupport() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lippy-robotics.gt.tc/support.html")))
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Unable to open the Scout Support Center")
                .setMessage("Scout couldn't open the Support Center. Please make sure Internet access is enabled in Scout, confirm that your device is connected to the Internet, and try again.")
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showLicenses() {
        AlertDialog.Builder(this)
            .setTitle("Open Source Software & Credits")
            .setMessage(
                "OFFLINE AI\n\n" +

                "TinyLlama 1.1B Chat v1.0\n" +
                "Apache 2.0 · Copyright © Zhang Peiyuan et al., Singapore University of Technology and Design\n" +
                "github.com/jzhang38/TinyLlama\n\n" +

                "llama.cpp\n" +
                "MIT License · Copyright © Georgi Gerganov and contributors\n" +
                "github.com/ggerganov/llama.cpp\n\n" +

                "──────────────────────────\n" +
                "FACE RECOGNITION\n\n" +

                "MobileFaceNet\n" +
                "MIT License · Copyright © 2019 syaringan357\n" +
                "Lightweight on-device face recognition model\n" +
                "github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing\n\n" +

                "ArcFace (InsightFace)\n" +
                "License: Under review — see insightface.ai\n" +
                "Face recognition training framework\n" +
                "github.com/deepinsight/insightface\n\n" +

                "TensorFlow Lite\n" +
                "Apache 2.0 · Copyright © Google LLC\n" +
                "tensorflow.org\n\n" +

                "──────────────────────────\n" +
                "CAMERA & VISION\n\n" +

                "Google ML Kit (Face Detection, Image Labeling)\n" +
                "Google APIs Terms of Service apply\n" +
                "developers.google.com/ml-kit\n\n" +

                "CameraX (AndroidX)\n" +
                "Apache 2.0 · Copyright © The Android Open Source Project\n" +
                "developer.android.com/training/camerax\n\n" +

                "──────────────────────────\n" +
                "NETWORKING & STORAGE\n\n" +

                "OkHttp\n" +
                "Apache 2.0 · Copyright © Square, Inc.\n" +
                "square.github.io/okhttp\n\n" +

                "Room (AndroidX)\n" +
                "Apache 2.0 · Copyright © The Android Open Source Project\n" +
                "developer.android.com/training/data-storage/room\n\n" +

                "──────────────────────────\n" +
                "UI FRAMEWORK\n\n" +

                "AndroidX & Material Components\n" +
                "Apache 2.0 · Copyright © The Android Open Source Project / Google\n" +
                "developer.android.com/jetpack\n\n" +

                "Kotlin\n" +
                "Apache 2.0 · Copyright © JetBrains s.r.o.\n" +
                "kotlinlang.org\n\n" +

                "──────────────────────────\n" +
                "EXTERNAL SERVICES\n\n" +

                "National Weather Service API (weather.gov)\n" +
                "Public Domain · Published by the U.S. Government\n" +
                "U.S. locations only · No tracking\n" +
                "weather.gov\n\n" +

                "Google Gemini API (optional)\n" +
                "Google AI Terms of Service apply\n" +
                "Used only when you provide your own API key\n" +
                "ai.google.dev/gemini-api/terms"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showPrivacyPolicy() {
        AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(
                "Lippy Robotics · Effective Date: July 8, 2026\n\n" +

                "YOUR DATA STAYS ON YOUR DEVICE\n" +
                "All of Scout's memory stays on your device. Your name, family members' names, " +
                "favorites, face recognition data, and conversation history are stored in local " +
                "files and databases only. Nothing is uploaded to our servers.\n\n" +

                "CAMERA & FACE RECOGNITION\n" +
                "Scout uses your device camera to recognize faces and see the room. Face " +
                "recognition data (mathematical embeddings) is stored locally and never " +
                "transmitted. The camera is only active while the Scout app is open.\n\n" +

                "MICROPHONE\n" +
                "Scout listens for your voice through your device microphone. Voice input is " +
                "processed on-device unless you choose to enable Online Features (see below).\n\n" +

                "OPTIONAL ONLINE FEATURES\n" +
                "If you enable Online Features and provide a Google Gemini API key, your voice " +
                "queries will be sent to Google's Gemini API using your own key. This is governed " +
                "by Google's Privacy Policy and Terms of Service. Lippy Robotics never receives " +
                "your queries — the connection goes directly from your device to Google.\n\n" +

                "WEATHER\n" +
                "If you ask Scout about the weather, your approximate device location is sent to " +
                "the U.S. National Weather Service (api.weather.gov) to retrieve a local " +
                "forecast. No personal data is included in this request.\n\n" +

                "NO ACCOUNTS REQUIRED\n" +
                "Scout does not require you to create an account. We do not collect your name, " +
                "email address, or any personal information.\n\n" +

                "NO ANALYTICS OR TRACKING\n" +
                "Scout does not include analytics, crash reporting, or ad tracking. No usage " +
                "data is collected or transmitted to Lippy Robotics.\n\n" +

                "DATA EXPORT\n" +
                "You can export Scout's memory at any time using the voice command " +
                "\"export brain.\" The exported file stays on your device until you choose to " +
                "share or delete it.\n\n" +

                "CHILDREN\n" +
                "Scout is not directed at children under 13. If you are under 13, please ask " +
                "a parent or guardian before using Scout.\n\n" +

                "CHANGES TO THIS POLICY\n" +
                "We may update this Privacy Policy from time to time. Changes will be reflected " +
                "in the app with an updated effective date.\n\n" +

                "CONTACT\n" +
                "Questions? Email us at lippyroboticslabs@gmail.com"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showTermsOfUse() {
        AlertDialog.Builder(this)
            .setTitle("Terms of Use")
            .setMessage(
                "Lippy Robotics · Effective Date: July 8, 2026\n\n" +

                "AGREEMENT\n" +
                "By downloading or using Scout, you agree to these Terms of Use. If you do not " +
                "agree, please do not use the app.\n\n" +

                "ABOUT THIS APP\n" +
                "Scout is a personal AI companion app for Android, created and maintained by " +
                "Lippy Robotics. Scout is provided \"as is,\" without warranties of any kind. " +
                "While we work to keep Scout reliable, we cannot guarantee uninterrupted " +
                "operation or compatibility with every Android device.\n\n" +

                "SCOUT IS A COMPANION, NOT AN ADVISOR\n" +
                "Scout is not a substitute for professional medical, legal, financial, mental " +
                "health, safety, or emergency advice. Nothing Scout says should be treated as " +
                "such. If you or someone you know is in an emergency, contact the appropriate " +
                "services immediately.\n\n" +

                "HOW YOU USE SCOUT\n" +
                "You are responsible for how you use Scout and for any content you choose to " +
                "teach or share with him. Scout is designed to be family-friendly, but no " +
                "software is perfect and unexpected responses may occasionally occur.\n\n" +

                "THIRD-PARTY CONNECTIONS\n" +
                "If you choose to connect Scout to third-party services such as Google Gemini " +
                "using your own API key, those services are governed by their own terms and " +
                "privacy policies. Lippy Robotics has no control over those services.\n\n" +

                "HOW SCOUT IS SOLD\n" +
                "Scout is sold as a one-time purchase. After purchase, you receive a license to " +
                "use your copy of Scout. We do not offer refunds except where required by law " +
                "or Google Play policy.\n\n" +

                "UPDATES\n" +
                "Scout may receive updates through Google Play. We may add, change, or remove " +
                "features over time. We intend to continue maintaining Scout's core companion " +
                "features and improving the app over time.\n\n" +

                "CHANGES TO TERMS\n" +
                "We may update these Terms of Use from time to time. When we do, we will update " +
                "the Effective Date above. Continued use of Scout after changes are posted means " +
                "you accept the updated terms.\n\n" +

                "LIMITATION OF LIABILITY\n" +
                "To the fullest extent permitted by law, Lippy Robotics is not responsible for " +
                "any indirect, incidental, or consequential damages arising from the use or " +
                "inability to use Scout.\n\n" +

                "YOUR DATA\n" +
                "How Scout handles your data is described in the Privacy Policy, available in " +
                "this app under Settings > Privacy & Data.\n\n" +

                "CONTACT\n" +
                "Questions? Email us at lippyroboticslabs@gmail.com"
            )
            .setPositiveButton("Close", null)
            .show()
    }
}
