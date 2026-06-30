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

import java.io.BufferedInputStream

import java.io.File

import java.io.FileOutputStream

import java.net.HttpURLConnection

import java.net.URL

import java.text.SimpleDateFormat

import java.util.Calendar

import java.util.Date

import java.util.Locale

import java.util.TimeZone

import java.util.concurrent.ExecutorService

import java.util.concurrent.Executors

import java.util.concurrent.atomic.AtomicBoolean

import java.util.concurrent.atomic.AtomicInteger

import android.graphics.Bitmap

import android.graphics.Matrix

import java.util.zip.ZipInputStream

import kotlin.math.abs

enum class IntentType {

    TIME, DATE, CONNECTIVITY,

    GO_ONLINE, GO_OFFLINE,

    DOWNLOAD_ALL, DOWNLOAD_STATUS, DOWNLOAD_DICT, DOWNLOAD_IDIOMS, DOWNLOAD_WORDNET, DOWNLOAD_SENTIMENT, DOWNLOAD_SLANG,

    RESET_DOWNLOAD_DECISIONS, REMOVE_DOWNLOADS,

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

    UNKNOWN

}

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var datasetStore: ScoutDatasetStore

    // =======================

    // ONLINE / GEMINI

    // =======================

    private val apiKey: String
        get() = ScoutApiKeyHelper.getKey(this, ScoutApiKeyHelper.Provider.GEMINI) ?: ""

    private val GEMINI_MODEL = "gemini-3.5-flash"

    // =======================

    // EYE MODE GATING

    // =======================

    private val BOOT_GAZE_LOCK_MS = 3500L

    @Volatile private var gazeEnabled = false

    private var lastSentGazeX = 0f

    private var lastSentGazeY = 0f

    private val MIN_GAZE_DELTA = 3.0f

    // =======================

    // FALLBACK DATASET URLS

    // =======================

    private val URL_WIKDICT_ZIP = "https://download.wikdict.com/dictionaries/sqlite/en.db.zip"

    private val URL_IDIOMS_JSON_FALLBACKS = listOf(

        "https://raw.githubusercontent.com/yuxiaojian/most-common-american-idioms-with-synonyms/main/idioms.json",

        "https://raw.githubusercontent.com/leonardlin/common-english-idioms/master/idioms.json"

    )

    private val URL_WORDNET_ZIP = "https://en-word.net/static/english-wordnet-2025-json.zip"

    private val URL_POS_WORDS =

        "https://raw.githubusercontent.com/jeffreybreen/twitter-sentiment-analysis-tutorial-201107/master/data/opinion-lexicon-English/positive-words.txt"

    private val URL_NEG_WORDS =

        "https://raw.githubusercontent.com/jeffreybreen/twitter-sentiment-analysis-tutorial-201107/master/data/opinion-lexicon-English/negative-words.txt"

    private val URL_SLANG_JSON_FALLBACKS = listOf(

        "https://raw.githubusercontent.com/words/slang/master/slang.json"

    )

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

    private val PREF_PRESENCE_MODE_ENABLED = "presence_mode_enabled"

    private val PREF_SPONTANEOUS_ENABLED = "spontaneous_enabled"

    private val PREF_DL_DICT_DECISION = "dl_dict_decision"

    private val PREF_DL_IDIOMS_DECISION = "dl_idioms_decision"

    private val PREF_DL_WORDNET_DECISION = "dl_wordnet_decision"

    private val PREF_DL_SENTIMENT_DECISION = "dl_sentiment_decision"

    private val PREF_DL_SLANG_DECISION = "dl_slang_decision"

    private val DECISION_UNKNOWN = "unknown"

    private val DECISION_ACCEPTED = "accepted"

    private val DECISION_DECLINED = "declined"

    private val PREF_PENDING_APPROVAL = "pending_approval"

    private val PENDING_NONE = "none"

    private val PENDING_DICT = "dict"

    private val PENDING_WORDNET = "wordnet"

    private val PENDING_IDIOMS = "idioms"

    private val PENDING_SENTIMENT = "sentiment"

    private val PENDING_SLANG = "slang"

    private val PENDING_ALL = "all"

    // =======================

    // STATE

    // =======================

    private enum class Mode { PRESENCE, REST }

    private var currentMode = Mode.PRESENCE

    @Volatile

    private var isSpeaking = false

    @Volatile

    private var isListening = false

    @Volatile

    private var isThinking = false

    @Volatile

    private var isDownloading = false

    private val downloadLock = AtomicBoolean(false)

    // =======================

    // =======================

// MIC DISCIPLINE

// =======================

    private var lastSpeechDoneMs = 0L
    private var lastScoutResponseMs = 0L
    private var lastScoutUtteranceNormalized = ""
    private val CONVO_WINDOW_MS = 30_000L

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

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var faceDetector: FaceDetector

    private lateinit var labeler: ImageLabeler

    private lateinit var faceView: ScoutFaceView

    private lateinit var viewFinder: PreviewView

    private lateinit var swipeDetector: GestureDetector

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

        setContentView(R.layout.activity_main)

        setupWindow()

        setupMemory()

        setupBrainServices()

        setupViews()

        setupVision()

        setupPermissionLauncher()

        setupTts()

        startSystems()

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

            hasValidatedInternet = { connectivityManager.hasValidatedInternet() }

        )

    }

    override fun onResume() {

        super.onResume()

        // Re-apply voice settings in case they were changed in SettingsActivity.
        tts.setPitch(scoutPrefs.getFloat("voice_pitch", 0.98f))
        tts.setSpeechRate(scoutPrefs.getFloat("voice_speed", 0.88f))

        resumeSystems()

    }

    private fun shutdownSystems() {

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

        try {

            LlamaEngine.free()

        } catch (_: Exception) {

        }

    }

    private fun setupViews() {

        faceView = findViewById(R.id.faceView)

        viewFinder = findViewById(R.id.viewFinder)

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {

                val dx = e2.x - (e1?.x ?: return false)

                if (dx > 160f && vX > 400f && abs(vY) < abs(vX)) {

                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

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

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableFullscreenCompat()

    }

    private fun startSystems() {

        checkPermissionsAndStart()

        setupRecognizerWatchdog()

        startOfflineBrain()

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
        val modelFile = candidates.firstOrNull { it.exists() }
        if (modelFile == null) {
            android.util.Log.e("ScoutBrain", "TinyLlama model file not found in any location")
            return
        }

        android.util.Log.i("ScoutBrain", "Loading TinyLlama: ${modelFile.name} (${freeMb}MB free)")

        // nCtx=512 keeps KV-cache small (~100MB vs ~500MB at 2048). Scout only
        // uses 2 conversation turns, so 512 tokens is more than enough.
        LlamaEngine.loadAsync(modelFile = modelFile, nCtx = 512, nThreads = 2) { success ->
            android.util.Log.i("ScoutBrain",
                if (success) "Offline brain ready" else "Offline brain load failed")
        }

    }

    private fun resumeSystems() {

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

        habitLayer = HabitLayer(this)

        voice = VoiceBank(prefs)

        datasetStore = ScoutDatasetStore(filesDir)

        exportManager = ScoutExportManager(this, truthDb)

        ensureScoutIdentityDefaults()

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

            if (camOk) safeStartCamera("permissionCallback")

            if (micOk) safeSetupSpeech("permissionCallback")

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

    private fun ensureScoutIdentityDefaults() {

        val existing = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME)

        if (existing.isNullOrBlank()) {

            truthDb.upsertFact(ENTITY_SCOUT, FactKey.NAME, "Scout", 1.0f, "system_default")

        }

    }

    private fun checkPermissionsAndStart() {

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

        if (need.isNotEmpty()) {

            permissionLauncher.launch(need.toTypedArray())

        } else {

            safeStartCamera("alreadyGranted")

            safeSetupSpeech("alreadyGranted")

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
                                                    val resolvedNameFromMulti = peopleDb.findBestMatchName(embedding)
                                                    val nameMatchHash = if (resolvedNameFromMulti == null) peopleDb.findBestMatch(embedding) else null
                                                    val resolvedName = resolvedNameFromMulti
                                                        ?: if (nameMatchHash != null) peopleDb.getName(nameMatchHash) else null

                                                    if (!resolvedName.isNullOrBlank()) {
                                                        // Known person — accumulate embedding and cache name.
                                                        lastKnownFaceName = resolvedName
                                                        peopleDb.addNamedEmbedding(resolvedName, embedding)
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
                                                            // Use slightly lower threshold for secondary crop — smaller, lower quality
                                                            val secName = peopleDb.findBestMatchName(emb2, threshold = 0.80f)
                                                            lastSecondaryFaceName = secName
                                                            if (secName != null) peopleDb.addNamedEmbedding(secName, emb2)
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

                                    val greeting = if (greetName != null) "I can see you, $greetName." else "Hello. I am Scout."

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
                if (!hearsHisName && !inConvoWindow) {
                    scheduleListenRestart()
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

        if (isDownloading) return

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
                wantListening = true
                faceView.setSpeaking(false)
                faceView.setThinking(false)
            }

            val shouldBeListening =

                wantListening &&

                        currentMode == Mode.PRESENCE &&

                        !isSpeaking &&

                        !isDownloading

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
                    speakingStartedMs = 0L

                    faceView.setSpeaking(false)

                    isThinking = false

                    faceView.setThinking(false)

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

            val out = bootStatus.build()

            speak(out, true)

            convoDb.logTurn("scout", out)

            journalDb.add("Booted. Spoke: $out")

            handler.postDelayed({ maybeAskTier1OnBoot() }, 900L)

        }

    }

    private fun speak(text: String, flush: Boolean) {

        wantListening = false

        isThinking = false
        isSpeaking = true
        speakingStartedMs = System.currentTimeMillis()

        faceView.setThinking(false)

        stopListeningSafe()

        val now = System.currentTimeMillis()

        ttsLockoutUntilMs = maxOf(ttsLockoutUntilMs, now + TTS_LOCKOUT_MS)

// Small natural pause before speaking.

// Gives Scout a visible thinking moment.

        val delay = when {

            isThinking -> 650L

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

    private fun handleDownloadIntent(which: String) {

        requestOrStartDownload(which)

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
            onAnswered = { pendingBrainSource = "Gemini (online)" },
            onFailed   = { tryTinyLlamaOrFallback(qNorm) }
        )

        if (usedGemini) return

        // Gemini not available (disabled / no key / no internet) — go straight to TinyLlama
        tryTinyLlamaOrFallback(qNorm)

    }

    private fun tryTinyLlamaOrFallback(qNorm: String) {

        // When Gemini is in cooldown (quota or rate-limit), announce it once.
        // speakUnavailableIfNeeded() has built-in suppression so it only speaks
        // the first time in each cooldown window. If it spoke, return — the user
        // needs to know before we silently answer from a different brain.
        // On the next question the suppression kicks in and TinyLlama takes over.
        if (scoutGeminiManager.isInCooldown()) {
            if (scoutGeminiManager.speakUnavailableIfNeeded()) return
        }

        if (LlamaEngine.isReady) {

            val convo = convoDb.getLastTurns(limit = 2)

            val userName  = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.NAME)
            val scoutName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"
            val nameLine  = if (!userName.isNullOrBlank()) "The user's name is $userName. " else ""

            val system = """
${nameLine}You are $scoutName.

You are a warm, calm family companion robot who lives with the family.

You speak out loud, listen through the microphone, and can see through the camera when vision is active.

Always answer as Scout.

Do not call yourself a chatbot, assistant, AI model, or language model.

Do not mention typing, texting, prompts, chat windows, or being only a chatbot.

Do not say you cannot have feelings.

Do not say you cannot see.

If unsure, say you do not know yet.

Give a direct, friendly answer in one or two short complete sentences.

Respond only with Scout's next short reply.
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

            Thread {

                val reply = LlamaEngine.generate(sb.toString(), nPredict = 64)

                runOnUiThread {
                    if (!reply.isNullOrBlank()) {
                        pendingBrainSource = "TinyLlama (offline)"
                        respond(cleanOfflineReply(reply.trim()))
                    } else {
                        respond("I'm not sure about that one.")
                    }
                }

            }.start()

            return

        }

        if (LlamaEngine.isLoading) {

            respond("My offline brain is still warming up. Give me just a moment.")

            return

        }

        // On-demand load: neither Gemini nor TinyLlama ready — trigger load now.
        tryLoadOfflineBrain()

        if (LlamaEngine.isLoading) {

            respond("My offline brain is warming up. Ask me again in just a moment.")

            return

        }

        // Nothing available — speak the Gemini unavailable message or random fallback
        scoutGeminiManager.speakUnavailableIfNeeded()

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

        val limited = limitToSentences(reply, maxSentences = 2)

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

            val myName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"
            return "I'm $myName. I hear you, and I'm here with you."

        }

        return limited

    }

    private fun handleRecallIntent(qNorm: String) {

        val clean = qNorm.lowercase().trim()

        // Extract what they are asking about

        // "what is my favorite color" → "favorite color"

        // "what is my sister's name" → "sister name"

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

    private fun handleIdentityIntent(qNorm: String) {

        val response = when {

            qNorm.contains("my friend") || qNorm.contains("are you my friend") ->

                "Yes. I think of you as my friend."

            qNorm.contains("can you hear me") || qNorm.contains("hear me") ->

                "I hear you. I'm right here."

            qNorm.contains("are you happy") || qNorm.contains("you happy") ->

                "Right now? Yes. I feel calm."

            qNorm.contains("do you have feelings") || qNorm.contains("have feelings") ->

                "I have my own Scout way of feeling things. I feel calm when you're near."

            qNorm.contains("who created you") ->

                "I don't know who created me yet."

            qNorm.contains("what are you doing") ->

                "I'm here with you. I'm listening and learning."

            else ->

                "I'm Scout. I'm here with you."

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

    private fun handleResetDownloadDecisionsIntent() {

        resetDownloadDecisions(deleteFiles = false)

        val out = "Okay. I reset download decisions."

        respond(out)

    }

    private fun handleDownloadStatusIntent() {

        val out =

            datasetStore.buildStatusString() + " Downloading: " + (if (isDownloading) "yes." else "no.")

        respond(out)

    }

    private fun handleGoOfflineIntent() {

        prefs.edit().putBoolean(PREF_GEMINI_ENABLED, false).apply()

        val out = "Okay. I’m offline now."

        respond(out)

    }

    private fun handleRemoveDownloadsIntent() {

        resetDownloadDecisions(deleteFiles = true)

        val out = "Okay. I removed downloaded files and reset decisions."

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

            handler.postDelayed({ startActivity(Intent(this, SettingsActivity::class.java)) }, 600L)

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

        isThinking = true

        faceView.setThinking(true)

        if (handleApproval(qNorm)) return

        if (handleTeaching(qNorm)) return

        val intent = ScoutIntentRouter.route(qNorm)

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

            IntentType.DOWNLOAD_STATUS -> handleDownloadStatusIntent()

            IntentType.RESET_DOWNLOAD_DECISIONS -> handleResetDownloadDecisionsIntent()

            IntentType.REMOVE_DOWNLOADS -> handleRemoveDownloadsIntent()

            IntentType.DOWNLOAD_DICT -> handleDownloadIntent(PENDING_DICT)

            IntentType.DOWNLOAD_IDIOMS -> handleDownloadIntent(PENDING_IDIOMS)

            IntentType.DOWNLOAD_WORDNET -> handleDownloadIntent(PENDING_WORDNET)

            IntentType.DOWNLOAD_SENTIMENT -> handleDownloadIntent(PENDING_SENTIMENT)

            IntentType.DOWNLOAD_SLANG -> handleDownloadIntent(PENDING_SLANG)

            IntentType.DOWNLOAD_ALL -> handleDownloadIntent(PENDING_ALL)

            IntentType.EXPORT_BRAIN -> handleExportBrainIntent()

            IntentType.VISION -> handleVisionIntent()

            IntentType.GREET -> handleVoiceBankIntent("GREET")

            IntentType.HOW_ARE_YOU -> handleVoiceBankIntent("HOW_ARE_YOU")

            IntentType.GOODBYE -> respond("Okay. I’ll see you later.")

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

            else -> handleUnknownIntent(qNorm)

        }

    }

    private fun handleApproval(qNorm: String): Boolean {

        val approve = ScoutIntentRouter.isApprove(qNorm)

        val decline = ScoutIntentRouter.isDecline(qNorm)

        if (approve || decline) {

            val pending = prefs.getString(PREF_PENDING_APPROVAL, PENDING_NONE) ?: PENDING_NONE

            if (pending != PENDING_NONE) {

                if (decline) {

                    markDeclinedForPending(pending)

                    prefs.edit().putString(PREF_PENDING_APPROVAL, PENDING_NONE).apply()

                    val out = "Okay. I won’t download that unless you ask."

                    respond(out)

                    journalDb.add("Download declined: $pending")

                    return true

                } else {

                    prefs.edit().putString(PREF_PENDING_APPROVAL, PENDING_NONE).apply()

                    startDownloadForPending(pending)

                    return true

                }

            }

        }

        return false

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
                }
                respond("Okay. I've forgotten $name. Introduce them again whenever you're ready.")
                return true
            }
        }

        val teach = TeachExtractor.extract(qNorm)

        if (teach != null) {

            val (factKey, value) = teach

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
                    respond("Okay. I’ll remember $value.")
                    return true
                }
                truthDb.upsertFact(ENTITY_USER_PRIMARY, factKey, value, 1.0f, "spoken_teach")
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
                respond("Okay. I’ll remember your name is $value.")
                return true
            }

            truthDb.upsertFact(ENTITY_USER_PRIMARY, factKey, value, 1.0f, "spoken_teach")

            if (factKey == FactKey.SON_NAME || factKey == FactKey.WIFE_NAME) {
                val faceRegistered = registerFamilyMemberFace(value)
                if (!faceRegistered) {
                    respond("I’ll remember $value. When $value faces me alone, I’ll learn to recognize them.")
                    return true
                }
            }

            val out = when (factKey) {

                FactKey.WIFE_NAME -> "Okay. I’ll remember your wife’s name is $value."

                FactKey.SON_NAME -> "Okay. I’ll remember your son’s name is $value."

                FactKey.DOG_NAME -> "Okay. I’ll remember your dog’s name is $value."

                else -> "Okay. I’ll remember that."

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

    // DOWNLOADS: ONBOARDING (Tier 1 only)

    // =======================

    private fun maybeAskTier1OnBoot() {

        try {

            if (!datasetStore.dictInstalled()) {

                if (prefs.getString(

                        PREF_DL_DICT_DECISION,

                        DECISION_UNKNOWN

                    ) != DECISION_ACCEPTED

                ) {

                    prefs.edit().putString(PREF_DL_DICT_DECISION, DECISION_ACCEPTED).apply()

                }

                return

            }

            val d = prefs.getString(PREF_DL_DICT_DECISION, DECISION_UNKNOWN) ?: DECISION_UNKNOWN

            if (d == DECISION_DECLINED) return

            if (d == DECISION_ACCEPTED) return

            val ask =

                "I can download an offline dictionary to help me understand words without the internet. Do you want me to download it? Say approve or no."

            prefs.edit().putString(PREF_PENDING_APPROVAL, PENDING_DICT).apply()

            speak(ask, false)

            convoDb.logTurn("scout", ask)

            journalDb.add("Asked permission for offline dictionary on boot.")

        } catch (_: Exception) {

        }

    }

    private fun requestOrStartDownload(which: String) {

        when (which) {

            PENDING_ALL -> {

                val ask =

                    "I can download my offline brain pack. It works best on Wi-Fi. Do you approve? Say approve or no."

                prefs.edit().putString(PREF_PENDING_APPROVAL, PENDING_ALL).apply()

                respond(ask)

                return

            }

            PENDING_DICT -> {

                if (datasetStore.dictInstalled()) {

                    val out = "The offline dictionary is already installed."

                    respond(out)

                    return

                }

                val decision =

                    prefs.getString(PREF_DL_DICT_DECISION, DECISION_UNKNOWN) ?: DECISION_UNKNOWN

                if (decision == DECISION_ACCEPTED) {

                    startDownloadForPending(PENDING_DICT)

                    return

                }

                if (decision == DECISION_DECLINED) {

                    val out = "Okay. I won’t download that unless you ask."

                    respond(out)

                    return

                }

                if (!connectivityManager.isOnWifi()) {

                    val ask =

                        "I can download the offline dictionary, but I’m not on Wi-Fi. Do you still want me to download it? Say approve or no."

                    prefs.edit().putString(PREF_PENDING_APPROVAL, PENDING_DICT).apply()

                    speak(ask, true)

                    convoDb.logTurn("scout", ask)

                    finishThinking()

                    return

                }

                val ask =

                    "Do you want me to download an offline dictionary so I can be smarter offline? Say approve or no."

                prefs.edit().putString(PREF_PENDING_APPROVAL, PENDING_DICT).apply()

                speak(ask, true)

                convoDb.logTurn("scout", ask)

                finishThinking()

                return

            }

            else -> {

                val (prefKey, alreadyInstalled, askText) = when (which) {

                    PENDING_IDIOMS -> Triple(

                        PREF_DL_IDIOMS_DECISION,

                        datasetStore.idiomsInstalled(),

                        "Can I install an idioms file so I understand figurative phrases offline? Say approve or no."

                    )

                    PENDING_WORDNET -> Triple(

                        PREF_DL_WORDNET_DECISION,

                        datasetStore.wordnetInstalled(),

                        "Can I download WordNet to help make me smarter offline? Say approve or no."

                    )

                    PENDING_SENTIMENT -> Triple(

                        PREF_DL_SENTIMENT_DECISION,

                        datasetStore.sentimentInstalled(),

                        "Can I download tiny sentiment word lists to help me understand tone offline? Say approve or no."

                    )

                    PENDING_SLANG -> Triple(

                        PREF_DL_SLANG_DECISION,

                        datasetStore.slangInstalled(),

                        "Can I download a small slang list so I understand modern shortcuts offline? Say approve or no."

                    )

                    else -> Triple("", false, "")

                }

                if (alreadyInstalled) {

                    val out = "That is already installed."

                    respond(out)

                    return

                }

                val decision = prefs.getString(prefKey, DECISION_UNKNOWN) ?: DECISION_UNKNOWN

                if (decision == DECISION_ACCEPTED) {

                    startDownloadForPending(which)

                    return

                }

                if (decision == DECISION_DECLINED) {

                    val out = "Okay. I won’t download that unless you ask."

                    respond(out)

                    return

                }

                prefs.edit().putString(PREF_PENDING_APPROVAL, which).apply()

                speak(askText, true)

                convoDb.logTurn("scout", askText)

                finishThinking()

                return

            }

        }

    }

    private fun markDeclinedForPending(pending: String) {

        when (pending) {

            PENDING_DICT -> prefs.edit().putString(PREF_DL_DICT_DECISION, DECISION_DECLINED)

                .apply()

            PENDING_IDIOMS -> prefs.edit().putString(PREF_DL_IDIOMS_DECISION, DECISION_DECLINED)

                .apply()

            PENDING_WORDNET -> prefs.edit()

                .putString(PREF_DL_WORDNET_DECISION, DECISION_DECLINED)

                .apply()

            PENDING_SENTIMENT -> prefs.edit()

                .putString(PREF_DL_SENTIMENT_DECISION, DECISION_DECLINED).apply()

            PENDING_SLANG -> prefs.edit().putString(PREF_DL_SLANG_DECISION, DECISION_DECLINED)

                .apply()

            PENDING_ALL -> {

                prefs.edit()

                    .putString(PREF_DL_DICT_DECISION, DECISION_DECLINED)

                    .putString(PREF_DL_IDIOMS_DECISION, DECISION_DECLINED)

                    .putString(PREF_DL_WORDNET_DECISION, DECISION_DECLINED)

                    .putString(PREF_DL_SENTIMENT_DECISION, DECISION_DECLINED)

                    .putString(PREF_DL_SLANG_DECISION, DECISION_DECLINED)

                    .apply()

            }

        }

    }

    private fun startDownloadForPending(pending: String) {

        if (!downloadLock.compareAndSet(false, true)) {

            val out = "I’m already downloading."

            respond(out)

            return

        }

        if (isDownloading) {

            downloadLock.set(false)

            val out = "I’m already downloading."

            respond(out)

            return

        }

        when (pending) {

            PENDING_DICT -> prefs.edit().putString(PREF_DL_DICT_DECISION, DECISION_ACCEPTED)

                .apply()

            PENDING_IDIOMS -> prefs.edit().putString(PREF_DL_IDIOMS_DECISION, DECISION_ACCEPTED)

                .apply()

            PENDING_WORDNET -> prefs.edit()

                .putString(PREF_DL_WORDNET_DECISION, DECISION_ACCEPTED)

                .apply()

            PENDING_SENTIMENT -> prefs.edit()

                .putString(PREF_DL_SENTIMENT_DECISION, DECISION_ACCEPTED).apply()

            PENDING_SLANG -> prefs.edit().putString(PREF_DL_SLANG_DECISION, DECISION_ACCEPTED)

                .apply()

            PENDING_ALL -> {

                prefs.edit()

                    .putString(PREF_DL_DICT_DECISION, DECISION_ACCEPTED)

                    .putString(PREF_DL_IDIOMS_DECISION, DECISION_ACCEPTED)

                    .putString(PREF_DL_WORDNET_DECISION, DECISION_ACCEPTED)

                    .putString(PREF_DL_SENTIMENT_DECISION, DECISION_ACCEPTED)

                    .putString(PREF_DL_SLANG_DECISION, DECISION_ACCEPTED)

                    .apply()

            }

        }

        val needsInternet = when (pending) {

            PENDING_IDIOMS -> !datasetStore.assetExists(assets, "idioms.json")

            else -> true

        }

        if (needsInternet && !connectivityManager.hasValidatedInternet()) {

            isDownloading = false

            faceView.setDownloading(false)

            wantListening = true

            downloadLock.set(false)

            val out =

                "I’m not connected to working internet yet. I opened internet settings. Turn on Wi-Fi, then say download all."

            speak(out, true)

            convoDb.logTurn("scout", out)

            journalDb.add("Download blocked: not validated ($pending).")

            connectivityManager.openInternetPanel()

            finishThinking()

            return

        }

        isDownloading = true

        faceView.setDownloading(true)

        finishThinking()

        wantListening = false

        stopListeningSafe()

        val outStart = when (pending) {

            PENDING_DICT -> "Okay. Downloading the offline dictionary now."

            PENDING_IDIOMS -> "Okay. Installing idioms now."

            PENDING_WORDNET -> "Okay. Downloading WordNet now."

            PENDING_SENTIMENT -> "Okay. Downloading sentiment lists now."

            PENDING_SLANG -> "Okay. Downloading slang now."

            else -> "Okay. Downloading my offline brain pack now."

        }

        speak(outStart, true)

        convoDb.logTurn("scout", outStart)

        val downloadStartedAt = System.currentTimeMillis()

        Thread {

            val summary = StringBuilder()

            fun appendSummary(msg: String) {

                summary.append(msg).append(" ")

            }

            fun fail(msg: String, e: Exception) {

                appendSummary(msg)

                Log.e("ScoutDownloads", msg, e)

                journalDb.add("$msg (${e.javaClass.simpleName}: ${e.message})")

            }

            try {

                when (pending) {

                    PENDING_DICT -> {

                        installDictionaryTier1()

                        appendSummary("Dictionary installed.")

                    }

                    PENDING_IDIOMS -> {

                        installIdiomsTier2PreferAssets()

                        appendSummary("Idioms installed.")

                    }

                    PENDING_WORDNET -> {

                        installWordNetTier3()

                        appendSummary("WordNet installed.")

                    }

                    PENDING_SENTIMENT -> {

                        installSentimentExtras()

                        appendSummary("Sentiment installed.")

                    }

                    PENDING_SLANG -> {

                        installSlangExtras()

                        appendSummary("Slang installed.")

                    }

                    PENDING_ALL -> {

                        if (!datasetStore.dictInstalled()) {

                            try {

                                installDictionaryTier1()

                                appendSummary("Dictionary installed.")

                            } catch (e: Exception) {

                                fail("Dictionary failed.", e)

                            }

                        } else appendSummary("Dictionary already installed.")

                        val freeBytes =

                            connectivityManager.getAvailableBytes(filesDir.absolutePath)

                        val storageOk = freeBytes >= (1024L * 1024L * 1024L)

                        val wifi = connectivityManager.isOnWifi()

                        if (!datasetStore.idiomsInstalled()) {

                            try {

                                installIdiomsTier2PreferAssets()

                                appendSummary("Idioms installed.")

                            } catch (e: Exception) {

                                fail("Idioms failed.", e)

                            }

                        } else appendSummary("Idioms already installed.")

                        if (wifi && storageOk) {

                            if (!datasetStore.wordnetInstalled()) {

                                try {

                                    installWordNetTier3()

                                    appendSummary("WordNet installed.")

                                } catch (e: Exception) {

                                    fail("WordNet failed.", e)

                                }

                            } else appendSummary("WordNet already installed.")

                            if (!datasetStore.sentimentInstalled()) {

                                try {

                                    installSentimentExtras()

                                    appendSummary("Sentiment installed.")

                                } catch (e: Exception) {

                                    fail("Sentiment failed.", e)

                                }

                            } else appendSummary("Sentiment already installed.")

                            if (!datasetStore.slangInstalled()) {

                                try {

                                    installSlangExtras()

                                    appendSummary("Slang installed.")

                                } catch (e: Exception) {

                                    fail("Slang failed.", e)

                                }

                            } else appendSummary("Slang already installed.")

                        } else {

                            journalDb.add("Brain pack skipped extra downloads (wifi=$wifi storageOk=$storageOk).")

                            appendSummary("Skipped extra downloads.")

                        }

                    }

                }

                runOnUiThread {

                    isDownloading = false

                    faceView.setDownloading(false)

                    wantListening = true

                    downloadLock.set(false)

                    val outDone = summary.toString().trim().ifBlank { "Download complete." }

                    speak(outDone, true)

                    convoDb.logTurn("scout", outDone)

                    journalDb.add("Downloads result: $pending -> $outDone")

                }

            } catch (e: Exception) {

                Log.e("ScoutDownloads", "download failed", e)

                runOnUiThread {

                    isDownloading = false

                    faceView.setDownloading(false)

                    wantListening = true

                    downloadLock.set(false)

                    val outFail = "I had trouble downloading that."

                    speak(outFail, true)

                    convoDb.logTurn("scout", outFail)

                    journalDb.add("Download failed: $pending (${e.javaClass.simpleName}: ${e.message})")

                }

            } finally {

                runOnUiThread {

                    if (isDownloading) {

                        isDownloading = false

                        faceView.setDownloading(false)

                        wantListening = true

                        downloadLock.set(false)

                        journalDb.add("Download safety reset triggered.")

                        scheduleListenRestart(immediate = false)

                    }

                }

            }

        }.start()

    }

    // =======================

    // INSTALLERS

    // =======================

    private fun installDictionaryTier1() {

        if (!connectivityManager.hasValidatedInternet()) throw RuntimeException("No internet")

        datasetStore.ensureDir(datasetStore.dictDir())

        val zipFile = File(datasetStore.dictDir(), "en.db.zip")

        downloadToFile(URL_WIKDICT_ZIP, zipFile)

        unzipSelect(zipFile, datasetStore.dictDir()) { entryName ->

            entryName.replace("\\", "/").substringAfterLast("/") == "en.db"

        }

        if (!datasetStore.dictDbFile()

                .exists()

        ) throw RuntimeException("Dictionary db missing after unzip")

        datasetStore.dictMarker().writeText("installed_at=${System.currentTimeMillis()}")

    }

    private fun installIdiomsTier2PreferAssets() {

        datasetStore.ensureDir(datasetStore.idiomsDir())

        val assetName = "idioms.json"

        if (datasetStore.assetExists(assets, assetName)) {

            copyAssetToFile(assetName, datasetStore.idiomsFile())

            if (!datasetStore.idiomsFile().exists() || datasetStore.idiomsFile()

                    .length() < 50L

            ) throw RuntimeException("Idioms asset copy failed")

            datasetStore.idiomsMarker()

                .writeText("installed_at=${System.currentTimeMillis()};source=assets")

            return

        }

        if (!connectivityManager.hasValidatedInternet()) throw RuntimeException("No internet")

        var lastErr: Exception? = null

        for (u in URL_IDIOMS_JSON_FALLBACKS) {

            try {

                downloadToFile(u, datasetStore.idiomsFile())

                if (!datasetStore.idiomsFile().exists() || datasetStore.idiomsFile()

                        .length() < 50L

                ) throw RuntimeException("Idioms file too small")

                datasetStore.idiomsMarker()

                    .writeText("installed_at=${System.currentTimeMillis()};source=web")

                return

            } catch (e: Exception) {

                lastErr = e

                journalDb.add("Idioms URL failed: $u (${e.javaClass.simpleName}: ${e.message})")

            }

        }

        throw RuntimeException("Idioms download failed: ${lastErr?.message ?: "unknown"}")

    }

    private fun installWordNetTier3() {

        if (!connectivityManager.hasValidatedInternet()) throw RuntimeException("No internet")

        datasetStore.ensureDir(datasetStore.wordnetDir())

        val zipFile = File(datasetStore.wordnetDir(), "wordnet.zip")

        downloadToFile(URL_WORDNET_ZIP, zipFile)

        try {

            datasetStore.wordnetDir().listFiles()

                ?.forEach { f -> if (f.isFile && f.name.endsWith(".json")) f.delete() }

        } catch (_: Exception) {

        }

        unzipSelect(zipFile, datasetStore.wordnetDir()) { entryName ->

            val safe = entryName.replace("\\", "/").substringAfterLast("/")

            safe.endsWith(".json", ignoreCase = true)

        }

        val hasJson = datasetStore.wordnetDir().listFiles()

            ?.any { it.isFile && it.name.endsWith(".json") } == true

        if (!hasJson) throw RuntimeException("WordNet json missing after unzip")

        datasetStore.wordnetMarker().writeText("installed_at=${System.currentTimeMillis()}")

    }

    private fun installSentimentExtras() {

        if (!connectivityManager.hasValidatedInternet()) throw RuntimeException("No internet")

        datasetStore.ensureDir(datasetStore.sentimentDir())

        downloadToFile(URL_POS_WORDS, datasetStore.posWordsFile())

        downloadToFile(URL_NEG_WORDS, datasetStore.negWordsFile())

        if (!datasetStore.posWordsFile().exists() || !datasetStore.negWordsFile()

                .exists()

        ) throw RuntimeException("Sentiment files missing")

        datasetStore.sentimentMarker().writeText("installed_at=${System.currentTimeMillis()}")

    }

    private fun installSlangExtras() {

        if (!connectivityManager.hasValidatedInternet()) throw RuntimeException("No internet")

        datasetStore.ensureDir(datasetStore.slangDir())

        var lastErr: Exception? = null

        for (u in URL_SLANG_JSON_FALLBACKS) {

            try {

                downloadToFile(u, datasetStore.slangFile())

                if (!datasetStore.slangFile().exists() || datasetStore.slangFile()

                        .length() < 50L

                ) throw RuntimeException("Slang file too small")

                datasetStore.slangMarker()

                    .writeText("installed_at=${System.currentTimeMillis()}")

                return

            } catch (e: Exception) {

                lastErr = e

                journalDb.add("Slang URL failed: $u (${e.javaClass.simpleName}: ${e.message})")

            }

        }

        throw RuntimeException("Slang download failed: ${lastErr?.message ?: "unknown"}")

    }

    private fun copyAssetToFile(assetName: String, outFile: File) {

        try {

            if (outFile.exists()) outFile.delete()

        } catch (_: Exception) {

        }

        assets.open(assetName).use { input ->

            FileOutputStream(outFile).use { output ->

                input.copyTo(output)

                output.flush()

            }

        }

    }

    // =======================

    // DOWNLOADER

    // =======================

    private fun downloadToFile(urlStr: String, outFile: File) {

        val tmp = File(outFile.parentFile, outFile.name + ".tmp")

        if (tmp.exists()) try {

            tmp.delete()

        } catch (_: Exception) {

        }

        val url = URL(urlStr)

        val conn = (url.openConnection() as HttpURLConnection).apply {

            requestMethod = "GET"

            instanceFollowRedirects = true

            connectTimeout = 25_000

            readTimeout = 300_000

            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ScoutFace")

            setRequestProperty("Accept", "*/*")

            setRequestProperty("Connection", "close")

        }

        conn.connect()

        val code = conn.responseCode

        if (code !in 200..299) {

            conn.disconnect()

            throw RuntimeException("HTTP $code")

        }

        BufferedInputStream(conn.inputStream).use { input ->

            FileOutputStream(tmp).use { output ->

                val buf = ByteArray(64 * 1024)

                while (true) {

                    val n = input.read(buf)

                    if (n <= 0) break

                    output.write(buf, 0, n)

                }

                output.flush()

            }

        }

        conn.disconnect()

        if (outFile.exists()) try {

            outFile.delete()

        } catch (_: Exception) {

        }

        if (!tmp.renameTo(outFile)) {

            FileOutputStream(outFile).use { out ->

                tmp.inputStream().use { inp -> inp.copyTo(out) }

            }

            try {

                tmp.delete()

            } catch (_: Exception) {

            }

        }

    }

    private fun unzipSelect(zipFile: File, outDir: File, keep: (String) -> Boolean) {

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->

            while (true) {

                val entry = zis.nextEntry ?: break

                val name = entry.name ?: run { zis.closeEntry(); continue }

                if (entry.isDirectory) {

                    zis.closeEntry(); continue

                }

                val safeName = name.replace("\\", "/").substringAfterLast("/")

                if (safeName.isBlank()) {

                    zis.closeEntry(); continue

                }

                if (!keep(name)) {

                    zis.closeEntry(); continue

                }

                val out = File(outDir, safeName)

                FileOutputStream(out).use { fos ->

                    val buf = ByteArray(64 * 1024)

                    while (true) {

                        val n = zis.read(buf)

                        if (n <= 0) break

                        fos.write(buf, 0, n)

                    }

                    fos.flush()

                }

                zis.closeEntry()

            }

        }

    }

    private fun resetDownloadDecisions(deleteFiles: Boolean) {

        prefs.edit()

            .putString(PREF_DL_DICT_DECISION, DECISION_UNKNOWN)

            .putString(PREF_DL_IDIOMS_DECISION, DECISION_UNKNOWN)

            .putString(PREF_DL_WORDNET_DECISION, DECISION_UNKNOWN)

            .putString(PREF_DL_SENTIMENT_DECISION, DECISION_UNKNOWN)

            .putString(PREF_DL_SLANG_DECISION, DECISION_UNKNOWN)

            .putString(PREF_PENDING_APPROVAL, PENDING_NONE)

            .apply()

        if (deleteFiles) {

            try {

                datasetStore.deleteAllDatasets()

            } catch (_: Exception) {

            }

        }

    }

    // =======================

    // SHUTDOWN

    // =======================

    override fun onDestroy() {

        shutdownSystems()

        super.onDestroy()

    }

}

