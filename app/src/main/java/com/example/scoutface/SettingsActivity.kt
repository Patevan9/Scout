package com.example.scoutface

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var scoutPrefs: SharedPreferences
    private lateinit var memPrefs: SharedPreferences

    private val screenStack = ArrayDeque<String>()

    private var tts: TextToSpeech? = null
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    private val BG       = Color.parseColor("#0D1728")
    private val BG_ROW   = Color.parseColor("#19293F")
    private val ACCENT   = Color.parseColor("#4A8EFF")
    private val DIM_BLUE = Color.parseColor("#1E3D6E")
    private val TXT      = Color.WHITE
    private val TXT_SEC  = Color.parseColor("#8AAFC8")
    private val TXT_MUTE = Color.parseColor("#4A6280")
    private val DIV      = Color.parseColor("#1A2D45")

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
        push(S_MAIN)
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
            @Suppress("DEPRECATION")
            super.onBackPressed()
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
        val body = vCol(BG).padded(0, dp(8), 0, dp(24))

        body.addView(sectionRow("👤", "1. Identity & Voice",  "Customize Scout's name and how he speaks", Color.parseColor("#1A2D4A")) { push(S_IDENTITY) })
        body.addView(div())
        body.addView(sectionRow("🧠", "2. Brain & Behavior",   "How Scout thinks and responds",           Color.parseColor("#221A0D")) { push(S_BRAIN) })
        body.addView(div())
        body.addView(sectionRow("🔧", "3. Builder's Workbench","Hardware, controls, and chassis",         Color.parseColor("#0D2326")) { push(S_WORKBENCH) })
        body.addView(div())
        body.addView(sectionRow("🛡️", "4. Privacy & Data",     "Manage Scout's memory and privacy",       Color.parseColor("#0D2A0D")) { push(S_PRIVACY) })
        body.addView(div())
        body.addView(sectionRow("⭐", "5. Extras & Support",   "Cosmetics, support, and more",            Color.parseColor("#2A2408")) { push(S_EXTRAS) })
        body.addView(footerNote("💙  Scout is built for families.\nSafe, private, and always on your side."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    private fun mainHeader(): View {
        val v = vCol(BG).padded(dp(20), dp(52), dp(20), dp(16))
        v.addView(lbl("Settings", 28f, TXT, bold = true))
        v.addView(lbl("Customize Scout to fit your family.", 13f, TXT_SEC).padded(0, dp(4), 0, dp(14)))
        v.addView(div())
        return v
    }

    // ─── IDENTITY & VOICE ───────────────────────────────────────

    private fun identityScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("👤", "1. Identity & Voice", "Customize Scout's name and how he speaks."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(0, dp(8), 0, dp(32))

        val name = scoutPrefs.getString("robot_name", "Scout") ?: "Scout"
        body.addView(navRow("Robot Name", name, "This is how Scout introduces himself") { push(S_ROBOT) })
        body.addView(div())
        body.addView(sliderRow("🎵", "Voice Pitch",  "Adjust how high or low Scout's voice sounds", "voice_pitch",  scoutPrefs, 0.5f, 2.0f, 1.0f, preview = true))
        body.addView(div())
        body.addView(sliderRow("⚡", "Voice Speed",  "Adjust how fast or slow Scout speaks",        "voice_speed",  scoutPrefs, 0.5f, 2.0f, 1.0f, preview = true))
        body.addView(div())
        body.addView(navRow("Voice Tone", "Warm  ✦ Future", "Choose a different tone for Scout") { toast("Voice Tone personalities coming in a future update!") })
        body.addView(footerNote("✦  More voice tone options coming in a future update!"))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── BRAIN & BEHAVIOR ───────────────────────────────────────

    private fun brainScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("🧠", "2. Brain & Behavior", "How Scout thinks and responds."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(0, dp(8), 0, dp(32))

        body.addView(toggleRow("Offline Mode", "Only use data stored on this device",
            !memPrefs.getBoolean("gemini_enabled", true)
        ) { on -> memPrefs.edit().putBoolean("gemini_enabled", !on).apply() })
        body.addView(div())
        body.addView(navRow("Online Brain Helper", "Gemini / Llama", "Use an AI to make Scout smarter") { toast("Brain model selection coming in a future update!") })
        body.addView(div())
        body.addView(navRow("API Key", "", "Connect Scout to online services") { push(S_APIKEY) })
        body.addView(div())
        body.addView(toggleRow("Kid Safe Filter", "Keep conversations family-friendly",
            scoutPrefs.getBoolean("kid_safe_filter", true)
        ) { on -> scoutPrefs.edit().putBoolean("kid_safe_filter", on).apply() })
        body.addView(div())
        body.addView(toggleRow("Pet Safety Protocol Awareness", "Scout mentions pet safety when relevant",
            scoutPrefs.getBoolean("pet_safety", true)
        ) { on -> scoutPrefs.edit().putBoolean("pet_safety", on).apply() })
        body.addView(div())
        body.addView(toggleRow("Presence Mode", "Scout adapts when you're nearby",
            memPrefs.getBoolean("presence_mode_enabled", true)
        ) { on -> memPrefs.edit().putBoolean("presence_mode_enabled", on).apply() })
        body.addView(div())
        body.addView(toggleRow("Allow Spontaneous Comments", "Scout may share observations",
            memPrefs.getBoolean("spontaneous_enabled", true)
        ) { on -> memPrefs.edit().putBoolean("spontaneous_enabled", on).apply() })
        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── BUILDER'S WORKBENCH ────────────────────────────────────

    private fun workbenchScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("🔧", "3. Builder's Workbench", "Hardware, controls, and chassis."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(0, dp(8), 0, dp(32))

        body.addView(toggleRow("Enable Hardware Mode", "Use motors, sensors, and chassis",
            scoutPrefs.getBoolean("hardware_mode", false)
        ) { on -> scoutPrefs.edit().putBoolean("hardware_mode", on).apply() })
        body.addView(div())
        body.addView(navRow("Motor Controls", "✦ Future", "Drive arms, lights, and more") { toast("Motor Controls coming in a future update!") })
        body.addView(div())
        body.addView(navRow("Bluetooth Pairing", "✦ Future", "Pair with Scout's hardware") { toast("Bluetooth Pairing coming in a future update!") })
        body.addView(footerNote("✦  More work and controls coming in a future update!"))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── PRIVACY & DATA ─────────────────────────────────────────

    private fun privacyScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("🛡️", "4. Privacy & Data", "Manage Scout's memory and privacy."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(0, dp(8), 0, dp(32))

        body.addView(navRow("Memory Export", "", "Save Scout's memory to a file") { toast("Use the voice command 'export brain' for now — UI export coming soon!") })
        body.addView(div())
        body.addView(navRow("Import Memory", "", "Load memory from a file to restore or transfer to this device") { toast("Memory Import coming in a future update!") })
        body.addView(div())
        body.addView(navRow("Reset Memory Layers", "", "Clear Scout's memory") { confirmReset() })
        body.addView(div())
        body.addView(navRow("Camera Controls", "✦ Future", "Manage access controls") { toast("Camera Controls coming in a future update!") })
        body.addView(div())
        body.addView(toggleRow("Voice Camera Commands", "Scout will look at you when you speak",
            scoutPrefs.getBoolean("voice_cam_cmds", true)
        ) { on -> scoutPrefs.edit().putBoolean("voice_cam_cmds", on).apply() })
        body.addView(footerNote("🔒  Your data stays private.\nAll memory files stay on your device unless you choose to share them."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── EXTRAS & SUPPORT ───────────────────────────────────────

    private fun extrasScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("⭐", "5. Extras & Support", "Cosmetics, support, and more."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(0, dp(8), 0, dp(32))

        body.addView(navRow("Cosmetics  ✦ Future", "", "Change Scout's backpack and look") { toast("Cosmetics coming in a future update!") })
        body.addView(div())
        body.addView(navRow("Support", "", "Get help and connect to support") { showSupport() })
        body.addView(div())
        body.addView(navRow("About Scout", "", "Version and info") { showAbout() })
        body.addView(div())
        body.addView(navRow("Licenses", "", "Open source licenses") { showLicenses() })
        body.addView(footerNote("💙  Thank you for supporting Scout!"))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── ROBOT NAME ─────────────────────────────────────────────

    private fun robotNameScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("👤", "Robot Name", "This is how Scout introduces himself."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(20), dp(24), dp(20), dp(32))

        body.addView(lbl("Robot Name", 13f, TXT_SEC).padded(0, 0, 0, dp(8)))

        val cur = scoutPrefs.getString("robot_name", "Scout") ?: "Scout"
        val edit = EditText(this).apply {
            setText(cur)
            textSize = 16f
            setTextColor(TXT)
            setHintTextColor(TXT_MUTE)
            hint = "Scout"
            setBackgroundColor(BG_ROW)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSelection(cur.length)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        body.addView(edit)
        body.addView(lbl("Choose the name Scout will use when speaking to you and others.", 13f, TXT_SEC).padded(0, dp(10), 0, dp(20)))
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
        body.addView(tipCard("💡  You can change this anytime.\nScout will remember."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── API KEY ────────────────────────────────────────────────

    private fun apiKeyScreen(): View {
        val root = vCol(BG).fillParent()
        root.addView(subHeader("🔑", "API Key", "Connect Scout to online services."))
        val scroll = ScrollView(this).wrapWeight()
        val body = vCol(BG).padded(dp(20), dp(24), dp(20), dp(32))

        val key = ScoutApiKeyHelper.getKey(this, ScoutApiKeyHelper.Provider.GEMINI)
        val hasKey = !key.isNullOrBlank()

        body.addView(lbl("Google Gemini API Key", 14f, TXT_SEC).padded(0, 0, 0, dp(8)))
        body.addView(lbl(
            if (hasKey) "✓  API key is saved and active" else "No key saved yet",
            14f,
            if (hasKey) Color.parseColor("#4CAF50") else TXT_MUTE
        ).padded(0, 0, 0, dp(20)))
        body.addView(actionBtn(if (hasKey) "Update API Key" else "Set Up API Key") {
            startActivity(Intent(this, ApiKeySetupActivity::class.java))
        })
        body.addView(tipCard("🔒  Your key is stored securely on this device only.\nScout never sends your key anywhere else."))

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    // ─── SHARED HEADER ──────────────────────────────────────────

    private fun subHeader(icon: String, title: String, sub: String): View {
        val v = vCol(BG).padded(0, dp(48), 0, 0)
        val backRow = hRow(BG).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(16), dp(4))
            isClickable = true; isFocusable = true
            setOnClickListener { pop() }
        }
        backRow.addView(lbl("←", 22f, ACCENT).padded(dp(8), dp(8), dp(16), dp(8)))
        backRow.addView(lbl("$icon  $title", 16f, TXT, bold = true))
        v.addView(backRow)
        if (sub.isNotEmpty()) v.addView(lbl(sub, 13f, TXT_SEC).padded(dp(20), dp(4), dp(20), dp(14)))
        v.addView(div())
        return v
    }

    // ─── ROW BUILDERS ───────────────────────────────────────────

    private fun sectionRow(icon: String, title: String, sub: String, bg: Int, onClick: () -> Unit): View {
        val row = hRow(bg).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        val iconBox = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0F1F35"))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(16) }
        }
        iconBox.addView(TextView(this).apply {
            text = icon; textSize = 20f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })
        row.addView(iconBox)
        val col = vCol(Color.TRANSPARENT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(lbl(title, 15f, TXT, bold = true))
        col.addView(lbl(sub, 12f, TXT_SEC).padded(0, dp(2), 0, 0))
        row.addView(col)
        row.addView(lbl("›", 22f, TXT_MUTE).padded(dp(8), 0, 0, 0))
        return row
    }

    private fun navRow(title: String, value: String, sub: String, onClick: () -> Unit): View {
        val row = hRow(BG_ROW).apply {
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
        val row = hRow(BG_ROW).apply {
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

    private fun sliderRow(icon: String, title: String, sub: String, key: String, prefs: SharedPreferences, min: Float, max: Float, def: Float, preview: Boolean = false): View {
        val col = vCol(BG_ROW).padded(dp(20), dp(14), dp(20), dp(14))
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

    // ─── SMALL HELPERS ──────────────────────────────────────────

    private fun div() = View(this).apply {
        setBackgroundColor(DIV)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun footerNote(text: String): LinearLayout {
        val v = vCol(Color.TRANSPARENT).padded(dp(20), dp(20), dp(20), dp(8))
        v.addView(div())
        v.addView(lbl(text, 12f, ACCENT).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
            setLineSpacing(0f, 1.4f)
        })
        return v
    }

    private fun tipCard(text: String): View {
        val v = vCol(DIM_BLUE).padded(dp(16), dp(14), dp(16), dp(14))
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) }
        v.addView(lbl(text, 13f, TXT_SEC).apply { setLineSpacing(0f, 1.4f) })
        return v
    }

    private fun actionBtn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f; isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(ACCENT)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
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

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("About Scout")
            .setMessage("Scout is a local-first AI companion robot app.\n\nBuilt for families. Safe, private, and always on your side.\n\nVersion 1.0")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSupport() {
        AlertDialog.Builder(this)
            .setTitle("Support")
            .setMessage("Need help with Scout? Visit our support page or reach out for assistance.")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showLicenses() {
        AlertDialog.Builder(this)
            .setTitle("Open Source Licenses")
            .setMessage(
                "MobileFaceNet — MIT License\n" +
                "TensorFlow Lite — Apache 2.0\n" +
                "ML Kit — Google APIs Terms\n" +
                "CameraX — Apache 2.0\n" +
                "OkHttp — Apache 2.0\n" +
                "Room — Apache 2.0"
            )
            .setPositiveButton("Close", null)
            .show()
    }
}
