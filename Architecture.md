Last updated: July 29, 2026
Based on commit: e6c2e829248035c5b01482e139bb1215549f381e
Status: Current

# Scout — Architecture

This document explains how Scout is actually built today, from reading the code directly (not from planning notes). It's meant to let a new developer (human or AI) understand the system without reading all ~15,000 lines of Kotlin first. For *what's been built and when*, see `Scout_Master_Summary.md`. For *what's being worked on right now*, see `MAIN BUILD PATH - ACTIVE.md`. For *known technical debt*, see `MainActivity Cleanup.md`.

Scout is a single-module Android app (package `com.example.scoutface`, min SDK 26, target/compile SDK 34). There is no server backend — every database is a local SQLite file inside the app's own storage, and the only outbound network calls are to Google's Gemini API (optional, user-provided key) and the U.S. National Weather Service (no key required).

---

## 1. Overall Architecture

Scout has one real UI screen — `MainActivity`, a single `Activity` in landscape orientation that owns the face animation, camera preview (invisible, analysis-only), microphone, and all conversational logic. Everything else (Settings, Onboarding, API key setup, the startup/model-download gate, diagnostics, the dev benchmark tool) is a secondary `Activity` launched on top of it and returned from.

There is no MVVM/MVI framework, no dependency injection container, and no Compose — the UI is built programmatically with classic `View`/`LinearLayout` in `SettingsActivity`, `OnboardingActivity`, etc. (`ScoutFaceView` is a custom-drawn `View` for the animated face). `MainActivity` directly constructs and owns every subsystem object as instance fields (databases, `GeminiClient`, `VisionAnswerBuilder`, `ScoutPresenceDecider`, etc.) — there's no central registry or service locator.

The one significant architectural extraction so far is the `brain` package (`com.example.scoutface.brain`) — pure-logic classes (intent routing, fact extraction, entity resolution, memory gating, prompt building) that `MainActivity` calls into but that don't themselves touch Android APIs like `Context`, speech, or camera. This split started recently (the personal-memory/entity work) and is the intended direction for future extraction out of `MainActivity`.

Two things are true process-wide singletons, not `MainActivity` instance fields, specifically so they survive Activity recreation (screen rotation, multi-window resize) without leaking a thread or losing loaded state:
- `LlamaEngine` (`object`) — owns the native TinyLlama context.
- `ScoutLlamaController` (`object`) — owns the single generation executor and the "is this caller still valid" token that gates delivering a TinyLlama result back to the UI.

Everything else — the six SQLite databases, `GeminiClient`, `ScoutPresenceDecider`, `HabitLayer`, etc. — is constructed fresh in `MainActivity.onCreate()` on every Activity instance (including on a configuration-change recreation). That's cheap for the SQLite wrappers (`SQLiteOpenHelper` handles connection reuse internally) but is a real, current architectural gap for `HabitLayer` and `ScoutPresenceDecider`, whose in-memory state (social battery, presence timers) resets on every rotation — see `MAIN BUILD PATH - ACTIVE.md`.

---

## 2. MainActivity Responsibilities

`MainActivity.kt` (≈4,900 lines) is Scout's coordinator. It owns:
- The face animation view (`ScoutFaceView`) and the "Mode" (`PRESENCE`/`REST`) driving it.
- Speech recognition lifecycle (`SpeechRecognizer`, wake-word detection, listening windows).
- Text-to-speech (`TextToSpeech`) output.
- The CameraX pipeline (face detection, scene labeling, face recognition/embedding — all inline inside one large `ImageAnalysis.Analyzer` callback in `startCamera()`).
- Intent routing and response dispatch (`handleQuery()` and the many `handleXxxIntent()` methods).
- Teaching/fact storage (`handleTeaching()`).
- The presence/proactive-greeting logic (idle-silence remark, return greeting).
- Wiring every other subsystem together (constructing all six databases, `GeminiClient`, `ScoutPromptBuilder`, `HabitLayer`, `VisionAnswerBuilder`, etc. in `setupBrainServices()`/`setupMemory()`).

It does **not** own: TinyLlama's native lifecycle (that's `LlamaEngine`/`ScoutLlamaController`), Gemini's HTTP details (`GeminiClient`) or its request-discipline/quota logic (`ScoutGeminiManager`), weather fetching (`ScoutWeatherManager`), or calendar reading (`CalendarReader`). `MainActivity` calls into all of these but the actual work lives in dedicated classes.

`MainActivity.kt` is the single largest file in the codebase by a wide margin (the next-largest, `ScoutFaceView.kt`, is under a third its size) and is the primary target of `MainActivity Cleanup.md`.

---

## 3. Offline Brain (TinyLlama)

**Model**: TinyLlama-1.1B-Chat (Q4_K_M quantized `.gguf`, ~669MB), run via a bundled llama.cpp build (prebuilt `.so` libraries in `app/src/main/jniLibs/arm64-v8a/`, arm64-only). Scout's own native glue is `app/src/main/cpp/scout_llama_jni.cpp`, compiled to `libscout_llama.so` via CMake.

**Loading**: on first run, `ModelDownloadActivity` downloads the model from a GitHub Release asset (`MODEL_DOWNLOAD_URL` in `ModelDownloadActivity.kt`) into the app's external files directory via Android's `DownloadManager`. On every subsequent launch, `ModelDownloadActivity` is still the gate every launch passes through — it also *triggers* `LlamaEngine.loadAsync()` and waits for the model to finish loading into memory before ever returning control to `MainActivity`. `MainActivity` never shows its face, asks for permissions, or starts camera/mic until `LlamaEngine.isReady` is true (see §16, Startup Sequence).

**`LlamaEngine`** (`object`, process-wide singleton): owns the native context handle, wraps `load()`/`generate()`/`generateBenchmark()`/`freeIfIdle()`. A single `ReentrantLock` (`nativeLock`) serializes every native call — two threads can never touch the native `llama_context` concurrently; a `generate()` call while another is in flight simply returns `null` rather than blocking, except where the caller explicitly serializes via the controller below. `freeIfIdle(maxWaitMs)` uses `tryLock` with a bounded timeout so a real Activity close can free the ~800MB model without ever risking an ANR from an unbounded lock wait.

**`ScoutLlamaController`** (`object`, process-wide singleton): the actual entry point every generation goes through. It owns:
- The single-thread `ExecutorService` all `generate()` calls run on (so back-to-back questions serialize instead of racing).
- `currentToken`, a `Long` bumped by `registerOwner()` (once per new `MainActivity` instance) and `newGeneration()` (once per new question). `generateAsync()` only delivers its result to the caller's `onResult` callback if the token it captured is still current when the generation finishes — this is what prevents a slow generation from a since-destroyed `MainActivity` instance (or a superseded earlier question) from touching a stale/detached UI. A stale result is silently discarded, logged internally via the controller's own application-scoped `DiagLog`.
- `shutdownForRealClose()`, called only when `MainActivity.onDestroy()` fires for a genuine close (`!isChangingConfigurations()`) — frees the native model only if nothing is actively generating. On a configuration-change recreation, the model is deliberately left loaded (freeing and reloading an 800MB model on every screen rotation would be a serious regression); `invalidateOwner()` still runs unconditionally on every `onDestroy()` so a still-in-flight generation can never deliver its result to a destroyed Activity instance, regardless of why it was destroyed.

**Prompt construction**: `tryTinyLlamaOrFallback()` in `MainActivity.kt` builds a prompt grounding TinyLlama in relevant TruthDb facts (see §5) before generating, using a chat-style template (`<|user|>`/`<|assistant|>` tags). `nativeGenerate()` prefills in chunks bounded to `n_batch` (512 tokens) rather than one call sized to the whole prompt — a single-batch overflow caused a native SIGABRT before this was fixed.

**Reply cleanup**: `limitToSentences()` caps offline replies at 2 sentences; `cleanOfflineReply()` strips model artifacts.

**Benchmarking**: `LlamaBenchmarkActivity` (dev-only, hidden behind a 7-tap unlock in Settings → About Scout) exercises `LlamaEngine.generateBenchmark()` across fixed prompts and thread-count combinations, purely for on-device performance measurement — it never touches production generation behavior.

---

## 4. Online Brain (Gemini)

**`GeminiClient`**: a thin, synchronous wrapper around Google's `generateContent` REST endpoint (raw `HttpURLConnection`, not OkHttp — despite OkHttp being a declared Gradle dependency, see `MainActivity Cleanup.md`). Handles: a single-flight guard (one Gemini request in flight at a time), a cooldown clock that honors Google's `retryDelay` on HTTP 429, and daily-quota detection (a 429 whose `quotaId` contains `"PerDay"` triggers a much longer, distinct cooldown so Scout can give an honest "daily limit" message instead of "back in a minute"). `generateReply()` must be called from a worker thread.

**`ScoutGeminiManager`**: sits between `MainActivity` and `GeminiClient`, handling request discipline that's about *when to ask*, not *how to ask*: duplicate-prompt detection with a short reply cache (avoids re-hitting the network for the same question asked twice in a row), a minimum gap between requests, and "quota message" discipline — Scout announces Gemini's unavailability once per cooldown window, not on every subsequent question.

**`ScoutPromptBuilder`**: pure string assembly. Builds Gemini's system instruction (Scout's persona + a habit-context summary from `HabitLayer`) and the three possible "online unavailable" messages (daily quota / short cooldown / generic failure).

**Model**: `gemini-3.5-flash-lite` (configurable per-request via a `MainActivity` constant). The API key is provided by the user via `ApiKeySetupActivity`, encrypted at rest (see §17, Privacy Model) via `ScoutSecureKeyStore`, and read through `ScoutApiKeyHelper.getKey()`.

**Fallback chain**: Gemini → TinyLlama. `tryGemini()`'s `onFailed` callback routes into `tryTinyLlamaOrFallback()` so a Gemini failure (quota, network, timeout) doesn't leave Scout silent. OpenAI and Claude keys can be saved via `ApiKeySetupActivity` but nothing in the app currently sends requests to either provider — see `MAIN BUILD PATH - ACTIVE.md`.

---

## 5. TruthDb

Scout's single fact store (`scout_truth.db`, one table: `entity_memory`). Schema: `(entity, fact_key, value, confidence, source, last_confirmed, created_at, updated_at)`, unique on `(entity, fact_key)`.

Facts are **entity-scoped**, not user-scoped: `entity` is a slug like `"user_primary"`, `"scout"`, `"diana"`, or `"nicolas"` — a person or pet's own facts (birthday, nickname, favorite color) live under their own entity rather than as relation-prefixed keys (`wife_birthday`) under the user. The user's own entity still holds pointer facts (`wife_name`, `son_name`, `dog_name`) that resolve "my wife" to whichever entity slug currently holds that relationship — see `ScoutEntityResolver` below.

Aliases (nicknames) are stored as a single comma-joined `"aliases"` fact per entity (`TruthDb.addAlias()`/`getAliases()`) rather than a separate table, so "Nicolas" can pick up "Nick" and later "Nicky" without a schema change.

`upsertFact()` returns whether the write actually changed anything (new fact, or a value that differs from what was already there) — callers use this to decide whether the write is worth journaling into `JournalDb`.

Three collaborating `brain` classes read/write TruthDb without themselves needing to know about speech, camera, or the UI:
- **`ScoutFactExtractor`**: extracts `(subject, property, value)` from a spoken statement about *someone other than the user* ("Diana's birthday is November 27th"), anchored on property keywords and known entity names rather than one fixed sentence shape, so word order can vary. Deliberately regex/keyword-based, not model-based — TruthDb is the authoritative store, so extraction into it must stay deterministic and inspectable.
- **`TeachExtractor`**: the older, still-active extractor for identity/relation-name teaching that's tied to face recognition ("this is my wife Diana", "my dog's name is Nicolas") — a large, repetitive set of hand-written regexes per relation (see `MainActivity Cleanup.md` for the duplication this creates).
- **`ScoutEntityResolver`**: resolves a spoken subject ("my wife", "diana") to the entity slug its facts should attach to, and builds a fresh alias map from TruthDb on every call (so a name taught moments ago is recognized immediately, not cached stale).
- **`ScoutMemoryGate`**: decides whether an otherwise-unrouted question might be a personal-memory question that must never reach Gemini ungrounded — see §6.

---

## 6. Memory System

Scout's memory is deliberately split across purpose-specific stores rather than one general database:

| Store | File | Purpose |
|---|---|---|
| `TruthDb` | `scout_truth.db` | Structured facts (see §5) — the only source a spoken answer is grounded in. |
| `JournalDb` | `scout_journal.db` | Free-text memory-reel entries (`first_met`, `teaching`, `correction`, `milestone`, `freeform`) plus general system-event notes. Not used for answering questions directly today — a narrative log, not a queryable fact store. |
| `ConversationDb` | `scout_convo.db` | Rolling conversation turns (role + text), used to build Gemini's short conversational context window. |
| `PeopleDb` | `scout_people.db` | Face recognition data — see §8. |
| `HabitLayer` | `scout_habits.json` (flat file, not SQLite) | Decaying-relevance topic/time-of-day/person-presence tracking, feeding a one-paragraph "habit context" into Gemini's system prompt. Keyword extraction with a stop-word list; scores decay with a 14-day half-life. |
| `DiagnosticDb` | `scout_diagnostic.db` | Privacy-safe diagnostic event log — see §14. Structurally separate from every store above so a diagnostic report can never leak memory content. |

**The personal-memory gate** (`ScoutMemoryGate.isPossiblePersonalMemoryQuery()`) is the key privacy/correctness mechanism tying this together: any question that might be about the owner, family, pets, or anything Scout's been taught is checked against TruthDb *before* it's ever allowed to reach Gemini. It's deliberately biased toward over-triggering (a false positive just costs a wasted TruthDb check; a false negative would leak a personal question to a fact-blind cloud model). Two signals, either sufficient: a self-reference word + a personal-topic word, or the query mentioning a name Scout already knows (including aliases). If TruthDb has nothing for that entity, Scout gives a hard "I don't know" rather than letting a downstream brain guess.

A separate `ScoutExportManager` can export TruthDb + named `PeopleDb` rows (not embeddings, not `JournalDb`, not diagnostics) to a shareable JSON file — "export brain"/"export memory" voice command.

There is also a **dead**, unused Room-based database (`ScoutDatabase.kt` + `PersonEntity.kt`, table `people_memory` in `scout_brain.db`) left over from an earlier approach — nothing in the app constructs or queries it. See `MainActivity Cleanup.md`.

---

## 7. PeopleDb

See §8 (Face Recognition) — `PeopleDb` (`scout_people.db`) is the face-recognition-specific store, kept separate from `TruthDb` because it holds raw embedding `BLOB`s and cosine-similarity matching logic that has nothing to do with general fact storage. Two tables: `people` (one row per distinct face hash — name, first/last seen, a single legacy embedding column) and `person_embeddings` (many embeddings per named person, up to 12, for match robustness across lighting/angle).

---

## 8. Face Recognition

**Pipeline**: ML Kit `FaceDetector` (fast mode) finds faces per analyzed frame → `FaceEmbedder` (a bundled `MobileFaceNet.tflite`, InsightFace/ArcFace-trained, 512-dim embeddings, TensorFlow Lite / LiteRT interpreter) turns a cropped, upright face bitmap into a 512-float vector → `PeopleDb.findBestMatchName()` compares it against every named person's stored embeddings via cosine similarity.

**Matching discipline**: a match requires both crossing a similarity threshold (0.65f default, historically tuned up from lower values after cross-person false matches) *and* beating the next-best candidate by a minimum margin (0.08f) — if two people score similarly close, Scout says nothing rather than guessing wrong. `findBestMatchNameWithScore()` additionally exposes the winning score so `MainActivity` only calls `addNamedEmbedding()` (accumulating training samples) on confidently-matched frames, preventing profile pollution from borderline matches.

**Fixed-capacity profiles**: each named person caps at 12 stored embeddings; once full, a new embedding replaces whichever existing one is most redundant (highest cosine similarity to the incoming sample) rather than the profile freezing on whatever 12 frames happened to be captured first.

**Multi-person handling**: the largest face in frame is "primary"; a second, smaller face is embedded and matched in the same executor job. `VisionAnswerBuilder` composes responses like "I see Patrick and Diana" or "I see Patrick and someone else" depending on how many faces are known.

**Face introduction**: "this is my wife Diana" while a face is in frame registers that face against `wife_name`'s value via `registerFamilyMemberFace()`, with a pending-introduction mechanism to handle the case where the named person is the *second* (not primary) face in frame.

**"Forget"**: "Scout, forget [name]" wipes both the `people` row and every `person_embeddings` row for that name.

**Model history**: originally a 192-dim model; upgraded to the current 512-dim ArcFace/MobileFaceNet model, with `PeopleDb`'s `onUpgrade()` clearing incompatible old embeddings (names preserved) on that schema bump.

---

## 9. SpeechRecognizer

Android's built-in `SpeechRecognizer` API, not a custom STT model. Two separate recognizer `Intent` configurations, built by a shared `buildRecognizerIntent(silenceMs, possiblySilenceMs)` helper:
- **Wake-word listening** (waiting to hear Scout's name): shorter silence timeouts (5s/4s).
- **Open-conversation listening** (inside an active conversation window, no wake word required): longer timeouts (10s/7s), since a real reply often has natural pauses.

`maybeStartListening()` is the single gatekeeper for starting a session — it checks (in order, each logged via a controlled `DiagLog.ListenAttemptReason` enum for diagnostics) whether the Activity is resumed, listening is enabled, the conversation gate allows it, Scout isn't speaking/thinking, a session isn't already running, permissions are granted, startup has settled, boot has finished, and any cooldown has expired.

**Mic discipline**: `isCapturingSpeech` (true only between `onBeginningOfSpeech()` and session end) is distinct from `isListening` (true almost continuously while idle, since sessions just cycle) — several other subsystems (presence timers, presence-initiated speech) key off the former specifically so they don't misread "a recognizer session happens to be open" as "someone is actively talking."

**Recognizer watchdog** (`runRecognizerWatchdog()`): periodically checks that listening hasn't silently stalled and restarts it if so, gated on `speechEverStarted` so it can't mistake "not started yet by design" (e.g., during the startup stagger, see §16) for a fault needing recovery.

**Self-echo guard**: Scout ignores results that closely match his own most recent spoken utterance, to avoid reacting to his own TTS bleeding back into the mic.

---

## 10. Wake-word System

Not a dedicated wake-word model — Scout's configured name (read from TruthDb, not hardcoded, so a renamed Scout is recognized correctly everywhere) is matched as a **whole word** against recognized speech (`containsWholeWord()`), never a bare substring — a bare-substring match against a short name risked false positives (e.g. matching inside unrelated words). In `PresenceMode.SLEEP` specifically, `ScoutPresenceDecider.looksLikeDirectAddress()` requires the utterance to start with or clearly address Scout's name (or phrases like "wake up", "are you there") before Scout responds at all; in every other presence mode, Scout always responds once speech is recognized (the wake word only gates whether a *new* listening session is required vs. an open conversation window is still active).

---

## 11. Presence System

`ScoutPresenceDecider` (in `brain`, no Android/camera dependency of its own — `MainActivity` feeds it face/timing signals) owns Scout's social timing:

**Presence modes**, driven purely by time of day: `ACTIVE` (9am–7pm, fully engaged), `CALM` (6–9am, 8–10pm, gentler), `QUIET` (10pm–midnight, minimal spontaneous behavior), `SLEEP` (midnight–6am, direct-address only).

**Social battery** (0–100): depletes per conversation turn, recharges passively during silence; spontaneous comments are withheld below a low-battery threshold.

**Two "presence moments"**, each with its own conservative threshold and both sharing a global cross-moment cooldown so they can't stack close together:
- **Idle-silence acknowledgment**: a rare, quiet remark ("It's nice having you around") after someone's been continuously present with no conversation for a long stretch — proof Scout is still "there" without being a check-in or question. Driven by a gap-tolerant presence streak `MainActivity` tracks from camera face-sighting (a brief missed frame doesn't reset it, only a gap exceeding a grace period does).
- **Return greeting**: driven by genuine-absence + stabilized-return detection, also tracked in `MainActivity` from actual face presence (not just a gap between Scout's own responses, which is what the old, since-removed mechanism incorrectly used). An absence must exceed a minimum duration to count as "genuine" (filtering out someone briefly stepping out of frame), and a return must hold stable for a few seconds before Scout actually speaks (filtering out a flicker).

Both moments currently ship with production-scale cooldowns but **temporarily lowered presence/absence thresholds** left over from on-device smoke-testing — see `MAIN BUILD PATH - ACTIVE.md` for the exact values and restore status.

**Listening reminder** (a related but separate mechanism, implemented directly in `MainActivity`'s camera analyzer, not in `ScoutPresenceDecider`): reminds someone to say Scout's name if they appear to be trying to talk to him without addressing him by name. Vision-gated on ML Kit's head-yaw output — only counts a face as "facing Scout" within a yaw/size/center-offset bound, sustained continuously for 1.5 seconds — specifically to avoid interrupting a nearby conversation not directed at Scout.

---

## 12. Camera Pipeline

Built on CameraX (`camera-core`/`camera2`/`lifecycle`/`view`), entirely inside `startCamera()` in `MainActivity.kt` — currently one very large function (see `MainActivity Cleanup.md`). Key characteristics:

- **Preview is invisible** — the camera exists only for `ImageAnalysis`, not a visible viewfinder.
- **Explicit 640×480 target resolution** — avoids CameraX's default (often much larger), which would allocate an oversized bitmap on every analyzed frame.
- **~7fps throttle** on ML Kit processing (both face detection and, separately, scene labeling at their own, slower cadence) to bound memory pressure, tuned specifically for stability on a lower-end test device (Galaxy A32).
- **Buffer lifecycle discipline**: frames close immediately when skipped by the throttle; a reference-counted holder pattern (`bitmapRefs`) ensures a bitmap is recycled only once every async consumer — face detector, labeler, embedding executor — has actually finished with it, not on whichever finishes first.
- **Freed during "busy" states**: the camera buffer is released immediately whenever Scout is thinking (Gemini in flight) or speaking, since ML Kit inference is the largest transient memory consumer and gaze/greeting logic is already gated off in those states anyway.

Within the analyzer callback: face detection → the direct-address/listening-reminder gate (§11) → face-recognition embedding (deferred until `startupSettled`, a fixed delay after the camera actually starts, so embedding never runs on the very first frames) → presence/absence tracking (§11) all run in sequence per accepted frame.

Scene labeling (ML Kit `ImageLabeling`) runs on a separate, slower cadence than face detection, filtered through a fixed household-object whitelist (`VisionAnswerBuilder.kt`'s `OBJECT_WHITELIST`) so raw ML Kit noise never reaches spoken output. There is a second, unused, duplicate label-filtering implementation (`VisionLabelFilter.kt`) — see `MainActivity Cleanup.md`.

---

## 13. Settings / Scout Home Direction

`SettingsActivity` is a single Activity managing a stack of named "screens" (`identityScreen`, `brainScreen`, `workbenchScreen`, `privacyScreen`, `extrasScreen`, `robotNameScreen`, `apiKeyScreen`, etc.) built programmatically, navigated via a manual back-stack (`screenStack: ArrayDeque<String>`) rather than Fragments or Navigation Component. Sections include AI Provider (Gemini/OpenAI/Claude — only Gemini is wired end-to-end), Voice & TTS (with a live preview that speaks a sample line), Behavior (spontaneous comments, presence mode, calendar awareness toggles), Brain & Behavior, and About Scout (privacy policy, terms, diagnostics, the hidden dev-benchmark unlock).

There is no separate "Scout Home"/dashboard screen today — `MainActivity`'s own face view is the only persistent "home" surface; Settings is reached via a swipe-right gesture, voice command, or a first-boot hint, and is a destination you navigate to and back from, not a landing hub.

---

## 14. Builder's Workbench

`SettingsActivity.workbenchScreen()` — a "what has Scout learned" surface, primarily surfacing `handleWhatYouLearnedQuery()`-style fact listing and (per `SettingsActivity`'s section naming) developer/diagnostic-adjacent tools distinct from ordinary user-facing Behavior settings. Also the entry point that reveals the hidden Performance Benchmark row once the 7-tap developer unlock has been triggered from About Scout.

---

## 15. Diagnostics

`DiagLog` (facade) + `DiagnosticDb` (`scout_diagnostic.db`, structurally isolated from every memory store) form Scout's privacy-safe diagnostic system. Every `DiagLog` method accepts only controlled enum values or numeric fields clamped non-negative — never free text, never speech content, never a name, never a database value, never a full exception message or stack trace (only `javaClass.simpleName`, itself sanitized). Every call is wrapped in try/catch so a logging failure can never interrupt normal behavior.

Events cover: boot, listen start/stop/attempt-reason, speech results (character counts only, never text), intent routing, which brain path actually started, Gemini routing decisions, TinyLlama lifecycle events, weather cache hits/misses, network success/failure by area, response completion timing, and controlled errors by subsystem.

`DiagReportActivity` renders the last 7 days of events plus the crash log (a bounded, size-bounded `scout_crash.txt` the uncaught-exception handler writes directly, independent of SQLite) as a plain-text report, viewable in-app or shareable via `ACTION_SEND`. Settings' Diagnostics section offers view, share, and delete-all. Retention: 7 days or 1,000 entries, whichever is reached first, purged once per app session.

---

## 16. Privacy Model

- **No Lippy Robotics server exists.** Every database is local SQLite/JSON in the app's own storage. There is nothing to leak *to Lippy Robotics* because nothing is ever sent there.
- **Gemini** (optional, user-enabled): receives the conversation text and system prompt (persona + habit-context summary) for a request, governed by Google's own policies. The personal-memory gate (§6) exists specifically to keep TruthDb-grounded personal questions from ever reaching Gemini.
- **NWS weather API**: receives device coordinates for a forecast lookup; no Lippy Robotics involvement, no API key.
- **API keys** (Gemini/OpenAI/Claude): encrypted at rest via `ScoutSecureKeyStore` — AES-256-GCM with a key held in the Android Keystore (hardware-backed where the device supports it), versioned stored format (`"v1:<iv>:<ciphertext>"`), never `androidx.security-crypto`'s `EncryptedSharedPreferences` (deprecated for real reliability reasons, not just API churn). A one-time migration encrypts any pre-existing plaintext beta-era key on first read. Deliberately not logged anywhere, including on encryption/decryption failure.
- **Diagnostics**: see §15 — structurally incapable of containing memory content by construction, not just by convention.
- **Backup/device-transfer**: `data_extraction_rules.xml`/`backup_rules.xml` explicitly exclude cloud backup of API keys, face data, diagnostics, and the model file. TruthDb, conversation history, and journal data are currently allowed to transfer to a new device for Scout identity continuity — an explicit, discussed product decision, not an oversight.
- **Cleartext traffic**: disabled (`usesCleartextTraffic` removed from the manifest) — every network call goes over HTTPS.

---

## 17. Startup Sequence

1. `MainActivity.onCreate()` — if onboarding hasn't been completed (`OnboardingActivity.PREF_ONBOARDING_DONE`), launches `OnboardingActivity` and returns immediately; nothing else in this list runs yet.
2. `ScoutLlamaController.registerOwner(applicationContext)` claims this Activity instance as the current valid owner of TinyLlama generation.
3. `setContentView`, `setupWindow()`, `setupMemory()` (constructs all databases), `setupBrainServices()` (constructs `GeminiClient`, `VisionAnswerBuilder`, etc.), `setupViews()`, `setupVision()`, `setupPermissionLauncher()`, `setupTts()` all run — this builds the object graph and face view, but does **not** start camera, mic, or show a greeting.
4. **The hard gate**: if `LlamaEngine.isReady` is already true (e.g., surviving a configuration-change recreation), `startSystems()` runs immediately. Otherwise, `ModelDownloadActivity` is launched via an `ActivityResultLauncher` and `MainActivity` waits — Scout has no face, asks no permissions, and starts no camera/mic — until it returns `RESULT_OK`, which only happens once `LlamaEngine.isReady` is genuinely true. `ModelDownloadActivity` itself runs three phases in order: Downloading (only if the model file isn't present locally — via Android `DownloadManager` from a GitHub Release asset), Loading (triggers `LlamaEngine.loadAsync()` itself, the only way to avoid a load-ordering race with `MainActivity`'s own boot), and a brief Preparing beat.
5. `startSystems()` → `checkPermissionsAndStart()` → (once permissions are granted) `resumeSystems()`, which calls `requestCameraStartup()` and `requestSpeechStartup()`.
6. **Startup stagger**: camera starts 3 seconds and speech recognition 4.5 seconds after the brain-ready gate opens — not because either is expensive alone, but because starting camera + ML Kit + SpeechRecognizer simultaneously was found (via full on-device logcat capture) to collide with a one-time, multi-second ART bytecode-verification pass Android performs over Google Play Services' ML Kit classes on first use, causing enough memory pressure to kill GMS's persistent process and take Scout down as a side effect. The stagger only protects this cold-start collision — steady-state restarts (e.g., returning from Settings) are unaffected.
7. `startupSettled` becomes true 6 seconds after the camera actually starts, additionally gating the (expensive) face-embedding step specifically.
8. The boot announcement (spoken by TTS) is deferred until this point too — `pendingBootAnnouncement`, built fresh at actual speak-time rather than captured early, so it can never describe a stale "still warming up" state.

---

## 18. Threading / Background Tasks

No coroutines or RxJava anywhere in the codebase — concurrency is plain `Thread`, `ExecutorService`, and `Handler(Looper)`, called directly.

- **`ScoutLlamaController.executor`**: single-thread `ExecutorService`, process-wide, serializes all TinyLlama generation calls. Results are posted back via a `Handler(Looper.getMainLooper())`.
- **`cameraExecutor`**: backs `ImageAnalysis.setAnalyzer()`.
- **`embedExecutor`**: face-embedding work (the TFLite interpreter call + `PeopleDb` matching), submitted from within the camera analyzer callback so it doesn't block frame analysis itself.
- **Gemini requests**: a raw `Thread { }` per request inside `ScoutGeminiManager.tryGemini()`/`GeminiClient.generateReply()` (synchronous HTTP call), with an `AtomicBoolean` single-flight guard preventing two concurrent requests.
- **Weather requests**: similarly a raw background thread inside `ScoutWeatherManager`.
- **`HabitLayer`**: debounced saves via `Handler.postDelayed` (2s) rather than writing its JSON file on every single utterance.
- **LlamaEngine's `nativeLock`**: a `ReentrantLock` (not `synchronized`) specifically so `freeIfIdle()` can use a bounded `tryLock(timeout)` — every other native entry point (`load()`, `generate()`, `generateBenchmark()`) uses ordinary `withLock {}` mutual exclusion.
- **Main-thread discipline**: all UI mutation, TTS calls, and `respond()` invocations happen on the main thread; background threads communicate results back via `runOnUiThread {}` or a `Handler` post.

---

## 19. Future Architecture Notes

Not yet built, but worth knowing about when reading current code:

- **`HabitLayer` and `ScoutPresenceDecider` are recreated per Activity instance**, unlike `LlamaEngine`/`ScoutLlamaController` — their in-memory state (social battery, presence timers, habit topic scores before the next debounced save) resets on every configuration change. `HabitLayer` persists to disk on a debounce, so a rotation loses only very recent unsaved state; `ScoutPresenceDecider`'s cooldown/battery state is not persisted at all and fully resets. This wasn't a problem worth solving until the presence layer existed — now that it does, promoting one or both to a process-wide owner (the same pattern used for `ScoutLlamaController`) is a reasonable future direction, not yet done.
- **Two parallel phrase-pool systems exist** (`VoiceBank.say()`, an older hardcoded `when` block with simple last-pick-avoidance, and `Phrases.pick()`, a newer generic pool+cooldown-window mechanism) — new phrase categories should probably consolidate onto `Phrases`, but nothing currently forces existing `VoiceBank` categories to migrate.
- **OpenAI/Claude are placeholder providers today** — keys can be saved via `ApiKeySetupActivity`, encrypted correctly, but no client exists to actually call either API; only Gemini is wired end-to-end. `Provider.isAvailable` in `ApiKeySetupActivity.kt` hides them from the picker until that work happens.
- **Play Asset Delivery (PAD)** was considered as an alternative to the current GitHub-Release-asset download for the ~669MB model, to let the Play Store handle delivery instead of Scout's own `DownloadManager` flow — not implemented.
- **The `brain` package split is incomplete** — most of `MainActivity`'s logic (camera, speech, presence orchestration, response dispatch) still lives directly in `MainActivity.kt`. The personal-memory/entity subsystem (`ScoutMemoryGate`, `ScoutFactExtractor`, `ScoutEntityResolver`) is the current model for what further extraction should look like: pure logic, Android-API-free, unit-testable in isolation.
