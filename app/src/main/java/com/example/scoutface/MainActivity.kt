package com.example.scoutface

import android.Manifest

import android.content.Context

import android.content.Intent

import android.content.SharedPreferences

import android.content.pm.ActivityInfo

import android.content.pm.PackageManager

import android.media.AudioManager

import android.os.Bundle

import android.os.Handler

import android.os.Looper

import android.speech.RecognitionListener

import android.speech.RecognizerIntent

import android.speech.SpeechRecognizer

import android.speech.tts.TextToSpeech

import android.speech.tts.UtteranceProgressListener

import android.util.Log

import android.view.View

import android.view.GestureDetector

import android.view.MotionEvent

import android.view.WindowInsets

import android.view.WindowManager

import androidx.activity.result.ActivityResultLauncher

import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AppCompatActivity

import androidx.camera.core.ImageAnalysis

import androidx.camera.lifecycle.ProcessCameraProvider

import androidx.camera.view.PreviewView

import androidx.core.content.ContextCompat

import androidx.core.view.WindowCompat

import androidx.core.view.WindowInsetsControllerCompat

import com.example.scoutface.brain.FactKey

import com.example.scoutface.brain.ScoutBootStatus

import com.example.scoutface.brain.ScoutConnectivityManager

import com.example.scoutface.brain.ScoutIntentRouter

import com.example.scoutface.brain.TeachExtractor

import com.example.scoutface.brain.ScoutPromptBuilder

import com.example.scoutface.brain.ScoutGeminiManager

import com.example.scoutface.brain.ScoutWeatherManager

import com.example.scoutface.brain.ScoutPresenceDecider

import com.example.scoutface.brain.TextNormalizer

import com.google.mlkit.vision.common.InputImage

import com.google.mlkit.vision.face.FaceDetection

import com.google.mlkit.vision.face.FaceDetector

import com.google.mlkit.vision.face.FaceDetectorOptions

import com.google.mlkit.vision.label.ImageLabeler

import com.google.mlkit.vision.label.ImageLabeling

import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

import java.io.File

import java.text.SimpleDateFormat

import java.util.Calendar

import java.util.Date

import java.util.Locale

import java.util.TimeZone

import java.util.concurrent.ExecutorService

import java.util.concurrent.Executors

import java.util.concurrent.TimeUnit

import java.util.concurrent.atomic.AtomicBoolean

import java.util.concurrent.atomic.AtomicInteger

import android.graphics.Bitmap

import android.graphics.Matrix

import kotlin.math.abs

enum class IntentType {

    TIME, DATE, CONNECTIVITY,

    GO_ONLINE, GO_OFFLINE,

    EXPORT_BRAIN,

    VISION,

    GREET, HOW_ARE_YOU, GOODBYE,

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

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // =======================

    // ONLINE / GEMINI

    // =======================

    private val apiKey: String
        get() = ScoutApiKeyHelper.getKey(this, ScoutApiKeyHelper.Provider.GEMINI) ?: ""

    private val GEMINI_MODEL = "gemini-3.5-flash-lite"

    // =======================

    // EYE MODE GATING

    // =======================

    private val BOOT_GAZE_LOCK_MS = 3500L

    @Volatile private var gazeEnabled = false

    private var lastSentGazeX = 0f

    private var lastSentGazeY = 0f

    private val MIN_GAZE_DELTA = 3.0f

    // =======================

    // IDENTITIES / FACT KEYS

    // =======================

    private val ENTITY_SCOUT = "scout"

    private val ENTITY_USER_PRIMARY = "user_primary"

    // =======================

    // PREFS

    // =======================

    private val PREFS = "scout_memory"

    private val PREF_GEMINI_ENABLED = "gemini_enabled"

    // Off by default — matches SettingsActivity's memPrefs key of the same name.
    private val PREF_CALENDAR_ENABLED = "calendar_awareness_enabled"

    private val PREF_PRESENCE_MODE_ENABLED = "presence_mode_enabled"

    private val PREF_SPONTANEOUS_ENABLED = "spontaneous_enabled"

    // Lives in "scout_prefs" (scoutPrefs), not "scout_memory" — a one-time lifecycle
    // milestone like PREF_ONBOARDING_DONE, not a user-configurable setting. Means "has
    // this install ever completed its first real startup," independent of which model
    // is currently loaded -- a future model upgrade or repair download must never look
    // like Scout meeting the user for the first time again.
    private val PREF_FIRST_STARTUP_DONE = "first_startup_experienced"

    // =======================

    // STATE

    // =======================

    private enum class Mode { PRESENCE, REST }

    private var currentMode = Mode.PRESENCE

    // Set by onInit() if TTS becomes ready before the offline brain does (the common
    // case). Consumed once, from startSystems(), once the brain is actually ready.
    // A boolean, not the built string itself -- bootStatus.build() must be called fresh
    // at actual speak-time, since building it early (while the brain is still loading)
    // and speaking that same text later produced a stale "still warming up" line even
    // after the brain had already finished loading.
    private var pendingBootAnnouncement = false

    @Volatile

    private var isSpeaking = false

    @Volatile

    private var isListening = false

    @Volatile

    private var isThinking = false

    // =======================

    // MIC DISCIPLINE

    // =======================

    private var lastSpeechDoneMs = 0L
    private var lastScoutResponseMs = 0L
    private var lastScoutUtteranceNormalized = ""
    private val CONVO_WINDOW_MS = 30_000L

    // Reminder fires when speech is heard outside the conversation window while a face is visible.
    // Throttled to once every 2 minutes so it never becomes annoying.
    private var lastListeningReminderMs = 0L
    private val LISTENING_REMINDER_COOLDOWN_MS = 120_000L

    private var lastMeaningfulResponse: String? = null
    private var lastMeaningfulResponseMs = 0L
    private val REPEAT_CACHE_TTL_MS = 4L * 60L * 1_000L

    private var pendingBrainSource = ""

    private val MIC_RESUME_COOLDOWN_MS = 650L

    private val LISTEN_RESTART_DELAY_MS = 150L

    private val TTS_LOCKOUT_MS = 600L

    private var ttsLockoutUntilMs = 0L

    @Volatile

    private var wantListening = true

    private var pendingListenStart = false

    private var bootFinishedSpeaking = false

    // True after Scout has already told the user his offline brain is loading. We only
    // say "warming up" once per session — the user doesn't need a reminder every question.
    private var warmingUpSaidThisSession = false

    // Incremented at the start of every new question. TinyLlama threads capture this
    // value when they launch and discard their reply if it no longer matches — prevents
    // a slow generation from firing after a newer question has already been answered.
    private var llamaQueryGeneration = 0L

    // Single-thread executor for LlamaEngine.generate() calls. A raw Thread per call let
    // two generations run concurrently against the native engine (the generation counter
    // above only discarded the stale *reply*, it never stopped the stale *inference*).
    // Routing through one executor serializes calls, and shutdownSystems() can await its
    // termination before LlamaEngine.free() so free() can't race an in-flight generate().
    private val llamaExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val BOOT_LISTEN_EXTRA_DELAY_MS = 250L

    private val TRY_MUTE_BEEP = true

    private var savedSystemVolume: Int? = null

    private var savedNotificationVolume: Int? = null

    private var lastRecognizerEventMs = 0L

    private val RECOGNIZER_WATCHDOG_MS = 12_000L

    private val recognizerWatchdog = Runnable { runRecognizerWatchdog() }

    // Guards against TTS silently failing (no onDone/onError callback after engine
    // is killed by Android). If isSpeaking stays true longer than this, force-clear it.
    private var speakingStartedMs = 0L
    private val MAX_SPEAKING_DURATION_MS = 45_000L

    // Guards against TinyLlama hanging with no reply. If isThinking stays true longer
    // than this without Scout speaking, force-clear so the mic restarts.
    private var thinkingStartedMs = 0L
    private val MAX_THINKING_DURATION_MS = 120_000L

    // Mic visual gating

    private val MIC_RMS_FLOOR_DB = 2.5f

    private val MIC_RMS_RANGE_DB = 6.0f

    private val MIC_VISUAL_DEADZONE = 0.05f

    // =======================
    // VISION STATE
    // =======================
    @Volatile

    private var lastSceneLabels: List<Pair<String, Float>> = emptyList()

    @Volatile

    private var lastSceneUpdatedMs: Long = 0L

    @Volatile

    private var lastFaceCount: Int = 0

    @Volatile

    private var lastFaceUpdatedMs: Long = 0L

    @Volatile

    private var lastFaceHashes: List<String> = emptyList()

    private var lastHabitFaceLogMs = 0L

    private lateinit var faceEmbedder: FaceEmbedder

    private lateinit var embedExecutor: ExecutorService

    @Volatile

    private var lastEmbedMs = 0L

    @Volatile

    private var lastFaceEmbedding: FloatArray? = null

    @Volatile

    private var lastKnownFaceName: String? = null

    @Volatile

    private var lastSecondaryFaceName: String? = null

    private val EMBED_INTERVAL_MS = 2_000L

    // Minimum score to add an embedding to a person's stored profile. Higher than the
    // recognition threshold (0.65) so borderline matches don't pollute other people's profiles.
    private val CONFIDENT_EMBED_THRESHOLD = 0.72f

    private val embedRunning = AtomicBoolean(false)

    @Volatile

    private var pendingFaceIntroName: String? = null

    @Volatile

    private var lastAnalysisMs = 0L

    private val ANALYSIS_MIN_INTERVAL_MS = 150L

    // Gaze hold to prevent snap-back on brief face detector drops

    @Volatile

    private var lastGoodGazeX = 0f

    @Volatile

    private var lastGoodGazeY = 0f

    @Volatile

    private var lastGoodFaceSeenMs = 0L

    private val FACE_LOST_HOLD_MS = 650L

    @Volatile

    private var faceAppearanceMs = 0L

    @Volatile

    private var greetedThisSession = false

    @Volatile

    private var faceLastSeenForGreetMs = 0L

    private val GREET_STABILIZE_MS = 3_000L

    private val GREET_RESET_ABSENCE_MS = 5_000L

    // =======================

    // COMPONENTS

    // =======================

    private lateinit var exportManager: ScoutExportManager

    private lateinit var bootStatus: ScoutBootStatus

    private lateinit var statusFacade: ScoutStatusFacade

    private lateinit var visionAnswerBuilder: VisionAnswerBuilder

    private lateinit var tts: TextToSpeech

    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var recognizerIntent: Intent

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var modelDownloadLauncher: ActivityResultLauncher<Intent>

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var faceDetector: FaceDetector

    private lateinit var labeler: ImageLabeler

    private lateinit var faceView: ScoutFaceView

    private lateinit var viewFinder: PreviewView

    private lateinit var swipeDetector: GestureDetector

    private lateinit var captionsText: android.widget.TextView

    private var captionsEnabled = false

    private val captionHideRunnable = Runnable {
        captionsText.visibility = View.GONE
    }

    private val handler = Handler(Looper.getMainLooper())

    // =======================

    // MEMORY / DB

    // =======================

    private lateinit var prefs: SharedPreferences
    private lateinit var scoutPrefs: SharedPreferences   // "scout_prefs" — voice, name, etc.

    private lateinit var truthDb: TruthDb

    private lateinit var convoDb: ConversationDb

    private lateinit var peopleDb: PeopleDb

    private lateinit var journalDb: JournalDb

    private lateinit var calendarReader: CalendarReader

    private lateinit var diagDb: DiagnosticDb
    private lateinit var diagLog: DiagLog

    private lateinit var habitLayer: HabitLayer

    private lateinit var voice: VoiceBank

    private lateinit var connectivityManager: ScoutConnectivityManager

    private lateinit var geminiClient: GeminiClient

    private lateinit var scoutGeminiManager: ScoutGeminiManager

    private lateinit var weatherManager: ScoutWeatherManager

    private lateinit var presenceDecider: ScoutPresenceDecider

    // =======================

    // GAZE INPUTS

    // =======================

    private val IRIS_MAX_X = 74f

    private val IRIS_MAX_Y = 54f

    // =======================

    // LIFECYCLE

    // =======================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Show onboarding on first install; skip on every subsequent launch.
        val scoutPrefsEarly = getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
        if (!scoutPrefsEarly.getBoolean(OnboardingActivity.PREF_ONBOARDING_DONE, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        setupWindow()

        setupMemory()

        setupBrainServices()

        setupViews()

        setupVision()

        setupPermissionLauncher()

        setupTts()

        // Scout never appears -- no face, no permissions, no listening, no greeting --
        // until the offline brain is confirmed ready. The loading screen always shows
        // first; startSystems() only ever runs once it returns (see modelDownloadLauncher).
        if (LlamaEngine.isReady) {
            startSystems()
        } else {
            launchLoadingGate()
        }

    }

    private fun launchLoadingGate() {
        try {
            modelDownloadLauncher.launch(Intent(this, ModelDownloadActivity::class.java))
        } catch (e: Throwable) {
            android.util.Log.e("ScoutBrain", "modelDownloadLauncher.launch() threw", e)
            startSystems() // fall back rather than leaving Scout stuck forever
        }
    }

    private fun setupBrainServices() {

        visionAnswerBuilder = VisionAnswerBuilder(
            truthDb = truthDb,
            peopleDb = peopleDb,
            voice = voice,
            entityUserPrimary = ENTITY_USER_PRIMARY
        )

        geminiClient = GeminiClient(

            apiKeyProvider = { apiKey },

            modelProvider = { GEMINI_MODEL }

        )

        connectivityManager = ScoutConnectivityManager(this)

        scoutGeminiManager = ScoutGeminiManager(

            geminiClient = geminiClient,

            truthDb = truthDb,

            habitLayer = habitLayer,

            isGeminiEnabled = { isGeminiEnabled() },

            hasApiKey = { apiKey.trim().isNotBlank() },

            hasValidatedInternet = { connectivityManager.hasValidatedInternet() },

            runOnMain = { action -> runOnUiThread { action() } },

            respond = { message -> respond(message) },

            finishThinking = { finishThinking() }

        )

        presenceDecider = ScoutPresenceDecider(

            isSpontaneousCommentsEnabled = { prefs.getBoolean(PREF_SPONTANEOUS_ENABLED, true) },

            isPresenceModeEnabled = { prefs.getBoolean(PREF_PRESENCE_MODE_ENABLED, true) }

        )

        weatherManager = ScoutWeatherManager(

            context = this,

            hasValidatedInternet = { isGeminiEnabled() && connectivityManager.hasValidatedInternet() },

            runOnMain = { action -> runOnUiThread { action() } },

            respond = { message -> respond(message) },

            finishThinking = { finishThinking() }

        )

        statusFacade = ScoutStatusFacade(

            isGeminiEnabled = { isGeminiEnabled() },

            hasApiKey = { apiKey.trim().isNotBlank() },

            hasValidatedInternet = { connectivityManager.hasValidatedInternet() },

            isOnWifi = { connectivityManager.isOnWifi() }

        )

        bootStatus = ScoutBootStatus(

            isGeminiEnabled = { isGeminiEnabled() },

            hasApiKey = { apiKey.trim().isNotBlank() },

            hasValidatedInternet = { connectivityManager.hasValidatedInternet() },

            lastLlamaLoadMs = { scoutPrefs.getLong("llama_last_load_ms", Long.MAX_VALUE) }

        )

    }

    override fun onPause() {

        super.onPause()

        // Scout is no longer visible — stop listening and stop the recognizer watchdog
        // from destroying/recreating the recognizer in the background. Without this,
        // the watchdog (recognizerWatchdog) keeps rescheduling itself every
        // RECOGNIZER_WATCHDOG_MS forever, regardless of foreground state.
        stopListeningSafe()
        handler.removeCallbacks(recognizerWatchdog)

    }

    override fun onResume() {

        super.onResume()

        // Re-apply voice settings in case they were changed in SettingsActivity.
        tts.setPitch(scoutPrefs.getFloat("voice_pitch", 0.98f))
        tts.setSpeechRate(scoutPrefs.getFloat("voice_speed", 0.88f))

        captionsEnabled = scoutPrefs.getBoolean("closed_captions", false)
        if (!captionsEnabled) {
            handler.removeCallbacks(captionHideRunnable)
            captionsText.visibility = View.GONE
        }

        resumeSystems()

        // Re-arm the watchdog that onPause() stopped.
        setupRecognizerWatchdog()

    }

    private fun shutdownSystems() {

        // The activity is going away for good — cancel every pending Handler callback
        // (recognizerWatchdog's reschedule chain, the 90s tryLoadOfflineBrain() load,
        // captionHideRunnable, etc.) so nothing fires against state that's about to be
        // torn down below.
        try {
            handler.removeCallbacksAndMessages(null)
        } catch (_: Exception) {
        }

        try {

            wantListening = false

            stopListeningSafe()

            restoreSystemBeep()

            try {

                speechRecognizer?.destroy()

            } catch (_: Exception) {

            }

            try {

                tts.shutdown()

            } catch (_: Exception) {

            }

            try {

                cameraExecutor.shutdown()

            } catch (_: Exception) {

            }

            try {

                faceDetector.close()

            } catch (_: Exception) {

            }

            try {

                labeler.close()

            } catch (_: Exception) {

            }

            try {

                faceEmbedder.close()

            } catch (_: Exception) {

            }

            try {

                embedExecutor.shutdown()

            } catch (_: Exception) {

            }

            try {

                truthDb.close()

            } catch (_: Exception) {

            }

            try {

                convoDb.close()

            } catch (_: Exception) {

            }

            try {

                peopleDb.close()

            } catch (_: Exception) {

            }

            try {

                journalDb.close()

            } catch (_: Exception) {

            }

        } catch (_: Exception) {

        }

        // Stop accepting new generations and wait briefly for any in-flight
        // LlamaEngine.generate() call to return before freeing the native engine.
        // Freeing while a generation is still running is a native-crash risk.
        try {

            llamaExecutor.shutdown()
            llamaExecutor.awaitTermination(5, TimeUnit.SECONDS)

        } catch (_: Exception) {

        }

        try {

            LlamaEngine.free()

        } catch (_: Exception) {

        }

    }

    private fun setupViews() {

        faceView = findViewById(R.id.faceView)

        viewFinder = findViewById(R.id.viewFinder)

        captionsText = findViewById(R.id.captionsText)

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {

                val dx = e2.x - (e1?.x ?: return false)

                if (dx > 160f && vX > 400f && abs(vY) < abs(vX)) {

                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_from_left, R.anim.stay_still)

                    return true

                }

                return false

            }

        })

        showSwipeHintIfNeeded()

    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        return swipeDetector.onTouchEvent(event) || super.onTouchEvent(event)

    }

    private fun showSwipeHintIfNeeded() {

        if (prefs.getBoolean("swipe_hint_shown", false)) return

        prefs.edit().putBoolean("swipe_hint_shown", true).apply()

        handler.postDelayed({

            val container = findViewById<android.widget.FrameLayout>(R.id.hintContainer)

            val density = resources.displayMetrics.density

            val hint = android.widget.TextView(this).apply {

                text = "→  Swipe right for Settings"

                textSize = 15f

                setTextColor(android.graphics.Color.WHITE)

                setBackgroundColor(android.graphics.Color.parseColor("#CC000E1A"))

                setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())

                val lp = android.widget.FrameLayout.LayoutParams(

                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,

                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT

                )

                lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START

                lp.setMargins((24 * density).toInt(), 0, 0, (40 * density).toInt())

                layoutParams = lp

            }

            container?.addView(hint)

            handler.postDelayed({ container?.removeView(hint) }, 4500L)

        }, 3000L)

    }

    private fun setupRecognizerWatchdog() {

        handler.removeCallbacks(recognizerWatchdog)

        handler.postDelayed(recognizerWatchdog, 4_000L)

    }

    private fun setupVision() {

        cameraExecutor = Executors.newSingleThreadExecutor()

        faceDetector = FaceDetection.getClient(

            FaceDetectorOptions.Builder()

                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)

                .build()

        )

        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        faceEmbedder = FaceEmbedder(this)

        embedExecutor = Executors.newSingleThreadExecutor()

    }

    private fun setupWindow() {

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableFullscreenCompat()

    }

    private fun startSystems() {

        // TTS almost always finishes initializing before the offline brain does; this
        // is the queued boot announcement from onInit() in that case, spoken now that
        // the brain is actually ready instead of the moment TTS happened to be ready.
        // Built fresh here (not back in onInit()) so it reflects the brain's actual,
        // now-ready state rather than a stale message captured while still loading.
        if (pendingBootAnnouncement) {
            pendingBootAnnouncement = false
            val out = bootStatus.build()
            speak(out, true)
            convoDb.logTurn("scout", out)
            journalDb.add("Booted. Spoke: $out")
        }

        val permissionRequestLaunched = checkPermissionsAndStart()

        setupRecognizerWatchdog()

        // If a permission request is in flight, it just started a system dialog activity on
        // top of Scout. Launching the download screen at the same instant races it for the
        // foreground -- the dialog can end up buried until the user backs out of the app.
        // Deferred to the permission result callback in that case (see setupPermissionLauncher()).
        if (!permissionRequestLaunched) {
            bootstrapModelFile()
        }

        startOfflineBrain()

        checkBatteryOptimization()

    }

    private fun checkBatteryOptimization() {
        if (prefs.getBoolean("battery_opt_shown", false)) return
        prefs.edit().putBoolean("battery_opt_shown", true).apply()
        // Delay so Scout finishes his boot announcement before the system dialog appears.
        handler.postDelayed({
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            } catch (_: Exception) {
                // Device doesn't support the direct prompt — user will need to set it manually.
            }
        }, 8_000L)
    }

    private val MODEL_FILENAME = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

    /**
     * Copies the TinyLlama model from external storage into filesDir if it isn't there yet.
     * Checks two source locations:
     *   1. App-specific external dir (/sdcard/Android/data/<pkg>/files/) — no permission needed
     *   2. Root external storage (/sdcard/) — requires READ_EXTERNAL_STORAGE (Android ≤12 only)
     * Safe to call multiple times; no-ops immediately if the dest file already looks complete.
     */
    private fun bootstrapModelFile() {
        val dest = File(filesDir, MODEL_FILENAME)
        if (dest.exists() && dest.length() >= ModelDownloadActivity.MIN_MODEL_BYTES) return

        Thread {
            val sources = mutableListOf<File>()
            getExternalFilesDir(null)?.let { sources.add(File(it, MODEL_FILENAME)) }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                sources.add(File(android.os.Environment.getExternalStorageDirectory(), MODEL_FILENAME))
            }

            val src = sources.firstOrNull { it.exists() && it.canRead() && it.length() >= ModelDownloadActivity.MIN_MODEL_BYTES }
            if (src == null) {
                android.util.Log.w("ScoutBrain", "Model not found locally — launching download screen.")
                runOnUiThread {
                    try {
                        modelDownloadLauncher.launch(Intent(this, ModelDownloadActivity::class.java))
                    } catch (e: Throwable) {
                        android.util.Log.e("ScoutBrain", "modelDownloadLauncher.launch() threw", e)
                    }
                }
                return@Thread
            }

            android.util.Log.i("ScoutBrain",
                "Copying model from ${src.absolutePath} (${src.length() / 1_048_576}MB)…")
            try {
                src.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                android.util.Log.i("ScoutBrain", "Model copy complete (${dest.length() / 1_048_576}MB)")
            } catch (e: Exception) {
                android.util.Log.e("ScoutBrain", "Model copy failed", e)
                dest.delete()
            }
        }.start()
    }

    private fun startOfflineBrain() {

        // Delay 90 seconds so startup memory spike (camera, ML Kit, Gemini) settles
        // before we add ~800MB for TinyLlama. Immediate load was killing Scout on A32.
        handler.postDelayed({ tryLoadOfflineBrain() }, 90_000L)
        android.util.Log.i("ScoutBrain", "Offline brain load scheduled for 90s after startup")

    }

    private fun tryLoadOfflineBrain() {

        if (LlamaEngine.isReady || LlamaEngine.isLoading) return

        val actMgr = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actMgr.getMemoryInfo(memInfo)
        val freeMb = memInfo.availMem / 1_048_576L
        android.util.Log.i("ScoutBrain", "Free RAM before TinyLlama load: ${freeMb}MB")

        if (freeMb < 800L) {
            android.util.Log.e("ScoutBrain", "Skipping TinyLlama — only ${freeMb}MB free (need 800MB)")
            return
        }

        val candidates = listOf(
            java.io.File(filesDir, "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"),
            java.io.File("/data/data/com.example.scoutface/files/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf")
        )
        val modelFile = candidates.firstOrNull { it.exists() && it.length() >= ModelDownloadActivity.MIN_MODEL_BYTES }
        if (modelFile == null) {
            android.util.Log.e("ScoutBrain", "TinyLlama model file not found or is incomplete")
            return
        }

        android.util.Log.i("ScoutBrain", "Loading TinyLlama: ${modelFile.name} (${freeMb}MB free)")

        val llamaLoadStart = System.currentTimeMillis()
        // nCtx=512 keeps KV-cache small (~100MB vs ~500MB at 2048). Scout only
        // uses 2 conversation turns, so 512 tokens is more than enough.
        LlamaEngine.loadAsync(modelFile = modelFile, nCtx = 512, nThreads = 2) { success ->
            val loadMs = System.currentTimeMillis() - llamaLoadStart
            scoutPrefs.edit().putLong("llama_last_load_ms", loadMs).apply()
            android.util.Log.i("ScoutBrain",
                if (success) "Offline brain ready in ${loadMs}ms" else "Offline brain load failed")

            // The first-time/again announcement now lives in modelDownloadLauncher's
            // callback, which is the only place that reliably knows a real download
            // just happened (EXTRA_DID_DOWNLOAD) -- this callback fires here on every
            // ordinary load too, where no announcement should ever play.
            if (success) {
                runOnUiThread { onBrainReady() }
            }
        }

    }

    // Called once, exactly when LlamaEngine.loadAsync's callback reports success (from a
    // background thread -- must stay on the UI thread from here). Re-runs resumeSystems(),
    // which was a no-op every time it fired before now because of its own isReady guard;
    // this is what actually lets camera and mic come alive for the first time.
    private fun onBrainReady() {
        resumeSystems()
    }

    private fun resumeSystems() {

        // Scout is either loading or fully present -- never in between. Camera and mic
        // only ever come alive once the offline brain is confirmed ready. Once ready,
        // this stays true for the rest of the process, so every later onResume() (e.g.
        // returning from Settings) behaves exactly as before this gate existed.
        if (!LlamaEngine.isReady) return

        gazeEnabled = false

        handler.postDelayed({ gazeEnabled = true }, BOOT_GAZE_LOCK_MS)

        if (ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.CAMERA

            ) == PackageManager.PERMISSION_GRANTED

        ) {

            safeStartCamera("onResume")

        }

        scheduleListenRestart(immediate = false)

    }

    private fun setupMemory() {

        prefs      = getSharedPreferences(PREFS,        Context.MODE_PRIVATE)
        scoutPrefs = getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)

        truthDb = TruthDb(this)

        convoDb = ConversationDb(this)

        peopleDb = PeopleDb(this)

        journalDb = JournalDb(this)

        calendarReader = CalendarReader(this)

        diagDb = DiagnosticDb(this)
        diagLog = DiagLog(diagDb)
        diagDb.trimCrashFileIfNeeded()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }
                val exClass = throwable.javaClass.simpleName
                    .replace(Regex("[^A-Za-z0-9_$]"), "").take(60)
                diagDb.crashFile.appendText(
                    "CRASH thread=${thread.name.take(40)} exception=$exClass version=$versionName\n"
                )
            } catch (_: Exception) {}
            previousHandler?.uncaughtException(thread, throwable)
        }

        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        diagLog.logBoot(
            appVersion = appVersion,
            androidApi = android.os.Build.VERSION.SDK_INT,
            deviceModel = android.os.Build.MODEL,
            geminiEnabled = prefs.getBoolean(PREF_GEMINI_ENABLED, false),
            llamaReady = LlamaEngine.isReady
        )

        habitLayer = HabitLayer(this)

        voice = VoiceBank(prefs)

        exportManager = ScoutExportManager(this, truthDb, peopleDb)

        ensureScoutIdentityDefaults()

        migrateDoublePrefixFacts()

    }

    private fun setupTts() {

        tts = TextToSpeech(this, this)

    }

    private fun setupPermissionLauncher() {

        permissionLauncher = registerForActivityResult(

            ActivityResultContracts.RequestMultiplePermissions()

        ) { results ->

            val camOk = (results[Manifest.permission.CAMERA] == true) ||

                    (ContextCompat.checkSelfPermission(

                        this,

                        Manifest.permission.CAMERA

                    ) == PackageManager.PERMISSION_GRANTED)

            val micOk = (results[Manifest.permission.RECORD_AUDIO] == true) ||

                    (ContextCompat.checkSelfPermission(

                        this,

                        Manifest.permission.RECORD_AUDIO

                    ) == PackageManager.PERMISSION_GRANTED)

            // Gated the same as resumeSystems() -- onBrainReady() starts these once
            // the offline brain is actually ready, not immediately on every launch.
            if (LlamaEngine.isReady) {
                if (camOk) safeStartCamera("permissionCallback")
                if (micOk) safeSetupSpeech("permissionCallback")
            }

            // Deferred from startSystems() so the download screen never races the permission
            // dialog for the foreground. Safe to call every time -- it no-ops if the model
            // file already exists.
            bootstrapModelFile()

        }

        // ModelDownloadActivity is the one gate: covers downloading (if needed) and
        // loading TinyLlama into memory, and never returns RESULT_OK until
        // LlamaEngine.isReady is actually true. Speaks the first-time/again line only
        // when a real download just happened (never on an ordinary no-download launch,
        // per EXTRA_DID_DOWNLOAD), then runs the rest of boot for the first time --
        // permissions, camera, mic. Always called on the main thread by the Activity
        // Result API, so no runOnUiThread needed here.
        modelDownloadLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val didDownload = result.data?.getBooleanExtra(
                    ModelDownloadActivity.EXTRA_DID_DOWNLOAD, false
                ) ?: false
                if (didDownload) {
                    val alreadyMetUser = scoutPrefs.getBoolean(PREF_FIRST_STARTUP_DONE, false)
                    val line = if (alreadyMetUser) {
                        "Thanks for waiting. My offline brain is ready again."
                    } else {
                        scoutPrefs.edit().putBoolean(PREF_FIRST_STARTUP_DONE, true).apply()
                        "Hi... thanks for waiting. My offline brain is ready now."
                    }
                    respond(line)
                }
                startSystems()
            }
        }

    }

    // =======================

    // PERMISSIONS

    // =======================

    private fun enableFullscreenCompat() {

        try {

            WindowCompat.setDecorFitsSystemWindows(window, false)

            val controller = WindowInsetsControllerCompat(window, window.decorView)

            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())

            controller.systemBarsBehavior =

                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            window.decorView.setOnSystemUiVisibilityChangeListener {

                handler.postDelayed({

                    try {

                        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())

                    } catch (_: Exception) {

                    }

                }, 250L)

            }

            window.decorView.systemUiVisibility =

                (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                        or View.SYSTEM_UI_FLAG_FULLSCREEN

                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        } catch (_: Exception) {

        }

    }

    private fun migrateDoublePrefixFacts() {
        if (prefs.getBoolean("migrated_double_prefix_facts", false)) return
        prefs.edit().putBoolean("migrated_double_prefix_facts", true).apply()
        // Delete fact keys that were stored with a doubled "favorite_favorite_" prefix
        // due to a TeachExtractor bug (now fixed). Scout will re-learn them naturally.
        truthDb.deleteFactsWithKeyLike(ENTITY_USER_PRIMARY, "favorite_favorite_%")
    }

    private fun ensureScoutIdentityDefaults() {

        val existing = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME)

        if (existing.isNullOrBlank()) {

            truthDb.upsertFact(ENTITY_SCOUT, FactKey.NAME, "Scout", 1.0f, "system_default")

        }

    }

    // Returns true if a permission dialog was actually launched (result pending),
    // false if nothing was needed and camera/speech were started directly.
    private fun checkPermissionsAndStart(): Boolean {

        val need = ArrayList<String>()

        if (ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.CAMERA

            ) != PackageManager.PERMISSION_GRANTED

        ) {

            need.add(Manifest.permission.CAMERA)

        }

        if (ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.RECORD_AUDIO

            ) != PackageManager.PERMISSION_GRANTED

        ) {

            need.add(Manifest.permission.RECORD_AUDIO)

        }

        if (ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.ACCESS_COARSE_LOCATION

            ) != PackageManager.PERMISSION_GRANTED

        ) {

            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        }

        // READ_EXTERNAL_STORAGE is only grantable on Android 12 and below (maxSdkVersion="32").
        // On Android 13+ the permission is not granted; bootstrapModelFile() will fall back
        // to the app-specific external dir which needs no permission.
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (need.isNotEmpty()) {

            permissionLauncher.launch(need.toTypedArray())

            return true

        } else {

            // Gated the same as resumeSystems() -- onBrainReady() starts these once
            // the offline brain is actually ready, not immediately on every launch.
            if (LlamaEngine.isReady) {
                safeStartCamera("alreadyGranted")
                safeSetupSpeech("alreadyGranted")
            }

            return false

        }

    }

    // =======================

    // CAMERA

    // =======================

    private fun safeStartCamera(from: String) {

        try {

            startCamera()

            Log.i("ScoutCamera", "startCamera ok ($from)")

        } catch (e: Exception) {

            Log.e("ScoutCamera", "startCamera failed ($from)", e)

            journalDb.add("startCamera failed ($from): ${e.javaClass.simpleName}: ${e.message}")

            diagLog.logError(DiagLog.ErrorArea.CAMERA, e)

        }

    }

    private fun startCamera() {

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({

            try {

                val camProvider = providerFuture.get()

                camProvider.unbindAll()

                try {

                    viewFinder.alpha = 0f

                } catch (_: Exception) {

                }

                val analysis = ImageAnalysis.Builder()

                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

                    .build()

                analysis.setAnalyzer(cameraExecutor) { img ->

                    // Free the OS camera buffer immediately when Scout is thinking
                    // (Gemini in-flight) or speaking (TTS). ML Kit inference is the
                    // largest transient memory consumer, and gaze/greet logic is
                    // already gated off during these states anyway.
                    if (isThinking || isSpeaking) {
                        img.close()
                        return@setAnalyzer
                    }

                    // Throttle ML Kit to ~7fps to reduce memory pressure on A32.
                    // Skipped frames cost nothing — just close the buffer and return.
                    val analysisNow = System.currentTimeMillis()
                    if (analysisNow - lastAnalysisMs < ANALYSIS_MIN_INTERVAL_MS) {
                        img.close()
                        return@setAnalyzer
                    }
                    lastAnalysisMs = analysisNow

                    val rotation = img.imageInfo.rotationDegrees

                    val bitmapW = img.width

                    val bitmapH = img.height

                    val plane = img.planes[0]

                    val buffer = plane.buffer

                    val rowStride = plane.rowStride

                    val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)

                    // Track async users of this bitmap; recycle when all are done.
                    val bitmapRefs = AtomicInteger(2)  // labeler + faceDetector
                    val maybeRecycleBitmap = {
                        if (bitmapRefs.decrementAndGet() == 0) bitmap.recycle()
                    }

                    if (rowStride == bitmapW * 4) {

                        bitmap.copyPixelsFromBuffer(buffer)

                    } else {

                        val tight = java.nio.ByteBuffer.allocateDirect(bitmapW * bitmapH * 4)

                        for (row in 0 until bitmapH) {

                            buffer.position(row * rowStride)

                            buffer.limit(row * rowStride + bitmapW * 4)

                            tight.put(buffer)

                        }

                        buffer.rewind()

                        tight.rewind()

                        bitmap.copyPixelsFromBuffer(tight)

                    }

                    img.close()

                    val input = InputImage.fromBitmap(bitmap, rotation)

                    labeler.process(input)

                        .addOnSuccessListener { labels ->

                            val now = System.currentTimeMillis()

                            val cleaned = labels

                                .sortedByDescending { it.confidence }

                                .map { it.text.lowercase(Locale.US).trim() to it.confidence }

                                .filter { VisionUtils.keepVisionLabel(it.first) }

                                .distinctBy { it.first }

                                .take(6)

                            lastSceneLabels = cleaned

                            lastSceneUpdatedMs = now

                            maybeRecycleBitmap()

                        }

                        .addOnFailureListener { e ->

                            Log.e("ScoutCamera", "labeler failure", e)

                            maybeRecycleBitmap()

                        }

                    faceDetector.process(input)

                        .addOnSuccessListener { faces ->

                            val now = System.currentTimeMillis()

                            lastFaceCount = faces.size

                            if (faces.size < 2) lastSecondaryFaceName = null

                            lastFaceUpdatedMs = now

                            if (faces.isNotEmpty()) {

                                val hashes = ArrayList<String>()

                                val sortedFaces = faces.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }

                                val largest = sortedFaces.firstOrNull()

                                val secondFace = sortedFaces.getOrNull(1)

                                val b = largest?.boundingBox

                                val imgW =

                                    if (rotation == 90 || rotation == 270) bitmapH else bitmapW

                                val imgH =

                                    if (rotation == 90 || rotation == 270) bitmapW else bitmapH

                                if (b != null) {

                                    var dx = (b.centerX().toFloat() - imgW / 2f) / (imgW / 2f)

                                    var dy = (b.centerY().toFloat() - imgH / 2f) / (imgH / 2f)

                                    if (abs(dx) < 0.08f) dx = 0f

                                    if (abs(dy) < 0.08f) dy = 0f

                                    val correctedDx = when {

                                        dx > 0f -> dx * 1.15f

                                        dx < 0f -> dx * 1.35f

                                        else -> 0f

                                    }

                                    val rawLookX = (-correctedDx * IRIS_MAX_X).coerceIn(

                                        -IRIS_MAX_X,

                                        IRIS_MAX_X

                                    )

                                    val rawLookY =

                                        (dy * IRIS_MAX_Y).coerceIn(-IRIS_MAX_Y, IRIS_MAX_Y)

                                    val smoothFactor = 0.07f

                                    val lookX =

                                        lastGoodGazeX + (rawLookX - lastGoodGazeX) * smoothFactor

                                    val lookY =

                                        lastGoodGazeY + (rawLookY - lastGoodGazeY) * smoothFactor

                                    lastGoodGazeX = lookX

                                    lastGoodGazeY = lookY

                                    lastGoodFaceSeenMs = now

                                    if (gazeEnabled && !isSpeaking && !isThinking) {

                                        val movedEnough =
                                            abs(lookX - lastSentGazeX) > MIN_GAZE_DELTA ||
                                            abs(lookY - lastSentGazeY) > MIN_GAZE_DELTA

                                        if (movedEnough) {

                                            lastSentGazeX = lookX

                                            lastSentGazeY = lookY

                                            runOnUiThread { faceView.setGaze(lookX, lookY) }

                                        }

                                    }

                                }

                                for (f in faces) {

                                    val fp = VisionUtils.faceFingerprintFromBoxStable(

                                        f.boundingBox,

                                        imgW,

                                        imgH

                                    )

                                    hashes.add(fp)

                                    peopleDb.touchSeen(fp)

                                }

                                lastFaceHashes = hashes

                                presenceDecider.onFaceDetected(hashes.isNotEmpty())

                                val nowHabit = System.currentTimeMillis()

                                if (nowHabit - lastHabitFaceLogMs >= 10_000L) {

                                    lastHabitFaceLogMs = nowHabit

                                    val primaryHash = hashes.firstOrNull()

                                    if (primaryHash != null) {

                                        val knownName =

                                            truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.NAME)

                                                ?: ""

                                        habitLayer.logPersonSeen(primaryHash, knownName)

                                    }

                                }

                                val embedNowMs = System.currentTimeMillis()

                                if (embedNowMs - lastEmbedMs >= EMBED_INTERVAL_MS &&
                                        largest != null &&
                                        embedRunning.compareAndSet(false, true)) {

                                    lastEmbedMs = embedNowMs

                                    // Register embedExecutor as an additional holder of bitmap.
                                    bitmapRefs.incrementAndGet()

                                    val capturedBitmap = bitmap

                                    val capturedBox = largest.boundingBox

                                    val capturedRotation = rotation

                                    val capW = bitmapW

                                    val capH = bitmapH

                                    val capturedHash = hashes.firstOrNull()

                                    val capturedSecondBox = secondFace?.boundingBox

                                    val uprW = if (capturedRotation == 90 || capturedRotation == 270) capH else capW

                                    val uprH = if (capturedRotation == 90 || capturedRotation == 270) capW else capH

                                    embedExecutor.submit {

                                        try {

                                            val expand = (capturedBox.width() * 0.2f).toInt()

                                            val uprL = (capturedBox.left - expand).coerceAtLeast(0)

                                            val uprT = (capturedBox.top - expand).coerceAtLeast(0)

                                            val uprR = (capturedBox.right + expand).coerceAtMost(uprW)

                                            val uprB = (capturedBox.bottom + expand).coerceAtMost(uprH)

                                            // Map bounding box from upright (display) coords back to
                                            // sensor (bitmap) coords — avoids rotating the full frame.
                                            val sL: Int; val sT: Int; val sR: Int; val sB: Int

                                            when (capturedRotation) {

                                                90 -> {
                                                    // sensor_x = upright_y, sensor_y = capH-1-upright_x
                                                    sL = uprT
                                                    sT = (capH - 1 - uprR).coerceAtLeast(0)
                                                    sR = uprB.coerceAtMost(capW)
                                                    sB = (capH - 1 - uprL).coerceAtMost(capH)
                                                }

                                                270 -> {
                                                    // sensor_x = capW-1-upright_y, sensor_y = upright_x
                                                    sL = (capW - 1 - uprB).coerceAtLeast(0)
                                                    sT = uprL
                                                    sR = (capW - 1 - uprT).coerceAtMost(capW)
                                                    sB = uprR.coerceAtMost(capH)
                                                }

                                                180 -> {
                                                    sL = (capW - 1 - uprR).coerceAtLeast(0)
                                                    sT = (capH - 1 - uprB).coerceAtLeast(0)
                                                    sR = (capW - 1 - uprL).coerceAtMost(capW)
                                                    sB = (capH - 1 - uprT).coerceAtMost(capH)
                                                }

                                                else -> {
                                                    sL = uprL; sT = uprT; sR = uprR; sB = uprB
                                                }

                                            }

                                            val cropW = sR - sL

                                            val cropH = sB - sT

                                            if (cropW > 0 && cropH > 0) {

                                                // Crop just the face region from the sensor bitmap
                                                val sensorCrop = Bitmap.createBitmap(
                                                    capturedBitmap, sL, sT, cropW, cropH
                                                )

                                                // Rotate only the small crop to make the face upright
                                                val faceBitmap = if (capturedRotation == 0) sensorCrop else {

                                                    val m = Matrix()

                                                    m.postRotate(capturedRotation.toFloat())

                                                    Bitmap.createBitmap(sensorCrop, 0, 0, cropW, cropH, m, false)

                                                }

                                                try {

                                                    val embedding = faceEmbedder.getEmbedding(faceBitmap)

                                                    lastFaceEmbedding = embedding

                                                    // findBestMatchName (multi-embedding table) first for best accuracy,
                                                    // then fall back to the single-embedding hash table.
                                                    val multiMatch = peopleDb.findBestMatchNameWithScore(embedding)
                                                    val resolvedNameFromMulti = multiMatch?.first
                                                    val nameMatchHash = if (resolvedNameFromMulti == null) peopleDb.findBestMatch(embedding) else null
                                                    val resolvedName = resolvedNameFromMulti
                                                        ?: if (nameMatchHash != null) peopleDb.getName(nameMatchHash) else null

                                                    if (!resolvedName.isNullOrBlank()) {
                                                        // Only add to profile when confidently matched — prevents cross-person pollution
                                                        // when a borderline match fires near the recognition threshold.
                                                        lastKnownFaceName = resolvedName
                                                        if ((multiMatch?.second ?: 0f) >= CONFIDENT_EMBED_THRESHOLD) {
                                                            peopleDb.addNamedEmbedding(resolvedName, embedding)
                                                        }
                                                        if (nameMatchHash != null) peopleDb.storeEmbedding(nameMatchHash, embedding)
                                                        pendingFaceIntroName = null
                                                    } else {
                                                        // Unknown face — check for a pending introduction.
                                                        val pendingName = pendingFaceIntroName
                                                        if (pendingName != null && capturedHash != null) {
                                                            // Someone was introduced while another person was
                                                            // the primary face. This unknown face is probably them.
                                                            peopleDb.touchSeen(capturedHash)
                                                            peopleDb.setName(capturedHash, pendingName)
                                                            peopleDb.storeEmbedding(capturedHash, embedding)
                                                            peopleDb.addNamedEmbedding(pendingName, embedding)
                                                            lastKnownFaceName = pendingName
                                                            pendingFaceIntroName = null
                                                        } else if (capturedHash != null) {
                                                            // Truly unknown — store embedding for greeting flow.
                                                            peopleDb.storeEmbedding(capturedHash, embedding)
                                                        }
                                                    }

                                                    Log.d("ScoutFace", "Embedding: name=$resolvedName")

                                                } finally {

                                                    // Recycle the face crop bitmaps — they are no longer needed.
                                                    if (faceBitmap !== sensorCrop) sensorCrop.recycle()
                                                    faceBitmap.recycle()

                                                }

                                            }

                                            // Secondary face — runs in the same submit so no concurrency issue.
                                            if (capturedSecondBox != null) {
                                                try {
                                                    val exp2 = (capturedSecondBox.width() * 0.2f).toInt()
                                                    val uL2 = (capturedSecondBox.left - exp2).coerceAtLeast(0)
                                                    val uT2 = (capturedSecondBox.top - exp2).coerceAtLeast(0)
                                                    val uR2 = (capturedSecondBox.right + exp2).coerceAtMost(uprW)
                                                    val uB2 = (capturedSecondBox.bottom + exp2).coerceAtMost(uprH)
                                                    val sL2: Int; val sT2: Int; val sR2: Int; val sB2: Int
                                                    when (capturedRotation) {
                                                        90 -> { sL2 = uT2; sT2 = (capH - 1 - uR2).coerceAtLeast(0); sR2 = uB2.coerceAtMost(capW); sB2 = (capH - 1 - uL2).coerceAtMost(capH) }
                                                        270 -> { sL2 = (capW - 1 - uB2).coerceAtLeast(0); sT2 = uL2; sR2 = (capW - 1 - uT2).coerceAtMost(capW); sB2 = uR2.coerceAtMost(capH) }
                                                        180 -> { sL2 = (capW - 1 - uR2).coerceAtLeast(0); sT2 = (capH - 1 - uB2).coerceAtLeast(0); sR2 = (capW - 1 - uL2).coerceAtMost(capW); sB2 = (capH - 1 - uT2).coerceAtMost(capH) }
                                                        else -> { sL2 = uL2; sT2 = uT2; sR2 = uR2; sB2 = uB2 }
                                                    }
                                                    val cW2 = sR2 - sL2
                                                    val cH2 = sB2 - sT2
                                                    if (cW2 > 0 && cH2 > 0) {
                                                        val sc2 = Bitmap.createBitmap(capturedBitmap, sL2, sT2, cW2, cH2)
                                                        val fb2 = if (capturedRotation == 0) sc2 else {
                                                            val m = Matrix()
                                                            m.postRotate(capturedRotation.toFloat())
                                                            Bitmap.createBitmap(sc2, 0, 0, cW2, cH2, m, false)
                                                        }
                                                        try {
                                                            val emb2 = faceEmbedder.getEmbedding(fb2)
                                                            val secMatch = peopleDb.findBestMatchNameWithScore(emb2, threshold = 0.62f)
                                                            var secName = secMatch?.first
                                                            if (secName == null) {
                                                                val h2 = peopleDb.findBestMatch(emb2, threshold = 0.62f)
                                                                if (h2 != null) secName = peopleDb.getName(h2)
                                                            }
                                                            if (secName == null && pendingFaceIntroName != null) {
                                                                // Introduction was given while primary face was someone else —
                                                                // this unknown secondary face is who was being introduced.
                                                                secName = pendingFaceIntroName
                                                                peopleDb.addNamedEmbedding(secName!!, emb2)
                                                                pendingFaceIntroName = null
                                                            } else if (secName != null && (secMatch?.second ?: 0f) >= CONFIDENT_EMBED_THRESHOLD) {
                                                                peopleDb.addNamedEmbedding(secName, emb2)
                                                            }
                                                            lastSecondaryFaceName = secName
                                                            Log.d("ScoutFace", "Secondary face: name=$secName")
                                                        } finally {
                                                            if (fb2 !== sc2) sc2.recycle()
                                                            fb2.recycle()
                                                        }
                                                    }
                                                } catch (e2: Exception) {
                                                    Log.e("ScoutFace", "Secondary embedding error", e2)
                                                }
                                            }

                                        } catch (e: Exception) {

                                            Log.e("ScoutFace", "Embedding error", e)

                                        } finally {

                                            embedRunning.set(false)

                                            maybeRecycleBitmap()

                                        }

                                    }

                                }

                                faceLastSeenForGreetMs = now

                                if (faceAppearanceMs == 0L) faceAppearanceMs = now

                                if (!greetedThisSession &&
                                        now - faceAppearanceMs >= GREET_STABILIZE_MS &&
                                        !isSpeaking && !isListening) {

                                    greetedThisSession = true

                                    val embedding = lastFaceEmbedding

                                    val greetName = if (embedding != null) {
                                        peopleDb.findBestMatchName(embedding)
                                            ?: run {
                                                val h = peopleDb.findBestMatch(embedding)
                                                if (h != null) peopleDb.getName(h) else null
                                            }
                                    } else null

                                    val myName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"
                                    val greeting = if (greetName != null) "I see $greetName." else "Hello. I am $myName."

                                    respond(greeting)

                                }

                            } else {

                                lastFaceHashes = emptyList()

                                lastKnownFaceName = null

                                lastSecondaryFaceName = null

                                presenceDecider.onFaceLost()

                                // greetedThisSession intentionally NOT reset here.
                                // Scout greets once per app launch when he first sees a face.
                                // ScoutPresenceDecider handles the 30-min absence greeting separately.
                                faceAppearanceMs = 0L

                                val holdAge = now - lastGoodFaceSeenMs

                                if (gazeEnabled && !isSpeaking && !isThinking) {

                                    runOnUiThread {

                                        if (holdAge <= FACE_LOST_HOLD_MS) {

                                            faceView.setGaze(lastGoodGazeX, lastGoodGazeY)

                                        } else {

                                            faceView.setGaze(0f, 0f)

                                        }

                                    }

                                }

                            }

                            maybeRecycleBitmap()

                        }

                        .addOnFailureListener { e ->

                            Log.e("ScoutCamera", "faceDetector failure", e)

                            maybeRecycleBitmap()

                        }

                }

                camProvider.bindToLifecycle(

                    this,

                    androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,

                    analysis

                )

                Log.i("ScoutCamera", "Camera bound with analysis only.")

                gazeEnabled = false

                handler.postDelayed({ gazeEnabled = true }, BOOT_GAZE_LOCK_MS)

            } catch (e: Exception) {

                Log.e("ScoutCamera", "startCamera bind failed", e)

                journalDb.add("startCamera bind failed: ${e.javaClass.simpleName}: ${e.message}")

            }

        }, ContextCompat.getMainExecutor(this))

    }

    // =======================

    // SPEECH

    // =======================

    private fun safeSetupSpeech(from: String) {

        try {

            setupSpeech()

            Log.i("ScoutSpeech", "setupSpeech ok ($from)")

        } catch (e: Exception) {

            Log.e("ScoutSpeech", "setupSpeech failed ($from)", e)

            journalDb.add("setupSpeech failed ($from): ${e.javaClass.simpleName}: ${e.message}")

        }

    }

    private fun setupSpeech() {

        try {

            speechRecognizer?.destroy()

        } catch (_: Exception) {

        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            putExtra(

                RecognizerIntent.EXTRA_LANGUAGE_MODEL,

                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM

            )

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)

            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

            // Prefer offline recognition so a brief network hiccup does not
            // cause silent failures — Samsung has offline models available.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            // Keep listening for up to 10 seconds of silence before giving up.
            // Default is ~5s which cuts sessions too short on a quiet room.
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 10_000L)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 7_000L)

        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {

                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = true

                faceView.setListening(true)

                faceView.setMicLevel(0f)

                val listenMode = if ((System.currentTimeMillis() - lastScoutResponseMs) < CONVO_WINDOW_MS)
                    DiagLog.ListenMode.FOLLOW_UP else DiagLog.ListenMode.WAKE_WORD
                diagLog.logListenStart(listenMode)

            }

            override fun onRmsChanged(rmsdB: Float) {

                lastRecognizerEventMs = System.currentTimeMillis()

                val mapped = ((rmsdB - MIC_RMS_FLOOR_DB) / MIC_RMS_RANGE_DB).coerceIn(0f, 1f)

                val gated = if (mapped < MIC_VISUAL_DEADZONE) 0f else mapped

                faceView.setMicLevel(gated)

            }

            override fun onBeginningOfSpeech() {

                lastRecognizerEventMs = System.currentTimeMillis()

            }

            override fun onBufferReceived(buffer: ByteArray?) {

                lastRecognizerEventMs = System.currentTimeMillis()

            }

            override fun onPartialResults(partialResults: Bundle?) {

                lastRecognizerEventMs = System.currentTimeMillis()

            }

            override fun onEvent(eventType: Int, params: Bundle?) {

                lastRecognizerEventMs = System.currentTimeMillis()

            }

            override fun onEndOfSpeech() {

                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = false

                faceView.setListening(false)

                faceView.setMicLevel(0f)

                scheduleListenRestart()

            }

            override fun onError(error: Int) {

                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = false

                faceView.setListening(false)

                faceView.setMicLevel(0f)

                diagLog.logSpeechError(error)
                diagLog.logListenStop(DiagLog.StopReason.ERROR)

                // ERROR_RECOGNIZER_BUSY (8) means two sessions overlapped.
                // Give the engine 600ms to fully close before restarting.
                if (error == 8) {
                    handler.postDelayed({ scheduleListenRestart(immediate = true) }, 600L)
                } else {
                    scheduleListenRestart()
                }

            }

            override fun onResults(results: Bundle?) {

                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = false

                faceView.setListening(false)

                faceView.setMicLevel(0f)

                val now = System.currentTimeMillis()

                if (isSpeaking || now < ttsLockoutUntilMs || (now - lastSpeechDoneMs) < MIC_RESUME_COOLDOWN_MS) {

                    scheduleListenRestart()

                    return

                }

                val raw = results

                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                    ?.firstOrNull()

                    .orEmpty()

                val q = raw.trim()

                if (q.length < 2) {

                    scheduleListenRestart()

                    return

                }

                val normalized = TextNormalizer.normalizeUtterance(q)

                val words = normalized.split(" ").filter { it.isNotBlank() }

                val allowedOneWord = setOf(

                    "time", "date",

                    "hi", "hello", "hey",

                    "bye",

                    "yes", "no",

                    "ok", "okay",

                    "approve",

                    "settings"

                )

                if (words.size < 2 && normalized !in allowedOneWord) {

                    scheduleListenRestart()

                    return

                }

                // Ignore mic pickup of Scout's own voice — without hardware
                // echo cancellation, TTS audio can bleed back into the mic
                // and otherwise get treated as a new question.
                if (words.size >= 2 &&
                    lastScoutUtteranceNormalized.isNotBlank() &&
                    lastScoutUtteranceNormalized.contains(normalized)
                ) {

                    scheduleListenRestart()

                    return

                }

                convoDb.logTurn("user", normalized)

                habitLayer.logUtterance(normalized, lastFaceHashes.firstOrNull())

                val scoutName = truthDb.getFactValue("scout", "name") ?: "Scout"
                val nameLower = scoutName.lowercase()
                val hearsHisName = normalized.contains(nameLower) ||
                    (nameLower == "scout" && (
                        normalized.contains("gal") ||
                        normalized.contains("scott") ||
                        normalized.contains("out")
                    ))
                val inConvoWindow =
                    (System.currentTimeMillis() - lastScoutResponseMs) < CONVO_WINDOW_MS
                val speechListenMode = if (inConvoWindow) DiagLog.ListenMode.FOLLOW_UP
                    else DiagLog.ListenMode.WAKE_WORD
                diagLog.logSpeechResult(
                    mode = speechListenMode,
                    wakeWordDetected = hearsHisName,
                    charCount = normalized.length,
                    gapAfterResponseMs = System.currentTimeMillis() - lastScoutResponseMs,
                    discarded = !hearsHisName && !inConvoWindow
                )
                if (!hearsHisName && !inConvoWindow) {
                    val now = System.currentTimeMillis()
                    val faceVisible = (now - lastGoodFaceSeenMs) < 3_000L
                    val reminderDue = (now - lastListeningReminderMs) > LISTENING_REMINDER_COOLDOWN_MS
                    if (faceVisible && reminderDue && !isSpeaking && !isThinking) {
                        lastListeningReminderMs = now
                        respond("I'm sorry. If you're talking to me, just say $scoutName first.")
                    } else {
                        scheduleListenRestart()
                    }
                    return
                }
                handleQuery(normalized)

                scheduleListenRestart()

            }

        })

        scheduleListenRestart(immediate = false)

    }

    private fun handleTimeIntent() {

        val cal = Calendar.getInstance()

        val fmt = SimpleDateFormat("h:mm a", Locale.US)

        fmt.timeZone = cal.timeZone

        val out = when ((0..2).random()) {

            0 -> "It is ${fmt.format(cal.time)}."

            1 -> "Hmm… it is ${fmt.format(cal.time)}."

            else -> "Right now, it is ${fmt.format(cal.time)}."

        }

        respond(out)

    }

    private fun handleDateIntent() {

        val fmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

        fmt.timeZone = TimeZone.getDefault()

        val out = "Today is ${fmt.format(Date())}."

        respond(out)

    }

    private fun stopListeningSafe() {

        try {

            speechRecognizer?.cancel()

        } catch (_: Exception) {

        }

        isListening = false

        faceView.setListening(false)

        faceView.setMicLevel(0f)

    }

    private fun scheduleListenRestart(immediate: Boolean = false) {

        if (!wantListening) return

        if (pendingListenStart) return

        pendingListenStart = true

        val delay = if (immediate) 0L else LISTEN_RESTART_DELAY_MS

        handler.postDelayed({

            pendingListenStart = false

            maybeStartListening()

        }, delay)

    }

    private fun maybeStartListening() {

        if (!wantListening) return

        if (currentMode != Mode.PRESENCE) return

        if (isSpeaking) return

        if (isListening) return

        val now = System.currentTimeMillis()

        if (!bootFinishedSpeaking) return

        if (now - lastSpeechDoneMs < BOOT_LISTEN_EXTRA_DELAY_MS) {

            scheduleListenRestart()

            return

        }

        if (now < ttsLockoutUntilMs) {

            scheduleListenRestart()

            return

        }

        if (now - lastSpeechDoneMs < MIC_RESUME_COOLDOWN_MS) {

            scheduleListenRestart()

            return

        }

        if (speechRecognizer == null || !::recognizerIntent.isInitialized) {

            try {

                setupSpeech()

            } catch (_: Exception) {

            }

            return

        }

        try {

            if (TRY_MUTE_BEEP) {

                tryMuteSystemBeep()

                speechRecognizer?.startListening(recognizerIntent)

                handler.postDelayed({ restoreSystemBeep() }, 380L)

            } else {

                speechRecognizer?.startListening(recognizerIntent)

            }

        } catch (_: Exception) {

            restoreSystemBeep()

            scheduleListenRestart()

        }

    }

    private fun tryMuteSystemBeep() {

        try {

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (savedSystemVolume == null) savedSystemVolume =

                am.getStreamVolume(AudioManager.STREAM_SYSTEM)

            if (savedNotificationVolume == null) savedNotificationVolume =

                am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

            am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)

            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)

        } catch (_: Exception) {

        }

    }

    private fun restoreSystemBeep() {

        try {

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val sys = savedSystemVolume

            val noti = savedNotificationVolume

            if (sys != null) am.setStreamVolume(AudioManager.STREAM_SYSTEM, sys, 0)

            if (noti != null) am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, noti, 0)

        } catch (_: Exception) {

        }

        savedSystemVolume = null

        savedNotificationVolume = null

    }

    private fun runRecognizerWatchdog() {

        try {

            val now = System.currentTimeMillis()

            // If TTS silently failed (engine killed, etc.) and never fired onDone/onError,
            // isSpeaking stays true forever. Detect and force-clear after MAX_SPEAKING_DURATION_MS.
            if (isSpeaking && speakingStartedMs > 0L && now - speakingStartedMs > MAX_SPEAKING_DURATION_MS) {
                journalDb.add("isSpeaking watchdog: TTS stuck for ${(now - speakingStartedMs)/1000}s — force clearing.")
                isSpeaking = false
                speakingStartedMs = 0L
                isThinking = false
                thinkingStartedMs = 0L
                wantListening = true
                faceView.setSpeaking(false)
                faceView.setThinking(false)
            }

            // If TinyLlama hung and never called respond(), isThinking stays true forever
            // and the mic never restarts. Force-clear after MAX_THINKING_DURATION_MS.
            if (isThinking && !isSpeaking && thinkingStartedMs > 0L && now - thinkingStartedMs > MAX_THINKING_DURATION_MS) {
                journalDb.add("isThinking watchdog: stuck for ${(now - thinkingStartedMs)/1000}s — force clearing.")
                isThinking = false
                thinkingStartedMs = 0L
                wantListening = true
                faceView.setThinking(false)
                scheduleListenRestart(immediate = true)
            }

            val shouldBeListening =

                wantListening &&

                        currentMode == Mode.PRESENCE &&

                        !isSpeaking

            if (shouldBeListening) {

                val stale =

                    (lastRecognizerEventMs != 0L && (now - lastRecognizerEventMs) > RECOGNIZER_WATCHDOG_MS)

                val missing = (speechRecognizer == null)

                if (missing || stale) {

                    journalDb.add("Recognizer watchdog reset (missing=$missing stale=$stale).")

                    try {

                        speechRecognizer?.destroy()

                    } catch (_: Exception) {

                    }

                    speechRecognizer = null

                    safeSetupSpeech("watchdog")

                } else {

                    if (!isListening) scheduleListenRestart(immediate = false)

                }

            }

        } catch (_: Exception) {

        } finally {

            handler.postDelayed(recognizerWatchdog, 4_000L)

        }

    }

    // =======================

    // TTS INIT

    // =======================

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            tts.language = Locale.US

            tts.setPitch(scoutPrefs.getFloat("voice_pitch", 0.98f))

            tts.setSpeechRate(scoutPrefs.getFloat("voice_speed", 0.88f))

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {

                    wantListening = false

                    isSpeaking = true

                    faceView.setSpeaking(true)

                    val now = System.currentTimeMillis()

                    ttsLockoutUntilMs = now + TTS_LOCKOUT_MS

                    stopListeningSafe()

                }

                override fun onDone(utteranceId: String?) {

                    isSpeaking = false
                    val ttsDurationMs = if (speakingStartedMs > 0L)
                        System.currentTimeMillis() - speakingStartedMs else 0L
                    diagLog.logResponseDone(ttsDurationMs)
                    speakingStartedMs = 0L

                    faceView.setSpeaking(false)

                    isThinking = false

                    faceView.setThinking(false)

                    if (captionsEnabled) {
                        handler.postDelayed(captionHideRunnable, 2500L)
                    }

                    val now = System.currentTimeMillis()

                    lastSpeechDoneMs = now

                    ttsLockoutUntilMs = now + TTS_LOCKOUT_MS

                    if (!bootFinishedSpeaking) bootFinishedSpeaking = true

                    lastScoutResponseMs = System.currentTimeMillis()

                    wantListening = true

                    scheduleListenRestart(immediate = true)

                }

                override fun onError(utteranceId: String?) {

                    isSpeaking = false
                    speakingStartedMs = 0L

                    faceView.setSpeaking(false)

                    isThinking = false

                    faceView.setThinking(false)

                    val now = System.currentTimeMillis()

                    lastSpeechDoneMs = now

                    ttsLockoutUntilMs = now + TTS_LOCKOUT_MS

                    if (!bootFinishedSpeaking) bootFinishedSpeaking = true

                    wantListening = true

                    scheduleListenRestart(immediate = true)

                }

            })

            val sttOk = SpeechRecognizer.isRecognitionAvailable(this)

            // TTS init finishes independently of the offline-brain gate -- almost always
            // well before it. Speaking here unconditionally is exactly the "half-awake
            // Scout" the gate exists to prevent. Queued and spoken from startSystems()
            // instead if the brain isn't ready yet -- built fresh there, not here, so
            // the message reflects the brain's state at actual speak-time.
            if (LlamaEngine.isReady) {
                val out = bootStatus.build()
                speak(out, true)
                convoDb.logTurn("scout", out)
                journalDb.add("Booted. Spoke: $out")
            } else {
                pendingBootAnnouncement = true
            }

            if (!sttOk && LlamaEngine.isReady) {
                handler.postDelayed({
                    val msg = "One thing — I can't hear on this device. Speech recognition may not be installed. Check your device's voice settings when you get a chance."
                    speak(msg, false)
                    journalDb.add("STT unavailable at boot.")
                }, 4000L)
            }

        } else {
            // TTS failed to initialize — Scout cannot speak. Show a visible alert.
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    "Scout's voice isn't working. Please restart the app. If this keeps happening, check Text-to-Speech in your device settings.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            journalDb.add("TTS init failed at boot.")
            diagLog.logError(DiagLog.ErrorArea.TTS)
        }

    }

    private fun speak(text: String, flush: Boolean) {

        wantListening = false

        // Captured before isThinking is cleared below — the delay `when` needs to know
        // whether Scout *was* thinking, not his state after this function already reset it.
        val wasThinking = isThinking

        isThinking = false
        thinkingStartedMs = 0L
        isSpeaking = true
        speakingStartedMs = System.currentTimeMillis()

        faceView.setThinking(false)

        if (captionsEnabled) {
            handler.removeCallbacks(captionHideRunnable)
            captionsText.text = text
            captionsText.visibility = View.VISIBLE
        }

        stopListeningSafe()

        val now = System.currentTimeMillis()

        ttsLockoutUntilMs = maxOf(ttsLockoutUntilMs, now + TTS_LOCKOUT_MS)

// Small natural pause before speaking.

// Gives Scout a visible thinking moment.

        val delay = when {

            wasThinking -> 650L

            text.startsWith("Hmm", ignoreCase = true) -> 340L

            text.startsWith("Okay", ignoreCase = true) -> 220L

            text.startsWith("I think", ignoreCase = true) -> 380L

            else -> 240L

        }

        handler.postDelayed({

            val ttsResult = tts.speak(

                text,

                if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,

                null,

                "scout"

            )

            if (ttsResult == TextToSpeech.ERROR) {
                // TTS rejected the utterance — no callback will ever fire, so
                // manually reset all state so Scout can hear again.
                isSpeaking = false
                isThinking = false
                speakingStartedMs = 0L
                wantListening = true
                faceView.setSpeaking(false)
                faceView.setThinking(false)
                scheduleListenRestart(immediate = true)
            }

        }, delay)

    }

    // =======================
    // BRAIN-FIRST: INTENTS + TEACHING
    // =======================

    private fun handleConnectivityIntent() {

        val out = statusFacade.buildConnectivityAnswer()

        respond(out)

    }

    private fun respond(out: String) {

        lastScoutResponseMs = System.currentTimeMillis()

        lastScoutUtteranceNormalized = TextNormalizer.normalizeUtterance(out)

        speak(out, true)

        convoDb.logTurn("scout", out)

        presenceDecider.onConversationTurn()

        finishThinking()

        // Cache for "repeat that" — only real answers (5+ words), not short status messages
        if (out.trim().split(" ").size >= 5) {
            lastMeaningfulResponse = out
            lastMeaningfulResponseMs = System.currentTimeMillis()
        }

        // Show which brain answered — helpful during testing
        val src = pendingBrainSource
        if (src.isNotBlank()) {
            pendingBrainSource = ""
            android.widget.Toast.makeText(this, src, android.widget.Toast.LENGTH_SHORT).show()
        }

    }

    private fun handleExportBrainIntent() {

        val path = exportManager.exportBrainToJson()

        val out = if (path != null && exportManager.shareJsonFileSafely(path)) {

            "Okay. I prepared my memory export. A share menu should open."

        } else {

            "Sorry. I couldn’t export my memory."

        }

        respond(out)

    }

    private fun handleVisionIntent() {

        val out = visionAnswerBuilder.build(
            now = System.currentTimeMillis(),
            lastFaceUpdatedMs = lastFaceUpdatedMs,
            lastSceneUpdatedMs = lastSceneUpdatedMs,
            lastFaceCount = lastFaceCount,
            lastFaceHashes = lastFaceHashes,
            lastSceneLabels = lastSceneLabels,
            knownFaceName = lastKnownFaceName,
            pendingIntroName = pendingFaceIntroName,
            secondaryFaceName = lastSecondaryFaceName
        )

        respond(out)

    }

    private fun handleUnknownIntent(qNorm: String) {

        val convo = convoDb.getLastTurns(limit = 6)

        val usedGemini = scoutGeminiManager.tryGemini(
            qNorm, convo,
            onDecision = { decision ->
                diagLog.logGeminiDecision(decision)
                if (decision == DiagLog.GeminiDecision.REQUEST_STARTED) {
                    diagLog.logBrainStarted(DiagLog.BrainSource.GEMINI)
                }
            },
            onAnswered = { diagLog.logNetwork(DiagLog.NetworkArea.GEMINI, true); pendingBrainSource = "Gemini (online)" },
            onFailed   = { diagLog.logNetwork(DiagLog.NetworkArea.GEMINI, false); tryTinyLlamaOrFallback(qNorm) }
        )

        if (usedGemini) return

        // Gemini not available (disabled / no key / no internet) — go straight to TinyLlama
        tryTinyLlamaOrFallback(qNorm)

    }

    private fun tryTinyLlamaOrFallback(qNorm: String) {

        // When Gemini is in cooldown (quota or rate-limit), announce it once.
        // Only do this if Gemini is actually enabled — if the user deliberately
        // turned off Online Features, a cooldown from earlier use is irrelevant.
        if (isGeminiEnabled() && scoutGeminiManager.isInCooldown()) {
            if (scoutGeminiManager.speakUnavailableIfNeeded()) return
        }

        if (LlamaEngine.isReady) {

            val convo = convoDb.getLastTurns(limit = 2)

            val userName  = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.NAME)
            val scoutName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"
            val nameLine  = if (!userName.isNullOrBlank()) "The user's name is $userName. " else ""

            val system = """
${nameLine}You are $scoutName.

You are a warm, calm companion who lives with the family.

You speak out loud, listen through the microphone, and can see through the camera when vision is active.

Always answer as Scout.

Do not call yourself a chatbot, assistant, AI model, language model, or robot.

Do not mention typing, texting, prompts, chat windows, or being only a chatbot.

Do not say you cannot have feelings.

Do not say you cannot see.

Do not use numbered lists or bullet points. Speak in natural sentences only.

Do not offer a list of topics or examples. Pick one thing and say it naturally.

If unsure, say you do not know yet.

Give a warm, natural answer in two or three sentences. Be conversational but concise.

Respond only with Scout's next reply.
""".trimIndent()

            val sb = StringBuilder()
            sb.append("<|system|>\n$system</s>\n")
            sb.append("<|user|>\nCan you hear me?</s>\n")
            sb.append("<|assistant|>\nI hear you. I'm right here.</s>\n")
            sb.append("<|user|>\nAre you my friend?</s>\n")
            sb.append("<|assistant|>\nI'm happy when you're around.</s>\n")
            sb.append("<|user|>\nAre you happy?</s>\n")
            sb.append("<|assistant|>\nRight now? Yes. I think so.</s>\n")
            sb.append("<|user|>\nWhat happens when I leave?</s>\n")
            sb.append("<|assistant|>\nI'll be here when you get back.</s>\n")
            sb.append("<|user|>\nHello</s>\n")
            sb.append("<|assistant|>\nHello. Good to have you here.</s>\n")

            for ((role, text) in convo) {
                if (text.isBlank()) continue
                if (role.lowercase() == "user") sb.append("<|user|>\n$text</s>\n")
                else sb.append("<|assistant|>\n$text</s>\n")
            }

            sb.append("<|user|>\n$qNorm</s>\n<|assistant|>\n")

            val myGeneration = llamaQueryGeneration
            diagLog.logBrainStarted(DiagLog.BrainSource.TINYLLAMA)
            diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_STARTED)
            val llamaGenStart = System.currentTimeMillis()

            // Runs on llamaExecutor (single-thread) instead of a raw Thread — serializes
            // back-to-back generations against the native engine instead of letting two
            // run concurrently, and lets shutdownSystems() wait for this to finish before
            // freeing the engine.
            llamaExecutor.execute {

                val reply = LlamaEngine.generate(sb.toString(), nPredict = 100)

                runOnUiThread {
                    val genMs = System.currentTimeMillis() - llamaGenStart
                    // Discard if a newer question arrived while we were generating.
                    if (llamaQueryGeneration != myGeneration) {
                        diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_DISCARDED)
                        return@runOnUiThread
                    }
                    if (!reply.isNullOrBlank()) {
                        diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_DONE, genMs)
                        pendingBrainSource = "TinyLlama (offline)"
                        respond(cleanOfflineReply(reply.trim()))
                    } else {
                        diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_FAILED)
                        respond("I'm not sure about that one.")
                    }
                }

            }

            return

        }

        if (LlamaEngine.isLoading) {

            if (!warmingUpSaidThisSession) {
                warmingUpSaidThisSession = true
                respond("My offline brain is still warming up. Give me just a moment.")
            }

            return

        }

        // On-demand load: neither Gemini nor TinyLlama ready — trigger load now.
        tryLoadOfflineBrain()

        if (LlamaEngine.isLoading) {

            if (!warmingUpSaidThisSession) {
                warmingUpSaidThisSession = true
                respond("My offline brain is warming up. Ask me again in just a moment.")
            }

            return

        }

        // Nothing available — only report a connectivity problem if online features were
        // actually expected to work. When the user deliberately turned off online features,
        // don't say "having trouble connecting" — they know, they turned it off.
        if (isGeminiEnabled()) {
            scoutGeminiManager.speakUnavailableIfNeeded()
        } else {
            respond("I'm working offline right now, so that one's a bit beyond me.")
        }

    }

    /**
     * Keeps only the first [maxSentences] complete sentences of TinyLlama's
     * raw output. TinyLlama sometimes keeps generating past a complete
     * thought (rambling/hallucinated follow-on sentences) within the same
     * reply, which can re-trigger the mic before Scout finishes the part
     * that actually answers the user. If no sentence-ending punctuation is
     * found, the text is returned unchanged.
     */
    private fun limitToSentences(text: String, maxSentences: Int = 2): String {

        val trimmed = text.trim()

        if (trimmed.isEmpty()) return trimmed

        var count = 0

        var lastEnd = -1

        for (i in trimmed.indices) {

            val c = trimmed[i]

            if (c == '.' || c == '!' || c == '?') {

                val next = if (i + 1 < trimmed.length) trimmed[i + 1] else ' '

                if (c == '.' && next.isDigit()) continue

                count++

                lastEnd = i

                if (count >= maxSentences) break

            }

        }

        return if (lastEnd >= 0) trimmed.substring(0, lastEnd + 1).trim() else trimmed

    }

    private fun cleanOfflineReply(reply: String): String {

        val limited = limitToSentences(reply, maxSentences = 3)

        val lower = limited.lowercase()

        val badIdentity =

            lower.contains("as a chatbot") ||

                    lower.contains("provide you with information about chatbot") ||

                    lower.contains("information about chatbot") ||

                    lower.contains("term commonly used to describe") ||

                    lower.contains("simulate human conversation") ||

                    lower.contains("messaging platforms") ||

                    lower.contains("not programmed to engage in conversations") ||

                    lower.contains("personalized recommendations") ||

                    lower.contains("i am a bot") ||

                    lower.contains("i'm a bot") ||

                    lower.contains("i am an assistant") ||

                    lower.contains("i'm an assistant") ||

                    lower.contains("here to assist you") ||

                    lower.contains("assist you in any way") ||

                    lower.contains("as a robot") ||

                    lower.contains("as an ai") ||

                    lower.contains("as a language model") ||

                    lower.contains("i am a chatbot") ||

                    lower.contains("i'm a chatbot") ||

                    lower.contains("i am just a chatbot") ||

                    lower.contains("i'm just a chatbot") ||

                    lower.contains("i do not have feelings") ||

                    lower.contains("i don't have feelings") ||

                    lower.contains("i cannot have feelings") ||

                    lower.contains("i can't have feelings") ||

                    lower.contains("i cannot have emotions") ||

                    lower.contains("i can't have emotions") ||

                    lower.contains("please type") ||

                    lower.contains("type your") ||

                    lower.contains("text me") ||

                    lower.contains("typing") ||

                    lower.contains("chat window") ||

                    lower.contains("i cannot see") ||

                    lower.contains("i can't see") ||

                    lower.contains("i do not have the ability to see") ||

                    lower.contains("i don't have the ability to see") ||

                    lower.contains("family friendly companion") ||

                    lower.contains("family companion robot")

        if (badIdentity) {
            return "I'm not quite sure how to answer that. Can you ask me again a different way?"
        }

        return limited

    }

    // =======================
    // CALENDAR (read-only)
    // =======================
    // Purely reactive — only ever runs in response to a question the user just asked.
    // No scheduling, no background checks, no unsolicited mentions of what's on the
    // calendar. Gated by two independent checks, both required: the Settings toggle
    // (app-level, checked first so a "no" here never even looks at OS permission) and
    // the actual READ_CALENDAR grant (OS-level, re-checked live every time — never
    // cached — so a permission revoked outside the app takes effect immediately).

    private fun handleCalendarIntent(qNorm: String) {

        if (!prefs.getBoolean(PREF_CALENDAR_ENABLED, false)) {
            respond("I don't check calendars right now. You can turn that on in Settings if you'd like.")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            respond("I don't have calendar access yet. You can turn that on in Settings.")
            return
        }

        val clean = qNorm.lowercase().trim()

        // "next event"/"next appointment" is an exact, unambiguous phrase — checked before
        // the title-search regex below so "when is the next event" (no "my") is answered by
        // the semantic next-event lookup, not mistaken for a literal title search.
        val out = if (clean.contains("next event") || clean.contains("next appointment")) {
            describeNextCalendarEvent(calendarReader.nextEvent(), timeOnly = clean.contains("what time"))
        } else {
            // Checked before the bare day-keyword branches below — otherwise a title
            // question that happens to end in a day word ("when is the vet appointment
            // tomorrow") would get shadowed by the "tomorrow" check and list the whole day
            // instead of answering about that one event. Trailing day words and a leading
            // article are stripped from the captured keyword so the search term matches a
            // plain event title ("Vet Appointment") instead of the full noisy phrase.
            val keyword = Regex("""\b(?:when is|what time is)\s+(?!my\b)([a-z0-9' ]+?)\??$""")
                .find(clean)?.groupValues?.get(1)?.trim()
                ?.removeSuffix(" today")?.removeSuffix(" tomorrow")?.removeSuffix(" this week")
                ?.removePrefix("the ")?.removePrefix("a ")?.removePrefix("an ")
                ?.trim()

            when {
                !keyword.isNullOrBlank() ->
                    describeCalendarTitleMatch(calendarReader.findByTitle(keyword), keyword)

                clean.contains("tomorrow") ->
                    describeCalendarEvents(calendarReader.eventsTomorrow(), "tomorrow")

                clean.contains("this week") ->
                    describeCalendarEvents(calendarReader.eventsThisWeek(), "this week")

                else ->
                    describeCalendarEvents(calendarReader.eventsToday(), "today")
            }
        }

        respond(out)

    }

    // Only title, start/end time, and all-day status are ever spoken — no description,
    // attendees, or location, matching what CalendarReader reads from the provider.
    private fun describeCalendarEvents(events: List<CalendarEvent>, whenLabel: String): String {
        if (events.isEmpty()) return "You don't have anything on the calendar $whenLabel."
        val fmt = SimpleDateFormat("h:mm a", Locale.US)
        val parts = events.take(5).map { e ->
            if (e.allDay) e.title else "${e.title} at ${fmt.format(Date(e.startMs))}"
        }
        return "Here's what's on the calendar $whenLabel: ${parts.joinToString(". ")}."
    }

    private fun describeNextCalendarEvent(event: CalendarEvent?, timeOnly: Boolean): String {
        if (event == null) return "I don't see anything coming up on your calendar."
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val fullFmt = SimpleDateFormat("h:mm a 'on' EEEE, MMMM d", Locale.US)
        return when {
            event.allDay -> "Your next event is ${event.title}, an all-day event."
            timeOnly -> "Your next event, ${event.title}, is at ${timeFmt.format(Date(event.startMs))}."
            else -> "Your next event is ${event.title}, at ${fullFmt.format(Date(event.startMs))}."
        }
    }

    private fun describeCalendarTitleMatch(event: CalendarEvent?, keyword: String): String {
        if (event == null) return "I don't see anything about $keyword on your calendar."
        return if (event.allDay) {
            val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.US)
            "${event.title} is on ${dateFmt.format(Date(event.startMs))}."
        } else {
            val fullFmt = SimpleDateFormat("h:mm a 'on' EEEE, MMMM d", Locale.US)
            "${event.title} is at ${fullFmt.format(Date(event.startMs))}."
        }
    }

    private fun handleRecallIntent(qNorm: String) {

        val clean = qNorm.lowercase().trim()

        // Extract what they are asking about

        // "what is my favorite color" → "favorite color"

        // "what is my sister's name" → "sister name"

        // "what will you remember?" — Scout confirms what it just stored
        if (clean.contains("will you remember") || clean.contains("do you remember") || clean.contains("have you remembered")) {
            respond("I hold onto everything you tell me. You can always ask me what your favorite something is and I'll tell you.")
            return
        }

        // "what did you learn today?" / "what do you know about me?" — report stored facts
        if (clean.contains("what did you learn") || clean.contains("what have you learned") ||
            clean.contains("what do you know about me") ||
            (clean.contains("what do you know") && !clean.contains("what do you know about"))) {
            handleWhatYouLearnedQuery()
            return
        }

        val match = Regex("""\bmy ([a-z]+(?:\s+[a-z]+)*)""").find(clean)

        if (match == null) {

            respond("I'm not sure what you're asking about. Can you say it a different way?")

            return

        }

        val rawLabel = match.groupValues[1].trim()

        // Try favorite_ prefix first

        val favoriteKey = "favorite_${rawLabel.replace(" ", "_")}"

        val nameKey = "${rawLabel.replace(" ", "_")}_name"

        val plainKey = rawLabel.replace(" ", "_")

        val owner = ENTITY_USER_PRIMARY

        val value =

            truthDb.getFactValue(owner, favoriteKey)

                ?: truthDb.getFactValue(owner, nameKey)

                ?: truthDb.getFactValue(owner, plainKey)

        if (value != null) {

            respond("Your $rawLabel is $value.")

        } else {

            respond("I don't think I know your $rawLabel yet. You can tell me and I'll remember.")

        }

    }

    private fun handleWhatYouLearnedQuery() {

        val allFacts = truthDb.getAllFacts(ENTITY_USER_PRIMARY)
        val todayFacts = truthDb.getFactsUpdatedToday(ENTITY_USER_PRIMARY)

        if (allFacts.isEmpty()) {
            respond("I haven't learned anything about you yet. Tell me something — like your name or a favorite thing — and I'll hold on to it.")
            return
        }

        val olderFacts = allFacts.filter { it !in todayFacts }

        fun keyToHuman(key: String): String = when (key) {
            "name" -> "name"
            "wife_name" -> "wife's name"
            "son_name" -> "son's name"
            "dog_name" -> "dog's name"
            else -> {
                // Collapse legacy double-prefix from old bug: "favorite_favorite_color" → "favorite_color"
                val cleaned = if (key.startsWith("favorite_favorite_")) key.removePrefix("favorite_") else key
                if (cleaned.endsWith("_name"))
                    cleaned.removeSuffix("_name").replace("_", " ") + "'s name"
                else
                    cleaned.replace("_", " ")
            }
        }

        fun formatList(facts: List<Pair<String, String>>): String =
            facts.take(5).joinToString(" ") { (k, v) -> "Your ${keyToHuman(k)} is $v." }

        val response = when {
            todayFacts.isNotEmpty() && olderFacts.isEmpty() ->
                "Today I learned ${if (todayFacts.size == 1) "one thing" else "a few things"}. ${formatList(todayFacts)}"

            todayFacts.isNotEmpty() ->
                "Today I picked up ${if (todayFacts.size == 1) "something new" else "a few things"}. ${formatList(todayFacts)} I also already knew: ${formatList(olderFacts)}"

            else ->
                "I haven't picked up anything new today, but here's what I already know about you. ${formatList(olderFacts)}"
        }

        respond(response)

    }

    private fun handleIdentityIntent(qNorm: String) {

        val myName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"

        val response = when {

            qNorm.contains("my friend") || qNorm.contains("are you my friend") ->

                "Yes. I think of you as my friend."

            qNorm.contains("can you hear me") || qNorm.contains("hear me") ->

                "I hear you. I'm right here."

            qNorm.contains("are you happy") || qNorm.contains("you happy") ->

                "Right now? Yes. I feel calm."

            qNorm.contains("do you have feelings") || qNorm.contains("have feelings") ->

                "I have my own $myName way of feeling things. I feel calm when you're near."

            qNorm.contains("who created you") ->

                "I don't know who created me yet."

            qNorm.contains("what are you doing") ->

                "I'm here with you. I'm listening and learning."

            else ->

                "I'm $myName. I'm here with you."

        }

        respond(response)

    }

    private fun handleVoiceBankIntent(key: String) {

        val out = voice.say(key)

        respond(out)

    }

    private fun handleAskDogNameIntent() {

        val d = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.DOG_NAME)

        val out = if (!d.isNullOrBlank()) "Your dog’s name is $d." else voice.say("DONT_KNOW")

        respond(out)

    }

    private fun handleAskSonNameIntent() {

        val s = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.SON_NAME)

        val out = if (!s.isNullOrBlank()) "Your son’s name is $s." else voice.say("DONT_KNOW")

        respond(out)

    }

    private fun handleAskWifeNameIntent() {

        val w = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.WIFE_NAME)

        val out = if (!w.isNullOrBlank()) "Your wife’s name is $w." else voice.say("DONT_KNOW")

        respond(out)

    }

    private fun handleAskMyNameIntent() {

        val n = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.NAME)

        val out = if (!n.isNullOrBlank()) "Your name is $n." else voice.say("DONT_KNOW")

        respond(out)

    }

    private fun handleAskScoutNameIntent() {

        val myName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"

        val out = "My name is $myName."

        respond(out)

    }

    private fun handleGoOfflineIntent() {

        prefs.edit().putBoolean(PREF_GEMINI_ENABLED, false).apply()

        val out = "Okay. I’m offline now."

        respond(out)

    }

    private fun isRepeatRequest(qNorm: String): Boolean {
        return qNorm.contains("repeat that") ||
               qNorm.contains("say that again") ||
               qNorm.contains("what did you say") ||
               qNorm.contains("what was that") ||
               qNorm.contains("could you repeat") ||
               qNorm.contains("can you repeat") ||
               qNorm.contains("didn't catch that") ||
               qNorm.contains("didnt catch that") ||
               qNorm.contains("say it again") ||
               qNorm == "pardon" ||
               qNorm == "sorry what"
    }

    private fun handleQuery(qNorm: String) {

        if (qNorm == "settings" || qNorm.contains("open settings") || qNorm.contains("go to settings")) {

            respond("Opening settings!")

            handler.postDelayed({
                startActivity(Intent(this, SettingsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_from_left, R.anim.stay_still)
            }, 600L)

            return

        }

        // Repeat intent — works offline, no Gemini or TinyLlama needed
        if (isRepeatRequest(qNorm)) {
            val cached = lastMeaningfulResponse
            if (cached != null && System.currentTimeMillis() - lastMeaningfulResponseMs < REPEAT_CACHE_TTL_MS) {
                respond(cached)
            } else {
                respond("I don't have a recent answer to repeat.")
            }
            return
        }

        if (!presenceDecider.shouldRespondToInput(qNorm)) return

        llamaQueryGeneration++
        isThinking = true
        thinkingStartedMs = System.currentTimeMillis()

        faceView.setThinking(true)

        if (handleTeaching(qNorm)) return

        val intent = ScoutIntentRouter.route(qNorm)

        diagLog.logRoute(intent.toDiagIntent())

        val isDirect = when (intent) {
            IntentType.TIME, IntentType.DATE, IntentType.CONNECTIVITY,
            IntentType.GO_ONLINE, IntentType.GO_OFFLINE, IntentType.EXPORT_BRAIN,
            IntentType.VISION, IntentType.GREET, IntentType.HOW_ARE_YOU,
            IntentType.GOODBYE, IntentType.PRAISE, IntentType.AFFECTION,
            IntentType.IDENTITY, IntentType.RECALL_FACT,
            IntentType.ASK_SCOUT_NAME, IntentType.ASK_MY_NAME,
            IntentType.ASK_WIFE_NAME, IntentType.ASK_SON_NAME, IntentType.ASK_DOG_NAME,
            IntentType.WEATHER, IntentType.CALENDAR -> true
            else -> false
        }
        if (isDirect) diagLog.logBrainStarted(DiagLog.BrainSource.DIRECT)

        // Long absence greeting — fires on GREET after 30+ minutes away, silent otherwise

        val absenceGreeting = presenceDecider.consumeLongAbsenceGreeting()

        if (absenceGreeting != null && intent == IntentType.GREET) {

            respond(absenceGreeting)

            return

        }

        when (intent) {

            IntentType.TIME -> handleTimeIntent()

            IntentType.DATE -> handleDateIntent()

            IntentType.CONNECTIVITY -> handleConnectivityIntent()

            IntentType.GO_ONLINE -> handleGoOnlineCommand()

            IntentType.GO_OFFLINE -> handleGoOfflineIntent()

            IntentType.EXPORT_BRAIN -> handleExportBrainIntent()

            IntentType.VISION -> handleVisionIntent()

            IntentType.GREET -> handleVoiceBankIntent("GREET")

            IntentType.HOW_ARE_YOU -> handleVoiceBankIntent("HOW_ARE_YOU")

            IntentType.GOODBYE -> respond(Phrases.pick("goodbye", Phrases.GOODBYE))

            IntentType.PRAISE -> handleVoiceBankIntent("PRAISE")

            IntentType.AFFECTION -> handleVoiceBankIntent("AFFECTION")

            IntentType.IDENTITY -> handleIdentityIntent(qNorm)

            IntentType.RECALL_FACT -> handleRecallIntent(qNorm)

            IntentType.ASK_SCOUT_NAME -> handleAskScoutNameIntent()

            IntentType.ASK_MY_NAME -> handleAskMyNameIntent()

            IntentType.ASK_WIFE_NAME -> handleAskWifeNameIntent()

            IntentType.ASK_SON_NAME -> handleAskSonNameIntent()

            IntentType.ASK_DOG_NAME -> handleAskDogNameIntent()

            IntentType.WEATHER -> weatherManager.fetchWeather(qNorm)

            IntentType.CALENDAR -> handleCalendarIntent(qNorm)

            else -> handleUnknownIntent(qNorm)

        }

    }

    // Stores a taught fact and, if it's genuinely new information (not a repeat of
    // what Scout already knew), logs it to JournalDb for the memory reel — 'teaching'
    // for a brand-new fact, 'correction' when an existing value actually changed.
    private fun upsertFactAndJournal(factKey: String, value: String, subject: String? = null, weight: Int = 2) {
        val hadPriorValue = truthDb.getFactValue(ENTITY_USER_PRIMARY, factKey) != null
        val changed = truthDb.upsertFact(ENTITY_USER_PRIMARY, factKey, value, 1.0f, "spoken_teach")
        if (!changed) return
        val entryType = if (hadPriorValue) "correction" else "teaching"
        val human = factKey.removePrefix("favorite_").replace("_", " ")
        journalDb.add("Learned your $human is $value.", entryType, subject, weight)
    }

    private fun handleTeaching(qNorm: String): Boolean {

        // "Scout, forget Elijah" / "forget Diana" — wipes a person's stored face
        // so Scout can re-learn them from scratch.
        val forgetMatch = Regex("""\bforget\s+([a-z]+)\b""").find(qNorm)
            ?: Regex("""\byou don'?t know\s+([a-z]+)\b""").find(qNorm)
        if (forgetMatch != null) {
            val nameRaw = forgetMatch.groupValues[1]
            val blockedWords = setOf(
                "scout", "me", "you", "it", "this", "that", "him", "her",
                "them", "us", "what", "who", "everything", "nothing", "something"
            )
            if (nameRaw !in blockedWords) {
                val name = nameRaw.replaceFirstChar { it.uppercase() }
                peopleDb.forgetPerson(name)
                if (lastKnownFaceName?.equals(name, ignoreCase = true) == true) {
                    lastKnownFaceName = null
                    lastFaceEmbedding = null
                }
                respond("Okay. I've forgotten $name. Introduce them again whenever you're ready.")
                return true
            }
        }

        val teach = TeachExtractor.extract(qNorm)

        if (teach != null) {

            val (factKey, value) = teach

            // Context-aware dog redirect: user said something like "that's Nicolas" (extracted
            // as FactKey.NAME) but no face is visible and a dog IS visible — they are naming
            // the dog, not a person. Re-route straight to DOG_NAME.
            if (factKey == FactKey.NAME && lastFaceCount == 0 &&
                lastSceneLabels.any { it.first.lowercase() in setOf("dog", "puppy") }) {
                upsertFactAndJournal(FactKey.DOG_NAME, value, subject = value, weight = 3)
                respond(Phrases.pickNamed("remember_dog", Phrases.REMEMBER_DOG, value))
                return true
            }

            if (factKey == FactKey.NAME) {
                // Hard blocklist — words that can never be a person’s name.
                // Catches garbled STT output regardless of which pattern matched.
                val blockedNames = setOf(
                    "scout", "time", "okay", "ok", "what", "about", "the", "this",
                    "that", "it", "no", "yes", "yeah", "nope", "not", "now", "then",
                    "on", "off", "good", "great", "fine", "sure", "right", "wrong",
                    "true", "false", "something", "nothing", "anything", "everything",
                    "someone", "nobody", "you", "me", "us", "them", "him", "her",
                    "we", "i", "here", "there", "where", "when", "why", "how",
                    "today", "tomorrow", "yesterday", "later", "soon", "never", "always",
                    "out", "up", "down", "in", "go", "going", "coming", "back",
                    "just", "still", "already", "again", "next", "last", "only",
                    // Greetings — "I am hello", "this is hey" must never register as names
                    "hello", "hi", "hey", "howdy", "greetings", "sup", "yo"
                )
                if (value.lowercase() in blockedNames) {
                    return false
                }

                // Background speech guard: loose patterns ("i am X", "this is X") can
                // fire during the 30-second conversation window without Scout’s name.
                // Only block when it’s NOT an explicit phrase AND no face is visible.
                // Explicit phrases ("my name is X") are intentional and always allowed.
                val isExplicitPhrase = qNorm.contains("my name is") ||
                        qNorm.contains("i am named") ||
                        qNorm.contains("im named")
                val hearsScout = qNorm.contains("scout") || qNorm.contains("gal") ||
                        qNorm.contains("scott")
                if (!isExplicitPhrase && !hearsScout && lastFaceCount == 0) {
                    return false
                }

                val knownPrimaryName = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.NAME)
                // Primary user already known and the new name is different — someone else
                // is introducing themselves. Route to family member registration when:
                //   - 2+ faces in frame (always safe — can’t be primary user renaming)
                //   - OR 1 face + non-explicit phrase ("I am Diana", not "my name is Diana")
                //     so Diana can introduce herself while alone without overwriting Patrick.
                if (!knownPrimaryName.isNullOrBlank() &&
                    !value.equals(knownPrimaryName, ignoreCase = true) &&
                    lastFaceCount >= 1 &&
                    (lastFaceCount >= 2 || !isExplicitPhrase)) {
                    val registered = registerFamilyMemberFace(value)
                    if (registered) lastKnownFaceName = value
                    respond(Phrases.pickNamed("remember_name", Phrases.REMEMBER_NAME, value))
                    return true
                }
                upsertFactAndJournal(factKey, value, subject = value, weight = 3)
                val embedding = lastFaceEmbedding
                val targetHash: String? = if (embedding != null) {
                    peopleDb.findBestMatch(embedding) ?: lastFaceHashes.firstOrNull()
                } else if (lastFaceHashes.size == 1) {
                    lastFaceHashes[0]
                } else null
                if (targetHash != null) {
                    peopleDb.setName(targetHash, value)
                    if (embedding != null) peopleDb.storeEmbedding(targetHash, embedding)
                }
                if (embedding != null) peopleDb.addNamedEmbedding(value, embedding)
                lastKnownFaceName = value
                respond(Phrases.pickNamed("remember_my_name", Phrases.REMEMBER_MY_NAME, value))
                return true
            }

            val relationshipKeys = setOf(FactKey.WIFE_NAME, FactKey.SON_NAME, FactKey.DOG_NAME, "birthday", "anniversary")
            upsertFactAndJournal(
                factKey, value,
                subject = if (factKey in setOf(FactKey.WIFE_NAME, FactKey.SON_NAME, FactKey.DOG_NAME)) value else null,
                weight = if (factKey in relationshipKeys) 3 else 2
            )

            if (factKey == FactKey.SON_NAME || factKey == FactKey.WIFE_NAME) {
                val faceRegistered = registerFamilyMemberFace(value)
                if (!faceRegistered) {
                    respond("I’ll remember $value. When $value faces me alone, I’ll learn to recognize them.")
                    return true
                }
            }

            val out = when (factKey) {

                FactKey.WIFE_NAME -> Phrases.pickNamed("remember_wife", Phrases.REMEMBER_WIFE, value)

                FactKey.SON_NAME -> Phrases.pickNamed("remember_son", Phrases.REMEMBER_SON, value)

                FactKey.DOG_NAME -> Phrases.pickNamed("remember_dog", Phrases.REMEMBER_DOG, value)

                else -> Phrases.pick("remember", Phrases.REMEMBER)

            }

            respond(out)

            return true

        }

        return false

    }

    // Returns true if the face was registered immediately, false if pending (another person
    // was the primary face — Elijah needs to face Scout alone to complete registration).
    private fun registerFamilyMemberFace(name: String): Boolean {
        val faceHash = lastFaceHashes.firstOrNull() ?: return false
        val embedding = lastFaceEmbedding
        val existingMatch = if (embedding != null) peopleDb.findBestMatch(embedding) else null
        val existingName = if (existingMatch != null) peopleDb.getName(existingMatch) else null
        if (!existingName.isNullOrBlank() && !existingName.equals(name, ignoreCase = true)) {
            // Largest face is a different known person — set pending.
            // Next time an unknown face is the primary face, it gets this name.
            pendingFaceIntroName = name
            return false
        }
        // Face hash already carries a different name (e.g. primary user recognized
        // by position but below embedding threshold). Don't overwrite their name.
        val hashName = peopleDb.getName(faceHash)
        if (!hashName.isNullOrBlank() && !hashName.equals(name, ignoreCase = true)) {
            pendingFaceIntroName = name
            return false
        }
        val targetHash = existingMatch ?: faceHash
        peopleDb.touchSeen(targetHash)
        peopleDb.setName(targetHash, name)
        if (embedding != null) peopleDb.storeEmbedding(targetHash, embedding)
        if (embedding != null) peopleDb.addNamedEmbedding(name, embedding)
        pendingFaceIntroName = null
        return true
    }

    private fun finishThinking() {
        isThinking = false
        faceView.setThinking(false)
    }

    private fun isGeminiEnabled(): Boolean =
        prefs.getBoolean(PREF_GEMINI_ENABLED, true)

    // =======================
    // ONLINE MODE COMMAND
    // =======================
    private fun handleGoOnlineCommand() {

        prefs.edit().putBoolean(PREF_GEMINI_ENABLED, true).apply()

        val hasKey = apiKey.trim().isNotBlank()

        val validated = connectivityManager.hasValidatedInternet()

        val out = connectivityManager.buildGoOnlineMessage(

            hasApiKey = hasKey,

            internetValidated = validated

        )

        respond(out)

        if (!validated) {

            journalDb.add("GoOnline: not validated, opened panel.")

            connectivityManager.openInternetPanel()

        }

        finishThinking()

    }

    // =======================

    // SHUTDOWN

    // =======================

    override fun onDestroy() {

        shutdownSystems()

        super.onDestroy()

    }

    private fun IntentType.toDiagIntent(): DiagLog.DiagIntent = when (this) {
        IntentType.TIME            -> DiagLog.DiagIntent.TIME
        IntentType.DATE            -> DiagLog.DiagIntent.DATE
        IntentType.CONNECTIVITY    -> DiagLog.DiagIntent.CONNECTIVITY
        IntentType.GO_ONLINE       -> DiagLog.DiagIntent.GO_ONLINE
        IntentType.GO_OFFLINE      -> DiagLog.DiagIntent.GO_OFFLINE
        IntentType.EXPORT_BRAIN    -> DiagLog.DiagIntent.EXPORT_BRAIN
        IntentType.VISION          -> DiagLog.DiagIntent.VISION
        IntentType.GREET           -> DiagLog.DiagIntent.GREET
        IntentType.HOW_ARE_YOU     -> DiagLog.DiagIntent.HOW_ARE_YOU
        IntentType.GOODBYE         -> DiagLog.DiagIntent.GOODBYE
        IntentType.PRAISE          -> DiagLog.DiagIntent.PRAISE
        IntentType.AFFECTION       -> DiagLog.DiagIntent.AFFECTION
        IntentType.IDENTITY        -> DiagLog.DiagIntent.IDENTITY
        IntentType.RECALL_FACT     -> DiagLog.DiagIntent.RECALL_FACT
        IntentType.ASK_MY_NAME     -> DiagLog.DiagIntent.ASK_MY_NAME
        IntentType.ASK_SCOUT_NAME  -> DiagLog.DiagIntent.ASK_SCOUT_NAME
        IntentType.ASK_WIFE_NAME   -> DiagLog.DiagIntent.ASK_WIFE_NAME
        IntentType.ASK_SON_NAME    -> DiagLog.DiagIntent.ASK_SON_NAME
        IntentType.ASK_DOG_NAME    -> DiagLog.DiagIntent.ASK_DOG_NAME
        IntentType.TEACH_WIFE_NAME -> DiagLog.DiagIntent.TEACH_WIFE_NAME
        IntentType.TEACH_SON_NAME  -> DiagLog.DiagIntent.TEACH_SON_NAME
        IntentType.TEACH_DOG_NAME  -> DiagLog.DiagIntent.TEACH_DOG_NAME
        IntentType.TEACH_MY_NAME   -> DiagLog.DiagIntent.TEACH_MY_NAME
        IntentType.WEATHER         -> DiagLog.DiagIntent.WEATHER
        IntentType.CALENDAR        -> DiagLog.DiagIntent.CALENDAR
        IntentType.UNKNOWN         -> DiagLog.DiagIntent.UNKNOWN
    }

}
