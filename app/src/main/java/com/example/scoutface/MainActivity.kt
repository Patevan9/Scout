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

import com.example.scoutface.brain.CalendarDateParser

import com.example.scoutface.brain.FactKey

import com.example.scoutface.brain.ScoutBootStatus

import com.example.scoutface.brain.AwarenessResolver
import com.example.scoutface.brain.AwarenessState
import com.example.scoutface.brain.ScoutConnectivityManager

import com.example.scoutface.brain.ScoutIntentRouter
import com.example.scoutface.brain.ScoutMemoryGate

import com.example.scoutface.brain.TeachExtractor
import com.example.scoutface.brain.ScoutEntityResolver
import com.example.scoutface.brain.CalendarFollowupMatcher
import com.example.scoutface.brain.CalendarFollowupTopic
import com.example.scoutface.brain.PendingCalendarFollowup
import com.example.scoutface.brain.DateOwnerMatch
import com.example.scoutface.brain.ScoutFactExtractor

import com.example.scoutface.brain.ScoutPromptBuilder

import com.example.scoutface.brain.ScoutGeminiManager

import com.example.scoutface.brain.ScoutWeatherManager

import com.example.scoutface.brain.ScoutPresenceDecider

import com.example.scoutface.brain.FuzzyNameMatcher
import com.example.scoutface.brain.ScoutSpeechAvailabilityMonitor

import com.example.scoutface.brain.CompanionSignals
import com.example.scoutface.brain.CourtesyIntent
import com.example.scoutface.brain.MomentCandidate
import com.example.scoutface.brain.MomentCategory
import com.example.scoutface.brain.ScoutArrivalLatch
import com.example.scoutface.brain.ScoutCompanionMemoryEligibility
import com.example.scoutface.brain.ScoutCompanionMomentsEngine
import com.example.scoutface.brain.ScoutCourtesyMatcher
import com.example.scoutface.brain.ScoutMemoryPhraser
import com.example.scoutface.brain.ScoutPresenceStreakTracker
import com.example.scoutface.brain.ScoutReturnGreetingGate
import com.example.scoutface.brain.ScoutStaleResultGuard
import com.example.scoutface.brain.StaleFact

import com.example.scoutface.brain.TextNormalizer

import com.example.scoutface.brain.ScoutMicRestartTiming
import com.example.scoutface.brain.ScoutConversationState
import com.example.scoutface.brain.ConversationEndReason
import com.example.scoutface.brain.ScoutSpeechLanguage
import com.example.scoutface.brain.ScoutTimeOfDay
import com.example.scoutface.brain.ScoutBusyBrainState
import com.example.scoutface.brain.BusyBrainDiscardReason
import com.example.scoutface.brain.ScoutBusyBrainPolicy
import com.example.scoutface.brain.ScoutBusyBrainDelivery
import com.example.scoutface.brain.ScoutPendingAnswerGate
import com.example.scoutface.brain.ScoutGreetingIdentity
import com.example.scoutface.brain.ScoutBeepMuteGuard
import com.example.scoutface.brain.ScoutVisionGate
import com.example.scoutface.brain.ScoutVoiceSelector
import com.example.scoutface.brain.VoiceCandidate

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

import java.util.concurrent.atomic.AtomicBoolean

import java.util.concurrent.atomic.AtomicInteger

import android.graphics.Bitmap

import android.graphics.Matrix

import kotlin.math.abs

enum class IntentType {

    TIME, DATE, LANGUAGE, TIME_OF_DAY, CONNECTIVITY,

    GO_ONLINE, GO_OFFLINE,

    EXPORT_BRAIN,

    VISION,

    GREET, HOW_ARE_YOU, GOODBYE, STOP_LISTENING,

    PRAISE, AFFECTION,

    IDENTITY,

    RECALL_FACT,

    ASK_MY_NAME, ASK_SCOUT_NAME,

    ASK_WIFE_NAME, ASK_SON_NAME, ASK_DOG_NAME,

    TEACH_WIFE_NAME, TEACH_SON_NAME, TEACH_DOG_NAME, TEACH_MY_NAME,

    FAMILY_NAMES,

    OPEN_CALENDAR_SETTINGS,

    WEATHER,

    CALENDAR,

    WHOSE_DATE_EVENT,

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

    // True only between onBeginningOfSpeech() and the recognizer session ending --
    // unlike isListening (true almost continuously while idle, since sessions
    // just cycle), this reflects whether a user utterance is actually being
    // captured right now. Used to keep presence-initiated speech from cutting
    // someone off mid-sentence.
    @Volatile

    private var isCapturingSpeech = false

    @Volatile

    private var isThinking = false

    // =======================

    // MIC DISCIPLINE

    // =======================

    private var lastSpeechDoneMs = 0L
    private var lastScoutResponseMs = 0L
    private var lastScoutUtteranceNormalized = ""
    private val CONVO_WINDOW_MS = 30_000L

    // Better Conversation State -- Phase 1. Wraps CONVO_WINDOW_MS/
    // PRESENCE_REPLY_WINDOW_MS (both unchanged, still the real timing source)
    // with an explicit "closed on purpose" signal neither timer alone can
    // express -- see ScoutConversationState's own doc comment. RAM-only,
    // reset to a fresh instance on every MainActivity (re)creation, same as
    // every other field on this screen.
    private val conversationState = ScoutConversationState()

    // Busy-Brain -- PR 1 (foundation/correctness only). Tracks whether a real
    // Gemini/TinyLlama generation is currently pending, independent of
    // isThinking -- see ScoutBusyBrainState's own doc comment. PR 1 does not
    // yet change microphone/isThinking timing; this exists so a second
    // AI-style question can never start a second generation or silently
    // invalidate the one already in flight, and so an explicitly-closed
    // conversation's pending answer is never spoken once it arrives.
    private val busyBrainState = ScoutBusyBrainState()

    // Busy-Brain -- PR 2. Holds a real Gemini/TinyLlama answer (or final
    // failure message) that arrived while Scout was speaking or handling
    // another accepted request -- delivered once Scout is free again (see
    // the TTS onDone() drain check), instead of flushing over whatever was
    // already being said.
    private var pendingAiAnswer: String? = null

    // pendingAiAnswer lifecycle fix. When the answer above was actually
    // queued (deliverAiResult() setting both together) -- NOT when
    // generation began. Read by ScoutPendingAnswerGate.decide() against
    // PENDING_AI_ANSWER_MAX_AGE_MS. Always cleared together with
    // pendingAiAnswer via clearPendingAiAnswer() below, at every clear site,
    // so the two fields can never desync.
    private var pendingAiAnswerQueuedAtMs: Long = 0L

    // pendingAiAnswer lifecycle fix. The one place pendingAiAnswer is ever
    // cleared -- delivery, expiry, supersede-by-a-new-generative-request, or
    // an explicit conversation close all route through this, so the answer
    // and its queued timestamp can never drift apart (e.g. a stale
    // timestamp surviving a clear, or vice versa).
    private fun clearPendingAiAnswer() {
        pendingAiAnswer = null
        pendingAiAnswerQueuedAtMs = 0L
    }

    // TTS lifecycle diagnostics (instrumentation only -- see DiagLog's
    // TtsDispatchSource/logTts*() doc comments). ttsDispatchCounter is a
    // small per-utterance counter, also passed to tts.speak() as the
    // Android utteranceId (replacing the previous hardcoded constant
    // "scout", which every utterance shared and which onStart()/onDone()/
    // onError() never actually read) -- confirmed via a repo-wide search
    // that no code compares an Android TTS utteranceId against "scout" or
    // any other literal, so this is inert for existing behavior; it only
    // makes onStart()/onDone()/onError() able to report which dispatch they
    // belong to.
    //
    // AtomicInteger, not a plain Int: speak() is not guaranteed to be
    // entered only from the main thread -- the pendingAiAnswer drain inside
    // onDone()/onError() calls respond()/speak() synchronously from
    // whichever thread the TTS engine delivered that callback on (see
    // ttsDispatchSources' own doc comment below on why that's not
    // guaranteed to be the main thread), while every other call site enters
    // speak() from the main thread. A plain "++ttsDispatchCounter" is a
    // non-atomic read-modify-write; two speak() calls racing from different
    // threads could read the same value and mint a duplicate dispatch id,
    // silently breaking the one guarantee this instrumentation exists to
    // provide. incrementAndGet() is atomic, so every dispatch id stays
    // unique even under back-to-back/cross-thread delivery.
    //
    // ttsDispatchSources maps each dispatch's own id to the source it was
    // requested with, so a callback correlates the source to the SAME
    // dispatch its utteranceId resolved to -- not to whichever dispatch
    // happens to be "most recent" at the moment the callback fires. Review
    // finding: an earlier version of this instrumentation used a single
    // mutable lastTtsDispatchSource field for this, which is racy in
    // exactly the back-to-back/re-entrant scenario this instrumentation
    // exists to diagnose -- speak() stamps that field synchronously at
    // dispatch time, before the 240-650ms delay to the actual tts.speak()
    // call, so a newer dispatch's speak() could overwrite it before an
    // older dispatch's onStart()/onDone()/onError() (timed entirely by the
    // TTS engine, not by this Activity) has fired, mislabeling the older
    // dispatch's own event with the newer dispatch's source. Keyed storage
    // makes that impossible: each id's source is fixed at the moment that
    // id is created and never touched by any other dispatch.
    //
    // lastTtsDispatchId remains a same-Activity fallback for id only, used
    // solely when the engine doesn't echo a parseable utteranceId at all
    // (some legacy engines omit it) -- id in that fallback case is still
    // "best guess, most recent," same caveat as before; every case where
    // the engine does echo utteranceId (the overwhelming majority) resolves
    // both id and source with no such caveat.
    // ConcurrentHashMap, not a plain map: Android's UtteranceProgressListener
    // callbacks (onStart()/onDone()/onError() below) aren't guaranteed to run
    // on the main thread -- they're typically delivered on the TTS engine's
    // own synthesis/binder thread -- while speak() writes this map from
    // whichever thread it's called from (normally main). A plain mutableMapOf
    // could throw or corrupt under that concurrent access; nothing here
    // catches such an exception the way DiagLog's own safe() wrapper does for
    // its logging calls. This is self-contained to the new diagnostics (this
    // map has no other reader/writer), so it doesn't touch any existing
    // production logic or timing.
    private val ttsDispatchCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private var lastTtsDispatchId = 0
    private val ttsDispatchSources = java.util.concurrent.ConcurrentHashMap<Int, DiagLog.TtsDispatchSource>()

    // Busy-Brain PR 2 status-feedback strings. Spoken via
    // respond(isStatusOnly = true) -- see that parameter's doc comment for
    // exactly what "status-only" excludes.
    private val BUSY_BRAIN_FILLERS = listOf(
        "Let me think about that.",
        "Give me a moment.",
        "Let me think.",
        "Let me work that out.",
        "I'm thinking about that."
    )
    private val BUSY_BRAIN_STILL_THINKING = "I'm still thinking about your last question."
    private val BUSY_BRAIN_DEFERRED = "I'll get to that once I've finished my last thought."

    // Busy-Brain polish. How long a generation must stay pending before
    // Scout actually says a thinking phrase out loud -- re-checked against
    // busyBrainState.isPending at fire time in scheduleBusyBrainFiller(),
    // not assumed. A fast answer (typical for Gemini) never triggers any
    // filler at all. Named separately so it can be tuned from real-device
    // testing without touching the scheduling logic itself.
    private val BUSY_BRAIN_FILLER_DELAY_MS = 2000L

    // pendingAiAnswer lifecycle fix. How long a queued answer stays eligible
    // to deliver once Scout is free again, measured from when it was
    // actually queued (deliverAiResult() setting pendingAiAnswerQueuedAtMs),
    // not from when generation began. Deliberately a standalone constant,
    // not derived from PRESENCE_REPLY_WINDOW_MS below -- a different concept
    // (how long Scout's own proactive remark stays wake-word-free
    // follow-up-able) that happens to share a similar magnitude. See
    // ScoutPendingAnswerGate.decide().
    private val PENDING_AI_ANSWER_MAX_AGE_MS = 30_000L

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

    // Guards maybeStartListening() against restarting the mic while MainActivity isn't
    // actually in the foreground (e.g. while SettingsActivity is on top). onPause() cancels
    // the recognizer and the watchdog, but a scheduleListenRestart() Handler callback queued
    // just before the pause can still fire later and call maybeStartListening() regardless --
    // that queued callback has no other way of knowing the activity was backgrounded since.
    private var isForeground = true

    private var pendingListenStart = false

    private var bootFinishedSpeaking = false

    // True after Scout has already told the user his offline brain is loading. We only
    // say "warming up" once per session — the user doesn't need a reminder every question.
    private var warmingUpSaidThisSession = false

    // Generation/owner token and executor both moved to ScoutLlamaController -- a
    // process-wide singleton, not a MainActivity instance field. A per-instance
    // executor meant a configuration-change recreation either had to permanently
    // shut it down (leaking whatever generation was still in flight, or blocking
    // teardown waiting for it) or leave it running forever with no way to reclaim
    // its thread (ExecutorService.shutdown() is required for that, and skipping it
    // was exactly the point). ScoutLlamaController.registerOwner() (called from
    // onCreate()) and .newGeneration() (called per-question) both bump the same
    // token; ScoutLlamaController.generateAsync() only delivers a result while
    // that token is still current, so a stale generation from a since-destroyed
    // instance can never touch this instance's (or a prior instance's) UI.

    private val BOOT_LISTEN_EXTRA_DELAY_MS = 250L

    // ── A32 startup stabilization (staggered init) ──────────────────────────
    // A controlled stabilization measure, not a final architecture -- see
    // requestCameraStartup()/requestSpeechStartup(). On a real Galaxy A32, camera
    // + ML Kit + SpeechRecognizer all starting in the same instant collided with a
    // one-time, multi-second ART bytecode-verification stall for Google Play
    // Services' ML Kit classes (11.1s and 3.5s verification events observed in a
    // real capture), triggering system-wide low-memory pressure severe enough
    // that Android killed Scout as a side effect of Google Play Services' own
    // persistent process dying -- not a Scout crash. These are TEST/stabilization
    // values; tune from the ScoutStartupTiming log once real A32 timing data
    // comes back from a clean run.
    private val CAMERA_STARTUP_STAGGER_MS = 3_000L
    private val SPEECH_STARTUP_STAGGER_MS = 4_500L
    private val STARTUP_SETTLE_MS = 6_000L

    // Idempotency guards for the camera/speech startup stagger. checkPermissionsAndStart(),
    // the permission-result callback, and resumeSystems() can all reach startup logic --
    // *Scheduled flags reset to false the instant the delayed callback runs (whether it
    // actually starts anything or bails out), so they only ever prevent two pending
    // callbacks stacking, never a legitimate later restart. *EverStarted flags are one-way
    // (false -> true, never back) and mean "the initial stagger has already happened once" --
    // once true, further requests behave exactly like the pre-existing unstaggered code,
    // since the stagger only exists to protect the cold-start collision, not steady-state
    // camera/mic restarts (e.g. returning from Settings).
    private var cameraStartupScheduled = false
    @Volatile private var cameraEverStarted = false
    private var speechStartupScheduled = false
    @Volatile private var speechEverStarted = false

    // True STARTUP_SETTLE_MS after the camera actually starts -- gates face embedding
    // (see the embedExecutor.submit condition) so embedding never runs during the startup
    // stagger window even if a face is detected in the very first analyzed frame.
    @Volatile private var startupSettled = false

    // Wall-clock startup timing diagnostics. Logged via logStartupTiming(), tag
    // "ScoutStartupTiming" -- lets a real-device logcat capture pinpoint exactly which
    // init step is blocking/starving the device instead of requiring manual reconstruction
    // from unrelated system log lines, as the previous crash investigation needed.
    private var startupTimingBaseMs = 0L

    // Dedupes logListenAttempt() calls so a tight restart loop can't flood the bounded
    // diagnostic report with hundreds of repeats of the same reason -- only actual
    // transitions are recorded, matching the logPresenceDebug()/logIdleDebug() dedup
    // pattern already used elsewhere in this file.
    private var lastListenAttemptReason: DiagLog.ListenAttemptReason? = null

    private val TRY_MUTE_BEEP = true

    private var savedSystemVolume: Int? = null

    private var savedNotificationVolume: Int? = null

    // Extends TRY_MUTE_BEEP to cover stopListeningSafe()'s cancel() call as
    // well as maybeStartListening()'s startListening() call -- see
    // ScoutBeepMuteGuard's own doc comment for why a simple null-check on
    // the two fields above is no longer enough once both sides can mute.
    private val beepMuteGuard = ScoutBeepMuteGuard()

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

    // Latched (not overwritten every frame) so a rare second-face-arrival
    // transition survives until the next throttled maybeMakeCompanionMoment()
    // check actually consumes it, instead of being clobbered by the very next
    // camera frame -- see the face-detection callback and
    // consumeSecondFaceArrivalSignal(). 0L means "nothing pending."
    private var secondFaceArrivalPendingSinceMs: Long = 0L

    // Bounds how stale a latched arrival can be before it's discarded rather
    // than acted on -- if every check happened to be blocked (speaking,
    // thinking, wrong mode) for longer than this, the "someone just joined"
    // framing would no longer be honest by the time it's finally spoken.
    private val SECOND_FACE_ARRIVAL_MAX_PENDING_MS = 5L * 60L * 1_000L // 5 min

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

    // Calendar Follow-up -- single, mutually-exclusive, RAM-only pending state
    // (see PendingCalendarFollowup's own doc comment). Resolved or dropped on
    // the very next user utterance in handleQuery(), unconditionally, before
    // the busyBrainState.isPending gate.
    @Volatile

    private var pendingCalendarFollowup: PendingCalendarFollowup? = null

    @Volatile

    private var lastAnalysisMs = 0L

    private val ANALYSIS_MIN_INTERVAL_MS = 150L

    // Scene labeling ("dog," "chair," "person") changes far more slowly than face
    // position, which drives Scout's gaze and needs the full ~7fps analysis cadence
    // above. Throttled separately so the labeler doesn't run on every accepted frame --
    // only face detection does. 1.5s is a reasonable starting interval, not something
    // that needed real-device tuning the way the vision-gating thresholds did.
    @Volatile
    private var lastLabelMs = 0L
    private val LABEL_MIN_INTERVAL_MS = 1_500L

    // Gaze hold to prevent snap-back on brief face detector drops

    @Volatile

    private var lastGoodGazeX = 0f

    @Volatile

    private var lastGoodGazeY = 0f

    @Volatile

    private var lastGoodFaceSeenMs = 0L

    private val FACE_LOST_HOLD_MS = 650L

    // Vision-led direct-address gate for the "say my name first" listening
    // reminder -- replaces a stale "any face within the last 3s" test that
    // couldn't distinguish someone actually facing Scout from a side
    // conversation or a person briefly crossing the room. Requires a
    // *sustained* qualifying face, not a single frame, so a passing glance or a
    // face mid-turn doesn't count.
    // TEST VALUES -- deliberately conservative for the first smoke test (a false
    // interruption is worse than a missed reminder). Tune from the diagnostic
    // values logged below once Diana's real-world testing gives us evidence.
    /** headEulerAngleY (yaw) tolerance -- how far the head can turn from facing
     *  the camera and still count as "oriented toward Scout." */
    private val LISTENING_REMINDER_MAX_YAW_DEGREES = 18f

    /** Face box height as a fraction of frame height -- filters out someone
     *  distant/crossing the room rather than actually addressing Scout. */
    private val LISTENING_REMINDER_MIN_FACE_HEIGHT_FRACTION = 0.18f

    /** How far off-center (normalized, same -1..1 scale as the existing gaze
     *  dx/dy) the face box can be and still qualify. */
    private val LISTENING_REMINDER_MAX_OFFSET = 0.40f

    /** How long the qualifying state above must hold continuously before it
     *  counts as sustained visual attention, not a passing glance. */
    private val DIRECT_ADDRESS_SUSTAIN_MS = 1_500L

    /** TEST VALUE. How recently the vision pipeline must have actually processed
     *  a frame for the streak to be trusted at reminder-decision time -- if
     *  frame processing has stalled, the last-known streak state could be stale
     *  rather than continuously reconfirmed. */
    private val VISION_FRESHNESS_MS = 1_000L

    @Volatile
    private var directAddressStreakStartMs = 0L // 0 = not currently facing Scout

    // Most recently measured values, cached for reminder-decision diagnostics --
    // the speech-recognition callback that decides whether to speak the reminder
    // runs separately from the vision callback that measures these.
    @Volatile private var lastYawDegrees = 0f
    @Volatile private var lastFaceHeightFraction = 0f
    @Volatile private var lastCenterOffset = 0f

    @Volatile

    private var faceAppearanceMs = 0L

    @Volatile

    private var greetedThisSession = false

    // Separate, gap-tolerant presence measurement for the idle-silence
    // acknowledgment only -- does not feed the arrival greeting above, which
    // keeps its existing strict faceAppearanceMs behavior unchanged. A brief
    // missed frame or a person glancing away shouldn't restart a 75-minute
    // timer; a genuine departure should.
    /** How long a face can go undetected before the presence streak below is
     *  considered broken (not just a missed frame). Named and tunable. */
    private val PRESENCE_GAP_GRACE_MS = 2L * 60L * 1_000L // 2 min

    @Volatile
    private var presencePresentSinceMs = 0L // when the current tolerant streak began

    @Volatile
    private var presenceLastSeenMs = 0L // last time a face was actually seen

    // Set right before speaking a presence-initiated remark; consumed once by the
    // TTS onDone callback to open the presence reply window below.
    @Volatile
    private var lastUtteranceWasPresenceRemark = false

    /** How long after a presence-initiated remark someone can reply naturally
     *  without saying Scout's name first. Starts when TTS finishes speaking, not
     *  when it begins. */
    private val PRESENCE_REPLY_WINDOW_MS = 40_000L

    @Volatile
    private var presenceReplyWindowUntilMs = 0L

    // =======================
    // PRESENCE LAYER -- PROACTIVE RETURN GREETING (Layer 1)
    //
    // Genuine-absence + stabilized-return tracking, driven by face presence
    // (reusing presenceLastSeenMs above, already updated every face-visible
    // frame -- no separate "last seen" timestamp needed). Deliberately separate
    // from both faceAppearanceMs (the once-per-launch first-contact greeting,
    // left untouched) and presencePresentSinceMs (the idle-silence streak,
    // which measures the opposite thing -- how long someone's been *present*).
    // =======================

    /** How long a face must be undetected before a gap is even acknowledged as a
     *  candidate absence -- absorbs single missed frames or a brief head-turn. */
    private val CAMERA_GAP_TOLERANCE_MS = 15_000L // 15 sec

    /** How much longer, past the tolerance above, an absence must continue before
     *  it's confirmed genuine -- worth a "welcome back" on return.
     *
     *  TEMPORARY SMOKE-TEST VALUE -- restore to 10L * 60L * 1_000L (~10 min)
     *  once A32 testing confirms the behavior. */
    private val MIN_GENUINE_ABSENCE_MS = 60_000L // ~1 min (TEMP, was ~10 min)

    /** How long a face must be continuously visible again, after a genuine
     *  absence, before Scout actually speaks. Its own named constant even though
     *  it starts at the same value as GREET_STABILIZE_MS, so the two can be
     *  tuned independently later. */
    private val RETURN_STABILIZATION_MS = 3_000L // 3 sec

    private var candidateAbsenceLogged = false  // avoids re-logging "absence started" every frame
    private var genuineAbsenceMarked = false    // true once the current absence crossed MIN_GENUINE_ABSENCE_MS
    private var returnStabilizingSinceMs = 0L   // 0 = not currently in a post-absence stabilization window

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

    // Verified by a human actually listening to the voice on real Fold 7 /
    // A32 hardware (see the TTS-voice diagnostic investigation) -- never
    // inferred from the name string, since Android's public Voice API has
    // no gender field. Ordered so a future verified name for another
    // device can be appended without disturbing this one.
    private val PREFERRED_VOICE_NAMES = listOf("en-us-x-iom-local")

    private val TARGET_TTS_ENGINE = "com.google.android.tts"

    // Guards setupTts()'s two-stage engine-preference fallback (see
    // onInit()) so the second (device-default) TextToSpeech construction
    // is only ever attempted once, not repeatedly if it also fails.
    private var awaitingDeviceDefaultTts = false

    private var speechRecognizer: SpeechRecognizer? = null

    // See setupSpeech()/buildRecognizerIntent() -- separate silence-timing variants
    // for idle/wake-word listening vs. an active conversation follow-up.
    private lateinit var recognizerIntentWake: Intent
    private lateinit var recognizerIntentConvo: Intent

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

    // AWARENESS LAYER -- Phase 1 only (Scout_Awareness_Layer_Spec.md). Live state +
    // rolling history + the sensor resolver that publishes to both. Zero consumers
    // read from these yet -- see start()/stop() call sites in startSystems()/
    // shutdownSystems() below.
    private lateinit var awarenessState: AwarenessState
    private lateinit var awarenessHistory: AwarenessHistoryDb
    private lateinit var awarenessResolver: AwarenessResolver

    private lateinit var geminiClient: GeminiClient

    private lateinit var scoutGeminiManager: ScoutGeminiManager

    private lateinit var weatherManager: ScoutWeatherManager

    private lateinit var presenceDecider: ScoutPresenceDecider

    // Tracks a sustained pattern of network-dependent recognizer failures so Scout
    // can be honest about it instead of silently retrying forever -- see
    // ScoutSpeechAvailabilityMonitor and its one call site in onError() below.
    private val speechAvailabilityMonitor = ScoutSpeechAvailabilityMonitor()

    // =======================

    // COMPANION MOMENTS -- see maybeMakeCompanionMoment() and its one call site
    // in the face-detection callback. The decision logic itself lives entirely
    // in ScoutCompanionMomentsEngine (brain package); everything here is just
    // gathering real signals for it and acting on its answer.

    private var lastCompanionMomentCheckMs = 0L

    // Set true the first time respond() is called for a real (non-presence-
    // initiated) conversational turn -- see respond() below. Feeds the
    // Curiosity category's "no conversation yet this session" bonus. Reset back
    // to false whenever the tolerant continuous-presence streak it's scoped to
    // itself restarts -- see ScoutPresenceStreakTracker and its one call site
    // in the face-detection callback.
    private var hasHadConversationThisSession = false

    // DB reads (JournalDb/TruthDb) for signal-gathering must not run on the
    // camera-analysis callback thread -- dedicated single-thread executor,
    // unrelated to and independent from ScoutLlamaController's own executor.
    private val companionMomentsExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()

    // Bumped in onDestroy() (before shutting the executor down) so any
    // companion-moment work already queued or in flight on
    // companionMomentsExecutor can recognize itself as stale and discard its
    // result instead of touching a destroyed Activity's UI/TTS -- mirrors the
    // generation/owner-token pattern ScoutLlamaController already uses for
    // TinyLlama generations. Read from both the main thread and
    // companionMomentsExecutor's background thread, hence @Volatile.
    @Volatile
    private var companionMomentsGeneration: Int = 0

    // =======================

    // GAZE INPUTS

    // =======================

    private val IRIS_MAX_X = 74f

    private val IRIS_MAX_Y = 54f

    // Single shared horizontal gain applied to a tracked face's offset from
    // center, regardless of which side it's on. Previously two different,
    // undocumented values (1.15x / 1.35x) applied depending on sign -- traced
    // to the repo's very first commit with no comment or PR explaining the
    // difference, and confirmed on-device to produce a real rightward gaze
    // bias while tracking. Equalized to one value; IRIS_MAX_X still governs
    // maximum travel independently of this gain.
    private val GAZE_TRACKING_GAIN = 1.25f

    // =======================

    // LIFECYCLE

    // =======================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        logStartupTiming("onCreate_start")

        // Show onboarding on first install; skip on every subsequent launch.
        val scoutPrefsEarly = getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
        if (!scoutPrefsEarly.getBoolean(OnboardingActivity.PREF_ONBOARDING_DONE, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Claims this instance as the current valid owner of TinyLlama generation --
        // see ScoutLlamaController. Any result from a previous (now-destroyed)
        // instance's still-in-flight generation is discarded the moment it
        // completes, since it was captured under an older token.
        ScoutLlamaController.registerOwner(applicationContext)

        setContentView(R.layout.activity_main)

        setupWindow()

        setupMemory()

        setupBrainServices()

        setupViews()
        logStartupTiming("ui_ready")

        setupVision()

        setupPermissionLauncher()

        setupTts()

        // Scout never appears -- no face, no permissions, no listening, no greeting --
        // until the offline brain is confirmed ready. The loading screen always shows
        // first; startSystems() only ever runs once it returns (see modelDownloadLauncher).
        if (LlamaEngine.isReady) {
            logStartupTiming("brain_already_ready")
            startSystems()
        } else {
            logStartupTiming("brain_not_ready_launching_loading_gate")
            launchLoadingGate()
        }

    }

    private fun launchLoadingGate() {
        try {
            modelDownloadLauncher.launch(Intent(this, ModelDownloadActivity::class.java))
        } catch (e: Throwable) {
            android.util.Log.e("ScoutBrain", "modelDownloadLauncher.launch() threw", e)
            showLoadingGateFailure()
        }
    }

    // The hard offline-brain gate exists so camera, mic, greeting, and conversation
    // systems never come alive before LlamaEngine.isReady is genuinely true (see
    // resumeSystems()/checkPermissionsAndStart()). startSystems() must never run
    // except either directly when the brain is already ready (onCreate()) or from
    // modelDownloadLauncher's own RESULT_OK callback, which never fires until the
    // brain genuinely is ready. Calling startSystems() here as a fallback would have
    // broken that guarantee the moment launch() itself failed to even show the
    // loading screen. Retrying is honest instead -- Scout stays visibly inert rather
    // than silently starting without its offline brain ready.
    private fun showLoadingGateFailure() {
        if (isFinishing || isDestroyed) return
        android.app.AlertDialog.Builder(this)
            .setTitle("Scout couldn't start setup")
            .setMessage("Something went wrong starting Scout's setup screen. Please try again.")
            .setCancelable(false)
            .setPositiveButton("Try Again") { _, _ -> launchLoadingGate() }
            .show()
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

        awarenessState = AwarenessState()
        awarenessResolver = AwarenessResolver(
            context = this,
            state = awarenessState,
            history = awarenessHistory,
            connectivityManager = connectivityManager
        )

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

    override fun onPause() {

        super.onPause()

        // Scout is no longer visible — stop listening and stop the recognizer watchdog
        // from destroying/recreating the recognizer in the background. Without this,
        // the watchdog (recognizerWatchdog) keeps rescheduling itself every
        // RECOGNIZER_WATCHDOG_MS forever, regardless of foreground state.
        isForeground = false
        stopListeningSafe()
        handler.removeCallbacks(recognizerWatchdog)

        // A pending requestCameraStartup()/requestSpeechStartup() delayed callback (or the
        // startupSettled timer chained after it) is deliberately NOT cancelled here -- each
        // one re-checks isForeground/isFinishing/isDestroyed for itself the moment it fires,
        // so it's simply ignored if the Activity is no longer in a valid state by then. Not
        // cancelling also means cameraStartupScheduled/speechStartupScheduled correctly reset
        // to false when that ignored callback runs, so a later resume schedules a fresh
        // stagger rather than being permanently stuck thinking one is still pending.
        // onDestroy() -> shutdownSystems() still purges every pending callback outright via
        // handler.removeCallbacksAndMessages(null), for the case where the Activity is gone
        // for good rather than just backgrounded.

    }

    override fun onResume() {

        super.onResume()

        isForeground = true

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
        // captionHideRunnable, the requestCameraStartup()/requestSpeechStartup() staggers
        // and the startupSettled timer chained after them, etc.) so nothing fires against
        // state that's about to be torn down below.
        try {
            handler.removeCallbacksAndMessages(null)
        } catch (_: Exception) {
        }

        try {

            wantListening = false

            stopListeningSafe()

            // forceRestoreSystemBeep(), not restoreSystemBeep(): the app is
            // closing for good, so every outstanding mute window (there can
            // now be more than one -- see ScoutBeepMuteGuard) must be
            // guaranteed closed here, not left to a scheduled callback that
            // handler.removeCallbacksAndMessages(null) above may have just
            // purged.
            forceRestoreSystemBeep()

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

                awarenessResolver.stop()

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

        // Invalidates this instance's owner token on every onDestroy() -- a real
        // close AND a configuration-change recreation alike. This is unconditional,
        // unlike the isChangingConfigurations() branch below: regardless of why
        // this instance is being destroyed, its UI/TTS are going away, so a
        // generation that finishes after this point must never be delivered to it.
        // A recreated instance's onCreate() calls registerOwner() moments later and
        // bumps the token again anyway.
        ScoutLlamaController.invalidateOwner()

        // TinyLlama's executor and native engine are owned by ScoutLlamaController
        // (process-wide), not by this Activity instance -- see its class doc. Only
        // tear the engine down on a genuine close, never on a configuration-change
        // recreation, where isChangingConfigurations() is true and a new instance
        // is about to call ScoutLlamaController.registerOwner() and keep using the
        // same, already-loaded ~800MB model. shutdownForRealClose() itself only
        // frees if nothing is actively generating right now (bounded wait, not an
        // unconditional block) -- see LlamaEngine.freeIfIdle().
        if (isChangingConfigurations) {
            android.util.Log.i("ScoutBrain",
                "onDestroy() during a configuration change -- leaving the offline brain " +
                "loaded for the recreated Activity instance.")
        } else {
            try {
                ScoutLlamaController.shutdownForRealClose()
            } catch (_: Exception) {
            }
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

    // Whole-word match, not a bare substring check -- "out" must not match inside
    // "about," "without," "outside," "shout," etc, and a short configured name (e.g.
    // "Al," "Sam") must not match inside an unrelated word either. Used for wake-word
    // detection below.
    private fun containsWholeWord(text: String, word: String): Boolean {
        if (word.isBlank()) return false
        return Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(text)
    }

    // Wall-clock elapsed time since the first call, one line per major startup boundary.
    // Tag "ScoutStartupTiming" -- pull this from a real-device logcat to see exactly
    // which init step is slow, instead of reconstructing it from unrelated system lines.
    private fun logStartupTiming(event: String) {
        if (startupTimingBaseMs == 0L) startupTimingBaseMs = System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - startupTimingBaseMs
        android.util.Log.i("ScoutStartupTiming", "+${elapsed}ms $event")
    }

    // Dedupes consecutive identical reasons before writing to the bounded diagnostic
    // report -- see lastListenAttemptReason's declaration for why.
    private fun logListenAttemptOnce(reason: DiagLog.ListenAttemptReason) {
        if (reason == lastListenAttemptReason) return
        lastListenAttemptReason = reason
        diagLog.logListenAttempt(reason)
    }

    // Idempotent, lifecycle-safe staggered camera/ML Kit startup. Safe to call from every
    // entry point that might want the camera running (checkPermissionsAndStart(), the
    // permission-result callback, resumeSystems()) -- only the first call during the
    // startup window actually schedules a delayed start; concurrent later calls are
    // no-ops. The delayed callback re-checks that the Activity is still in a valid,
    // foregrounded, permitted state before touching the camera, so a pause/resume or
    // permission-result race can never start a second camera pipeline or start one after
    // the Activity should no longer be doing camera work -- it's simply ignored, which is
    // sufficient since nothing here needs to be actively cancelled on pause (see onPause()).
    // Once the camera has started once, this stops staggering entirely and behaves exactly
    // like the pre-existing direct safeStartCamera() call -- the stagger only exists to
    // protect the cold-start collision, not steady-state restarts (e.g. returning from
    // Settings), which is why cameraEverStarted short-circuits to the original behavior.
    private fun requestCameraStartup(from: String) {
        if (!LlamaEngine.isReady) return
        if (cameraEverStarted) {
            safeStartCamera(from)
            return
        }
        if (cameraStartupScheduled) return
        cameraStartupScheduled = true
        logStartupTiming("camera_startup_scheduled from=$from delay=${CAMERA_STARTUP_STAGGER_MS}ms")
        handler.postDelayed({
            cameraStartupScheduled = false
            if (isFinishing || isDestroyed) {
                logStartupTiming("camera_startup_skipped from=$from reason=activity_gone")
                return@postDelayed
            }
            if (!isForeground) {
                logStartupTiming("camera_startup_skipped from=$from reason=not_foreground")
                return@postDelayed
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                logStartupTiming("camera_startup_skipped from=$from reason=no_permission")
                return@postDelayed
            }
            logStartupTiming("camera_startup_firing from=$from")
            // cameraEverStarted/startupSettled are only set once bindToLifecycle()
            // actually succeeds (see the onBound callback threaded through
            // safeStartCamera()/startCamera()) -- not merely once startup was
            // requested, since startCamera()'s provider lookup and binding are
            // asynchronous and can still fail after this point returns.
            safeStartCamera(from) {
                logStartupTiming("camera_bind_succeeded from=$from")
                cameraEverStarted = true
                handler.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    startupSettled = true
                    logStartupTiming("startup_settled")
                }, STARTUP_SETTLE_MS)
            }
        }, CAMERA_STARTUP_STAGGER_MS)
    }

    // Mirrors requestCameraStartup() for SpeechRecognizer setup -- same idempotency and
    // lifecycle-safety reasoning applies. Deliberately does NOT cover the recognizer
    // watchdog's own safeSetupSpeech("watchdog") call (that's an ongoing health-check
    // recovery path for after startup, not a startup path -- see its speechEverStarted
    // guard instead, so it doesn't fight this stagger by "fixing" a recognizer that's
    // simply not started yet by design).
    private fun requestSpeechStartup(from: String) {
        if (!LlamaEngine.isReady) return
        if (speechEverStarted) {
            safeSetupSpeech(from)
            return
        }
        if (speechStartupScheduled) return
        speechStartupScheduled = true
        logStartupTiming("speech_startup_scheduled from=$from delay=${SPEECH_STARTUP_STAGGER_MS}ms")
        handler.postDelayed({
            speechStartupScheduled = false
            if (isFinishing || isDestroyed) {
                logStartupTiming("speech_startup_skipped from=$from reason=activity_gone")
                return@postDelayed
            }
            if (!isForeground) {
                logStartupTiming("speech_startup_skipped from=$from reason=not_foreground")
                return@postDelayed
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                logStartupTiming("speech_startup_skipped from=$from reason=no_permission")
                return@postDelayed
            }
            logStartupTiming("speech_startup_firing from=$from")
            // safeSetupSpeech() -> setupSpeech() already ends with its own
            // scheduleListenRestart() call, which sets pendingListenStart -- an
            // explicit scheduleListenRestart(immediate = true) here would always be a
            // no-op (blocked by that same flag), so it's not called again. The actual
            // first listen start still happens, just via setupSpeech()'s own restart
            // rather than a second, redundant one.
            safeSetupSpeech(from)
            speechEverStarted = true
        }, SPEECH_STARTUP_STAGGER_MS)
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
            // Same reasoning as the immediate boot-announcement path in
            // onInit() -- a genuine spoken greeting, routed through
            // respond(isPresenceInitiated = true) so it opens the same
            // Scout-initiated conversation/reply-window as any other
            // presence-initiated remark.
            respond(out, isPresenceInitiated = true)
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

        awarenessResolver.start()

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
        // NOTE: this delay/value is untouched by the A32 startup-stagger work -- not
        // being re-tuned here, per explicit instruction not to touch TinyLlama loading.
        logStartupTiming("tinyllama_load_scheduled delay=90000ms")
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
            android.util.Log.i("ScoutBrain",
                if (success) "Offline brain ready in ${loadMs}ms" else "Offline brain load failed")
            logStartupTiming("tinyllama_load_done success=$success ms=$loadMs")

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

            requestCameraStartup("onResume")

        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            requestSpeechStartup("onResume")
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

        awarenessHistory = AwarenessHistoryDb(this)

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

        cleanupUnverifiedPrimaryUserHabitNames()

    }

    private fun setupTts() {

        // Explicit 3-arg constructor: request this engine specifically,
        // rather than whatever the device's current default happens to be.
        // Requesting an engine here is a per-instance bind -- it does not
        // change Settings.Secure.tts_default_synth or any other device-wide
        // default engine setting. If this specific engine can't be used,
        // onInit() below retries once with the device's own default engine
        // (the 2-arg constructor, Scout's original behavior).
        tts = TextToSpeech(this, this, TARGET_TTS_ENGINE)

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
                if (camOk) requestCameraStartup("permissionCallback")
                if (micOk) requestSpeechStartup("permissionCallback")
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

    // One-time cleanup for a real bug in the face-detection callback (fixed
    // at its source in a separate PR: the primary user's name used to be
    // passed to HabitLayer.logPersonSeen() for whichever face was merely
    // first-detected in a frame, regardless of whether that face was ever
    // actually verified as that person) that could label a habit-tracked
    // face hash with the primary user's name it never earned. Guarded
    // exactly like migrateDoublePrefixFacts() above -- set before running,
    // so it can never re-run and re-clear a name HabitLayer has since
    // genuinely re-learned through real recognition.
    //
    // The primary user's name is read fresh from TruthDb here, never
    // hardcoded -- if it's ever renamed, this cleanup (already run once)
    // stays a one-time pass against whatever name was current at the time
    // this device first ran it, exactly as intended: a fix for old bad data,
    // not an ongoing enforcement mechanism.
    private fun cleanupUnverifiedPrimaryUserHabitNames() {
        if (prefs.getBoolean("habit_primary_user_name_cleanup_done_v1", false)) return
        prefs.edit().putBoolean("habit_primary_user_name_cleanup_done_v1", true).apply()

        val primaryName = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.NAME) ?: return
        if (primaryName.isBlank()) return

        val cleared = habitLayer.clearUnverifiedPersonName(primaryName) { faceHash, name ->
            peopleDb.getName(faceHash)?.equals(name, ignoreCase = true) == true
        }
        if (cleared > 0) {
            journalDb.add("Habit cleanup: cleared $cleared unverified primary-user name label(s) from HabitLayer.")
        }
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
                requestCameraStartup("alreadyGranted")
                requestSpeechStartup("alreadyGranted")
            }

            return false

        }

    }

    // =======================

    // CAMERA

    // =======================

    // onBound fires only once bindToLifecycle() actually succeeds (see startCamera()) --
    // never on a synchronous startCamera() failure, and never if the async provider
    // lookup/binding fails afterward. Defaults to a no-op for callers (like the
    // post-startup steady-state restarts) that don't need to know.
    private fun safeStartCamera(from: String, onBound: () -> Unit = {}) {

        try {

            startCamera(onBound)

            Log.i("ScoutCamera", "startCamera ok ($from)")

        } catch (e: Exception) {

            Log.e("ScoutCamera", "startCamera failed ($from)", e)

            journalDb.add("startCamera failed ($from): ${e.javaClass.simpleName}: ${e.message}")

            diagLog.logError(DiagLog.ErrorArea.CAMERA, e)

        }

    }

    private fun startCamera(onBound: () -> Unit = {}) {

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

                    // Without an explicit target, CameraX picks its own default
                    // resolution, which can be considerably larger than needed for face
                    // detection/labeling -- every frame that passes the throttle below
                    // allocates a full ARGB bitmap (plus, if row-padded, a matching
                    // direct ByteBuffer) sized to whatever this resolution is. 640x480
                    // is more than enough for both ML Kit tasks and keeps that
                    // per-frame allocation small on the A32. setTargetResolution() is
                    // deprecated in favor of ResolutionSelector in newer CameraX
                    // versions but still fully supported -- kept for now since it's the
                    // simpler, longer-established API surface.
                    .setTargetResolution(android.util.Size(640, 480))

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

                    // Scene labels change far more slowly than face position, so the
                    // labeler doesn't need to run on every accepted (~7fps) frame the way
                    // face detection does -- throttled separately via lastLabelMs.
                    val runLabelerThisFrame = analysisNow - lastLabelMs >= LABEL_MIN_INTERVAL_MS

                    // Track async users of this bitmap; recycle when all are done. Only
                    // faceDetector holds a ref on a frame where the labeler is skipped --
                    // starting the count at 2 regardless would leak a ref and the bitmap
                    // would never recycle on those frames.
                    val bitmapRefs = AtomicInteger(if (runLabelerThisFrame) 2 else 1)
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

                    if (runLabelerThisFrame) {

                        lastLabelMs = analysisNow

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

                    }

                    faceDetector.process(input)

                        .addOnSuccessListener { faces ->

                            val now = System.currentTimeMillis()

                            // Latched, not overwritten every frame -- see
                            // secondFaceArrivalPendingSinceMs's declaration and
                            // consumeSecondFaceArrivalSignal(). Feeds
                            // ScoutCompanionMomentsEngine's Environment category, which is
                            // scoped only to a second person joining, not a return-from-
                            // absence (that stays Presence's job, see genuineAbsenceMarked).
                            if (lastFaceCount < 2 && faces.size >= 2 && !genuineAbsenceMarked) {
                                secondFaceArrivalPendingSinceMs = now
                            }

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

                                    val correctedDx = dx * GAZE_TRACKING_GAIN

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

                                    // Vision-led direct-address gate: does the current largest
                                    // face look like it's actually facing Scout? Extends the
                                    // streak while true, resets it the moment any single
                                    // condition fails -- every frame is re-evaluated from
                                    // scratch, so there's no accumulating qualifying moments
                                    // across a gap; one disqualifying frame ends the streak.
                                    val yaw = largest.headEulerAngleY
                                    val faceHeightFraction = b.height().toFloat() / imgH
                                    val centerOffset = maxOf(abs(dx), abs(dy))
                                    lastYawDegrees = yaw
                                    lastFaceHeightFraction = faceHeightFraction
                                    lastCenterOffset = centerOffset

                                    val disqualifyReason = when {
                                        abs(yaw) > LISTENING_REMINDER_MAX_YAW_DEGREES ->
                                            "yaw=$yaw beyond ±$LISTENING_REMINDER_MAX_YAW_DEGREES"
                                        faceHeightFraction < LISTENING_REMINDER_MIN_FACE_HEIGHT_FRACTION ->
                                            "faceHeight=$faceHeightFraction below $LISTENING_REMINDER_MIN_FACE_HEIGHT_FRACTION"
                                        centerOffset > LISTENING_REMINDER_MAX_OFFSET ->
                                            "centerOffset=$centerOffset beyond $LISTENING_REMINDER_MAX_OFFSET"
                                        else -> null
                                    }

                                    if (disqualifyReason == null) {
                                        if (directAddressStreakStartMs == 0L) {
                                            directAddressStreakStartMs = now
                                            logPresenceDebug("Direct-address streak started " +
                                                "(yaw=$yaw height=$faceHeightFraction offset=$centerOffset)")
                                        }
                                    } else {
                                        if (directAddressStreakStartMs != 0L) {
                                            logPresenceDebug("Direct-address streak reset: $disqualifyReason")
                                        }
                                        directAddressStreakStartMs = 0L
                                    }

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

                                        // Scout must never assume the first detected face in a
                                        // frame is ENTITY_USER_PRIMARY (Patrick) -- that's a fact
                                        // about who the app is registered to, not about which
                                        // face this is. Look up the real, already-known name for
                                        // THIS specific face hash instead (peopleDb.touchSeen(fp)
                                        // above already operates on these same hashes) -- if
                                        // Scout has genuinely identified this person before, via
                                        // the family-introduction/naming flow, that name is used;
                                        // otherwise no name at all, matching logPersonSeen()'s own
                                        // default. HabitLayer then only ever learns a name for a
                                        // hash when it's actually earned by that hash's own
                                        // recognition history, never assumed from the primary user.

                                        val knownName =

                                            peopleDb.getName(primaryHash)

                                                ?: ""

                                        habitLayer.logPersonSeen(primaryHash, knownName)

                                    }

                                }

                                val embedNowMs = System.currentTimeMillis()

                                // startupSettled (see requestCameraStartup()) withholds embedding
                                // entirely until STARTUP_SETTLE_MS after the camera actually starts,
                                // even if a face is detected in the very first analyzed frame --
                                // face detection/gaze-tracking above this point is unaffected, only
                                // the expensive embedding model + DB lookup is deferred.
                                if (startupSettled &&
                                        embedNowMs - lastEmbedMs >= EMBED_INTERVAL_MS &&
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

                                // Gap-tolerant streak for the idle-silence acknowledgment: only
                                // restart it if the gap since the last sighting exceeded the
                                // grace period -- otherwise this is the same streak continuing.
                                // Also defines "session" for hasHadConversationThisSession: that
                                // flag resets whenever this streak itself restarts, so Curiosity's
                                // "no conversation yet this session" bonus is scoped to the same
                                // continuous-presence window CURIOSITY_MIN_PRESENCE_MS measures.
                                val streakUpdate = ScoutPresenceStreakTracker.update(
                                    presentSinceMs = presencePresentSinceMs,
                                    lastSeenMs = presenceLastSeenMs,
                                    nowMs = now,
                                    gapGraceMs = PRESENCE_GAP_GRACE_MS
                                )
                                if (streakUpdate.streakRestarted) {
                                    if (presencePresentSinceMs == 0L) {
                                        logPresenceDebug("Tolerant presence streak started")
                                    } else {
                                        logPresenceDebug("Presence streak reset -- gap of " +
                                            "${(now - presenceLastSeenMs) / 1000}s exceeded the grace period")
                                    }
                                    hasHadConversationThisSession = false
                                } else {
                                    val gapMs = now - presenceLastSeenMs
                                    // Only meaningful gaps -- skips routine per-frame timing noise.
                                    if (gapMs > 5_000L) {
                                        logPresenceDebug("Brief face gap (${gapMs / 1000}s) within " +
                                            "grace period -- streak continues")
                                    }
                                }
                                presencePresentSinceMs = streakUpdate.newPresentSinceMs
                                presenceLastSeenMs = now

                                // Layer 1 return greeting: face is back. If we were tracking a
                                // genuine absence, this is a real return -- start/continue the
                                // stabilization window before actually speaking. If it was only
                                // a candidate (brief) absence, quietly cancel it; no greeting for
                                // a non-event.
                                if (genuineAbsenceMarked) {
                                    if (returnStabilizingSinceMs == 0L) {
                                        returnStabilizingSinceMs = now
                                        logPresenceDebug("Return face detected")
                                    }
                                    if (now - returnStabilizingSinceMs >= RETURN_STABILIZATION_MS) {
                                        logPresenceDebug("Return stabilized")
                                        maybeMakeReturnGreeting()
                                    }
                                } else if (candidateAbsenceLogged) {
                                    logPresenceDebug("Absence cancelled -- brief gap")
                                    candidateAbsenceLogged = false
                                }

                                maybeMakeIdleSilencePresenceRemark()

                                maybeMakeCompanionMoment()

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

                                // Return-greeting freshness fix: without this, lastFaceEmbedding
                                // would keep whatever the pre-absence person's embedding was
                                // indefinitely -- maybeMakeReturnGreeting()'s fresh PeopleDb
                                // lookup could then read a genuinely stale, pre-absence embedding
                                // if the async embed for the actual returning face hasn't resolved
                                // yet by the time the return greeting fires. Clearing it here
                                // guarantees any non-null value it holds later was captured after
                                // presence resumed -- never carried over from before an absence.
                                lastFaceEmbedding = null

                                lastSecondaryFaceName = null

                                presenceDecider.onFaceLost()

                                // greetedThisSession intentionally NOT reset here.
                                // Scout greets once per app launch when he first sees a face.
                                // The proactive return greeting (Layer 1, below) handles a real
                                // "welcome back" -- this first-contact greeting stays separate.
                                faceAppearanceMs = 0L

                                // No face at all -- definitely not sustaining direct address.
                                if (directAddressStreakStartMs != 0L) {
                                    logPresenceDebug("Direct-address streak reset: no face detected")
                                }
                                directAddressStreakStartMs = 0L
                                lastYawDegrees = 0f
                                lastFaceHeightFraction = 0f
                                lastCenterOffset = 0f

                                // Layer 1 return greeting: absence tracking, reusing
                                // presenceLastSeenMs (untouched in this branch) as the single
                                // source of truth for "how long has no face been seen" -- no
                                // separate timestamp to keep in sync. Doesn't restart the clock
                                // if an absence is already running: an intermittent flicker of
                                // return before stabilization completes shouldn't reset how long
                                // they've actually been gone.
                                val absenceGapMs = if (presenceLastSeenMs == 0L) 0L else now - presenceLastSeenMs
                                if (absenceGapMs >= CAMERA_GAP_TOLERANCE_MS) {
                                    if (!candidateAbsenceLogged) {
                                        candidateAbsenceLogged = true
                                        logPresenceDebug("Absence started")
                                    }
                                    if (!genuineAbsenceMarked && absenceGapMs >= MIN_GENUINE_ABSENCE_MS) {
                                        genuineAbsenceMarked = true
                                        logPresenceDebug("Genuine absence confirmed")
                                    }
                                }
                                returnStabilizingSinceMs = 0L // cancel any in-progress return stabilization

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

                onBound()

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

    private fun buildRecognizerIntent(silenceMs: Long, possiblySilenceMs: Long): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // ScoutSpeechLanguage.RECOGNITION_LOCALE, not a separate literal here --
            // this is also what handleLanguageIntent() answers "what language are
            // we speaking" from, so the two can never drift apart.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, ScoutSpeechLanguage.RECOGNITION_LOCALE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Prefer offline recognition so a brief network hiccup does not
            // cause silent failures — Samsung has offline models available.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", silenceMs)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", possiblySilenceMs)
        }

    private fun setupSpeech() {

        try {

            speechRecognizer?.destroy()

        } catch (_: Exception) {

        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // Two variants so idle/wake-word listening doesn't have to hold a
        // SpeechRecognizer session open as long as an active back-and-forth does.
        // maybeStartListening() picks between them per-session using the same
        // "are we in a conversation window" check onReadyForSpeech() already uses for
        // diagnostic logging. Active-conversation values are unchanged from before this
        // split; only idle/wake-word listening got shorter.
        recognizerIntentWake = buildRecognizerIntent(silenceMs = 5_000L, possiblySilenceMs = 4_000L)
        recognizerIntentConvo = buildRecognizerIntent(silenceMs = 10_000L, possiblySilenceMs = 7_000L)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {

                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = true

                isCapturingSpeech = false

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

                isCapturingSpeech = true

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

                // Deliberately does NOT call scheduleListenRestart(). onEndOfSpeech()
                // means the recognizer stopped hearing audio, not that it's done --
                // Android still has to deliver onResults()/onError() afterward, which can
                // take longer than LISTEN_RESTART_DELAY_MS (150ms). Restarting from here
                // risked calling startListening() again before the current session had
                // actually finished closing out, which is the classic
                // ERROR_RECOGNIZER_BUSY (error 8) overlap Scout already has special
                // handling for below -- a sign this was very likely already happening in
                // real use. onResults() and onError() each already call
                // scheduleListenRestart() on every one of their own paths, so restart
                // still always happens -- just from the event that actually means the
                // session is over. lastRecognizerEventMs is still updated here so the
                // watchdog's staleness check has an accurate timestamp if neither
                // onResults() nor onError() ever arrives.
                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = false

                isCapturingSpeech = false

                faceView.setListening(false)

                faceView.setMicLevel(0f)

            }

            override fun onError(error: Int) {

                lastRecognizerEventMs = System.currentTimeMillis()

                isListening = false

                isCapturingSpeech = false

                faceView.setListening(false)

                faceView.setMicLevel(0f)

                diagLog.logSpeechError(error)
                diagLog.logListenStop(DiagLog.StopReason.ERROR)

                // Always fed, regardless of error type -- onRecognizerError() itself
                // ignores anything that isn't ERROR_NETWORK/ERROR_NETWORK_TIMEOUT, and
                // the rolling window needs every qualifying error recorded to stay
                // accurate even on a cycle where the warning ends up skipped below.
                val shouldWarnAboutAvailability = speechAvailabilityMonitor.onRecognizerError(error)
                // isSpeaking guard: a recognizer session cancelled because Scout just
                // started speaking can still deliver a trailing onError() shortly
                // after -- speaking the warning on top of that would double up TTS.
                // Skipping here (without calling onWarned()) leaves the cooldown
                // untouched, so the very next qualifying error gets a fresh chance
                // once Scout isn't mid-utterance, rather than the warning being lost
                // for a full cooldown window over a timing coincidence.
                if (shouldWarnAboutAvailability && !isSpeaking) {
                    speechAvailabilityMonitor.onWarned()
                    diagLog.logSpeechAvailabilityWarning(speechAvailabilityMonitor.currentPatternSize())
                    // respond() -> speak() sets wantListening = false synchronously, so
                    // the scheduleListenRestart() call below (still reached on this same
                    // pass, since error is 1 or 2 here, never 8) becomes a no-op exactly
                    // the way it already does for any other spoken response -- the real
                    // restart comes from TTS's onDone once the warning finishes, so this
                    // cannot create a second, competing restart or a speak/listen loop.
                    respond("I'm having trouble hearing you right now. Speech recognition may be unavailable, and I can't reach the service it needs.")
                }

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

                isCapturingSpeech = false

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

                val scoutName = truthDb.getFactValue("scout", "name") ?: "Scout"
                val nameLower = scoutName.lowercase()

                // Courtesy layer (Phase 1) -- checked before both the one-word filter
                // and the wake-name gate below, on purpose: these are a small, fixed,
                // deterministic set of everyday courtesy phrases ("hi", "thank you",
                // "good night", ...) that should work without saying Scout's name,
                // without going through handleQuery()/ScoutIntentRouter/TinyLlama/
                // Gemini at all. See ScoutCourtesyMatcher's own doc comment for exactly
                // which forms are matched and why. Everything else in this function --
                // the wake-name requirement, the conversation window, real questions --
                // is completely unchanged.
                ScoutCourtesyMatcher.match(normalized, scoutName)?.let { courtesy ->
                    convoDb.logTurn("user", normalized)
                    handleCourtesy(courtesy)
                    scheduleListenRestart()
                    return
                }

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
                // and otherwise get treated as a new question. Matching logic
                // itself is unchanged here -- only the diagnostic call below is new.
                if (words.size >= 2 &&
                    lastScoutUtteranceNormalized.isNotBlank() &&
                    lastScoutUtteranceNormalized.contains(normalized)
                ) {

                    diagLog.logSelfEchoDiscarded(
                        charCount = normalized.length,
                        gapAfterResponseMs = System.currentTimeMillis() - lastScoutResponseMs
                    )

                    scheduleListenRestart()

                    return

                }

                convoDb.logTurn("user", normalized)

                habitLayer.logUtterance(normalized, lastFaceHashes.firstOrNull())

                // FuzzyNameMatcher gives any configured name (not just the default
                // "Scout") the same class of mishearing tolerance, via bounded
                // edit-distance whole-word matching rather than a hand-written list of
                // alternate spellings -- see its own doc comment for the exact
                // thresholds and why "out" alone can never match "scout" this way
                // (length difference alone rules it out). "Gal" is kept as its own
                // explicit exception below: a real STT mishearing observed for the
                // literal default name specifically, but not spelling-close enough for
                // any generic distance-based matcher to catch, so it can't be folded
                // into FuzzyNameMatcher without hardcoding a device-specific quirk into
                // an otherwise name-agnostic class. "Scott" no longer needs its own
                // entry -- it's exactly edit-distance 1 from "Scout" (5 letters, so
                // within FuzzyNameMatcher's own distance-1 tier) and is now caught
                // generically.
                val hearsHisName = FuzzyNameMatcher.matchesName(normalized, scoutName) ||
                    (nameLower == "scout" && containsWholeWord(normalized, "gal"))
                // Better Conversation State Phase 1: CONVO_WINDOW_MS/
                // PRESENCE_REPLY_WINDOW_MS are unchanged and still computed here as
                // before -- conversationState.evaluate() is the "next evaluation"
                // point for the silence-timeout transition (performs the actual
                // active -> inactive change here, not a stale read) and is also
                // what makes an explicit close ("goodbye"/"stop listening"/etc.)
                // override a still-recent timer: once closeExplicitly() has run,
                // this keeps reporting inactive even if the raw windows below
                // haven't technically expired yet.
                val conversationNowMs = System.currentTimeMillis()
                val convoWindowOpen = (conversationNowMs - lastScoutResponseMs) < CONVO_WINDOW_MS
                val presenceReplyWindowOpen = conversationNowMs < presenceReplyWindowUntilMs
                val conversationEvaluation = conversationState.evaluate(
                    conversationNowMs, convoWindowOpen, presenceReplyWindowOpen
                )
                if (conversationEvaluation.justTimedOut) logConversationEnd()
                val inConvoWindow = conversationEvaluation.isActive
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

                    // Vision-led direct-address gate (see directAddressStreakStartMs, updated
                    // per-frame in the face-analysis callback) -- replaces the old "any face
                    // within the last 3 seconds" test, which couldn't tell a side conversation
                    // or a person crossing the room from someone actually facing Scout.
                    //
                    // Even with vision gating, Scout still can't prove the visible person is
                    // the one speaking -- Diana could be talking off-camera while Elijah just
                    // happens to be looking at Scout. Vision gating narrows false positives, it
                    // doesn't eliminate them, which is exactly why the cooldown below stays as
                    // a second, independent layer of protection rather than being loosened.
                    val visionStale = (now - lastFaceUpdatedMs) >= VISION_FRESHNESS_MS
                    val sustainedMs = if (directAddressStreakStartMs != 0L) now - directAddressStreakStartMs else 0L
                    val sustained = directAddressStreakStartMs != 0L && sustainedMs >= DIRECT_ADDRESS_SUSTAIN_MS
                    val reminderDue = (now - lastListeningReminderMs) > LISTENING_REMINDER_COOLDOWN_MS

                    val reminderBlockReason = when {
                        visionStale -> "vision data stale"
                        lastFaceCount == 0 -> "no current face"
                        directAddressStreakStartMs == 0L -> "face not oriented toward Scout"
                        !sustained -> "visual attention not sustained"
                        !reminderDue -> "cooldown"
                        isSpeaking -> "Scout speaking"
                        isThinking -> "Scout thinking"
                        else -> null
                    }

                    logPresenceDebug("Listening reminder check: yaw=$lastYawDegrees " +
                        "height=$lastFaceHeightFraction offset=$lastCenterOffset " +
                        "sustainedMs=$sustainedMs reason=${reminderBlockReason ?: "eligible"}")

                    if (reminderBlockReason == null) {
                        lastListeningReminderMs = now
                        logPresenceDebug("Listening reminder spoken")
                        respond("I'm sorry. If you're talking to me, just say $scoutName first.")
                    } else {
                        scheduleListenRestart()
                    }
                    return
                }
                // A real turn is about to be dispatched -- opens a fresh
                // conversation if this was reached via the wake word from idle,
                // or just records the turn if the conversation was already active.
                if (conversationState.openFromUserTurn(System.currentTimeMillis())) {
                    diagLog.logConversationStarted(startedByScout = false)
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

        val out = when ((0..1).random()) {

            0 -> "It is ${fmt.format(cal.time)}."

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

    // Answers from ScoutSpeechLanguage's RECOGNITION_LOCALE -- the same constant
    // buildRecognizerIntent() configures the speech recognizer with -- so this
    // can never drift from what the recognizer is actually set to. Never a
    // separately hardcoded string, never TinyLlama.
    private fun handleLanguageIntent() {

        respond("We're speaking ${ScoutSpeechLanguage.spokenLanguageName()}.")

    }

    // Answers from ScoutTimeOfDay, which reuses HabitLayer.TIME_SLOTS' hour
    // boundaries -- see that file's doc comment for why PresenceMode was
    // considered and rejected as the source here. Two router phrasings share
    // this one intent but expect different answer shapes: "is it morning or
    // night" is itself posed in terms of exactly those four words, so it gets
    // spokenCategory(); "what time of day is it" is open-ended, so it gets
    // the more descriptive descriptiveLabel(). The two phrasings are the only
    // ones ScoutIntentRouter routes to TIME_OF_DAY, so checking for "morning
    // or night" here reliably tells them apart.
    private fun handleTimeOfDayIntent(qNorm: String) {

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val out = if (qNorm.contains("morning or night")) {
            "It's ${ScoutTimeOfDay.spokenCategory(hour)} right now."
        } else {
            "It's ${ScoutTimeOfDay.descriptiveLabel(hour)} right now."
        }

        respond(out)

    }

    private fun stopListeningSafe() {

        // Mirrors maybeStartListening()'s TRY_MUTE_BEEP handling on the start
        // side (see tryMuteSystemBeep()/restoreSystemBeep()) -- muting just
        // before cancel() suppresses whatever "stop" earcon the recognition
        // service plays when a session is cancelled, the same way muting
        // just before startListening() suppresses its "start" earcon. Reuses
        // the same 380ms window and the same guard pair; ScoutBeepMuteGuard
        // is what makes the two sides safe to overlap.
        if (TRY_MUTE_BEEP) {
            tryMuteSystemBeep()
            handler.postDelayed({ restoreSystemBeep() }, 380L)
        }

        try {

            speechRecognizer?.cancel()

        } catch (_: Exception) {

        }

        isListening = false

        faceView.setListening(false)

        faceView.setMicLevel(0f)

    }

    // delayMsOverrideMs: used only by maybeStartListening()'s three post-TTS
    // cooldown branches, to target the actual remaining time until the latest
    // active deadline (see ScoutMicRestartTiming) instead of the flat
    // LISTEN_RESTART_DELAY_MS poll below -- every other call site is
    // state-based, not deadline-based, so it keeps using immediate/the flat
    // poll exactly as before. Never lowers any threshold; only decides how
    // precisely the wait targets thresholds that are still computed exactly
    // as they always were.
    private fun scheduleListenRestart(immediate: Boolean = false, delayMsOverrideMs: Long? = null) {

        if (!wantListening) return

        if (pendingListenStart) return

        pendingListenStart = true

        val delay = delayMsOverrideMs ?: if (immediate) 0L else LISTEN_RESTART_DELAY_MS

        handler.postDelayed({

            pendingListenStart = false

            maybeStartListening()

        }, delay)

    }

    // Shared by all three post-TTS cooldown branches in maybeStartListening()
    // below -- whichever gate fails, the retry targets the actual latest
    // remaining deadline across all three, not just the one that happened to
    // fail first. Thresholds themselves (BOOT_LISTEN_EXTRA_DELAY_MS,
    // TTS_LOCKOUT_MS, MIC_RESUME_COOLDOWN_MS) are unchanged -- this only
    // changes how precisely the reschedule targets them.
    private fun nextListenRestartDelayMs(now: Long): Long =
        ScoutMicRestartTiming.computeRestartDelayMs(
            now = now,
            bootListenDeadlineMs = lastSpeechDoneMs + BOOT_LISTEN_EXTRA_DELAY_MS,
            ttsLockoutDeadlineMs = ttsLockoutUntilMs,
            micResumeDeadlineMs = lastSpeechDoneMs + MIC_RESUME_COOLDOWN_MS
        )

    private fun maybeStartListening() {

        // Every early-return branch below logs a controlled reason code (see
        // DiagLog.ListenAttemptReason) so a real-device diagnostic report can answer
        // "why didn't the mic start" precisely instead of requiring manual log
        // reconstruction. Order matches the original guard order; the RECORD_AUDIO
        // permission check and the isThinking/startupSettled checks are new additions
        // (all strictly more conservative -- they can only ever block startListening()
        // more, never less).

        if (!isForeground) { logListenAttemptOnce(DiagLog.ListenAttemptReason.ACTIVITY_NOT_RESUMED); return }

        if (!wantListening) { logListenAttemptOnce(DiagLog.ListenAttemptReason.LISTENING_DISABLED); return }

        if (currentMode != Mode.PRESENCE) { logListenAttemptOnce(DiagLog.ListenAttemptReason.CONVERSATION_GATE); return }

        if (isSpeaking) { logListenAttemptOnce(DiagLog.ListenAttemptReason.SCOUT_SPEAKING); return }

        if (isThinking) { logListenAttemptOnce(DiagLog.ListenAttemptReason.SCOUT_THINKING); return }

        if (isListening) { logListenAttemptOnce(DiagLog.ListenAttemptReason.ALREADY_LISTENING); return }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            logListenAttemptOnce(DiagLog.ListenAttemptReason.PERMISSIONS_MISSING)
            return
        }

        // Before the staggered initial speech setup has run once (see
        // requestSpeechStartup()), speechRecognizer is expected to be null by design --
        // this stops that from being (mis)treated as "not ready yet, set it up now,"
        // which is exactly what silently defeated the stagger before this guard existed.
        if (!speechEverStarted) { logListenAttemptOnce(DiagLog.ListenAttemptReason.STARTUP_NOT_SETTLED); return }

        val now = System.currentTimeMillis()

        if (!bootFinishedSpeaking) { logListenAttemptOnce(DiagLog.ListenAttemptReason.BOOT_NOT_FINISHED); return }

        if (now - lastSpeechDoneMs < BOOT_LISTEN_EXTRA_DELAY_MS) {

            logListenAttemptOnce(DiagLog.ListenAttemptReason.COOLDOWN)
            scheduleListenRestart(delayMsOverrideMs = nextListenRestartDelayMs(now))

            return

        }

        if (now < ttsLockoutUntilMs) {

            logListenAttemptOnce(DiagLog.ListenAttemptReason.COOLDOWN)
            scheduleListenRestart(delayMsOverrideMs = nextListenRestartDelayMs(now))

            return

        }

        if (now - lastSpeechDoneMs < MIC_RESUME_COOLDOWN_MS) {

            logListenAttemptOnce(DiagLog.ListenAttemptReason.COOLDOWN)
            scheduleListenRestart(delayMsOverrideMs = nextListenRestartDelayMs(now))

            return

        }

        if (speechRecognizer == null || !::recognizerIntentWake.isInitialized || !::recognizerIntentConvo.isInitialized) {

            logListenAttemptOnce(DiagLog.ListenAttemptReason.SPEECH_RECOGNIZER_NOT_READY)

            try {

                setupSpeech()

            } catch (_: Exception) {

            }

            return

        }

        // Same "are we in a conversation window" check onReadyForSpeech() uses for
        // diagnostic logging, computed here so the shorter/longer-silence variant can
        // actually be chosen before the session starts.
        val inConvoWindowForListen = (now - lastScoutResponseMs) < CONVO_WINDOW_MS ||
            now < presenceReplyWindowUntilMs
        val intentForThisSession = if (inConvoWindowForListen) recognizerIntentConvo else recognizerIntentWake

        try {

            if (TRY_MUTE_BEEP) {

                tryMuteSystemBeep()

                speechRecognizer?.startListening(intentForThisSession)
                logListenAttemptOnce(DiagLog.ListenAttemptReason.STARTLISTENING_CALLED)

                handler.postDelayed({ restoreSystemBeep() }, 380L)

            } else {

                speechRecognizer?.startListening(intentForThisSession)
                logListenAttemptOnce(DiagLog.ListenAttemptReason.STARTLISTENING_CALLED)

            }

        } catch (_: Exception) {

            logListenAttemptOnce(DiagLog.ListenAttemptReason.STARTLISTENING_EXCEPTION)
            restoreSystemBeep()

            scheduleListenRestart()

        }

    }

    // beginMute()/endMute() run unconditionally, outside the try block below --
    // that bookkeeping must never be skipped just because an AudioManager call
    // happens to throw, or a later matching call would desync from this one.
    // Only the actual capture/mute (or restore) is best-effort, same as before.
    private fun tryMuteSystemBeep() {

        if (!beepMuteGuard.beginMute()) return

        try {

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            savedSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)

            savedNotificationVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

            am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)

            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)

        } catch (_: Exception) {

        }

    }

    private fun restoreSystemBeep() {

        if (!beepMuteGuard.endMute()) return

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

    // Guaranteed shutdown-only restore. A normal restoreSystemBeep() call
    // can close out at most one outstanding mute window per call, but
    // shutdownSystems() purges every pending Handler callback
    // (removeCallbacksAndMessages(null)) before this runs -- so if more than
    // one mute window happened to be outstanding (e.g. a stop-side mute from
    // the final stopListeningSafe() plus an unrelated still-pending
    // start-side one), whichever window's own scheduled restore was purged
    // would otherwise never fire, leaving the stream volumes permanently
    // muted after Scout closes. This forces every outstanding window closed
    // and restores once, regardless of how many were pending.
    private fun forceRestoreSystemBeep() {

        if (!beepMuteGuard.forceReset()) return

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
                refreshThinkingFaceState()
            }

            // If TinyLlama hung and never called respond(), isThinking stays true forever
            // and the mic never restarts. Force-clear after MAX_THINKING_DURATION_MS.
            if (isThinking && !isSpeaking && thinkingStartedMs > 0L && now - thinkingStartedMs > MAX_THINKING_DURATION_MS) {
                journalDb.add("isThinking watchdog: stuck for ${(now - thinkingStartedMs)/1000}s — force clearing.")
                isThinking = false
                thinkingStartedMs = 0L
                wantListening = true
                refreshThinkingFaceState()
                scheduleListenRestart(immediate = true)
            }

            // Busy-Brain PR 1: a separate, independent check from the
            // isThinking watchdog above -- deliberately not folded into it,
            // since busyBrainState's own pending window isn't guaranteed to
            // clear in lockstep with isThinking (particularly once PR 2
            // decouples them further). Without this, a genuinely hung
            // generation would permanently block every future AI-style
            // question with "I'm still thinking about your last question."
            // Reuses MAX_THINKING_DURATION_MS rather than a second constant
            // -- the two watchdogs are still effectively coincident in PR 1's
            // world, since isThinking isn't cleared early yet.
            if (busyBrainState.isStuck(now, MAX_THINKING_DURATION_MS)) {
                if (busyBrainState.discard(BusyBrainDiscardReason.TIMEOUT)) {
                    journalDb.add("Busy-Brain watchdog: pending generation stuck — discarding.")
                    refreshThinkingFaceState()
                }
            }

            val shouldBeListening =

                wantListening &&

                        currentMode == Mode.PRESENCE &&

                        !isSpeaking

            if (shouldBeListening) {

                val stale =

                    (lastRecognizerEventMs != 0L && (now - lastRecognizerEventMs) > RECOGNIZER_WATCHDOG_MS)

                // speechEverStarted-gated: before the staggered initial speech setup
                // has fired (see requestSpeechStartup()), speechRecognizer == null is
                // expected by design, not a fault to "fix" -- without this guard the
                // watchdog would call safeSetupSpeech() on its very first 4s tick and
                // defeat the stagger entirely.
                val missing = speechEverStarted && (speechRecognizer == null)

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

        logStartupTiming("tts_oninit status=$status")

        // Two-stage engine preference: setupTts() requests com.google.android.tts
        // explicitly (see its own comment). If that attempt fails outright, retry
        // once with the device's own default engine instead of falling through
        // to the failure branch below -- exactly Scout's original (pre-this-
        // change) behavior for devices where Google TTS isn't available. A
        // SUCCESS status from the Google-engine request does NOT by itself
        // prove Google TTS is what actually bound (see ScoutVoiceSelector's
        // doc comment) -- this guard only decides whether to retry, it never
        // claims which engine ended up bound; applyPreferredVoice() below is
        // what actually verifies the result, from the resolved voice itself.
        if (!awaitingDeviceDefaultTts && status != TextToSpeech.SUCCESS) {

            awaitingDeviceDefaultTts = true

            try { tts.shutdown() } catch (_: Exception) { }

            tts = TextToSpeech(this, this)

            return

        }

        if (status == TextToSpeech.SUCCESS) {

            tts.language = Locale.US

            tts.setPitch(scoutPrefs.getFloat("voice_pitch", 0.98f))

            tts.setSpeechRate(scoutPrefs.getFloat("voice_speed", 0.88f))

            applyPreferredVoice()

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {

                    // TTS lifecycle diagnostics (instrumentation only) -- see
                    // resolveTtsDispatch()'s doc comment. Non-terminal: this
                    // dispatch's entry stays in ttsDispatchSources for
                    // onDone()/onError() to consume.
                    val (startedDispatchId, startedDispatchSource) = resolveTtsDispatch(utteranceId)
                    diagLog.logTtsStarted(startedDispatchId, startedDispatchSource)

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
                    // TTS lifecycle diagnostics (instrumentation only) -- see
                    // resolveTtsDispatch()'s doc comment. Terminal: this dispatch's
                    // entry is removed from ttsDispatchSources -- nothing else will
                    // ever fire for it, so it would otherwise never be cleaned up.
                    val (doneDispatchId, doneDispatchSource) = resolveTtsDispatch(utteranceId)
                    diagLog.logTtsCompleted(doneDispatchId, doneDispatchSource, ttsDurationMs)
                    ttsDispatchSources.remove(doneDispatchId)
                    speakingStartedMs = 0L

                    faceView.setSpeaking(false)

                    isThinking = false

                    refreshThinkingFaceState()

                    if (captionsEnabled) {
                        handler.postDelayed(captionHideRunnable, 2500L)
                    }

                    val now = System.currentTimeMillis()

                    lastSpeechDoneMs = now

                    ttsLockoutUntilMs = now + TTS_LOCKOUT_MS

                    if (!bootFinishedSpeaking) bootFinishedSpeaking = true

                    lastScoutResponseMs = System.currentTimeMillis()

                    // pendingAiAnswer lifecycle fix: capture whether THIS
                    // completing utterance was presence-initiated before the
                    // block below resets the flag -- must be read here, not
                    // after the if-block, since lastUtteranceWasPresenceRemark
                    // is cleared inside it. Passed to
                    // handlePendingAnswerAfterTts() below; see
                    // ScoutPendingAnswerGate's doc comment for why this
                    // matters (a presence-initiated completion must HOLD a
                    // fresh queued answer rather than draining onto it).
                    val completionWasPresenceInitiated = lastUtteranceWasPresenceRemark

                    if (lastUtteranceWasPresenceRemark) {
                        presenceReplyWindowUntilMs = System.currentTimeMillis() + PRESENCE_REPLY_WINDOW_MS
                        lastUtteranceWasPresenceRemark = false
                        // TEMPORARY SMOKE-TEST LOGGING -- remove or disable once A32
                        // testing confirms the behavior.
                        Log.d("ScoutPresenceDebug", "Forty-second reply window opened")
                        handler.postDelayed({
                            Log.d("ScoutPresenceDebug", "Forty-second reply window expired")
                        }, PRESENCE_REPLY_WINDOW_MS)
                    }

                    wantListening = true

                    // Busy-Brain PR 2 / pendingAiAnswer lifecycle fix: deliver,
                    // hold, or expire a held AI answer now that Scout is free,
                    // instead of unconditionally restarting the mic -- this is
                    // the one drain point for pendingAiAnswer (see
                    // deliverAiResult() and handlePendingAnswerAfterTts()).
                    handlePendingAnswerAfterTts(completionWasPresenceInitiated)

                }

                override fun onError(utteranceId: String?) {

                    // TTS lifecycle diagnostics (instrumentation only) -- see
                    // resolveTtsDispatch()'s doc comment. Terminal, same as
                    // onDone(): this dispatch's entry is removed here too.
                    val (erroredDispatchId, erroredDispatchSource) = resolveTtsDispatch(utteranceId)
                    diagLog.logTtsFailed(erroredDispatchId, erroredDispatchSource)
                    ttsDispatchSources.remove(erroredDispatchId)

                    isSpeaking = false
                    speakingStartedMs = 0L

                    faceView.setSpeaking(false)

                    isThinking = false

                    refreshThinkingFaceState()

                    // pendingAiAnswer lifecycle fix: capture before the reset
                    // immediately below -- same reasoning as onDone()'s
                    // matching comment above.
                    val completionWasPresenceInitiated = lastUtteranceWasPresenceRemark

                    // TTS never finished, so no reply window should open for it --
                    // just clear the flag rather than leave it to misattribute later.
                    lastUtteranceWasPresenceRemark = false

                    val now = System.currentTimeMillis()

                    lastSpeechDoneMs = now

                    ttsLockoutUntilMs = now + TTS_LOCKOUT_MS

                    if (!bootFinishedSpeaking) bootFinishedSpeaking = true

                    wantListening = true

                    // Busy-Brain PR 2 / pendingAiAnswer lifecycle fix: same
                    // shared decision as onDone() -- a TTS failure on the
                    // utterance that was in the way must not strand a queued
                    // AI answer indefinitely. See handlePendingAnswerAfterTts().
                    handlePendingAnswerAfterTts(completionWasPresenceInitiated)

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
                // Better Conversation State Phase 1: this is a genuine spoken
                // greeting intended for the user (see ScoutBootStatus/Phrases
                // BOOT_*), so it goes through respond(isPresenceInitiated =
                // true) like any other Scout-first remark -- the same
                // conservative reply window a return greeting or idle-silence
                // remark already gets, so a reply right after boot doesn't
                // need the wake word. respond() calls speak(out, true) and
                // convoDb.logTurn("scout", out) itself; only the boot-specific
                // journal line stays here.
                respond(out, isPresenceInitiated = true)
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

    // Picks Scout's default voice from whatever the currently bound TTS
    // engine actually reports -- see ScoutVoiceSelector's own doc comment
    // for exactly why this never assumes or claims which engine that is.
    // Called once from onInit(), right after language/pitch/speed are set
    // and before the utterance listener is attached -- the same point
    // Scout's voice has always been finalized by, just with an actual
    // choice made instead of silently accepting whatever the engine
    // defaulted to.
    private fun applyPreferredVoice() {

        val allVoices = try {
            tts.voices.orEmpty()
        } catch (_: Exception) {
            emptySet()
        }

        val candidates = allVoices.map { voice ->
            VoiceCandidate(
                name = voice.name,
                languageEn = voice.locale.language == "en",
                countryUs = voice.locale.country == "US",
                quality = voice.quality,
                networkRequired = voice.isNetworkConnectionRequired,
                notInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
            )
        }

        val resolvedName = ScoutVoiceSelector.choose(candidates, PREFERRED_VOICE_NAMES)
        val chosenVoice = resolvedName?.let { name -> allVoices.firstOrNull { it.name == name } }

        if (chosenVoice != null) {
            try { tts.voice = chosenVoice } catch (_: Exception) { }
        }

        // Ground truth, read back after selection -- never assumed, never
        // inferred from engine identity. TextToSpeech.getCurrentEngine()
        // exists at runtime but is annotated @UnsupportedAppUsage (excluded
        // from the public SDK), so it is never used here or anywhere else
        // in this file. getVoice() is public and is the only thing this log
        // line trusts.
        val activeVoiceName = try { tts.voice?.name } catch (_: Exception) { null }
        val diagLine = when {
            activeVoiceName != null && PREFERRED_VOICE_NAMES.contains(activeVoiceName) ->
                "TTS: preferred voice active ($activeVoiceName)"
            activeVoiceName != null ->
                "TTS: fallback voice active ($activeVoiceName) -- preferred voice not found on this device/engine"
            else ->
                "TTS: no offline en-US voice resolved -- using engine's own default"
        }
        journalDb.add(diagLine)

    }

    // TTS lifecycle diagnostics (instrumentation only). Resolves a TTS
    // callback's dispatch id -- preferring the engine's own echoed
    // utteranceId, ground truth for which dispatch this callback belongs to
    // -- and looks up THAT id's own stored source from ttsDispatchSources,
    // never a different (e.g. more recently dispatched) id's. Falls back to
    // NORMAL only if the id has no entry at all, which shouldn't happen for
    // any dispatch this Activity itself issued (see ttsDispatchSources'
    // field doc comment).
    private fun resolveTtsDispatch(utteranceId: String?): Pair<Int, DiagLog.TtsDispatchSource> {
        val id = utteranceId?.toIntOrNull() ?: lastTtsDispatchId
        val source = ttsDispatchSources[id] ?: DiagLog.TtsDispatchSource.NORMAL
        return id to source
    }

    private fun speak(text: String, flush: Boolean, ttsSource: DiagLog.TtsDispatchSource = DiagLog.TtsDispatchSource.NORMAL) {

        wantListening = false

        // Captured before isThinking is cleared below — the delay `when` needs to know
        // whether Scout *was* thinking, not his state after this function already reset it.
        val wasThinking = isThinking

        // TTS lifecycle diagnostics (instrumentation only) -- see the field
        // doc comment above and DiagLog.TtsDispatchSource/logTts*(). Source
        // is stored keyed by this dispatch's own id (not a shared "last"
        // field) so a later callback can never read a different, newer
        // dispatch's source -- see the field doc comment for why that
        // matters specifically for back-to-back/re-entrant dispatches.
        val dispatchId = ttsDispatchCounter.incrementAndGet()
        lastTtsDispatchId = dispatchId
        ttsDispatchSources[dispatchId] = ttsSource
        diagLog.logTtsRequested(dispatchId, ttsSource)

        isThinking = false
        thinkingStartedMs = 0L
        isSpeaking = true
        speakingStartedMs = System.currentTimeMillis()

        refreshThinkingFaceState()

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

                dispatchId.toString()

            )

            diagLog.logTtsSpeakCall(dispatchId, ttsSource, ok = ttsResult != TextToSpeech.ERROR)

            if (ttsResult == TextToSpeech.ERROR) {
                // TTS rejected the utterance — no callback will ever fire, so
                // manually reset all state so Scout can hear again. Also drop
                // this dispatch's entry from ttsDispatchSources -- no
                // onStart()/onDone()/onError() is ever coming to consume it,
                // so it would otherwise sit in the map for the rest of the
                // process's lifetime.
                ttsDispatchSources.remove(dispatchId)
                isSpeaking = false
                isThinking = false
                speakingStartedMs = 0L
                wantListening = true
                faceView.setSpeaking(false)
                refreshThinkingFaceState()
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

    // isPresenceInitiated: true only for Scout-initiated presence remarks (e.g.
    // the idle-silence acknowledgment), never for a real reply to the user.
    // Default false, so every existing call site is unaffected. When true, skips
    // onConversationTurn() -- calling that here would misread 75+ minutes of
    // silence as a long absence and wrongly queue a "welcome back" greeting for
    // the person's next real sentence, even though they never left -- and flags
    // the utterance for the TTS onDone callback to open the presence reply window.
    //
    // isStatusOnly (Busy-Brain PR 2): true for short Scout-side status
    // feedback that isn't conversation content -- a randomly-picked thinking
    // phrase ("Let me think about that," "Give me a moment," ...), "I'm
    // still thinking about your last question," "I'll get to that once I've
    // finished my last thought." These still speak
    // normally, still extend the conversation, still protect the self-echo
    // guard (lastScoutUtteranceNormalized) exactly like any other respond()
    // call -- they're excluded only from convoDb (so they never leak into
    // TinyLlama's own conversation-history context) and from the "repeat
    // that" cache (so a status line can never become what gets repeated).
    private fun respond(
        out: String,
        isPresenceInitiated: Boolean = false,
        isStatusOnly: Boolean = false,
        ttsSource: DiagLog.TtsDispatchSource = DiagLog.TtsDispatchSource.NORMAL
    ) {

        lastScoutResponseMs = System.currentTimeMillis()

        lastScoutUtteranceNormalized = TextNormalizer.normalizeUtterance(out)

        if (isPresenceInitiated) lastUtteranceWasPresenceRemark = true

        // Better Conversation State Phase 1. Deliberately does not touch
        // lastSpeechDoneMs, ttsLockoutUntilMs, or lastScoutUtteranceNormalized
        // above -- those audio-safety mechanisms (TTS lockout, mic-restart
        // cooldown, self-echo matching) are untouched by conversation state and
        // keep working exactly as before, including right after an explicit
        // close (see handleGoodbyeIntent()/handleStopListeningIntent()/
        // handleCourtesy(), which close *after* this call returns).
        if (isPresenceInitiated) {
            if (conversationState.openFromScoutInitiated(lastScoutResponseMs)) {
                diagLog.logConversationStarted(startedByScout = true)
            }
        } else {
            conversationState.onScoutTurn(lastScoutResponseMs)
        }

        speak(out, true, ttsSource)

        if (!isStatusOnly) {
            convoDb.logTurn("scout", out)
        }

        if (!isPresenceInitiated) {
            presenceDecider.onConversationTurn()
            hasHadConversationThisSession = true
        }

        finishThinking()

        // Cache for "repeat that" — only real answers (5+ words), never status-only lines
        if (!isStatusOnly && out.trim().split(" ").size >= 5) {
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

    // pendingAiAnswer lifecycle fix. Called from the same two places as
    // scheduleBusyBrainFiller() below (both real, once-per-question sites --
    // Gemini's REQUEST_STARTED, and TinyLlama's dispatch point), each already
    // gated on busyBrainState.tryBegin() having just returned true. A
    // genuinely NEW generative request supersedes any older, still-
    // undelivered pendingAiAnswer -- that older answer belongs to a
    // DIFFERENT, now-superseded question, so leaving it queued would let it
    // wrongly drain onto whatever THIS new question's own answer -- or some
    // unrelated later utterance -- turns out to be.
    //
    // Never reached for a courtesy/deterministic reply (neither ever calls
    // tryBegin() at all, so pendingAiAnswer is untouched by those), and never
    // reached a second time for a same-question Gemini -> TinyLlama fallback
    // (tryBegin() is already a no-op there).
    private fun onGenerativeRequestBegan() {
        finishThinking()
        scheduleBusyBrainFiller()
        if (pendingAiAnswer != null) {
            clearPendingAiAnswer()
            diagLog.logPendingAnswerDiscarded(DiagLog.PendingAnswerDiscardReason.SUPERSEDED)
        }
    }

    // Busy-Brain polish. Called only when busyBrainState.tryBegin() has just
    // returned true for this question -- i.e. once per question, never on a
    // Gemini -> TinyLlama same-question fallback (tryBegin() is a no-op
    // there, so this is never called a second time for it). Schedules a
    // one-time check BUSY_BRAIN_FILLER_DELAY_MS from now; if the generation
    // is still pending at that point, speaks exactly one randomly-picked
    // thinking phrase as status-only speech. If the generation has already
    // resolved (delivered normally if Scout was idle, held in
    // pendingAiAnswer otherwise) or was discarded (explicit close/watchdog),
    // busyBrainState.isPending is already false by then and nothing is said
    // -- re-checked fresh at fire time, never assumed. Does not touch
    // isThinking/mic-availability at all -- those are cleared immediately by
    // the caller, unaffected by whether this filler ends up speaking.
    //
    // Also re-checks ScoutBusyBrainDelivery.shouldQueue() (the same busy
    // check deliverAiResult() uses) before actually speaking -- without
    // this, the filler could itself QUEUE_FLUSH over a deterministic answer
    // Scout happens to be speaking right at the 2-second mark (e.g. the user
    // asked a safe follow-up while the generation was pending, and that
    // follow-up's own reply is still playing). If Scout is busy at the
    // check, the filler is simply skipped for this question rather than
    // rescheduled -- it's a reassurance, not essential information, and the
    // user already hears Scout actively speaking in that exact moment.
    private fun scheduleBusyBrainFiller() {
        handler.postDelayed({
            if (busyBrainState.isPending && !ScoutBusyBrainDelivery.shouldQueue(isSpeaking, isThinking)) {
                respond(BUSY_BRAIN_FILLERS.random(), isStatusOnly = true)
            }
        }, BUSY_BRAIN_FILLER_DELAY_MS)
    }

    // Busy-Brain PR 2. The single delivery point for a real Gemini/TinyLlama
    // answer (or a final failure message like "I'm not sure about that
    // one.") once its generation has resolved. Marks the pending question
    // complete either way -- what differs is whether the answer gets spoken
    // now or held.
    //
    // Never uses QUEUE_FLUSH over something already being said: if Scout is
    // mid-utterance (isSpeaking) or mid-dispatch of another accepted request
    // (isThinking), the answer is held in pendingAiAnswer and delivered from
    // the TTS onDone() drain check below instead, prefixed with "And about
    // your earlier question--" since something else happened while it
    // waited. If Scout is genuinely idle, it's spoken immediately with no
    // prefix.
    private fun deliverAiResult(answer: String) {
        busyBrainState.complete()
        refreshThinkingFaceState()
        if (ScoutBusyBrainDelivery.shouldQueue(isSpeaking, isThinking)) {
            // pendingAiAnswer lifecycle fix: the 30s expiry window starts
            // here, when the answer is actually queued -- not when
            // generation began. See PENDING_AI_ANSWER_MAX_AGE_MS.
            pendingAiAnswer = answer
            pendingAiAnswerQueuedAtMs = System.currentTimeMillis()
        } else {
            respond(ScoutBusyBrainDelivery.phraseDelivery(answer, wasQueued = false))
        }
    }

    // pendingAiAnswer lifecycle fix. The single shared decision point for
    // both onDone() and onError()'s pending-answer drain check, so the two
    // TTS callback paths can never drift into different delivery/hold/expiry
    // rules. wasPresenceInitiated must be the value the caller captured
    // BEFORE its own lastUtteranceWasPresenceRemark reset -- see both
    // callbacks' own comments and ScoutPendingAnswerGate's doc comment for
    // the full priority order (expiry checked before hold).
    private fun handlePendingAnswerAfterTts(wasPresenceInitiated: Boolean) {
        val queuedAnswer = pendingAiAnswer
        if (queuedAnswer == null) {
            // NONE -- nothing queued; ScoutPendingAnswerGate.decide() would
            // return NONE regardless of the other inputs, so there's nothing
            // useful to log or decide here.
            scheduleListenRestart(immediate = true)
            return
        }
        when (
            ScoutPendingAnswerGate.decide(
                hasQueuedAnswer = true,
                wasPresenceInitiated = wasPresenceInitiated,
                queuedAtMs = pendingAiAnswerQueuedAtMs,
                nowMs = System.currentTimeMillis(),
                maxAgeMs = PENDING_AI_ANSWER_MAX_AGE_MS
            )
        ) {
            ScoutPendingAnswerGate.Decision.DELIVER -> {
                clearPendingAiAnswer()
                respond(
                    ScoutBusyBrainDelivery.phraseDelivery(queuedAnswer, wasQueued = true),
                    ttsSource = DiagLog.TtsDispatchSource.DRAINED_PENDING_ANSWER
                )
            }
            ScoutPendingAnswerGate.Decision.EXPIRED -> {
                clearPendingAiAnswer()
                diagLog.logPendingAnswerDiscarded(DiagLog.PendingAnswerDiscardReason.EXPIRED)
                scheduleListenRestart(immediate = true)
            }
            ScoutPendingAnswerGate.Decision.HOLD -> {
                // Fresh answer, but this completion was presence-initiated
                // (boot greeting, idle-silence remark, return greeting,
                // Companion Moment) -- held for the next non-presence
                // completion, still subject to expiry from its original
                // queued time.
                scheduleListenRestart(immediate = true)
            }
            ScoutPendingAnswerGate.Decision.NONE -> {
                // Structurally unreachable here -- queuedAnswer == null
                // already returned above. Kept only so the when is exhaustive.
                scheduleListenRestart(immediate = true)
            }
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

    // Every fact Scout holds, across the user's own entity and every named
    // person/pet entity resolved from teaching (Diana, Nicolas, ...) -- not just
    // the user's own facts. The user's own entity is listed first so it survives
    // any downstream cap ahead of named entities' facts. Tagged with which entity
    // each fact is about so callers can attribute it correctly (e.g. "Diana's
    // birthday" vs. an unqualified "birthday").
    private fun getAllKnownFacts(): List<Triple<String, String, String>> {
        val entities = truthDb.getAllEntities().filter { it != ENTITY_SCOUT }
        val ordered = listOfNotNull(ENTITY_USER_PRIMARY.takeIf { it in entities }) +
            entities.filter { it != ENTITY_USER_PRIMARY }
        val out = mutableListOf<Triple<String, String, String>>()
        for (entity in ordered) {
            for ((key, value) in truthDb.getAllFacts(entity)) {
                if (key == "aliases") {
                    // Expand the comma-joined alias list into individual entries so
                    // the memory gate can recognize each nickname on its own, not
                    // just the literal joined string.
                    for (alias in truthDb.getAliases(entity)) out.add(Triple(entity, "alias", alias))
                } else {
                    out.add(Triple(entity, key, value))
                }
            }
        }
        return out
    }

    private fun handleUnknownIntent(qNorm: String) {

        // Structural guarantee, not a phrasing list: anything that might concern
        // the owner, family, pets, preferences, or learned facts is checked here,
        // before Gemini is ever considered. If it matches, this returns and
        // Gemini is never called for this query -- handlePersonalMemoryQuery()
        // below has no path to scoutGeminiManager at all. The gate is deliberately
        // broad (see ScoutMemoryGate) since a false positive just costs a wasted
        // TruthDb check, while a false negative would mean a personal question
        // reaching a fact-blind Gemini.
        val flatFacts = getAllKnownFacts().map { (_, k, v) -> k to v }
        if (ScoutMemoryGate.isPossiblePersonalMemoryQuery(qNorm.lowercase().trim(), flatFacts)) {
            handlePersonalMemoryQuery(qNorm, flatFacts)
            return
        }

        // Same structural guarantee as the memory gate above, for a distinct
        // capability: a camera/vision-shaped question that ScoutIntentRouter's
        // VISION patterns didn't literally match must still never reach
        // fact-blind, vision-blind Gemini -- real-device finding: Gemini's
        // system prompt carries no scene/face data and no confirmation Scout
        // even has a camera, so it falsely claimed "I don't have a camera to
        // see my surroundings." handleVisionIntent() is fully deterministic
        // and never denies having a camera, only that current data is stale
        // or unclear -- see ScoutVisionGate's doc comment for why this is its
        // own gate rather than folded into ScoutMemoryGate.
        if (ScoutVisionGate.isPossibleVisionQuery(qNorm.lowercase().trim())) {
            handleVisionIntent()
            return
        }

        // Busy-Brain PR 1: never start a second generation while one is
        // already pending. A read-only check -- the actual isPending flip
        // happens below, only once a real generation actually starts (see
        // REQUEST_STARTED and tryTinyLlamaOrFallback()), since tryGemini()
        // can also resolve synchronously (a cached reply, a cooldown block,
        // an already-in-flight block) without ever truly starting one.
        if (busyBrainState.isPending) {
            respond(BUSY_BRAIN_STILL_THINKING, isStatusOnly = true)
            return
        }

        val convo = convoDb.getLastTurns(limit = 6)

        val usedGemini = scoutGeminiManager.tryGemini(
            qNorm, convo,
            onDecision = { decision ->
                diagLog.logGeminiDecision(decision)
                if (decision == DiagLog.GeminiDecision.REQUEST_STARTED) {
                    diagLog.logBrainStarted(DiagLog.BrainSource.GEMINI)
                    // Busy-Brain polish: mic reopens right here, immediately,
                    // exactly as before -- tryBegin() only returns true the
                    // first time (never on a re-entrant call). Only the
                    // thinking-phrase SPEECH is now delayed (see
                    // scheduleBusyBrainFiller()); finishThinking() is called
                    // directly since nothing is spoken synchronously anymore
                    // to clear isThinking via respond() the way it used to.
                    // pendingAiAnswer lifecycle fix: also supersedes an older
                    // undelivered queued answer -- see onGenerativeRequestBegan().
                    if (busyBrainState.tryBegin(System.currentTimeMillis())) {
                        onGenerativeRequestBegan()
                    }
                }
            },
            onAnswered = { diagLog.logNetwork(DiagLog.NetworkArea.GEMINI, true); pendingBrainSource = "Gemini (online)" },
            onFailed   = { diagLog.logNetwork(DiagLog.NetworkArea.GEMINI, false); tryTinyLlamaOrFallback(qNorm) },
            // Capability-integrity backstop -- Gemini is fact-blind (and
            // vision-blind: its system prompt carries no scene/face data) and
            // has no write path of its own, so a free-text reply could claim
            // durable retention ("I'll remember that!") for a teaching-shaped
            // question, globally deny having any memory at all ("I don't have
            // the capability to learn"), promise a reminder Scout has no way
            // to schedule ("I'll remind you tomorrow"), falsely deny having a
            // camera ("I don't have a camera to see my surroundings"), or
            // break its own identity by treating its own name as an unrelated
            // third party ("Scout is not mentioned... may have moved or
            // passed away"). applyScoutCapabilityIntegrityGuards() catches
            // all five -- see its doc comment in ScoutFactExtractor.
            deliverResult = { answer -> deliverAiResult(ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(qNorm, answer)) },
            shouldDiscardResult = { busyBrainState.isDiscarded() },
            onDiscarded = {
                busyBrainState.discardReason?.let { diagLog.logBusyBrainDiscarded(it.toDiagReason(), DiagLog.BrainSource.GEMINI) }
                busyBrainState.complete()
                refreshThinkingFaceState()
            }
        )

        if (usedGemini) return

        // Gemini not available (disabled / no key / no internet) — go straight to TinyLlama
        tryTinyLlamaOrFallback(qNorm)

    }

    // TruthDb is authoritative here: an empty fact store means a hard, deterministic
    // "I don't know" (voice.say, no LLM call at all -- can't hallucinate what it never
    // asked a model for). A non-empty store reuses tryTinyLlamaOrFallback() as-is --
    // it already never calls Gemini and already grounds every reply in TruthDb's
    // facts, so this needs no separate/duplicated generation path.
    private fun handlePersonalMemoryQuery(qNorm: String, facts: List<Pair<String, String>>) {

        if (facts.isEmpty()) {
            respond(voice.say("DONT_KNOW"))
            return
        }

        // "who is Diana?" / "who's Diana?" -- answered by direct lookup instead of
        // asking TinyLlama to connect the dots itself. Confirmed on-device: even
        // with "wife's name: Diana" right there in its facts, a small model asked
        // "who is Diana" doesn't reliably infer "she's your wife" -- it talked
        // about the name Diana in general instead. This makes that one specific,
        // common question shape deterministic, the same way ASK_WIFE_NAME etc.
        // already answer "who is my wife" by direct lookup rather than guessing.
        Regex("""\bwho(?:'s|\s+is)\s+([a-z]+)\b""").find(qNorm.lowercase())?.let { m ->
            val name = m.groupValues[1]
            findRelationForName(name)?.let { relation ->
                respond("${name.replaceFirstChar { it.uppercase() }} is your $relation.")
                return
            }
        }

        // Busy-Brain PR 1: this is a second, genuinely new dispatch point
        // into the AI backends (distinct from handleUnknownIntent()'s own
        // check above -- reached only when the direct-lookup fast paths
        // above didn't resolve it), so it needs the same gate.
        if (busyBrainState.isPending) {
            respond(BUSY_BRAIN_STILL_THINKING, isStatusOnly = true)
            return
        }

        tryTinyLlamaOrFallback(qNorm)

    }

    // Reverse lookup: does this name (or one of its aliases) belong to the wife,
    // son, or dog? Checks aliases too, so "who is Nick" resolves the same way as
    // "who is Nicolas" once Nick is taught as a nickname.
    private fun findRelationForName(name: String): String? {
        val n = name.trim().lowercase()
        val relations = listOf(FactKey.WIFE_NAME to "wife", FactKey.SON_NAME to "son", FactKey.DOG_NAME to "dog")
        for ((key, label) in relations) {
            val stored = truthDb.getFactValue(ENTITY_USER_PRIMARY, key)?.trim()?.lowercase() ?: continue
            if (stored == n) return label
            if (truthDb.getAliases(stored).any { it.trim().lowercase() == n }) return label
        }
        return null
    }

    private fun tryTinyLlamaOrFallback(qNorm: String) {

        // When Gemini is in cooldown (quota or rate-limit), announce it once.
        // Only do this if Gemini is actually enabled — if the user deliberately
        // turned off Online Features, a cooldown from earlier use is irrelevant.
        if (isGeminiEnabled() && scoutGeminiManager.isInCooldown()) {
            if (scoutGeminiManager.speakUnavailableIfNeeded()) {
                // Busy-Brain PR 2: reached via Gemini's own onFailed fallback,
                // isPending may already be true from Gemini's REQUEST_STARTED
                // -- no TinyLlama generation is being dispatched after all,
                // so free the gate here. Harmless no-op on a fresh dispatch
                // (isPending is already false in that case).
                busyBrainState.complete()
                // speakUnavailableIfNeeded() already spoke (and refreshed the
                // face) above -- that refresh ran before isPending flipped
                // false here, so a second refresh is needed or the face could
                // be left showing "thinking" after this generation's slot is
                // actually free.
                refreshThinkingFaceState()
                return
            }
        }

        if (LlamaEngine.isReady) {

            val convo = convoDb.getLastTurns(limit = 2)

            val scoutName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"

            // Every fact TruthDb holds -- the user's own, plus anyone/anything
            // named Scout knows about (Diana, Nicolas, ...) -- not just a
            // hand-picked few. Capped defensively against nCtx=512 overflow, with
            // the user's own facts ordered first (see getAllKnownFacts()), so
            // foundational facts survive the cap even as more get added over time.
            //
            // This is a minimal, safe placeholder, not the final retrieval design.
            // It's an oldest-first dump-and-cap, not relevance-based selection --
            // once the fact count exceeds 12, a newer fact that's actually the one
            // being asked about could still get pushed out. Selecting only the
            // facts relevant to the current question is a later hardening step.
            //
            // Speaker-neutral by default: knowing the primary user's own name is
            // Patrick does not mean whoever is currently speaking IS Patrick --
            // there's no speaker identification in this app today (see
            // ConversationDb's role column: every recognized utterance is logged
            // as generic "user", regardless of who spoke it). Excluded here only
            // from this generic conversational fact dump, so an ordinary reply to
            // Diana or Elijah can't casually address them as "Patrick". Not
            // excluded from getAllKnownFacts() itself, which stays untouched and
            // still feeds the personal-memory gate above ("who is Patrick" still
            // needs to see this fact to route correctly) and TruthDb, which stays
            // untouched -- handleAskMyNameIntent() and the personal-memory direct
            // lookup already answer "what's my name"/"who is Patrick" straight
            // from TruthDb, not from this list.
            val allFacts = getAllKnownFacts()
                .filterNot { (entity, key, _) -> entity == ENTITY_USER_PRIMARY && key == FactKey.NAME }
                .take(12)
            val factsLine = if (allFacts.isNotEmpty()) {
                "Known facts: " +
                    allFacts.joinToString(" ") { (entity, k, v) ->
                        val label = if (entity == ENTITY_USER_PRIMARY) keyToHuman(k)
                                    else "${ScoutEntityResolver.displayName(entity)}'s ${keyToHuman(k)}"
                        "$label: $v."
                    } + " "
            } else ""

            val system = """
${factsLine}You are $scoutName.

You are a warm, calm companion who lives with the family.

You speak out loud, listen through the microphone, and can see through the camera when vision is active.

Always answer as Scout.

If asked about the user's name, family, or personal facts, answer only using the facts given above. Do not invent names, relationships, or details that aren't listed there.

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

            // Busy-Brain PR 1: the token is now bumped right here, only when a
            // TinyLlama generation is actually about to be dispatched -- not
            // unconditionally at the top of handleQuery() (see the comment
            // there). Reached either as a fresh dispatch (already gated by
            // busyBrainState.isPending checks in the two callers above) or as
            // Gemini's own same-question fallback -- either way, a fresh
            // token is correct here since it's a genuinely new native call.
            val myGeneration = ScoutLlamaController.newGeneration()
            diagLog.logBrainStarted(DiagLog.BrainSource.TINYLLAMA)
            diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_STARTED)
            val llamaGenStart = System.currentTimeMillis()

            // Busy-Brain polish: only clear isThinking / schedule the filler
            // check the first time this question begins pending --
            // tryBegin() returns false (a no-op) when reached as Gemini's
            // own same-question fallback, since Gemini's REQUEST_STARTED
            // already did both and the mic is already open.
            // pendingAiAnswer lifecycle fix: also supersedes an older
            // undelivered queued answer -- see onGenerativeRequestBegan().
            // A same-question Gemini->TinyLlama fallback reaches here too,
            // but tryBegin() is already a no-op for it, so it never
            // re-supersedes anything the way a genuinely new question would.
            if (busyBrainState.tryBegin(System.currentTimeMillis())) {
                onGenerativeRequestBegan()
            }

            // ScoutLlamaController runs this on its own process-wide single-thread
            // executor and already only invokes this callback (on the main thread) if
            // myGeneration is still current -- covers both "a newer question arrived"
            // and "this Activity instance was destroyed and replaced" with the same
            // check, so there's no separate staleness check needed here anymore. A
            // discard (stale token) is logged internally by ScoutLlamaController
            // itself, not via a callback here -- this callback is Activity-owned
            // (captures diagLog) and could otherwise run after this Activity was
            // destroyed, purely to log a diagnostic event.
            ScoutLlamaController.generateAsync(
                token = myGeneration,
                prompt = sb.toString(),
                nPredict = 100
            ) { reply ->

                // Busy-Brain PR 1: the conversation was explicitly closed, or
                // the stuck-generation watchdog gave up waiting, while this
                // generation was in flight -- its answer must never be
                // spoken. Distinct from the token check above: that guards
                // against a stale Activity instance/superseded question,
                // this guards against a still-valid, still-expected result
                // arriving after the user already said goodbye.
                if (busyBrainState.isDiscarded()) {
                    busyBrainState.discardReason?.let {
                        diagLog.logBusyBrainDiscarded(it.toDiagReason(), DiagLog.BrainSource.TINYLLAMA)
                    }
                    busyBrainState.complete()
                    refreshThinkingFaceState()
                    return@generateAsync
                }

                // Busy-Brain PR 2: deliverAiResult() marks the question
                // complete and either speaks the result now or holds it if
                // Scout is currently speaking/handling something else --
                // see its own doc comment. Applies to the failure message
                // too ("I'm not sure about that one.") since it's just as
                // subject to the same TTS-collision risk as a real answer.
                val genMs = System.currentTimeMillis() - llamaGenStart
                if (!reply.isNullOrBlank()) {
                    diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_DONE, genMs)
                    pendingBrainSource = "TinyLlama (offline)"
                    // Capability-integrity backstop -- same reasoning as the
                    // Gemini deliverResult above, applied after the existing
                    // identity-leak cleanup rather than instead of it.
                    deliverAiResult(ScoutFactExtractor.applyScoutCapabilityIntegrityGuards(qNorm, cleanOfflineReply(reply.trim())))
                } else {
                    diagLog.logLlama(DiagLog.LlamaEvent.GENERATION_FAILED)
                    deliverAiResult("I'm not sure about that one.")
                }

            }

            return

        }

        if (LlamaEngine.isLoading) {

            // Busy-Brain PR 2: none of these three remaining branches ever
            // dispatch a real generation -- each frees the gate (harmless
            // no-op on a fresh dispatch; matters when reached via Gemini's
            // onFailed fallback with isPending already true) and, when it
            // does speak, goes through deliverAiResult() rather than
            // respond() directly, for the same TTS-collision safety as any
            // other AI-path outcome.
            busyBrainState.complete()
            // Explicit refresh, not left to deliverAiResult() below --
            // warmingUpSaidThisSession can make that call conditional
            // (spoken once per session only), so this branch can return
            // without ever calling deliverAiResult() at all.
            refreshThinkingFaceState()

            if (!warmingUpSaidThisSession) {
                warmingUpSaidThisSession = true
                deliverAiResult("My offline brain is still warming up. Give me just a moment.")
            }

            return

        }

        // On-demand load: neither Gemini nor TinyLlama ready — trigger load now.
        tryLoadOfflineBrain()

        if (LlamaEngine.isLoading) {

            busyBrainState.complete()
            // Same reasoning as the branch above -- warmingUpSaidThisSession
            // can skip deliverAiResult() entirely.
            refreshThinkingFaceState()

            if (!warmingUpSaidThisSession) {
                warmingUpSaidThisSession = true
                deliverAiResult("My offline brain is warming up. Ask me again in just a moment.")
            }

            return

        }

        busyBrainState.complete()
        refreshThinkingFaceState()

        // Nothing available — only report a connectivity problem if online features were
        // actually expected to work. When the user deliberately turned off online features,
        // don't say "having trouble connecting" — they know, they turned it off.
        if (isGeminiEnabled()) {
            // speakUnavailableIfNeeded() speaks (if anything) via its own
            // internally-injected respond() call, not deliverAiResult() --
            // a narrow, accepted gap for this rare "nothing worked at all"
            // state, consistent with keeping PR 2's scope narrow.
            scoutGeminiManager.speakUnavailableIfNeeded()
        } else {
            deliverAiResult("I'm working offline right now, so that one's a bit beyond me.")
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
            respond("I don't check calendars right now. Here's Calendar Awareness in Settings, if you'd like to turn it on.")
            openSettingsScreen(SettingsActivity.S_CONNECTED)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            respond("I don't have calendar access yet. Here's Calendar Awareness in Settings, if you'd like to turn it on.")
            openSettingsScreen(SettingsActivity.S_CONNECTED)
            return
        }

        val clean = qNorm.lowercase().trim()

        // "am I free on July 10th" / "what do I have on the 10th of July" / "are we busy
        // next Saturday" — arbitrary date lookup, checked first since an explicit date is
        // the most specific, unambiguous signal available.
        val parsedDate = CalendarDateParser.parseDate(clean)

        // Captured only by the three branches below that name exactly one
        // unambiguous event -- never by the multi-event listings (today/
        // tomorrow/this-week), which stay deliberately silent on follow-up
        // since there'd be no way to know which event a reply refers to.
        var singleEvent: CalendarEvent? = null

        val out = if (parsedDate != null) {
            val events = calendarReader.eventsOnDate(parsedDate.timeInMillis)
            if (events.size == 1) singleEvent = events[0]
            describeCalendarForDate(
                events,
                parsedDate,
                isFreeBusyPhrasing = clean.contains("free") || clean.contains("busy") || clean.contains("available")
            )
        } else if (clean.contains("next event") || clean.contains("next appointment")) {
            val event = calendarReader.nextEvent()
            singleEvent = event
            describeNextCalendarEvent(event, timeOnly = clean.contains("what time"))
        } else {
            // Checked before the bare day-keyword branches below — otherwise a title
            // question that happens to end in a day word ("when is the vet appointment
            // tomorrow") would get shadowed by the "tomorrow" check and list the whole day
            // instead of answering about that one event. Trailing day words and a leading
            // article are stripped from the captured keyword so the search term matches a
            // plain event title ("Vet Appointment") instead of the full noisy phrase.
            val keyword = Regex("""\b(?:when is|what time is)\s+(?!my\b(?!\s+next\b))([a-z0-9' ]+?)\??$""")
                .find(clean)?.groupValues?.get(1)?.trim()
                ?.removeSuffix(" today")?.removeSuffix(" tomorrow")?.removeSuffix(" this week")
                // "my next injection" / "next injection" -> "injection" -- findByTitle()
                // requires the event's title to contain the whole search term, so filler
                // words like "my"/"next" have to come off before matching a bare event
                // title such as "injection".
                ?.removePrefix("my next ")?.removePrefix("my ")?.removePrefix("next ")
                ?.removePrefix("the ")?.removePrefix("a ")?.removePrefix("an ")
                ?.trim()

            when {
                !keyword.isNullOrBlank() -> {
                    val event = calendarReader.findByTitle(keyword)
                    singleEvent = event
                    describeCalendarTitleMatch(event, keyword)
                }

                clean.contains("tomorrow") ->
                    describeCalendarEvents(calendarReader.eventsTomorrow(), "tomorrow")

                clean.contains("this week") ->
                    describeCalendarEvents(calendarReader.eventsThisWeek(), "this week")

                else ->
                    describeCalendarEvents(calendarReader.eventsToday(), "today")
            }
        }

        respond(singleEvent?.let { appendCalendarFollowupIfWarranted(it, out) } ?: out)

    }

    // Calendar Follow-up -- notice/ask/learn/remember. Purely reactive: only
    // ever called from handleCalendarIntent() above, with the single
    // unambiguous event a direct question just described -- never from a
    // background scan. Birthday/Anniversary: asks at most once, only if
    // TruthDb doesn't already know; if it does already know, that knowledge
    // is spoken (see describeKnownOwner()) rather than only suppressing the
    // question. Otherwise, sets pendingCalendarFollowup so the very next turn
    // can resolve it (see handleQuery()). Doctor: a one-off caring question
    // with no data path at all.
    private fun appendCalendarFollowupIfWarranted(event: CalendarEvent, baseAnswer: String): String {

        val topic = CalendarFollowupMatcher.matchTopic(event.title) ?: return baseAnswer

        if (topic == CalendarFollowupTopic.DOCTOR) {
            pendingCalendarFollowup = PendingCalendarFollowup.DoctorCheckIn(System.currentTimeMillis())
            return "$baseAnswer Is everything okay?"
        }

        val (month, day) = CalendarFollowupMatcher.canonicalMonthDay(event.startMs, event.allDay)
        val factKeyPrefix = if (topic == CalendarFollowupTopic.ANNIVERSARY) "anniversary" else "birthday"
        val knownOwners = CalendarFollowupMatcher.findDateOwners(getAllKnownFacts(), factKeyPrefix, month, day)
        if (knownOwners.isNotEmpty()) {
            // Already known -- never ask again. Where TruthDb's match is a
            // single unambiguous name, speak it instead of only suppressing
            // the question; an ambiguous or unowned match (see
            // describeKnownOwner()) falls back to the plain base answer
            // rather than guessing.
            val known = CalendarFollowupMatcher.describeKnownOwner(knownOwners, topic, ENTITY_USER_PRIMARY)
            return if (known != null) "$baseAnswer $known" else baseAnswer
        }

        pendingCalendarFollowup = PendingCalendarFollowup.Clarification(
            topic = topic,
            eventTitle = event.title,
            eventDateMs = event.startMs,
            eventAllDay = event.allDay,
            askedAtMs = System.currentTimeMillis()
        )

        val question = if (topic == CalendarFollowupTopic.ANNIVERSARY) "Whose anniversary is that?" else "Whose birthday is that?"
        return "$baseAnswer $question"

    }

    // Arbitrary-date lookup ("am I free on July 10th") -- phrased as a free/busy answer
    // when the question was framed that way, otherwise as a plain listing like the
    // today/tomorrow/this-week variants.
    private fun describeCalendarForDate(events: List<CalendarEvent>, date: Calendar, isFreeBusyPhrasing: Boolean): String {
        val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.US)
        val label = dateFmt.format(Date(date.timeInMillis))
        if (events.isEmpty()) {
            return if (isFreeBusyPhrasing) {
                "You're free on $label — nothing on the calendar."
            } else {
                "You don't have anything on the calendar on $label."
            }
        }
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val parts = events.take(5).map { e ->
            if (e.allDay) e.title else "${e.title} at ${timeFmt.format(Date(e.startMs))}"
        }
        val prefix = if (isFreeBusyPhrasing) "You're not free on $label — you have" else "Here's what's on the calendar for $label:"
        return "$prefix ${parts.joinToString(". ")}."
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

    // Resolves a reply to a pending birthday/anniversary Clarification against
    // Scout's already-known entities/relations only -- never guesses. On
    // success, writes the durable fact to TruthDb (participant-scoped key for
    // anniversary -- see FactKey.custom() usage below) and speaks a
    // deterministic confirmation; on failure, changes nothing and returns
    // false so the caller falls through to normal handling of whatever the
    // user actually said.
    private fun tryResolveCalendarClarification(qNorm: String, pending: PendingCalendarFollowup.Clarification): Boolean {

        val aliasMap = ScoutEntityResolver.buildAliasMap(truthDb, ENTITY_USER_PRIMARY)
        val knownEntitySlugs = aliasMap.values.toSet()

        // Reuses ScoutEntityResolver's existing wife/son/dog relationship
        // resolution rather than a separate name-only understanding system.
        // resolveEntity() falls back to returning the bare relation word
        // itself (e.g. "wife") when that relation isn't actually known yet --
        // only entries that resolve to the primary user or a genuinely known
        // entity are kept, so a not-yet-taught relation is never guessed.
        val resolvedRelations = mutableMapOf<String, String>()
        for (rel in setOf("wife", "husband", "spouse", "son", "daughter", "kid", "child", "dog", "cat", "pet")) {
            val resolved = ScoutEntityResolver.resolveEntity(rel, truthDb, ENTITY_USER_PRIMARY)
            if (resolved == ENTITY_USER_PRIMARY || resolved in knownEntitySlugs) {
                resolvedRelations[rel] = resolved
            }
        }

        val resolvedEntity = CalendarFollowupMatcher.resolveClarificationReply(
            qNorm, pending.topic, aliasMap, resolvedRelations, ENTITY_USER_PRIMARY
        ) ?: return false

        val (month, day) = CalendarFollowupMatcher.canonicalMonthDay(pending.eventDateMs, pending.eventAllDay)
        // 2000 (a leap year) is used as the display-only placeholder year, not
        // an unset/cleared Calendar's implicit 1970 default -- 1970 isn't a
        // leap year, so a February 29th date would otherwise silently roll
        // over to March 1st here.
        val cal = Calendar.getInstance().apply { clear(); set(2000, month, day) }
        val displayDate = SimpleDateFormat("MMMM d", Locale.US).format(cal.time)
        val displayName = ScoutEntityResolver.displayName(resolvedEntity)

        when (pending.topic) {

            CalendarFollowupTopic.BIRTHDAY -> {
                truthDb.upsertFact(resolvedEntity, "birthday", displayDate, 1.0f, "calendar_clarification")
                val possessive = if (resolvedEntity == ENTITY_USER_PRIMARY) "your" else "$displayName's"
                respond("Got it. I'll remember $possessive birthday is $displayDate.")
            }

            CalendarFollowupTopic.ANNIVERSARY -> {
                // Participant-scoped key -- FactKey.custom() is the same
                // flexible-key builder TeachExtractor's own relation-prefixed
                // facts already use (e.g. "daughter_name"). Binds to the
                // specific named entity, not to whichever name WIFE_NAME
                // happens to point at, so this stays correct even if that
                // pointer is ever corrected later.
                truthDb.upsertFact(
                    ENTITY_USER_PRIMARY, FactKey.custom("anniversary_with_$resolvedEntity"),
                    displayDate, 1.0f, "calendar_clarification"
                )
                respond("Got it. I'll remember your anniversary with $displayName is $displayDate.")
            }

            CalendarFollowupTopic.DOCTOR -> return false // unreachable -- Clarification never carries DOCTOR

        }

        return true

    }

    // "whose birthday/anniversary is [date]" -- deterministic TruthDb recall,
    // never Gemini or TinyLlama. See CalendarFollowupMatcher.findDateOwners().
    private fun handleWhoseDateEventIntent(qNorm: String) {

        val clean = qNorm.lowercase().trim()

        val parsedDate = CalendarDateParser.parseDate(clean)
        if (parsedDate == null) {
            respond("I'm not sure which date you mean.")
            return
        }

        val prefix = if (clean.contains("anniversary")) "anniversary" else "birthday"
        val matches = CalendarFollowupMatcher.findDateOwners(
            getAllKnownFacts(), prefix,
            parsedDate.get(Calendar.MONTH), parsedDate.get(Calendar.DAY_OF_MONTH)
        )

        respond(describeDateOwnerMatches(matches, prefix))

    }

    private fun describeDateOwnerMatches(matches: List<DateOwnerMatch>, prefix: String): String {
        if (matches.isEmpty()) return "I don't have a $prefix on record for that date."
        val parts = matches.map { m ->
            when {
                prefix == "anniversary" && m.participantSlug != null ->
                    "your anniversary with ${ScoutEntityResolver.displayName(m.participantSlug)}"
                prefix == "anniversary" ->
                    "your anniversary — I don't have a record of who it's with"
                m.entity == ENTITY_USER_PRIMARY -> "your birthday"
                else -> "${ScoutEntityResolver.displayName(m.entity)}'s birthday"
            }
        }
        return "That's " + parts.joinToString(", and ") + "."
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

    private fun keyToHuman(key: String): String = when (key) {
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

    private fun handleWhatYouLearnedQuery() {

        val allFacts = truthDb.getAllFacts(ENTITY_USER_PRIMARY)
        val todayFacts = truthDb.getFactsUpdatedToday(ENTITY_USER_PRIMARY)

        if (allFacts.isEmpty()) {
            respond("I haven't learned anything about you yet. Tell me something — like your name or a favorite thing — and I'll hold on to it.")
            return
        }

        val olderFacts = allFacts.filter { it !in todayFacts }

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

            // Self-referential "can you learn?" / "do you have a memory?" --
            // grounded in real TruthDb state across every entity Scout knows
            // about (not just the primary user, so a fact learned about Diana,
            // Elijah, Nicolas, etc. counts too), never a canned denial. See
            // ScoutFactExtractor.looksLikeMemoryCapabilityQuestion()'s doc
            // comment for the routing side of this.
            ScoutFactExtractor.looksLikeMemoryCapabilityQuestion(qNorm) -> {
                val known = getAllKnownFacts().size
                if (known > 0) {
                    "Yes. I can learn facts you teach me and store them locally. Right now I know $known ${if (known == 1) "thing" else "things"} I've learned. I only say I remember something once it's actually been saved."
                } else {
                    "Yes. I can learn facts you teach me and store them locally. I don't know anything yet -- tell me something and I'll hold onto it. I only say I remember something once it's actually been saved."
                }
            }

            // Self-referential family/household-belonging statement or
            // question -- "Scout is part of the family," "You're part of our
            // family," "You're one of us." Deterministic and never written to
            // TruthDb: this is a stable persona trait (same bucket as
            // "$myName has a real camera," not user-taught information), so
            // it's never phrased as something Scout "remembers." See
            // ScoutFactExtractor.looksLikeSelfFamilyBelongingStatement()'s
            // doc comment for the routing side of this.
            ScoutFactExtractor.looksLikeSelfFamilyBelongingStatement(qNorm) ->

                "Yes, I feel like part of the family too. I'm glad to be here."

            else ->

                "I'm $myName. I'm here with you."

        }

        respond(response)

    }

    private fun handleVoiceBankIntent(key: String) {

        val out = voice.say(key)

        respond(out)

    }

    // Answers a Courtesy Phase 1 match (see ScoutCourtesyMatcher) directly via
    // respond() -- never through handleQuery()/ScoutIntentRouter, so these never
    // reach TinyLlama or Gemini. Uses Phrases.kt's own rotating-pool pattern
    // (Phrases.COURTESY_* -- separate pools from GREET/GOODBYE, not reused).
    //
    // Better Conversation State Phase 1: GREET/GOOD_MORNING may open a
    // conversation from idle; THANKS only ever extends one that's already
    // active, never opens by itself; GOOD_NIGHT/GOODBYE close explicitly. The
    // close happens after respond() so the closing reply itself is still
    // recorded as this conversation's last Scout turn before it ends.
    private fun handleCourtesy(courtesy: CourtesyIntent) {

        val now = System.currentTimeMillis()

        when (courtesy) {
            CourtesyIntent.GREET, CourtesyIntent.GOOD_MORNING -> {
                if (conversationState.openFromUserTurn(now)) diagLog.logConversationStarted(startedByScout = false)
            }
            CourtesyIntent.THANKS, CourtesyIntent.ACKNOWLEDGE, CourtesyIntent.WELCOME_BACK, CourtesyIntent.CONFIRM -> conversationState.extend(now)
            CourtesyIntent.GOOD_NIGHT, CourtesyIntent.GOODBYE -> { /* closed below, after respond() */ }
        }

        val pool = when (courtesy) {
            CourtesyIntent.GREET -> Phrases.COURTESY_GREET
            CourtesyIntent.GOOD_MORNING -> Phrases.COURTESY_GOOD_MORNING
            CourtesyIntent.THANKS -> Phrases.COURTESY_THANKS
            CourtesyIntent.GOOD_NIGHT -> Phrases.COURTESY_GOOD_NIGHT
            CourtesyIntent.GOODBYE -> Phrases.COURTESY_GOODBYE
            CourtesyIntent.ACKNOWLEDGE -> Phrases.COURTESY_ACKNOWLEDGE
            CourtesyIntent.WELCOME_BACK -> Phrases.COURTESY_WELCOME_BACK
            CourtesyIntent.CONFIRM -> Phrases.COURTESY_CONFIRM
        }

        respond(Phrases.pick("courtesy_${courtesy.name.lowercase()}", pool))

        if (courtesy == CourtesyIntent.GOOD_NIGHT || courtesy == CourtesyIntent.GOODBYE) {
            if (conversationState.closeExplicitly(System.currentTimeMillis())) {
                logConversationEnd()
                discardPendingGenerationForClosedConversation()
            }
        }

    }

    // "goodbye"/"bye"/"that's all"/"that will be all" said with the wake word
    // (or already inside an active conversation) -- the name-free forms of
    // "goodbye"/"bye"/"good night" are handled by handleCourtesy() instead.
    // Closes explicitly after the reply, same ordering as handleCourtesy().
    private fun handleGoodbyeIntent() {

        respond(Phrases.pick("goodbye", Phrases.GOODBYE))

        if (conversationState.closeExplicitly(System.currentTimeMillis())) {
            logConversationEnd()
            discardPendingGenerationForClosedConversation()
        }

    }

    // "stop listening" / "you can stop listening" -- ends the follow-up
    // conversation only. Does NOT disable Scout, Presence, or Companion
    // Moments -- those keep their own independent timers untouched.
    private fun handleStopListeningIntent() {

        respond("Okay.")

        if (conversationState.closeExplicitly(System.currentTimeMillis())) {
            logConversationEnd()
            discardPendingGenerationForClosedConversation()
        }

    }

    // Logs a conversation-end diagnostic from whatever ScoutConversationState
    // just recorded for the transition that occurred (endReason/endedAt/
    // startedAt). Call only right after a closeExplicitly()/evaluate() call
    // that's already confirmed a transition actually happened.
    private fun logConversationEnd() {
        val reason = conversationState.endReason ?: return
        diagLog.logConversationEnded(reason.toDiagReason(), conversationState.endedAt - conversationState.startedAt)
    }

    private fun ConversationEndReason.toDiagReason(): DiagLog.ConversationEndReason = when (this) {
        ConversationEndReason.SILENCE_TIMEOUT -> DiagLog.ConversationEndReason.SILENCE_TIMEOUT
        ConversationEndReason.EXPLICIT_END -> DiagLog.ConversationEndReason.EXPLICIT_END
    }

    private fun BusyBrainDiscardReason.toDiagReason(): DiagLog.BusyBrainDiscardReason = when (this) {
        BusyBrainDiscardReason.CONVERSATION_CLOSED -> DiagLog.BusyBrainDiscardReason.CONVERSATION_CLOSED
        BusyBrainDiscardReason.TIMEOUT -> DiagLog.BusyBrainDiscardReason.TIMEOUT
    }

    // Busy-Brain PR 1: called right after an explicit close (goodbye/stop
    // listening/good night) actually transitions the conversation, so a
    // Gemini/TinyLlama generation still pending from before the close never
    // gets spoken once it returns. Harmless no-op if nothing is pending --
    // see ScoutBusyBrainState.discard().
    private fun discardPendingGenerationForClosedConversation() {
        if (busyBrainState.discard(BusyBrainDiscardReason.CONVERSATION_CLOSED)) {
            journalDb.add("Busy-Brain: pending generation discarded (conversation closed).")
            refreshThinkingFaceState()
        }
        // pendingAiAnswer lifecycle fix: a real, confirmed gap -- the discard()
        // call above is a no-op once a generation has already resolved into
        // pendingAiAnswer (busyBrainState.isPending is already false by then),
        // so a stale queued answer could previously survive an explicit
        // "goodbye"/"stop listening"/"good night" and still drain onto some
        // later, unrelated utterance. Checked independently of the discard()
        // result above -- both can fire on the same close, and this one
        // doesn't depend on the other having fired.
        if (pendingAiAnswer != null) {
            clearPendingAiAnswer()
            diagLog.logPendingAnswerDiscarded(DiagLog.PendingAnswerDiscardReason.CONVERSATION_CLOSED)
        }
    }

    private fun handleAskDogNameIntent() {

        val d = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.DOG_NAME)

        val out = if (!d.isNullOrBlank()) "Your dog’s name is $d." else voice.say("DONT_KNOW")

        respond(out)

    }

    // "what are the names in my family" -- a summary across wife/son/dog, answered
    // straight from stored facts. Never sent to Gemini or TinyLlama, which have no
    // access to these facts and would otherwise have to guess.
    private fun handleFamilyNamesQuery() {

        val wife = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.WIFE_NAME)
        val son  = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.SON_NAME)
        val dog  = truthDb.getFactValue(ENTITY_USER_PRIMARY, FactKey.DOG_NAME)

        val parts = mutableListOf<String>()
        if (!wife.isNullOrBlank()) parts.add("your wife is $wife")
        if (!son.isNullOrBlank()) parts.add("your son is $son")
        if (!dog.isNullOrBlank()) parts.add("your dog is $dog")

        val out = if (parts.isEmpty()) {
            "I don't know anyone in your family yet. You can tell me their names and I'll remember."
        } else {
            "In your family, " + parts.joinToString(", ") + "."
        }

        respond(out)

    }

    // "turn on calendar" -- opens Settings straight to Calendar Awareness (Privacy & Data)
    // instead of just telling the user where to look, matching the existing "open settings"
    // shortcut's own delayed-launch/slide-in pattern.
    private fun handleOpenCalendarSettingsIntent() {

        val alreadyOn = prefs.getBoolean(PREF_CALENDAR_ENABLED, false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

        if (alreadyOn) {
            respond("Calendar Awareness is already on.")
            return
        }

        respond("Here's Calendar Awareness, in Settings.")

        openSettingsScreen(SettingsActivity.S_CONNECTED)

    }

    // Shared by every voice path that needs to land the user on a specific Settings
    // screen (Calendar Awareness, AI) instead of just telling them where to look and
    // leaving them to find it by hand.
    private fun openSettingsScreen(screen: String) {

        handler.postDelayed({
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_TARGET_SCREEN, screen)
            )
            overridePendingTransition(R.anim.slide_in_from_left, R.anim.stay_still)
        }, 600L)

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

        // Calendar Follow-up: resolve or drop a pending clarification/check-in
        // before anything else -- unconditional, not gated by busyBrainState
        // below. This answers a specific question Scout itself just asked,
        // not a new arbitrary generation; placing it inside handleTeaching()'s
        // busyBrainState.isPending gate further down would let an unrelated
        // still-pending generation silently swallow the user's answer.
        when (val pending = pendingCalendarFollowup) {
            is PendingCalendarFollowup.Clarification -> {
                pendingCalendarFollowup = null
                if (tryResolveCalendarClarification(qNorm, pending)) return
                // Unresolved: already cleared above, falls through to normal
                // handling of whatever the user actually said below.
            }
            is PendingCalendarFollowup.DoctorCheckIn -> {
                pendingCalendarFollowup = null
                // Full early return -- never reaches handleTeaching(),
                // ScoutIntentRouter, Gemini, or TinyLlama. The reply's content
                // is never parsed, extracted, or stored; see Phrases.DOCTOR_CHECKIN_ACK.
                respond(Phrases.pick("doctor_checkin_ack", Phrases.DOCTOR_CHECKIN_ACK))
                return
            }
            null -> {}
        }

        if (qNorm == "settings" || qNorm.contains("open settings") || qNorm.contains("go to settings")) {

            // Busy-Brain PR 2: screen navigation stays blocked while a
            // generation is pending, same as OPEN_CALENDAR_SETTINGS below --
            // this one is checked before intent routing even happens, so it
            // needs its own explicit gate rather than going through
            // ScoutBusyBrainPolicy.
            if (busyBrainState.isPending) {
                respond(BUSY_BRAIN_DEFERRED, isStatusOnly = true)
                return
            }

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

        val currentScoutName = truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"
        if (!presenceDecider.shouldRespondToInput(qNorm, currentScoutName)) return

        // Busy-Brain PR 1: ScoutLlamaController.newGeneration() used to be
        // bumped unconditionally right here, on every query -- which meant a
        // deterministic question asked while a TinyLlama generation was
        // still pending would silently invalidate it (its result would be
        // discarded as a stale generation once it returned). The token is
        // now only bumped at the point a TinyLlama generation is actually
        // dispatched -- see tryTinyLlamaOrFallback().
        isThinking = true
        thinkingStartedMs = System.currentTimeMillis()

        refreshThinkingFaceState()

        // Busy-Brain PR 2: teaching/forgetting/corrections are memory writes,
        // deliberately not on the approved safe-while-pending list -- skipped
        // entirely while a generation is pending rather than gated inside
        // handleTeaching() itself, so no partial match can slip a write
        // through. The utterance falls through to ordinary intent routing
        // below instead, same as if it simply hadn't matched a teaching
        // pattern.
        if (!busyBrainState.isPending && handleTeaching(qNorm)) return

        val intent = ScoutIntentRouter.route(qNorm)

        diagLog.logRoute(intent.toDiagIntent())

        // Busy-Brain PR 2: state-changing/navigation intents stay blocked
        // while a generation is pending -- only ScoutBusyBrainPolicy's
        // approved read-only/conversational set may proceed. UNKNOWN is
        // deliberately excluded from this check: it already has its own,
        // more specific arbitration inside handleUnknownIntent() /
        // handlePersonalMemoryQuery() (added in PR 1), which speaks
        // BUSY_BRAIN_STILL_THINKING rather than this generic deferral.
        if (busyBrainState.isPending && intent != IntentType.UNKNOWN &&
            !ScoutBusyBrainPolicy.isSafeWhilePending(intent)) {
            respond(BUSY_BRAIN_DEFERRED, isStatusOnly = true)
            return
        }

        val isDirect = when (intent) {
            IntentType.TIME, IntentType.DATE, IntentType.LANGUAGE, IntentType.TIME_OF_DAY, IntentType.CONNECTIVITY,
            IntentType.GO_ONLINE, IntentType.GO_OFFLINE, IntentType.EXPORT_BRAIN,
            IntentType.VISION, IntentType.GREET, IntentType.HOW_ARE_YOU,
            IntentType.GOODBYE, IntentType.STOP_LISTENING, IntentType.PRAISE, IntentType.AFFECTION,
            IntentType.IDENTITY, IntentType.RECALL_FACT,
            IntentType.ASK_SCOUT_NAME, IntentType.ASK_MY_NAME,
            IntentType.ASK_WIFE_NAME, IntentType.ASK_SON_NAME, IntentType.ASK_DOG_NAME,
            IntentType.FAMILY_NAMES, IntentType.OPEN_CALENDAR_SETTINGS,
            IntentType.WEATHER, IntentType.CALENDAR, IntentType.WHOSE_DATE_EVENT -> true
            else -> false
        }
        if (isDirect) diagLog.logBrainStarted(DiagLog.BrainSource.DIRECT)

        // Removed: the old conversation-gap-based "long absence greeting" here.
        // It never actually detected physical absence (only a gap between Scout's
        // own responses), and only fired if the very next thing said matched the
        // exact GREET intent -- confirmed broken in real use. The real, face-
        // based proactive return greeting lives in maybeMakeReturnGreeting(),
        // triggered from the face-tracking loop, not from here.

        when (intent) {

            IntentType.TIME -> handleTimeIntent()

            IntentType.DATE -> handleDateIntent()

            IntentType.LANGUAGE -> handleLanguageIntent()

            IntentType.TIME_OF_DAY -> handleTimeOfDayIntent(qNorm)

            IntentType.CONNECTIVITY -> handleConnectivityIntent()

            IntentType.GO_ONLINE -> handleGoOnlineCommand()

            IntentType.GO_OFFLINE -> handleGoOfflineIntent()

            IntentType.EXPORT_BRAIN -> handleExportBrainIntent()

            IntentType.VISION -> handleVisionIntent()

            IntentType.GREET -> handleVoiceBankIntent("GREET")

            IntentType.HOW_ARE_YOU -> handleVoiceBankIntent("HOW_ARE_YOU")

            IntentType.GOODBYE -> handleGoodbyeIntent()

            IntentType.STOP_LISTENING -> handleStopListeningIntent()

            IntentType.PRAISE -> handleVoiceBankIntent("PRAISE")

            IntentType.AFFECTION -> handleVoiceBankIntent("AFFECTION")

            IntentType.IDENTITY -> handleIdentityIntent(qNorm)

            IntentType.RECALL_FACT -> handleRecallIntent(qNorm)

            IntentType.ASK_SCOUT_NAME -> handleAskScoutNameIntent()

            IntentType.ASK_MY_NAME -> handleAskMyNameIntent()

            IntentType.ASK_WIFE_NAME -> handleAskWifeNameIntent()

            IntentType.ASK_SON_NAME -> handleAskSonNameIntent()

            IntentType.ASK_DOG_NAME -> handleAskDogNameIntent()

            IntentType.FAMILY_NAMES -> handleFamilyNamesQuery()

            IntentType.OPEN_CALENDAR_SETTINGS -> handleOpenCalendarSettingsIntent()

            IntentType.WEATHER -> weatherManager.fetchWeather(qNorm)

            IntentType.CALENDAR -> handleCalendarIntent(qNorm)

            IntentType.WHOSE_DATE_EVENT -> handleWhoseDateEventIntent(qNorm)

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
                // Reads the same TruthDb-configured name wake-word detection uses,
                // not a separate copy -- if Scout's renamed to Charlie, this hears
                // "Charlie" too. "gal"/"scott" stay as STT-mishearing tolerance only
                // when the configured name is actually still "Scout".
                val currentName = (truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout").lowercase()
                val hearsScout = qNorm.contains(currentName) ||
                        (currentName == "scout" && (qNorm.contains("gal") || qNorm.contains("scott")))
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

            // "my dog's name is Nicolas, but we call him Nick" -- a nickname riding
            // along with the name Scout just learned attaches to that same
            // person/pet's own entity (aliases, not a new relation-prefixed key),
            // so later mentions of "Nick" are recognized as Nicolas.
            var nickname: String? = null
            if (factKey == FactKey.WIFE_NAME || factKey == FactKey.SON_NAME || factKey == FactKey.DOG_NAME) {
                nickname = ScoutFactExtractor.extractNicknameClause(qNorm)
                if (nickname != null) {
                    truthDb.addAlias(value.trim().lowercase(), nickname)
                }
            }

            if (factKey == FactKey.SON_NAME || factKey == FactKey.WIFE_NAME) {
                val faceRegistered = registerFamilyMemberFace(value)
                if (!faceRegistered) {
                    respond("I’ll remember $value. When $value faces me alone, I’ll learn to recognize them.")
                    return true
                }
            }

            var out = when (factKey) {

                FactKey.WIFE_NAME -> Phrases.pickNamed("remember_wife", Phrases.REMEMBER_WIFE, value)

                FactKey.SON_NAME -> Phrases.pickNamed("remember_son", Phrases.REMEMBER_SON, value)

                FactKey.DOG_NAME -> Phrases.pickNamed("remember_dog", Phrases.REMEMBER_DOG, value)

                else -> Phrases.pick("remember", Phrases.REMEMBER)

            }

            if (nickname != null) out += " And I'll remember you call $value $nickname."

            respond(out)

            return true

        }

        // Facts about someone other than the user -- "Diana's birthday is
        // November 27," "my wife's favorite food is sushi" -- attach to that
        // person's own entity instead of a new relation-prefixed key, resolved
        // from whatever's already been taught (ScoutEntityResolver). Always a
        // deterministic confirmation naming exactly what was learned; TinyLlama
        // and Gemini are never involved in acknowledging a taught fact.
        val aliasMap = ScoutEntityResolver.buildAliasMap(truthDb, ENTITY_USER_PRIMARY)
        val aboutFacts = ScoutFactExtractor.extract(qNorm, aliasMap.keys)
        if (aboutFacts.isNotEmpty()) {
            val subjectPhrase = aboutFacts.first().subject
            val entity = aliasMap[subjectPhrase]
                ?: ScoutEntityResolver.resolveEntity(subjectPhrase, truthDb, ENTITY_USER_PRIMARY)
            val displayName = ScoutEntityResolver.displayName(entity)
            val confirmations = mutableListOf<String>()
            for (fact in aboutFacts) {
                if (fact.property == "nickname") {
                    truthDb.addAlias(entity, fact.value)
                    confirmations.add("you call $displayName ${fact.value}")
                } else {
                    truthDb.upsertFact(entity, fact.property, fact.value, 1.0f, "spoken_teach")
                    confirmations.add("$displayName's ${keyToHuman(fact.property)} is ${fact.value}")
                }
            }
            respond("Got it. I'll remember that " + confirmations.joinToString(", and ") + ".")
            return true
        }

        // Even when extraction above found nothing, this may still be a teaching
        // attempt phrased in a way Scout doesn't parse yet -- an honest ask to
        // rephrase beats silently falling through to TinyLlama, which would
        // improvise a reply that sounds like confirmation without anything
        // actually having been learned.
        if (ScoutFactExtractor.looksLikeUnrecognizedTeaching(qNorm, aliasMap.keys)) {
            respond(ScoutFactExtractor.UNRECOGNIZED_TEACHING_CLARIFICATION)
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
        refreshThinkingFaceState()
    }

    // Thinking-face lifecycle fix: the face's visual thinking state used to
    // be tied 1:1 to isThinking, which finishThinking() clears the moment a
    // Gemini/TinyLlama request actually dispatches -- deliberately early, so
    // maybeStartListening()'s `if (isThinking) return` gate doesn't block the
    // mic from reopening for Busy-Brain's "ask a follow-up while I'm still
    // generating" interruption feature. That left the face showing no
    // thinking cue at all for the entire real generation wait (the part a
    // user actually perceives), since busyBrainState.isPending -- which
    // already spans that whole window by design (see ScoutBusyBrainState's
    // own doc comment) -- was never consulted for the face at all.
    //
    // This derives the face's thinking visual from BOTH existing signals
    // without changing what either one means or how long it lasts: isThinking
    // keeps gating the microphone exactly as before, busyBrainState.isPending
    // keeps tracking the real generation window exactly as before. No new
    // persistent state -- purely a read of the two existing booleans, called
    // anywhere either one changes so the face never goes stale. Redundant
    // calls (e.g. right after a downstream call that will also refresh) are
    // intentional and harmless, not accidental duplication.
    private fun refreshThinkingFaceState() {
        faceView.setThinking(isThinking || busyBrainState.isPending)
    }

    // =======================
    // PRESENCE LAYER -- IDLE-SILENCE ACKNOWLEDGMENT (first, narrowest moment)
    // =======================

    /** How often to even evaluate the idle-silence check -- cheap, so this can be
     *  fairly frequent without cost; the real gating is in ScoutPresenceDecider. */
    private val PRESENCE_CHECK_INTERVAL_MS = 30L * 1_000L
    private var lastPresenceCheckMs = 0L

    /** Live, gap-tolerant continuous-presence duration. Zero if no face has been
     *  seen within the last PRESENCE_GAP_GRACE_MS, even between frames. */
    private fun currentTolerantPresenceMs(): Long {
        if (presencePresentSinceMs == 0L) return 0L
        val now = System.currentTimeMillis()
        if (now - presenceLastSeenMs > PRESENCE_GAP_GRACE_MS) return 0L
        return now - presencePresentSinceMs
    }

    // TEMPORARY SMOKE-TEST LOGGING (tag "ScoutPresenceDebug") -- remove or disable
    // once A32 testing confirms the behavior. Deduped so an unchanged reason
    // doesn't repeat on every throttled check.
    private var lastPresenceDebugMsg = ""
    private fun logPresenceDebug(msg: String) {
        if (msg == lastPresenceDebugMsg) return
        lastPresenceDebugMsg = msg
        Log.d("ScoutPresenceDebug", msg)
    }

    // Called from the face-tracking loop. Throttled internally, so it's safe to
    // call on every frame. Speaks only when every guard passes: not speaking,
    // not actively hearing a user utterance, not thinking/processing a request,
    // boot has finished, the app is foregrounded and showing the normal presence
    // screen, and ScoutPresenceDecider's own time-of-day/cooldown gates all agree.
    private fun maybeMakeIdleSilencePresenceRemark() {
        val now = System.currentTimeMillis()
        if (now - lastPresenceCheckMs < PRESENCE_CHECK_INTERVAL_MS) return
        lastPresenceCheckMs = now

        val blockReason = when {
            isSpeaking -> "speaking"
            isCapturingSpeech -> "capturing speech"
            isThinking -> "thinking"
            !bootFinishedSpeaking -> "still starting up"
            !isForeground || currentMode != Mode.PRESENCE -> "wrong app mode"
            else -> null
        }
        if (blockReason != null) {
            logPresenceDebug("Idle remark blocked: $blockReason")
            return
        }

        if (!presenceDecider.canMakeIdleSilenceRemark(currentTolerantPresenceMs())) return

        logPresenceDebug("Presence remark was spoken")
        presenceDecider.onIdleSilenceRemarkMade()
        respond(voice.say("PRESENCE_IDLE_SILENCE"), isPresenceInitiated = true)
    }

    // Own throttle, separate from the idle-silence check above -- this one is
    // only called once a stabilized return has already been detected (not on
    // every frame regardless of state), but stays throttled the same way so it
    // doesn't re-evaluate/re-log every single frame while blocked.
    private var lastReturnGreetingCheckMs = 0L

    private fun maybeMakeReturnGreeting() {
        val now = System.currentTimeMillis()
        if (now - lastReturnGreetingCheckMs < PRESENCE_CHECK_INTERVAL_MS) return
        lastReturnGreetingCheckMs = now

        val blockReason = when {
            isSpeaking -> "speaking"
            isCapturingSpeech -> "capturing speech"
            isThinking -> "thinking"
            !bootFinishedSpeaking -> "still starting up"
            !isForeground || currentMode != Mode.PRESENCE -> "wrong app mode"
            else -> null
        }
        if (blockReason != null) {
            logPresenceDebug("Return greeting blocked: $blockReason")
            return
        }

        if (!presenceDecider.canMakeReturnGreeting()) return

        logPresenceDebug("Greeting spoken (return)")
        presenceDecider.onReturnGreetingMade()

        // Return-greeting personalization: a FRESH PeopleDb lookup from the
        // CURRENT frame's embedding, right here at speak-time -- deliberately
        // NOT lastKnownFaceName, which is only cleared to null when no face
        // is detected at all, not when a face IS detected but fails to match
        // confidently that specific frame, so it can silently carry a stale
        // name forward. findBestMatchNameWithScore()'s own base
        // threshold/margin-vs-second-best-candidate protection is used
        // unchanged; ScoutGreetingIdentity then applies the stricter
        // CONFIDENT_EMBED_THRESHOLD a SPOKEN identity claim needs on top of
        // that. Falls back to the plain generic greeting -- unchanged -- for
        // every other case: no current embedding, no PeopleDb match, a
        // margin-rejected match, or a score below the stricter bar.
        val freshMatch = lastFaceEmbedding?.let { peopleDb.findBestMatchNameWithScore(it) }
        val speakableName = ScoutGreetingIdentity.resolveSpeakableName(
            matchedName = freshMatch?.first,
            matchScore = freshMatch?.second,
            confidenceThreshold = CONFIDENT_EMBED_THRESHOLD
        )
        val greeting = if (speakableName != null) {
            voice.say("PRESENCE_RETURN_GREETING_NAMED").replace("{name}", speakableName)
        } else {
            voice.say("PRESENCE_RETURN_GREETING")
        }
        respond(greeting, isPresenceInitiated = true)

        // This absence/return cycle is fully resolved -- ready to detect the next one.
        candidateAbsenceLogged = false
        genuineAbsenceMarked = false
        returnStabilizingSinceMs = 0L
    }

    // =======================
    // COMPANION MOMENTS -- wiring only. All decision logic (why speak, why
    // now, why this candidate, why not stay silent) lives in
    // ScoutCompanionMomentsEngine; nothing here re-implements or second-
    // guesses that decision. This section's only job is: gather real signals,
    // hand them to the engine, and -- only if it returns a candidate -- act on
    // the answer.
    //
    // Threading: DB reads (JournalDb, TruthDb) are deferred to
    // companionMomentsExecutor since SQLiteDatabase is documented safe for
    // cross-thread access; HabitLayer and ScoutPresenceDecider are NOT
    // documented thread-safe and are normally only ever touched from the main
    // thread elsewhere in this file, so anything from either of them is
    // captured on the main thread before the executor hop, never read from
    // the background thread.
    // =======================

    /**
     * Called from the face-tracking loop, same as the presence checks above.
     * Throttled internally (safe to call every frame). Consumption (cooldown
     * stamp, daily-budget-affecting JournalDb write, diagnostic log) only
     * happens once respond() has actually been called in speakCompanionMoment()
     * below -- never merely because the engine returned a candidate.
     */
    private fun maybeMakeCompanionMoment() {
        val now = System.currentTimeMillis()
        if (now - lastCompanionMomentCheckMs < PRESENCE_CHECK_INTERVAL_MS) return
        lastCompanionMomentCheckMs = now

        val blockReason = when {
            isSpeaking -> "speaking"
            isCapturingSpeech -> "capturing speech"
            isThinking -> "thinking"
            !bootFinishedSpeaking -> "still starting up"
            !isForeground || currentMode != Mode.PRESENCE -> "wrong app mode"
            // Real-device finding: a genuine return-from-absence stabilizing
            // right now must get the return greeting spoken first -- see
            // ScoutReturnGreetingGate's doc comment for the full race.
            ScoutReturnGreetingGate.isStabilizing(genuineAbsenceMarked, returnStabilizingSinceMs) ->
                "return greeting stabilizing"
            else -> null
        }
        if (blockReason != null) {
            logPresenceDebug("Companion moment blocked: $blockReason")
            return
        }

        // Captured on the main thread -- see the threading note above.
        val secondFaceJustAppeared = consumeSecondFaceArrivalSignal(now)
        val habitObservationContentKey = habitLayer.getIdleObservation()
        val continuousPresenceMs = currentTolerantPresenceMs()
        val msSinceLastProactiveRemark = presenceDecider.msSinceLastPresenceRemark(now)
        val hadConversationThisSession = hasHadConversationThisSession
        // Captured before dispatch so a result computed under an older
        // generation (this Activity instance destroyed in the meantime) can
        // recognize itself as stale -- see companionMomentsGeneration's doc.
        val generation = companionMomentsGeneration

        try {
            companionMomentsExecutor.execute {
                try {
                    val signals = buildCompanionSignals(
                        nowMs = now,
                        secondFaceJustAppeared = secondFaceJustAppeared,
                        habitObservationContentKey = habitObservationContentKey,
                        continuousPresenceMs = continuousPresenceMs,
                        msSinceLastProactiveRemark = msSinceLastProactiveRemark,
                        hasHadConversationThisSession = hadConversationThisSession
                    )
                    val candidate = ScoutCompanionMomentsEngine.evaluate(signals) ?: return@execute

                    // Grounded phrase resolution -- e.g. for Memory, this re-reads the
                    // fact's current value from TruthDb. If it's gone (or a fact
                    // couldn't otherwise be resolved into honest text), this returns
                    // null and nothing is spoken or consumed.
                    val text = resolveCompanionMomentText(candidate) ?: return@execute

                    if (ScoutStaleResultGuard.isStale(generation, companionMomentsGeneration)) {
                        logPresenceDebug("Companion moment discarded: stale generation")
                        return@execute
                    }

                    runOnUiThread {
                        speakCompanionMoment(candidate, text, generation)
                    }
                } catch (_: Exception) {
                    // A failed companion-moment attempt must never crash or
                    // destabilize anything else -- matches DiagLog's safe() convention.
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // The executor was already shut down (Activity destroyed between the
            // throttle check above and this call) -- safe to ignore, matches the
            // "silence is the default outcome" contract elsewhere in this feature.
        }
    }

    // Clears secondFaceArrivalPendingSinceMs unconditionally (one latched event
    // is only ever evaluated once) and reports whether it was still fresh
    // enough to act on -- see secondFaceArrivalPendingSinceMs's declaration.
    private fun consumeSecondFaceArrivalSignal(nowMs: Long): Boolean {
        val pendingSinceMs = secondFaceArrivalPendingSinceMs
        secondFaceArrivalPendingSinceMs = 0L
        return ScoutArrivalLatch.consume(pendingSinceMs, nowMs, SECOND_FACE_ARRIVAL_MAX_PENDING_MS)
    }

    /** Runs on the main thread -- the only place this feature actually calls respond(). */
    private fun speakCompanionMoment(candidate: MomentCandidate, text: String, generation: Int) {
        if (ScoutStaleResultGuard.isStale(generation, companionMomentsGeneration)) {
            // The Activity was destroyed (or superseded) between the background
            // hop and this runOnUiThread callback actually running -- runOnUiThread
            // posts to the main Looper, not to the Activity, so it can still fire
            // after onDestroy(). Never speak or touch UI/TTS for a stale generation.
            logPresenceDebug("Companion moment discarded before speaking: stale generation")
            return
        }

        val blockReason = when {
            isSpeaking -> "speaking"
            isCapturingSpeech -> "capturing speech"
            isThinking -> "thinking"
            !bootFinishedSpeaking -> "still starting up"
            !isForeground || currentMode != Mode.PRESENCE -> "wrong app mode"
            // Real-device finding: secondFaceJustAppeared is a latched signal
            // (see SECOND_FACE_ARRIVAL_MAX_PENDING_MS -- up to 5 minutes old by
            // the time it's even consumed), and nothing between candidate
            // creation and this point re-checks whether a second person is
            // still actually present. Without this, Scout could say "It's nice
            // having you both around" well after the second person already
            // left. lastFaceCount is the same live per-frame field the arrival
            // signal itself is derived from, just re-read at the last possible
            // moment instead of trusting the stale latch.
            candidate.category == MomentCategory.ENVIRONMENT && lastFaceCount < 2 ->
                "environment candidate no longer matches live face count ($lastFaceCount)"
            // Same re-check as the initial guard above, for the same reason as
            // the ENVIRONMENT live-face-count re-check just above it: state can
            // change during the background evaluation hop between the initial
            // guard and this point actually speaking.
            ScoutReturnGreetingGate.isStabilizing(genuineAbsenceMarked, returnStabilizingSinceMs) ->
                "return greeting stabilizing"
            else -> null
        }
        if (blockReason != null) {
            // State changed during the background evaluation hop -- discard.
            // Nothing is consumed: the cooldown, budget, and novelty history
            // all stay exactly as they were, so the next eligible check can
            // try again on its own merits rather than losing this attempt.
            logPresenceDebug("Companion moment discarded after background eval: $blockReason")
            return
        }

        respond(text, isPresenceInitiated = true)

        // Only recorded now that respond() has genuinely been called.
        val now = System.currentTimeMillis()
        presenceDecider.onExternalProactiveRemark(now)
        journalDb.add(candidate.contentKey, entryType = "companion_moment", subject = candidate.category.name, weight = 1)
        diagLog.logCompanionMoment(candidate.category.toDiagMomentCategory(), candidate.confidence, candidate.contributions)
        logPresenceDebug("Companion moment spoken: ${candidate.category}")
    }

    // Runs on companionMomentsExecutor's background thread -- see the threading
    // note above. Only JournalDb/TruthDb reads and pure computation happen here.
    private fun buildCompanionSignals(
        nowMs: Long,
        secondFaceJustAppeared: Boolean,
        habitObservationContentKey: String?,
        continuousPresenceMs: Long,
        msSinceLastProactiveRemark: Long,
        hasHadConversationThisSession: Boolean
    ): CompanionSignals {
        // Comfortably longer than the engine's longest category cooldown (24h for
        // Memory) so a still-relevant "last fired" entry is never missed -- not
        // coupled to that exact constant, just safely larger than it. Widened from
        // 48h to 30 days: Memory only fires when it happens to out-score every
        // other category, which real-world usage can easily go several days
        // without -- a 48h window meant a fact's "recently spoken" signal quietly
        // expired long before that, silently reverting its staleness clock back to
        // its original (often very old) TruthDb write time and letting the same
        // fact win the stalest-wins tie-break again and again across boots.
        val noveltyLookbackMs = 30L * 24L * 60L * 60L * 1_000L
        val recentMoments = journalDb.getEntriesSince("companion_moment", nowMs - noveltyLookbackMs)

        val lastFiredMsByCategory: Map<MomentCategory, Long> = recentMoments
            .groupBy { it.subject }
            .mapNotNull { (subject, entries) ->
                val category = MomentCategory.values().find { it.name == subject } ?: return@mapNotNull null
                category to entries.maxOf { it.createdAt }
            }
            .toMap()

        val lastFiredMsByContentKey: Map<String, Long> = recentMoments
            .groupBy { it.text }
            .mapValues { (_, entries) -> entries.maxOf { it.createdAt } }

        val proactiveMomentsFiredToday = journalDb.getEntriesSince(
            "companion_moment", startOfLocalDayMs(nowMs)
        ).size

        val msPerDay = 24L * 60L * 60L * 1_000L
        // Memory-eligibility filtering, real-device findings:
        //  - ENTITY_SCOUT is excluded the same way getAllKnownFacts() already
        //    excludes it elsewhere in this file -- Scout's own facts (e.g. its own
        //    name) must never be offered as an "I've been thinking about
        //    something you told me..." callback.
        //  - Bootstrap/identity keys and person-ranking favorite_<relation-word>
        //    keys are excluded via ScoutCompanionMemoryEligibility -- see its doc
        //    comment for the full "your favorite son is Elijah" trace.
        val staleTaughtFacts = truthDb.getAllEntities()
            .filter { it != ENTITY_SCOUT }
            .flatMap { entity ->
                truthDb.getAllFactsWithTimestamp(entity)
                    .filter { (factKey, _, _) -> ScoutCompanionMemoryEligibility.isCompanionMemoryEligible(factKey) }
                    .map { (factKey, _, updatedAt) ->
                        val contentKey = "memory:$entity:$factKey"
                        // "Days since last surfaced" combines both halves of the story:
                        // when the user last taught/changed the fact (TruthDb), and when
                        // Scout last spoke it as a companion moment (JournalDb) -- whichever
                        // is more recent resets the staleness clock.
                        val effectiveLastSurfaced = maxOf(updatedAt, lastFiredMsByContentKey[contentKey] ?: 0L)
                        val days = ((nowMs - effectiveLastSurfaced) / msPerDay).toInt().coerceAtLeast(0)
                        StaleFact(contentKey = contentKey, daysSinceLastSurfaced = days)
                    }
            }

        val curiosityContentKey = "curiosity:light_question"
        val lastCuriosityMs = lastFiredMsByContentKey[curiosityContentKey]
        val zoneId = java.time.ZoneId.systemDefault()

        return CompanionSignals(
            nowMs = nowMs,
            situationalGuardPassed = true, // already confirmed on the main thread before dispatch
            msSinceLastProactiveRemark = msSinceLastProactiveRemark,
            proactiveMomentsFiredToday = proactiveMomentsFiredToday,
            secondFaceJustAppeared = secondFaceJustAppeared,
            staleTaughtFacts = staleTaughtFacts,
            habitObservationContentKey = habitObservationContentKey,
            // No elevated-conversation-activity signal exists anywhere in this
            // codebase yet -- deliberately left false rather than inventing a new
            // heuristic mid-wiring-PR. HabitLayer.getIdleObservation() above is
            // Observation's only real signal source for now.
            conversationTurnRateElevated = false,
            continuousPresenceMs = continuousPresenceMs,
            hasHadConversationThisSession = hasHadConversationThisSession,
            msSinceLastCuriosityMoment = lastCuriosityMs?.let { nowMs - it },
            isFirstCuriosityOpportunityToday = ScoutCompanionMomentsEngine.isFirstMeetingToday(
                lastCuriosityMs, nowMs, zoneId
            ),
            // The first-meeting-today bonus is intentionally inert in this pass:
            // computing it correctly needs a person's *previous* last_seen value,
            // read before PeopleDb.touchSeen() overwrites it elsewhere in this
            // file -- deliberately not threading that through an unrelated,
            // already-working call site for one scoring bonus. Passing nowMs
            // here makes isFirstMeetingToday() always false (same calendar day
            // as itself), rather than always true, which would be the wrong
            // direction to fail in.
            presentPersonLastSeenMs = nowMs,
            zoneId = zoneId,
            lastFiredMsByContentKey = lastFiredMsByContentKey,
            lastFiredMsByCategory = lastFiredMsByCategory
        )
    }

    // Also runs on the background thread (called from within
    // maybeMakeCompanionMoment()'s executor block, before the runOnUiThread hop).
    // TruthDb reads and VoiceBank (backed by SharedPreferences) are both safe to
    // call from a background thread; keyToHuman() is a pure function.
    private fun resolveCompanionMomentText(candidate: MomentCandidate): String? = when (candidate.category) {
        MomentCategory.ENVIRONMENT -> voice.say("COMPANION_ENVIRONMENT")

        MomentCategory.CURIOSITY -> voice.say("COMPANION_CURIOSITY")

        MomentCategory.MEMORY -> {
            val parts = candidate.contentKey.split(":", limit = 3)
            if (parts.size != 3 || parts[0] != "memory") {
                null
            } else {
                val entity = parts[1]
                val factKey = parts[2]
                val value = truthDb.getFactValue(entity, factKey)
                if (value == null) {
                    null
                } else {
                    // Entity-aware wording -- a fact belonging to someone other than
                    // the user (e.g. entity="diana") must never be spoken as "your
                    // ...". Aborts (returns null) rather than guessing if the entity
                    // can't be honestly resolved into possessive wording.
                    ScoutMemoryPhraser.buildSentence(
                        intro = voice.say("COMPANION_MEMORY_INTRO"),
                        entity = entity,
                        userEntity = ENTITY_USER_PRIMARY,
                        scoutEntity = ENTITY_SCOUT,
                        humanFactKey = keyToHuman(factKey),
                        value = value,
                        displayName = ScoutEntityResolver::displayName
                    )
                }
            }
        }

        MomentCategory.OBSERVATION -> if (candidate.contentKey == "observation:elevated_activity") {
            voice.say("COMPANION_OBSERVATION_FALLBACK")
        } else {
            // contentKey IS the already-phrased sentence from
            // HabitLayer.getIdleObservation() for this category -- see the
            // wiring note on CompanionSignals.habitObservationContentKey.
            candidate.contentKey
        }
    }

    private fun startOfLocalDayMs(nowMs: Long, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        java.time.Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

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

            // Not connected at all -- fixing that comes first, so the OS's own
            // connectivity panel takes priority over Scout's own Settings screen.
            journalDb.add("GoOnline: not validated, opened panel.")

            connectivityManager.openInternetPanel()

        } else {

            // Internet's fine -- go straight to AI (where Online Features and the API
            // key live) instead of just describing status and leaving the user to hunt
            // for it themselves.
            openSettingsScreen(SettingsActivity.S_AI)

        }

        finishThinking()

    }

    // =======================

    // SHUTDOWN

    // =======================

    override fun onDestroy() {

        shutdownSystems()

        // Invalidate before tearing the executor down so any companion-moment
        // work already queued or mid-flight recognizes itself as stale (see
        // companionMomentsGeneration's doc) even in the brief window before
        // shutdownNow() actually takes effect. shutdownNow() (not shutdown())
        // because a result delivered after this Activity is destroyed is
        // actively unsafe, not just pointless -- unlike a plain shutdown(),
        // this drops any queued task and attempts to interrupt one already running.
        companionMomentsGeneration++
        companionMomentsExecutor.shutdownNow()

        super.onDestroy()

    }

    private fun MomentCategory.toDiagMomentCategory(): DiagLog.DiagMomentCategory = when (this) {
        MomentCategory.ENVIRONMENT -> DiagLog.DiagMomentCategory.ENVIRONMENT
        MomentCategory.MEMORY      -> DiagLog.DiagMomentCategory.MEMORY
        MomentCategory.OBSERVATION -> DiagLog.DiagMomentCategory.OBSERVATION
        MomentCategory.CURIOSITY   -> DiagLog.DiagMomentCategory.CURIOSITY
    }

    private fun IntentType.toDiagIntent(): DiagLog.DiagIntent = when (this) {
        IntentType.TIME            -> DiagLog.DiagIntent.TIME
        IntentType.DATE            -> DiagLog.DiagIntent.DATE
        IntentType.LANGUAGE        -> DiagLog.DiagIntent.LANGUAGE
        IntentType.TIME_OF_DAY     -> DiagLog.DiagIntent.TIME_OF_DAY
        IntentType.CONNECTIVITY    -> DiagLog.DiagIntent.CONNECTIVITY
        IntentType.GO_ONLINE       -> DiagLog.DiagIntent.GO_ONLINE
        IntentType.GO_OFFLINE      -> DiagLog.DiagIntent.GO_OFFLINE
        IntentType.EXPORT_BRAIN    -> DiagLog.DiagIntent.EXPORT_BRAIN
        IntentType.VISION          -> DiagLog.DiagIntent.VISION
        IntentType.GREET           -> DiagLog.DiagIntent.GREET
        IntentType.HOW_ARE_YOU     -> DiagLog.DiagIntent.HOW_ARE_YOU
        IntentType.GOODBYE         -> DiagLog.DiagIntent.GOODBYE
        IntentType.STOP_LISTENING  -> DiagLog.DiagIntent.STOP_LISTENING
        IntentType.PRAISE          -> DiagLog.DiagIntent.PRAISE
        IntentType.AFFECTION       -> DiagLog.DiagIntent.AFFECTION
        IntentType.IDENTITY        -> DiagLog.DiagIntent.IDENTITY
        IntentType.RECALL_FACT     -> DiagLog.DiagIntent.RECALL_FACT
        IntentType.ASK_MY_NAME     -> DiagLog.DiagIntent.ASK_MY_NAME
        IntentType.ASK_SCOUT_NAME  -> DiagLog.DiagIntent.ASK_SCOUT_NAME
        IntentType.ASK_WIFE_NAME   -> DiagLog.DiagIntent.ASK_WIFE_NAME
        IntentType.ASK_SON_NAME    -> DiagLog.DiagIntent.ASK_SON_NAME
        IntentType.ASK_DOG_NAME    -> DiagLog.DiagIntent.ASK_DOG_NAME
        IntentType.FAMILY_NAMES    -> DiagLog.DiagIntent.RECALL_FACT
        IntentType.OPEN_CALENDAR_SETTINGS -> DiagLog.DiagIntent.CALENDAR
        IntentType.TEACH_WIFE_NAME -> DiagLog.DiagIntent.TEACH_WIFE_NAME
        IntentType.TEACH_SON_NAME  -> DiagLog.DiagIntent.TEACH_SON_NAME
        IntentType.TEACH_DOG_NAME  -> DiagLog.DiagIntent.TEACH_DOG_NAME
        IntentType.TEACH_MY_NAME   -> DiagLog.DiagIntent.TEACH_MY_NAME
        IntentType.WEATHER         -> DiagLog.DiagIntent.WEATHER
        IntentType.CALENDAR        -> DiagLog.DiagIntent.CALENDAR
        // Reuses RECALL_FACT -- same precedent as FAMILY_NAMES above, a
        // deterministic TruthDb recall question, not a new diagnostic category.
        IntentType.WHOSE_DATE_EVENT -> DiagLog.DiagIntent.RECALL_FACT
        IntentType.UNKNOWN         -> DiagLog.DiagIntent.UNKNOWN
    }

}
