# Project Scout — Master Project Summary
**Last updated: July 29, 2026 | Version 51**

Upload this document at the start of every new Claude or ChatGPT conversation about Scout.
This is the single source of truth.

---

## July 26–29, 2026 — What Changed Since Version 50

✓ **Personal-memory questions now stop before Gemini — structural guarantee, not phrasing-dependent** — New `ScoutMemoryGate.isPossiblePersonalMemoryQuery()`, checked at the top of `handleUnknownIntent()` before `tryGemini()` is ever called. Deliberately biased toward over-triggering (a false positive just costs a wasted TruthDb check; a false negative would leak a personal question to fact-blind Gemini). Two independent signals: a self-reference word + personal-topic word (whole-word regex, not `.contains()`), or mention of a name Scout already knows. `handlePersonalMemoryQuery()` gives a hard "I don't know" when TruthDb is empty, otherwise reuses `tryTinyLlamaOrFallback()` (already grounds every reply in facts, never calls Gemini). `ScoutIntentRouter`'s wife/son/dog blocks now only fire for a single-relation query — compound mentions ("my wife and son's names") fall through to the gate instead of silently answering only one. July 26.

✓ **TinyLlama SIGABRT fixed — chunked prefill, not a single oversized batch** — Confirmed from on-device logcat: a 533-token prompt exceeded `n_batch=512` in one `llama_batch_init()`/`llama_decode()` call, and llama.cpp aborts (`ggml_abort` → SIGABRT) the instant that happens. The personal-memory gate's fact grounding could easily push a prompt past 512 tokens once a dozen facts plus history are folded in. `nativeGenerate()` now prefills in chunks of at most `kNBatch` (512) tokens, with absolute token positions preserved across chunks. Also fixed a related logit-indexing bug (`llama_get_logits_ith` indexes into the *last* decode call's batch, not a global position) and added a guard that refuses and logs instead of ever submitting an oversized batch again. July 26.

✓ **Teaching moved from sentence templates to entity+property extraction** — Root-caused two bugs: "Diana's birthday is November 27th" was never stored (`TeachExtractor` only recognized "my ___ is ___"), and nickname clauses ("we call him Nick") were silently dropped — no alias concept existed anywhere. New `ScoutFactExtractor.kt` extracts (subject, property, value) anchored on property keywords and known entity names, order-independent for dates ("Diana's birthday is Nov 27" / "Nov 27 is Diana's birthday" / "Diana was born on Nov 27" all extract the same fact). New `ScoutEntityResolver.kt` resolves "my wife"/"diana" to the entity slug its facts live under — no `wife_birthday`-style key needed, scales as Scout learns more people/pets. `TruthDb.addAlias()`/`getAliases()` store a real comma-joined alias list per entity (Nicolas/Nick/etc. all resolve together); `ScoutMemoryGate` and TinyLlama grounding both updated to recognize aliases. Teaching statements never reach TinyLlama/Gemini — `handleTeaching()` tries the extractor first and always confirms exactly what was learned, or asks for a rephrase via `looksLikeUnrecognizedTeaching()` (hint-word confidence signal, never required). `TeachExtractor.kt` (face-recognition identity teaching) left untouched. July 26.

✓ **"Who is Diana?" answered by direct TruthDb lookup, not TinyLlama inference** — On-device confirmed TinyLlama had the correct fact in its prompt but didn't reliably connect "wife's name: Diana" to "who is Diana" — answered with generic name trivia instead. `handlePersonalMemoryQuery()` now checks "who is/who's <name>" directly against `wife_name`/`son_name`/`dog_name` (and aliases) before ever falling back to TinyLlama, the same pattern as the existing `ASK_WIFE_NAME`-style handlers. July 27.

✓ **Presence Layer, moment 1 — idle-silence acknowledgment** — Per Patrick's real-world testing: four hours of continuous silent operation with zero acknowledgment reads as a camera watching the room, not a companion. `ScoutPresenceDecider` gains `canMakeIdleSilenceRemark()`/`onIdleSilenceRemarkMade()` — reuses the existing time-of-day mode (QUIET/SLEEP excluded), a global cross-moment cooldown, and a longer category-specific cooldown; presence threshold ~75 min (deliberately conservative for a first test). Fixed two real correctness issues found during design before shipping: `isListening` is true almost continuously while idle (recognizer sessions just cycle) — new `isCapturingSpeech` flag (set only between `onBeginningOfSpeech()` and session end) is the real gate instead; and `faceAppearanceMs` resets on any single missed frame, which would make a 75-minute uninterrupted timer nearly unreachable — new gap-tolerant streak (`presencePresentSinceMs`/`presenceLastSeenMs`, 2-min grace) used only by this feature. `respond()` gained `isPresenceInitiated` (default false) so a presence-initiated remark doesn't misread itself as a long-absence return. New 40-second presence reply window opens when Scout *finishes* speaking (TTS `onDone`, not `onStart`). Also fixed two hardcoded `"scout"` string checks (`looksLikeDirectAddress()`, `handleTeaching()`'s background-speech guard) to read the TruthDb-configured name instead. New `PRESENCE_IDLE_SILENCE` phrase pool. July 27.

✓ **Real proactive return greeting, replacing a broken one** — Confirmed via code inspection that Scout never had a working "welcome back": the vision first-contact greeting fires at most once per process and shared the `!isListening` bug above; `consumeLongAbsenceGreeting()` measured gaps between Scout's *own* responses (not the camera at all), set its pending flag too late to ever fire on the first post-absence utterance, and only worked if that utterance happened to parse as GREET. Removed entirely. New genuine-absence + stabilized-return tracking driven by actual face presence (reuses `presenceLastSeenMs`): `CAMERA_GAP_TOLERANCE_MS` (15s, absorbs missed frames), `MIN_GENUINE_ABSENCE_MS` (10 min production), `RETURN_STABILIZATION_MS` (3s) before Scout actually speaks. Gated the same way as the idle-silence remark (shared global cooldown + its own 30-min category cooldown). New `PRESENCE_RETURN_GREETING` phrase pool. Diagnostic logging added throughout (tag `ScoutPresenceDebug`). A temporary smoke-test build (lowered thresholds, extra logging) shipped first for A32 verification, then the presence-layer commits above were built at production values. July 27–28.

✓ **Listening reminder made vision-led, not just "a face existed"** — Root cause: the "say my name first" reminder fired off any face detected as frame-largest within 3 seconds, with zero regard for whether it was oriented toward Scout — almost certainly why Scout interrupted Diana talking to Elijah. New per-frame gate using ML Kit's existing head-yaw output (no detector-config change needed): a face only counts as "facing Scout" within a yaw tolerance, minimum face-height fraction, and center-offset bound, sustained continuously for 1.5s (any disqualifying frame resets the streak). Reminder decision is reason-based (no face / not oriented / not sustained / cooldown / busy / eligible) for diagnostics. Thresholds tightened to conservative test values in a follow-up pass (yaw 25°→18°, face height 12%→18%, offset 0.55→0.40), plus a `VISION_FRESHNESS_MS` staleness check so a stalled vision pipeline can't leave a stale "qualifying" streak sitting untouched, and real measured yaw/height/offset values logged (not just the pass/fail category) for evidence-based tuning. `isSpeaking`/`isThinking` folded into the same reason chain so a logged "eligible" always matches the real decision. July 28.

✓ **Dev-only on-device TinyLlama benchmark harness** — Instrumentation only, zero change to production generation behavior/thread config. Native: exposed `n_threads_batch` as a real struct field (was landing unread in ABI padding), added `llama_perf_context()` bindings, extracted a shared `runGeneration()` helper so the benchmark path and production `nativeGenerate()` use identical code with different parameters. Measures prefill time, true wall-clock time-to-first-token, generation time, and total duration; returns performance metrics only, never the generated reply text (matches DiagnosticDb's existing invariant). New hidden dev screen (7-tap unlock on "About Scout," mirroring Android's own build-number convention) runs 4 fixed synthetic prompts across 6 thread combinations. A follow-up fix replaced the harness's sequential run order (which biased results toward low-thread combos always running while the device was coolest) with a deterministic Latin-square rotation, plus a brief cooldown pause between runs and a `runIndex` field so results can be cross-referenced against thermal load. Fixed an XML manifest comment containing a literal `--` (illegal mid-comment, broke Gradle's manifest merge and silently prevented the prior commit's code from compiling at all). July 28.

✓ **A32 crash root-caused and fixed — startup collision, not a Scout or benchmark bug** — Confirmed from a full 12,078-message on-device logcat capture: camera + ML Kit face detection + SpeechRecognizer were all starting in the same instant, colliding with a one-time multi-second ART bytecode-verification pass over Google Play Services' ML Kit classes; the resulting memory pressure killed GMS's own persistent process, and Android killed Scout as a side effect of depending on a GMS content provider in that dying process — not Scout itself being heavy. Confirmed the new benchmark harness played no role (zero related log lines; native benchmark method only resolves on first call). `requestCameraStartup()`/`requestSpeechStartup()` now stagger camera (3s) and speech (4.5s) startup after the existing `LlamaEngine.isReady` gate opens, idempotent and lifecycle-safe (re-checks `isFinishing`/`isDestroyed`/`isForeground`/permission before firing; only protects cold start, steady-state restarts unaffected). New `startupSettled` flag (6s after camera starts) additionally gates face-embedding specifically. New `ListenAttemptReason` enum + deduped diagnostic logging across every `maybeStartListening()` branch, plus wall-clock startup timing markers. July 28.

✓ **Seven ChatGPT-reviewed privacy/reliability fixes** — Hard offline-brain gate could be bypassed via `launchLoadingGate()`'s catch block (now shows a non-cancelable retry dialog instead of silently starting systems). `LlamaEngine.free()` discarded `awaitTermination()`'s return value (fixed, later fully superseded by `ScoutLlamaController`, see below). OpenAI/Claude key setup was misleading — keys could be saved but nothing in Scout used them (now flagged via `Provider.isAvailable`, hidden from the picker). API keys were plain SharedPreferences strings, and the Android Studio sample backup-rules templates were untouched (real device-transfer exclusion rules written; keys later encrypted, see below). `ScoutMemoryGate` alias handling fixed for a plural-key mismatch. `TruthDb.upsertFact()` only updated `value`/`updated_at` on conflict, silently leaving `confidence`/`source`/`last_confirmed` stale (fixed). `TruthDb` schema migration reviewed — `onUpgrade()` is empty but there's no schema change yet to migrate, confirmed as a non-issue, not a bug. July 29.

✓ **Seven ChatGPT-reviewed mic/camera fixes** — `onEndOfSpeech()` no longer prematurely calls `scheduleListenRestart()` (real `ERROR_RECOGNIZER_BUSY` risk). Wake-word bare `"out"` substring match (and the same risk for short custom names via `.contains()`) replaced with a whole-word `containsWholeWord()` check. Silence timeout is now mode-aware (5s/4s wake-word listening vs. 10s/7s open-conversation listening) instead of one fixed value regardless of mode. `ImageAnalysis` now sets an explicit 640×480 target resolution instead of allocating a full-size bitmap every analyzed frame. Scene labeling (`ImageLabeling`) now throttled independently from face detection (1.5s minimum interval) instead of running at the same ~7fps cadence. `cameraEverStarted` now only sets after `bindToLifecycle()` actually succeeds, not immediately after calling `safeStartCamera()`. Forcing `EXTRA_PREFER_OFFLINE` with no fallback was flagged as a bigger feature/privacy decision and deliberately left unimplemented pending explicit direction. July 29.

✓ **API keys encrypted via Android Keystore; TinyLlama lifecycle race fixed** — New `ScoutSecureKeyStore` (AES-256-GCM, versioned `"v1:<iv>:<ciphertext>"` format) replaces plain-string SharedPreferences storage — deliberately not `androidx.security-crypto`'s `EncryptedSharedPreferences`/`MasterKey` (both deprecated since 1.1.0-alpha07 over real reliability problems, confirmed via research, not just API churn); uses only platform Keystore APIs, no new Gradle dependency. Encryption may create the Keystore key; decryption only ever looks up an existing one and fails cleanly (typed `Available`/`Unavailable` results for both directions) rather than risk decrypting old ciphertext with a mismatched fresh key. One-time migration encrypts any pre-existing plaintext beta key on first read. Separately, diagnosed a deeper lifecycle-concurrency issue: `MainActivity`'s old per-Activity-instance `llamaExecutor` and generation-counter meant a configuration-change recreation could either leak the old instance's executor thread or leave a stale generation able to deliver its result to a destroyed Activity's callback. New `ScoutLlamaController` (process-wide singleton) now owns the single generation executor and a unified owner/generation token for the app's entire lifetime; `shutdownSystems()` only frees the ~800MB model on a genuine close (`isChangingConfigurations()`-aware), via a bounded `tryLock`-based `LlamaEngine.freeIfIdle()` rather than blocking the main thread indefinitely. Two follow-up corrections after a second review pass: added `invalidateOwner()`, called unconditionally on every `onDestroy()` (not just real closes) so a generation finishing after a real close can never reach a destroyed Activity's callback; moved discard-event logging inside `ScoutLlamaController` itself (an application-Context-scoped `DiagLog` it owns) instead of a caller-supplied lambda that captured `MainActivity`'s Activity-scoped `diagLog`. Also gave `encrypt()` a typed failure result (`EncryptResult`) instead of throwing, and switched the plaintext-key migration write from `apply()` to `commit()` so a failed persist doesn't get silently treated as done. July 29.

---

## July 19, 2026 — 16KB Alignment CONFIRMED PASS on Real Release APK: What Changed Since Version 49

✓ **16KB page size — RESOLVED, verified against the actual built release APK.** Patrick built a signed **release** APK (not a debug build) and ran Google's own `zipalign -c -P 16 -v 4` verification tool directly against it — the authoritative local check for this requirement. Full itemized result: `libLiteRt.so`, `libLiteRtClGlAccelerator.so`, `libface_detector_v2_jni.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so`, `libggml.so`, `libimage_processing_util_jni.so`, `libllama-common.so`, `libllama.so`, `libmlkitcommonpipeline.so`, and `libscout_llama.so` — all 11 previously-flagged libraries — each individually listed **`(OK)`**, with an overall result of **"Verification successful."** Separately, installing this release APK on the Fold 7 no longer triggers the "Android App Compatibility" 16KB dialog at all.

**What this confirms about the July 18 investigation:** the dialog seen on July 18 was specific to the **debuggable** build — the dialog's own text says as much ("This warning is showing because this is a debuggable app which is currently being tested"). The five llama.cpp/ggml prebuilt libraries were correctly ELF-aligned all along (confirmed via `readelf` on July 18, later same day). `libimage_processing_util_jni.so`'s alignment was genuinely fixed by the July 10 ML Kit version bump — this zipalign pass is the first real confirmation that fix landed correctly in an actual build, not just an isolated AAR. The root cause really was the debug-build install path, exactly as hypothesized in the July 18 "Later Same Day" correction below.

**Play Store submission is unblocked on the 16KB front.** This is the first claim anywhere in this entire 16KB investigation verified against the real shipped artifact using Google's own tool — not an isolated file, not a debug-only dialog, not an inference. Every prior "REOPENED"/"blocked" entry below (dated July 18) is superseded by this entry; those entries are left in place as a historical record of the investigation rather than deleted.

---

## July 18, 2026 — What Changed Since Version 47

⚠ **16KB page size — REOPENED, contradicted by real Fold 7 device evidence** — Patrick's Samsung Fold 7 (Android 15) shows Android's own "Android App Compatibility" dialog at app launch — a live OS-level ELF alignment check. It lists **11 native libraries** as NOT 16KB aligned: `libLiteRt.so`, `libLiteRtClGlAccelerator.so` (LiteRT), `libface_detector_v2_jni.so`, `libimage_processing_util_jni.so`, `libmlkitcommonpipeline.so` (ML Kit), `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so` (the llama.cpp/ggml stack), and `libscout_llama.so`. This is every native library in the app, and it directly contradicts three separate "DONE"/"FULLY DONE" claims made across this document: the July 7 `scout_llama.so` alignment fix, the July 10 ML Kit alignment claim, and the July 17 "readelf VERIFIED PASS" for LiteRT (`libLiteRt.so` — the exact file that check reported as passing is the same file failing on the real device).

**Root cause, confirmed by reading `app/src/main/cpp/CMakeLists.txt` directly:** the `-Wl,-z,max-page-size=16384` linker flag added July 7 is applied only to the `scout_llama` build target — Scout's own thin JNI wrapper, the only native code this project actually compiles. `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, and `libggml-cpu-android_armv8.2_2.so` are **pre-built binaries checked directly into `app/src/main/jniLibs/arm64-v8a/`**. `CMakeLists.txt` only links against them (`-lllama -lllama-common -lggml -lggml-base -lggml-cpu-android_armv8.2_2`) — it never compiles them, so the flag has no mechanism to reach them. Even `libscout_llama.so` itself is still failing on the real device, meaning the July 7 fix may never have actually taken effect (a stale native build cache is the leading suspect — the flag is present in source but the `.so` may not have been rebuilt since).

The ML Kit and LiteRT "done"/"verified" statuses were both based on checking an isolated artifact (a Maven AAR, an extracted library) rather than the actual built and installed APK. This real-device dialog is the first check in this entire 16KB investigation that has actually looked at what ships.

**Real remaining work, not yet started:**
1. Source or rebuild 16KB-aligned versions of the five prebuilt llama.cpp/ggml libraries — either a newer upstream llama.cpp release built with alignment support, or a from-source NDK rebuild with the linker flag applied throughout the whole dependency chain.
2. Do a full clean rebuild and re-check `libscout_llama.so` specifically, to rule out a stale build artifact before assuming the flag itself is insufficient.
3. Re-verify ML Kit and LiteRT against the real built APK's bundled `.so` files, not an isolated AAR or Maven artifact.

**Play Store submission is NOT unblocked on the 16KB front.** Every prior "FULLY DONE"/"PASS" claim regarding 16KB alignment elsewhere in this document (dated July 7 through July 17) is superseded by this entry — those claims are left in place below as a historical record, each flagged inline, rather than deleted, so the investigation trail stays intact.

---

## July 18, 2026 (Later Same Day) — 16KB Root Cause Refined: Version 48's Diagnosis Was Partly Wrong

⚠ **Correction to the entry directly above.** The claim that the five prebuilt llama.cpp/ggml libraries (`libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so`) are unaligned at the source level does not hold up. Running `readelf -lW` directly against the actual files checked into `app/src/main/jniLibs/arm64-v8a/` in this repo — the real binaries, not documentation — shows every one of them already has `Align 0x4000` (16384 bytes = 16KB) on every LOAD segment:

```
libllama.so                      LOAD ... Align 0x4000
libllama-common.so               LOAD ... Align 0x4000
libggml.so                       LOAD ... Align 0x4000
libggml-base.so                  LOAD ... Align 0x4000
libggml-cpu-android_armv8.2_2.so LOAD ... Align 0x4000
```

These five are already ELF-aligned. Their naming (`libggml-cpu-android_armv8.2_2.so` matches the `GGML_CPU_ALL_VARIANTS=ON` output pattern) matches llama.cpp's official Android CI (`build-android.yml`) exactly, which builds arm64 via NDK 29.0.14206865 — well past the NDK r28 threshold where [Google's own docs](https://developer.android.com/guide/practices/page-sizes) confirm 16KB alignment is the compiled-in default with zero extra flags. This strongly indicates these five files were pulled from an official [ggml-org/llama.cpp GitHub release](https://github.com/ggml-org/llama.cpp/releases) (asset pattern `llama-bNNNNN-bin-android-arm64.tar.gz`), not hand-built for Scout, and that upstream build is already compliant.

This also explains a detail in the Fold 7 dialog that the July 18 entry above glossed over: `libimage_processing_util_jni.so` is the *only* library the dialog tags with the specific message "LOAD segment not aligned" — a real, confirmed ELF `p_align` failure. Every other library in the list of 11, including these five, gets a generic "Unknown error" — a different failure class that an aligned ELF file can still trigger.

**Revised hypothesis:** since the ELF files themselves check out, the remaining 10 "Unknown error" failures most likely trace to **APK packaging** — whether `.so` entries are stored uncompressed and page-aligned inside the installed APK's zip container, a property distinct from each library's own internal `p_align` — or to something specific about how a **debuggable** build installs from Android Studio. (The dialog itself says: "This warning is showing because this is a debuggable app which is currently being tested.") `libscout_llama.so`'s own alignment is still unverified — nobody has run `readelf` against a freshly compiled build; that remains genuinely open.

**Revised remaining work — replaces item 1 from the July 18 entry above:**
1. Build a clean **release** APK (not a debug install from Android Studio) and side-load it onto the Fold 7. Check whether the same 10 "Unknown error" libraries clear once it isn't a debug build.
2. If they still fail on a release build, run `zipalign -c -P 16 -v 4` against the actual built APK — this checks zip-level page alignment, separate from each library's internal ELF alignment.
3. `libimage_processing_util_jni.so` is the one library with a confirmed real ELF alignment defect (despite the ML Kit version bump on July 10) — that one needs a further ML Kit dependency version check, not a packaging fix.
4. Run `readelf -lW` on the freshly compiled `libscout_llama.so` after a clean build — this is the one native-stack library that has never actually been checked directly.

**Do not spend a session sourcing or rebuilding llama.cpp/ggml from source for 16KB alignment — the evidence here shows that specific fix isn't needed.** Start with a release-build install test instead. Play Store submission remains blocked pending that test.

---

## July 17, 2026 — What Changed Since Version 46

✓ **LiteRT import corrected — build was broken** — July 16's session changed `FaceEmbedder.kt`'s import to `com.google.ai.edge.litert.Interpreter` (matching the Maven artifact name), but this class does not exist inside the LiteRT 2.1.5 AAR at runtime. LiteRT rebrands the Maven coordinates while keeping `org.tensorflow.lite` as the internal Java package. Fixed: import reverted to `org.tensorflow.lite.Interpreter`. Build confirmed successful. Commit `83ed37f`.

✓ **16KB readelf verification COMPLETE — PASS** — Patrick ran `llvm-readelf.exe -l libLiteRt.so` on Windows (NDK 28.2.13676358). Steps: copied the AAR from `~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litert/litert/2.1.5/` to `.zip`, extracted, located `libLiteRt.so` inside `jni/arm64-v8a/`. All LOAD segments show `Align 0x4000`. Also verified `libLiteRtClGlAccelerator.so` — same result. Both PASS. 16KB compliance for LiteRT is now fully binary-verified. Play Store submission unblocked on the 16KB front. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the new July 18 section at the top of this file for the full correction and root cause.

✓ **"Favorite favorite" double-prefix bug fixed** — `TeachExtractor.kt` was unconditionally prepending `"favorite_"` to all `"my X is Y"` patterns, including cases where X already began with "favorite" (e.g., "my favorite color is cyan" → key `"favorite_favorite_color"`). Root cause confirmed by brain export JSON showing `favorite_favorite_color = Cyan` and `favorite_favorite_yes_my_favorite_color = Cyan` in the truth DB. Fixed: `startsWith("favorite")` guard at line 180 of `TeachExtractor.kt` — if `rawLabel` already starts with "favorite", it is passed directly to `FactKey.custom()` without prepending the prefix. Now "my favorite color is cyan" → key `"favorite_color"`. Commit `9b353a8`.

✓ **Display fix for old double-prefix keys in `keyToHuman()`** — `handleWhatYouLearnedQuery()` in `MainActivity` now preprocesses each key before rendering: if it starts with `"favorite_favorite_"`, one `"favorite_"` prefix is stripped before display. Scout now reads back "your favorite color is cyan" correctly for facts stored under the old bug, without needing to re-teach them. Same commit `9b353a8`.

✓ **Battery optimization prompt added** — `checkBatteryOptimization()` private method added to `MainActivity`. Called from `startSystems()` with an 8-second delay. Uses `PowerManager.isIgnoringBatteryOptimizations(packageName)` to check whether Scout is excluded from battery optimization, then fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (via URI `"package:$packageName"`) to navigate the user directly to the system setting. One-time only (prefs key `"battery_opt_shown"`). `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission added to `AndroidManifest.xml`. Note: Samsung maintains a second layer ("never sleeping apps") that the standard Android API does not reach — users on Samsung devices still need to add Scout to that list manually. Commit `1abcee1`.

✓ **Thinking watchdog added** — `thinkingStartedMs: Long` field added to `MainActivity` (near `speakingStartedMs`). Set to `System.currentTimeMillis()` when `isThinking = true` in `handleQuery()`; cleared to `0L` in `speak()` (alongside `isThinking = false`). Watchdog condition added to `runRecognizerWatchdog()`: if `isThinking && !isSpeaking && thinkingStartedMs > 0L && now - thinkingStartedMs > MAX_THINKING_DURATION_MS (120_000L)`, force-clears thinking state: `isThinking = false`, `thinkingStartedMs = 0L`, `wantListening = true`, `faceView.setThinking(false)`, `scheduleListenRestart(immediate = true)`, JournalDb log. Prevents Scout staying frozen with eyes still moving when TinyLlama hangs indefinitely. Commit `1abcee1`.

✓ **DB migration cleans up double-prefix fact keys** — `migrateDoublePrefixFacts()` private method in `MainActivity`. Called from `setupMemory()` on first run (prefs key `"migrated_double_prefix_facts"`). Calls `truthDb.deleteFactsWithKeyLike(ENTITY_USER_PRIMARY, "favorite_favorite_%")` to remove all double-prefix pollution, including the TTS self-echo entry `"favorite_favorite_yes_my_favorite_color"`. Commit `e24fad9`.

**TTS self-echo entry explained:** When Scout spoke "yes, my favorite color is cyan" (a Gemini reply), the TTS audio bled back through the mic within the 30-second conversation window. `onResults()` received the transcript "yes my favorite color is cyan." The self-echo guard checked `lastScoutUtteranceNormalized` — but the stored normalized text did not match due to the leading "yes" prefix. TeachExtractor then processed it as a new teaching phrase ("my favorite color is cyan" extracted) and stored it as `"favorite_favorite_yes_my_favorite_color"` (TeachExtractor tried to use the full matched string including "yes" as the label). This is a two-part vulnerability: (1) self-echo guard normalization mismatch, (2) TeachExtractor over-broad match of prefix fragments. Cleaned up by migration. Root fix deferred to future session.

✓ **TruthDb gains `deleteFact()` and `deleteFactsWithKeyLike()`** — Two new public methods added to `TruthDb.kt`:
- `deleteFact(entity, factKey)` — removes a single specific fact row.
- `deleteFactsWithKeyLike(entity, pattern)` — removes all facts for an entity where `fact_key LIKE ?` (SQL LIKE syntax, e.g. `"favorite_favorite_%"`).
Both use `entity.lowercase()` for case-insensitive matching, consistent with other TruthDb methods. Commit `e24fad9`.

✓ **People DB added to brain export** — `ScoutExportManager` constructor updated to accept `peopleDb: PeopleDb` (and `MainActivity` updated to pass it). Two new sections exported:
- `"people"` — named faces from the `people` table (`face_hash`, `name`, `first_met`, `last_seen`; no BLOB embeddings). Ordered by `last_seen DESC`. Only rows where name is not null and not empty.
- `"face_embeddings"` — per-name embedding count from `person_embeddings` table (`SELECT name, COUNT(*) GROUP BY name ORDER BY name`).
"Scout, export your brain" now gives a complete picture of both the truth DB and the people DB. Commit `aa10bc9`.

⚠ **"Very" — confirmed NOT in truth DB; must be in people DB** — Brain export JSON (sent by Patrick) contained no entry for "Very" in the truth section. "Very" is stored in the `people` table (face_hash-keyed, likely from an early hash-based recognition session before ArcFace). Patrick will run "Scout, export your brain" with the updated export (which now includes `people` and `face_embeddings` sections) and share the new JSON to identify the entry and fix it.

---

## July 16, 2026 — What Changed Since Version 45

✓ **LiteRT migration — code done** — `app/build.gradle.kts`: `org.tensorflow:tensorflow-lite:2.17.0` replaced with `com.google.ai.edge.litert:litert:2.1.5`. `FaceEmbedder.kt` line 5: import initially changed to `com.google.ai.edge.litert.Interpreter` (July 16), then corrected to `org.tensorflow.lite.Interpreter` (July 17, commit `83ed37f`) — LiteRT rebrands the Maven coordinates but the internal Java package is still `org.tensorflow.lite`. Build confirmed successful. Commits `9676192`, `83ed37f`. Alignment confirmed in 2.1.x line per GitHub issue #6299; Scout does not use GPU/OpenCL delegates. Readelf verification COMPLETE July 17 — see July 17 section above.

✓ **Face recognition accuracy — 3 root-cause bugs fixed** — Root cause of the repeated Diana/Elijah confusion isolated through code review. Three independent bugs in `PeopleDb.kt` and `MainActivity.kt` were each capable of causing person misidentification. All fixed in commit `b6c5579`. Patrick must run "Scout, forget [name]" for each affected person before re-introducing them — existing profiles may already be polluted from prior teach/forget cycles and must be cleared for clean re-training.

**Bug 1 — No margin check:** `findBestMatchName` returned a name whenever the top score exceeded the threshold, even when the second-place candidate scored nearly as high — a coin-flip situation where Scout would confidently name the wrong person. Fixed: `minMargin = 0.08f` parameter added to `findBestMatchName`. If the gap between the top two candidates is < 0.08f, the function returns `null` (Scout says nothing) rather than guessing. New function `findBestMatchNameWithScore` also added — returns `Pair<String, Float>?` so `MainActivity` can gate `addNamedEmbedding` calls on the winning confidence score.

**Bug 2 — Profile pollution:** `addNamedEmbedding` was called whenever a face matched above the recognition threshold (0.65f), including borderline and ambiguous frames. Any frame where two people's scores were close and the wrong name was returned as the winner would add the wrong person's embedding to the named profile. Fixed: `CONFIDENT_EMBED_THRESHOLD = 0.72f` constant added to `MainActivity`. Both the primary face path and the secondary face path now only call `addNamedEmbedding` when the match score is ≥ 0.72f — well above the 0.65f floor. Borderline matches contribute nothing to profiles.

**Bug 3 — Cap-and-stop / profile stagnation:** `addNamedEmbedding` hard-stopped at `MAX_EMBEDDINGS_PER_PERSON = 12` with an early `return`. Once a profile was full, it was frozen from whatever 12 samples were captured earliest — possibly all under uniform conditions. Fixed: at cap, `maxByOrNull` identifies the most redundant existing embedding (highest cosine similarity to the incoming one) and replaces it in-place via `db.update`. The stored set stays diverse as lighting, angle, and distance change over time. A private `scoreByPerson` helper was also extracted for cleaner score aggregation.

**Additional fix:** `handleTeaching()` in `MainActivity` — the `forgetPerson` code path now also sets `lastFaceEmbedding = null`. Previously, the last embedded frame persisted in memory across a forget/re-introduce cycle and could pre-seed the new profile with a stale embedding from before the forget.

---

## July 10–13, 2026 — What Changed Since Version 43

✓ **Privacy Policy — in-app dialog** — `SettingsActivity.kt`: `showPrivacyPolicy()` builds a scrollable `AlertDialog` with full policy text. Covers: Scout's offline-first design, Gemini as optional user-key-only service (governed by Google's own policies), NWS `api.weather.gov` receives device coordinates for weather (no Lippy Robotics involvement), no personal data collected or retained by Lippy Robotics. Accessible: Settings → About Scout → Privacy Policy. Fully offline — no browser required. DONE July 11.

✓ **Terms of Use — in-app dialog** — `SettingsActivity.kt`: `showTermsOfUse()` builds scrollable dialog with: acceptance clause ("By downloading or using Scout, you agree to these Terms"), service-as-is limitation, third-party services clause (Gemini governed by Google's own policies), changes-to-terms clause (continued use = acceptance of updates). Accessible: Settings → About Scout → Terms of Use. DONE July 11.

✓ **terms.html added to repo root** — Full Terms of Use HTML page for `lippy-robotics.gt.tc` website. Two Google Play compliance clauses added beyond the original design: (1) acceptance block at top; (2) changes-to-terms block before Limitation of Liability. Commit `b5735f5`. DONE July 10.

✓ **ML Kit updated for 16KB page alignment** — `face-detection` 16.1.6 → 16.1.7: arm64-v8a confirmed 16KB aligned (ML Kit issue #986, resolved Dec 2025; Scout is arm64-only so 32-bit ABI gap does not apply). `image-labeling` 17.0.7 → 17.0.9: pulls in fixed `vision-common`, resolving `libimage_processing_util_jni.so` alignment on arm64. Commit `60443f3`. DONE July 10. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the new July 18 section at the top of this file for the full correction and root cause.

✓ **LiteRT migration — code done (readelf pending)** — `app/build.gradle.kts`: `tensorflow-lite:2.17.0` replaced with `litert:2.1.5`. `FaceEmbedder.kt` import changed `org.tensorflow.lite.Interpreter` → `com.google.ai.edge.litert.Interpreter`. Drop-in replacement — same API surface, no logic changes. Commits `9676192`. Alignment confirmed in 2.1.x line per GitHub issue #6299; Scout does not use GPU/OpenCL delegates so `libLiteRtOpenClAccelerator.so` is irrelevant. Prior failed attempt (`litert:1.4.0` — not in Maven, reverted commit `eb8223e`) documented for history. **Readelf verification still pending** — Patrick runs `readelf -l liblitert_jni.so | grep -A1 LOAD` after next Android Studio build. `p_align: 0x4000` = pass; `p_align: 0x1000` = fail. Required before Play Store submission. DONE July 16 (code only).

✓ **DiagReportActivity.kt built (diagnostic reporting Step 4–6)** — New activity reads from `DiagnosticDb` and displays a formatted plain-text report in a monospace ScrollView. Four sections: Privacy Notice (verbatim policy disclosure wording), System Information (generated timestamp, Scout version, Android version + API level, device model), Event Log (last 7 days newest-first from `db.getAll()`), Crash Log (`db.crashFile` contents). Report is privacy-safe: no speech text, user names, family names, memories, photos, face data, location, API keys, exception messages, stack traces, URLs, or file paths. `EXTRA_SHOW_SHARE` boolean Intent extra determines launch mode. Registered in AndroidManifest. DONE July 13.

✓ **View/Share mode differentiation** — `activity_diag_report.xml` wraps all sharing controls (notes-field label, `EditText etNotes`, two guidance TextViews, Share button) inside a single `LinearLayout android:id="@+id/llShareControls"`. `DiagReportActivity.onCreate()` reads `EXTRA_SHOW_SHARE` (default false) and sets `llShareControls.visibility = VISIBLE / GONE`. View mode: clean read-only display. Share mode: full UI visible. Share flow: writes user notes + report to `filesDir/diag/diag_report.txt`, obtains URI via `FileProvider.getUriForFile()`, fires `ACTION_SEND` intent with `EXTRA_STREAM`, `EXTRA_EMAIL`, `EXTRA_SUBJECT`, `EXTRA_TEXT`, `ClipData` (Android 10+ read permission), and `FLAG_GRANT_READ_URI_PERMISSION`. DONE July 13.

✓ **Settings DIAGNOSTICS section wired** — Three `navRow()` entries in `SettingsActivity.kt`: (1) "View Diagnostic Report" → `DiagReportActivity` with `EXTRA_SHOW_SHARE=false`; (2) "Share Diagnostic Report" → with `EXTRA_SHOW_SHARE=true`; (3) "Clear Diagnostic History" → `confirmDeleteDiagLogs()`. Confirmation dialog lists what is removed (events, crash log, generated report file) and explicitly states memories, settings, and model files are unaffected. Delete call: `DiagnosticDb(this).use { db -> db.deleteAll() }` — `.use {}` guarantees DB close even if `deleteAll()` throws. DONE July 13.

✓ **Support button opens browser** — `showSupport()` in `SettingsActivity` replaced the old dead-end "Contact Us" AlertDialog. Now fires `Intent(Intent.ACTION_VIEW, Uri.parse("https://lippy-robotics.gt.tc/support.html"))`. Fallback AlertDialog with title "Unable to open the Scout Support Center" shown if no browser app handles the intent. Import `android.net.Uri` added. DONE July 13.

✓ **Reset Memory Layers destructive styling** — `private val DESTRUCTIVE = Color.parseColor("#FF4D4D")` added after `TXT_MUTE`. `navRow()` gains optional `titleColor: Int = TXT` parameter — label rendered in that color; all other callsites unaffected (default value). "Reset Memory Layers" passes `DESTRUCTIVE`. Confirmation dialog's positive "Reset" button colored red via `dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(DESTRUCTIVE)` — called after `.show()` so the button view already exists. Standard Android pattern for irreversible data-deletion actions. DONE July 13.

✓ **NDK 28.2 llvm-strip build fix** — `packaging { jniLibs { keepDebugSymbols += "*/x86_64/*.so" } }` added to `app/build.gradle.kts`. NDK 28.2's `llvm-strip` crashes with `STATUS_ILLEGAL_INSTRUCTION` when stripping x86_64 ELF binaries from the ML Kit bundle. AGP `keepDebugSymbols` tells the build system to skip stripping those ABIs entirely. Does not affect ARM64 (the production ABI for all Scout test devices). DONE July 13.

✓ **Gradle daemon OOM fix (Windows page file exhaustion)** — `org.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -Dfile.encoding=UTF-8` in `gradle.properties`. Root cause: G1GC (JVM default) reserves very large virtual address space upfront (~4× heap), exhausting the Windows page file on Patrick's machine. SerialGC reserves only what it immediately needs. `1024m` is sufficient for Scout's build. Secondary cause: antivirus background RAM consumption (uninstalled). Git pack memory limits also added (`pack.windowMemory 64m`, etc.) to prevent OOM during `git pull`. DONE July 13.

✓ **Google Play Data Safety analysis** — Full source code review completed. Conclusions: (1) Scout sends no data to Lippy Robotics servers → "No data collected" is correct, no collection box needed. (2) Gemini API call sends user query text to Google → declare "App interactions → User-generated content" as Shared / Optional. (3) Weather API (`api.weather.gov/points`) sends device coordinates to NWS → declare "Location → Approximate location" as Shared / Optional. Data Safety Step 2 answer: "Yes, my app shares user data with third parties." DONE July 13.

---

## July 7, 2026 (Session 2) — What Changed Since Version 42

✓ **Thinking glance amplitude raised** — `thinkGlanceSideX` raised from `8–20px` to `35–65px`. Previous range drove only 3–6px of face drift (invisible). New range drives 12–21px with the 0.32f faceGazeDriftX multiplier — clearly visible as a side glance. DONE July 7.

✓ **Thinking expression redesigned — curious and engaged** — Patrick provided clear direction: expression should read "Hmm, let me think" not "I'm tired." Four changes made:
- **Brow asymmetry**: One brow (side > 0) lifts 22px + gentle sine oscillation with questioning arch (thinkTilt -10f retained). Other brow (side < 0) barely moves (5px). Was both brows lifting nearly equally (24/26px) — that read as surprised, not curious.
- **thinkInnerLift reduced**: Was 20px on both sides (made quiet brow look worried/furrowed). Now 6px on side < 0 only — relaxed, natural.
- **Lid asymmetry**: Right eye target gains +0.08f droop during thinking (total ~0.15f vs left's 0.07f). Still mostly open — concentration, not sleepiness.
- **Mouth corner asymmetry**: Right corner sits 3px higher than left when thinking (corYR = cy - 3f vs corYL = cy + 2f). Barely perceptible thoughtful side-smile. Friendly and warm.
DONE July 7.

---

## July 7, 2026 (Session 1) — What Changed Since Version 41

✓ **16KB page alignment fix confirmed** — `target_link_options(scout_llama PRIVATE -Wl,-z,max-page-size=16384)` added to CMakeLists.txt. Fixes `dlopen` failure on Samsung devices running Linux 6.x kernels (Android 15 / Galaxy A32, Fold 7). Logcat confirmed: "scout_llama native library loaded successfully." DONE July 7. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the new July 18 section at the top of this file for the full correction and root cause.

✓ **bootstrapModelFile() added to MainActivity** — On every startup, Scout checks `filesDir` for the TinyLlama model. If absent, copies it from two source locations: (1) app-specific external dir `/sdcard/Android/data/com.example.scoutface/files/` (no permission needed, any Android version); (2) root `/sdcard/` (requires READ_EXTERNAL_STORAGE, Android ≤12 only). `READ_EXTERNAL_STORAGE` added to manifest with `maxSdkVersion="32"` — not requested on Android 13+. Copy runs in a background thread at startup; the 90-second TinyLlama load delay gives it ample time. After first successful copy, subsequent launches skip it. DONE July 7.

✓ **Offline fallback message fix** — When Online Features are deliberately turned OFF by the user, Scout no longer says "I'm having trouble connecting." Now says "I'm working offline right now, so that one's a bit beyond me." `speakUnavailableIfNeeded()` is only called when `isGeminiEnabled()` is true. DONE July 7.

✓ **TinyLlama confirmed working on A32 and Fold 7** — Model file pushed to both devices via adb. `bootstrapModelFile()` successfully copies from external to internal storage on both. TinyLlama answers questions with Online Features OFF. DONE July 7.

✓ **Head-turn amplitude fixed** — `faceGazeDriftX` multiplier was `0.07f` (max ±5px on 1920px canvas = ~2 physical pixels, completely invisible). Raised to `0.32f` for X and `0.26f` for Y — max ±24px X / ±14px Y. Now clearly readable as a neck turn when Scout looks toward someone. DONE July 7.

---

## July 4, 2026 — What Changed Since Version 39

✓ **PeopleDb threshold raised back to 0.65f** — ArcFace upgrade (July 3) lowered threshold to 0.60f, but this caused Diana/Elijah cross-contamination (Diana's face scored above 0.60f against Elijah's stored embeddings — root cause of "I see Elijah" when only Diana was present). Threshold raised back to 0.65f in both `findBestMatch` and `findBestMatchName`. `cursor.use {}` added to both methods (leak fix). `forgetPerson` made atomic with `beginTransaction()`/`setTransactionSuccessful()`/`endTransaction()`. `addNamedEmbedding` now checks `COUNT(*)` first and skips INSERT if already at `MAX_EMBEDDINGS_PER_PERSON (12)`. DONE July 4.

✓ **VisionAnswerBuilder dogLine + 2-face branch fix** — 3+ faces branch was missing `dogLine` (asymmetric with the 1- and 2-face branches); added. 2-face branch reorganized: `secondaryFaceName` arm now precedes `pendingIntroName` arm; new `else` arm handles case where primary is unknown but secondary is known. Freshness window 3500ms → 1800ms (line 196). DONE July 4.

✓ **Secondary face `findBestMatch` fallback** — Secondary face path now tries `findBestMatchName` first (person_embeddings table, threshold 0.55f), then falls back to `findBestMatch` (people.embedding BLOB, also 0.55f) + `getName()`. Closes the recognition gap when only the single-BLOB embedding exists for a person. DONE July 4.

✓ **Caption persistence fix** — When closed captions are turned off in Settings, `onResume()` now immediately hides the caption TextView and removes the pending hide Runnable. Previously the last spoken caption line lingered on screen after toggling captions off. DONE July 4.

✓ **Startup diagnostics** — At boot, Scout checks STT and TTS availability. TTS failure: Toast shown ("Scout's voice isn't working. Please restart the app…"). STT unavailable: Scout speaks a friendly warning 4 seconds after boot and logs to JournalDb. DONE July 4.

✓ **First-boot onboarding redirect** — Top of `MainActivity.onCreate()` checks `OnboardingActivity.PREF_ONBOARDING_DONE` in `scout_prefs`. If false, starts OnboardingActivity and finishes MainActivity immediately. New installs never reach the main UI until onboarding is complete. DONE July 4.

✓ **OnboardingActivity.kt (new)** — Full 5-screen onboarding flow. Screens: Welcome / Trial / This Is Just The Beginning / Privacy / Ready To Begin. `currentPage` is the single source of truth driving both navigation dots and the "X / 5" counter. Scout icon on screens 1 and 5 only. Colors: `#0D1728` bg, `#9BBEFF` active dot/button, `#2A3A5C` inactive dot, `#B0C4E8` body text. `finishOnboarding()` sets `PREF_ONBOARDING_DONE=true` in `scout_prefs` AND `gemini_enabled=false` in `scout_memory`. DONE July 4.

✓ **New installs default to offline mode** — `finishOnboarding()` writes `gemini_enabled=false` to `scout_memory` SharedPrefs. Gemini opt-in via Settings after adding a key. Prevents new users from being in "online mode not configured" state on first launch. DONE July 4.

✓ **BOOT_NO_KEY phrases replaced** — Old vague phrases replaced with actionable settings-access tip: "Open settings any time by sliding the screen to the right." / "Slide the screen to the right any time to open settings." / "You can open settings any time by sliding right." DONE July 4.

✓ **CLAUDE.md created** — New file in repo root. Documents full `git pull origin claude/test-coverage-analysis-hsp9lt` and `git push` commands, critical hardcoding rules, architecture quick reference, test devices, master doc list. Persists across session compaction so all future Claude instances have the context. DONE July 4.

✓ **ModelDownloadActivity.kt (new)** — Portrait-only loading screen for TinyLlama model download. All 39 humorous loading messages from Patrick's approved list. ObjectAnimator animation: message slides in from the right (320ms), holds for 3.8s, slides out left (280ms), next enters from the right. Messages shuffled at startup and reshuffled on each full cycle. `updateProgress(percent, downloaded, total, timeLeft)` method ready for Play Asset Delivery wiring. Layout: "SCOUT" wordmark + "AI COMPANION APP" subtitle, 220dp Scout face icon, animated message frame, `#9BBEFF` progress bar, downloaded/total/time row. Registered in AndroidManifest as portrait. DONE July 4.

---

## July 3, 2026 — What Changed Since Version 38

✓ **ArcFace face recognition upgrade** — MobileFaceNet (192-dim) replaced with InsightFace MobileFaceNet trained with ArcFace Additive Angular Margin Loss (512-dim, 4.8MB). Input: 112×112 RGB, preprocessing `(px - 127.5f) / 128f` unchanged. FaceEmbedder.kt: EMBEDDING_SIZE 192→512, output array `Array(1) { FloatArray(512) }`, input buffer single-batch (removed the repeat(2) loop). PeopleDb upgraded to v4; migration clears incompatible 192-dim embeddings (preserves names and face hashes — everyone re-introduces once). New cosine similarity threshold: 0.60f (ArcFace same-person range ~0.5–0.95, different-person ~0.0–0.4; 0.40f caused "everyone is Patrick" false positives). DONE July 3.

✓ **"I see you, X" → "I see X"** — VisionAnswerBuilder and MainActivity greeting path both updated. Scout now says "I see Patrick" and "I see Patrick and Diana" instead of "I can see you, Patrick." Sounds like a description, not an address — better match for what Patrick wanted. DONE July 3.

✓ **Diana (secondary face) fix** — Secondary face processing block now also consumes `pendingFaceIntroName`. Previously, introducing "this is my wife Diana" with two people in frame stored the pending name but the secondary face block never checked it — Diana was always "someone else." Fixed: if secondary face embedding doesn't match anyone AND `pendingFaceIntroName` is set, the pending name is assigned to the secondary face and stored via `addNamedEmbedding()`. DONE July 3.

✓ **Personality phrase pools — Phrases.kt (new file)** — New `Phrases` object with anti-repeat rolling window (cooldown = pool.size / 2; chosen phrase blocked until half the pool has been used). Scout no longer repeats the same line back-to-back. Pools: BOOT_ONLINE (6), BOOT_OFFLINE_FAST (5), BOOT_OFFLINE (6), BOOT_NO_INTERNET (4), BOOT_NO_KEY (3), REMEMBER (9), REMEMBER_NAME (6), REMEMBER_MY_NAME (5), REMEMBER_WIFE (5), REMEMBER_SON (5), REMEMBER_DOG (4), GOODBYE (7). `{name}` placeholder substituted via `pickNamed()`. DONE July 3.

✓ **Adaptive boot greeting — ScoutBootStatus.kt rewritten** — Offline boot greeting is now adaptive: if TinyLlama loaded in under 2 seconds last session (`llama_last_load_ms` in SharedPreferences), Scout picks from BOOT_OFFLINE_FAST (skips warming-up line). Otherwise picks from BOOT_OFFLINE (includes warming-up). TinyLlama load time measured and stored in SharedPreferences inside `tryLoadOfflineBrain()`. ScoutBootStatus now takes a `lastLlamaLoadMs: () -> Long` lambda (default Long.MAX_VALUE). DONE July 3.

✓ **Online boot phrases mention offline backup warming up** — All 6 BOOT_ONLINE phrases now include a line about the offline backup warming up in the background (e.g., "Online mode is on. My offline backup is warming up in the background."). Previously said nothing about warming up when online. DONE July 3.

✓ **Goodbye and Remember responses now varied** — `respond("Okay. I'll see you later.")` replaced with `Phrases.pick("goodbye", Phrases.GOODBYE)`. All remember confirmation responses replaced with Phrases pool calls. Scout no longer says the same goodbye or confirmation line every session. DONE July 3.

---

## June 30, 2026 — What Changed Since Version 37

✓ **Dynamic robot name — all spoken responses fixed** — Boot greeting, identity feelings reply, identity fallback, and offline brain fallback reply all now read the robot name from TruthDb at runtime (`truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"`). Renaming Scout in Settings is now fully reflected in every spoken line. No more hardcoded "Scout" in any spoken response. DONE June 30.
✓ **TeachExtractor.kt — 8 new teaching patterns** — "that person is my son/wife [name]", "that is my son/wife [name]", "his name is [name]", "her name is [name]", "that is [name]", "that person is [name]" all now recognized and stored. Root cause of "I see one person" after teaching a family member's face — TeachExtractor returned null → fell to Gemini → Gemini said "I'll remember" but stored nothing. DONE June 30.
✓ **VisionAnswerBuilder freshness extended 1800ms → 3500ms** — Camera is blocked during TTS (`isThinking || isSpeaking` gate). If Scout speaks for more than 1.8s before Patrick asks "what do you see?", face data was stale → "VISION_STALE" or "I see one person." 3500ms covers most TTS utterances. DONE June 30.
✓ **registerFamilyMemberFace() guard** — If the largest face's position-hash already carries a DIFFERENT person's name (i.e. primary user recognized by position but below embedding threshold), the incoming name is stored in `pendingFaceIntroName` instead of overwriting. Prevents the A32 misidentification where Scout called Patrick "Elijah." DONE June 30.
✓ **TinyLlama filter additions** — "family friendly companion" and "family companion robot" added to bad-response filter in `cleanOfflineReply()`. Stops TinyLlama from saying "and my name is Scout, a family friendly companion." DONE June 30.
✓ **Pet Mode design locked** — Nicolas Protocol renamed to Pet Mode. Covers ALL animals (dog, cat, bird, rabbit, etc.). When a pet first appears in frame: if pet name is stored in TruthDb → Scout says "Hello [name]." softly. If no name stored → Scout says "Well... hello there little one. I hope someone will tell me your name soon." Once per appearance (2-minute cooldown). Scout continues operating normally after the greeting — does NOT go silent. Future robot body: steer-around Bluetooth command when Scout is mobile. NOT YET CODED — design locked, implementation next.

---

## June 29, 2026 — What Changed Since Version 36

✓ **Launcher icon eyes fixed** — Face was at 100% of the foreground canvas; eyebrows at ~14% from top were being clipped by the circular launcher mask (safe zone = inner 66.7%). Scaled face to 68% of canvas with dark navy #0D1728 background. All 5 mipmap densities regenerated. Patrick confirmed: "icon looks good 👍"
✓ **Face threshold raised 0.75→0.82** — Father/son genetic similarity (Patrick/Elijah) caused cosine scores of 0.76–0.79, above the old 0.75 threshold. Genuine same-person matches score 0.80+. Threshold raised to 0.82 in `PeopleDb.findBestMatch()`. DONE June 29.
✓ **"Scout, forget [name]" command** — `forgetPerson(name)` now wipes both the `people` table (sets name='', embedding=NULL) and the new `person_embeddings` table (DELETE). Voice command parsed in `handleTeaching()`. DONE June 29.
✓ **TTS deafness bug fixed** — `speak()` sets `isSpeaking=true` and `wantListening=false`. If Android kills the TTS engine during idle and `tts.speak()` fails silently (no onDone/onError callback), Scout goes permanently deaf. Three-layer fix: (1) `speak()` checks return value — if `TextToSpeech.ERROR`, immediately clears `isSpeaking`; (2) `speakingStartedMs` timestamp set when speaking begins, cleared in `onDone`/`onError`; (3) 45-second watchdog in the recognizer watchdog loop force-clears `isSpeaking`, `isThinking`, sets `wantListening=true` if TTS is stuck. DONE June 29.
✓ **Voice slider changes now stick** — SettingsActivity was saving pitch/speed to `scout_prefs` but MainActivity was reading from `scout_memory` (different SharedPreferences file) and hardcoding defaults in `onInit()`. Fixed: `MainActivity.scoutPrefs` reads from `"scout_prefs"`. `onInit()` and `onResume()` both call `scoutPrefs.getFloat("voice_pitch", 0.98f)` / `getFloat("voice_speed", 0.88f)`. Patrick confirmed: "voice is fixed." DONE June 29.
✓ **Greeting words blocked from name storage** — "hello", "hi", "hey", "howdy", "greetings", "sup", "yo" added to `blockedNames` in `handleTeaching()`. Scout no longer says "I'll remember your name is hello." DONE June 29.
✓ **Gemini responses no longer truncated mid-sentence** — `maxOutputTokens` raised 250→600 in `GeminiClent.kt`. "Always end on a complete sentence — never stop mid-sentence." added to Gemini system prompt in `ScoutPromptBuilder.kt`. When Gemini returns `finishReason=MAX_TOKENS`: trims to last `.`/`!`/`?` boundary; returns null (falls through to TinyLlama) if no sentence boundary found. DONE June 29.
✓ **Gemini quota/cooldown announced to user** — Previously `tryTinyLlamaOrFallback()` only called `speakUnavailableIfNeeded()` after TinyLlama also failed. Now: `isInCooldown()` exposed on `ScoutGeminiManager`, cooldown check added at top of `tryTinyLlamaOrFallback()` — if in cooldown, `speakUnavailableIfNeeded()` is called immediately. `speakUnavailableIfNeeded()` returns `Boolean`: `true` = message spoken (caller returns), `false` = suppressed within repeat gap (TinyLlama answers normally). Repeat gaps: 6 hours for daily quota, 10 minutes for rate limit. Scout says: "Gemini says you've reached your daily limit, but I can do my best locally to help any way I can." DONE June 29.
✓ **Secondary face recognition** — Previously only the largest (primary) face got embedded per frame. The second face (e.g. Elijah when Patrick and Elijah are both in frame) was never processed — hence "someone else." Fix: (1) `PeopleDb` upgraded to v3, adds `person_embeddings` table (stores up to 5 embeddings per named person via `addNamedEmbedding()`; `findBestMatchName()` scans it and returns the name directly). (2) `MainActivity`: computes `secondFace` = second-largest face; captures `capturedSecondBox`; in the same `embedExecutor.submit` block, after primary face processing, also crops/embeds the secondary face and calls `findBestMatchName(emb2, threshold=0.80f)` → stores in `lastSecondaryFaceName` (@Volatile). Clears `lastSecondaryFaceName` when `faces.size < 2`. All name-confirmation paths also call `addNamedEmbedding()` to accumulate embeddings for future matching. (3) `VisionAnswerBuilder.build()` gets new `secondaryFaceName` param — used in `faceCount==2` branch. Scout now says "I can see you, Patrick and Elijah." DONE June 29.

---

## June 28, 2026 — What Changed Since Version 35

✓ **TinyLlama re-enabled with safe delayed load** — `startOfflineBrain()` restored. `tryLoadOfflineBrain()` helper added: 90-second startup delay, 800MB RAM guard (`availMem < 800MB → skip`), `nCtx=512` (reduced KV cache), `nThreads=2`. Model loaded from `filesDir/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf`. TinyLlama is back as the offline brain. Needs A32 real-world confirmation that LMKD crash does not return. DONE June 28.
✓ **TinyLlama automatic Gemini fallback** — `tryGemini()` now takes `onAnswered: (() -> Unit)?` and `onFailed: (() -> Unit)?` callbacks. When Gemini times out, 503s, or returns nothing, `onFailed` fires `tryTinyLlamaOrFallback(qNorm)`. Extracted helper shared by: direct path (Gemini disabled/no key/no internet) AND the Gemini `onFailed` path. Scout no longer silently fails when Gemini is down. DONE June 28.
✓ **Gemini timeouts reduced** — `connectTimeout = 10_000` (was 20,000), `readTimeout = 12_000` (was 30,000) in `GeminiClient.kt`. Was causing 30-second `SocketTimeoutException` hangs before TinyLlama fallback could kick in. DONE June 28.
✓ **"Repeat that" / "what did you say?" intent** — `isRepeatRequest()` added just before `handleQuery()`. Detects "repeat that", "say that again", "what did you say", "what was that", "pardon", "sorry what", and similar. `respond()` now caches the last meaningful answer (5+ words, `lastMeaningfulResponse`, 4-minute TTL `REPEAT_CACHE_TTL_MS`). Intent routed early in `handleQuery()` before any brain call — works offline instantly. DONE June 28.
✓ **Brain source Toast** — `pendingBrainSource` variable set before `respond()` ("Gemini (online)" or "TinyLlama (offline)"). Toast shown inside `respond()` after each answer. For testing — helps Patrick identify which brain is actually responding. DONE June 28.
✓ **Gemini default fixed** — `isGeminiEnabled()` was using `getBoolean(PREF_GEMINI_ENABLED, false)`. Default `false` meant Gemini was always blocked on fresh install even with a valid key saved. Fixed to `getBoolean(PREF_GEMINI_ENABLED, true)`. Note: Settings "Offline Mode" toggle correctly inverts `gemini_enabled` in `scout_memory` SharedPrefs. DONE June 28.
✓ **Gemini daily quota cooldown reduced** — `DAILY_QUOTA_COOLDOWN_MS = 60L * 60L * 1000L` (1 hour, was 6 hours) in `GeminiClent.kt`. Faster dev recovery after quota exhaustion from testing. DONE June 28.
✓ **Face greeting fires once per launch** — `greetedThisSession` was being reset to `false` every 5 seconds of face absence (when `GREET_RESET_ABSENCE_MS` elapsed). This caused the greeting to fire again every time the face briefly left frame. Fixed by removing the `greetedThisSession = false` reset block. Now only `faceAppearanceMs` is reset on absence. Scout greets once per boot only. DONE June 28.
✓ **STT reliability improved** — `RecognizerIntent` now includes `EXTRA_PREFER_OFFLINE = true` (avoids Samsung network STT dependency), `SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 10_000L` (longer silence window), `SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 7_000L`. `onError()` handles `ERROR_RECOGNIZER_BUSY` (error 8) with a 600ms delay before restart instead of immediate retry. DONE June 28.
✓ **Duplicate prompt serves cached Gemini answer** — Was saying "I heard that. I don't want to ask online twice." Now: on duplicate within the `duplicatePromptWindowMs` window, checks `lastGeminiReply` (4-minute TTL) and serves it if available. Allows through if no cache (resets `lastPromptMs = 0L` to bypass the guard). DONE June 28.
✓ **speakUnavailableIfNeeded() made public** — Needed so the fallback chain in `tryTinyLlamaOrFallback()` can call it from `MainActivity` when neither brain is available. DONE June 28.
✓ **Testing confirmed on A32** — Patrick confirmed active development and testing is on Samsung Galaxy A32. Fold 7 is listed as primary but A32 is the current working device.

---

## June 27, 2026 — What Changed Since Version 34

✓ **Wrong-name teaching with 2 people in frame FIXED** — Saying "this is my wife Diana" was sometimes stored as the primary user's name (Scout replied "I'll remember your name is Diana"). STT occasionally drops "my wife", making it sound like "this is Diana" → FactKey.NAME. Fixed by guard in `handleTeaching()`: if primary user already known AND incoming name differs AND 2+ faces in frame → treat as secondary person introduction, not primary user rename. DONE June 27.
✓ **ML Kit label whitelist** — Replaced old blacklist approach with OBJECT_WHITELIST in VisionAnswerBuilder.kt (~80 real household objects). Old blacklist couldn't block labels like "aerospace engineer", "dude", "vacation". Now only known household objects reach Scout's voice. DONE June 27.
✓ **`lastKnownFaceName` set immediately after teaching** — Previously set only by the embedExecutor background cycle (2s interval). If Patrick said "what do you see?" within 2 seconds of "I am Patrick", Scout still said "I see one person." Fixed by setting `lastKnownFaceName = value` immediately inside handleTeaching(). DONE June 27.
✓ **`finishThinking()` was empty no-op — FIXED** — Critical bug: `isThinking` was set to `true` in `handleQuery()` but never cleared when Gemini was blocked (cooldown, duplicate, quota message already suppressed). Face locked in thinking mode permanently. Camera dropped all frames (`isThinking || isSpeaking` gate at analyzer). Mic never restarted. Fixed by making `finishThinking()` actually call `isThinking = false` + `faceView.setThinking(false)`. DONE June 27.
✓ **Testing moved to Fold 7** — Listed in docs as primary device switch. Patrick is currently actively testing on A32 (confirmed June 28).

---

## June 21, 2026 — What Changed Since Version 33

✓ **A32 no longer crashing — CONFIRMED** — Scout ran through Gemini responses, face recognition, and extended idle without crashing. Patrick confirmed: "he is not crashing anymore." DONE June 21.
✓ **Camera frame throttle** — `ANALYSIS_MIN_INTERVAL_MS = 150ms` added to camera analyzer. ML Kit labeler and face detector now run at max ~7fps instead of up to 30fps. Reduces bitmap allocation and ML Kit memory pressure by ~4x. Root cause of the delayed LMKD kill after Gemini responses. DONE June 21.
✓ **Face name persistence fixed** — `lastKnownFaceName` volatile field added. VisionAnswerBuilder now uses this embedding-based name cache instead of the per-frame fingerprint hash (which changed every frame). Name refreshed every 2 seconds by embedExecutor, cleared when no face visible. Scout now says your name consistently, not just once. DONE June 21.
✓ **`findBestMatch` only scans named rows** — Changed SQL from `WHERE embedding IS NOT NULL` to `WHERE embedding IS NOT NULL AND name IS NOT NULL AND name != ''`. Unnamed hash rows accumulated from prior frames can no longer win the cosine similarity race. DONE June 21.
✓ **embedExecutor self-match bug fixed** — `findBestMatch` is now called BEFORE `storeEmbedding`. Previously the embedding was stored first; `findBestMatch` then found the just-stored embedding with similarity 1.0, always returning the current frame's unnamed hash. Reordering eliminated the self-match entirely. DONE June 21.
✓ **Face recognition threshold raised** — Raised from 0.65 to 0.75. Prevents family members with shared facial geometry (Patrick/Elijah) from being misidentified. Same-person genuine matches score 0.80+. DONE June 21.
✓ **Multi-person face introduction** — `registerFamilyMemberFace()` added. Patrick can say "this is my son Elijah" or "this is my wife Diana" while the person is visible and Scout stores their face. DONE June 21.
✓ **Pending face mechanism** — When a family member is introduced while two people are in frame (Patrick is the primary face), Scout sets `pendingFaceIntroName` and speaks "I'll remember [name]. When [name] faces me alone, I'll learn to recognize them." The next unknown face automatically gets the pending name. DONE June 21.
✓ **VisionAnswerBuilder two-person response** — `faceCount == 2` now says "I can see [Patrick] and one other person." instead of always "I see two people." DONE June 21.
✓ **Gemini maxOutputTokens raised** — Raised from 150 to 250 in GeminiClient. Prevents responses being cut off mid-sentence (e.g., "Snoopy is..." truncation). DONE June 21.
⚠ **Elijah/Diana face recognition** — Needs one solo introduction: family member faces Scout alone (or becomes the primary face in frame) after "this is my son Elijah" so the pending face embedding is captured. Works correctly once triggered.

---

## June 17–20, 2026 — What Changed Since Version 32

✓ **Face recognition Steps 2–4 COMPLETE** — FaceEmbedder.kt wired into camera pipeline. Face crops taken from ML Kit bounding boxes, embeddings computed per detected face (Step 2). PeopleDb schema updated with BLOB embedding column, cosine similarity matching replaces position-hash (Step 3). "This is X" / "My name is X" naming flow uses embedding-based identity; known face greets by name, unknown face triggers Guest Mode (Step 4). DONE June 17.
✓ **Embedding memory pressure fix** — Memory management and queue overflow prevention added to embedding pipeline. A32 freeze/force-close eliminated. DONE June 17.
✓ **ApiKeySetupActivity.kt wired** — Optional AI provider setup wizard now fully connected to secure storage. DONE June 17.
✓ **SettingsActivity BUILT — all 5 sections** — AI Provider (Gemini key entry, online/offline toggle), Voice & TTS (pitch/speed sliders), Behavior, Brain & Behavior, About Scout (version, licenses, contact). DONE June 18.
✓ **Hardcoded Gemini API key REMOVED** — Patrick's personal key removed from MainActivity.kt entirely. Now lives in encrypted SharedPreferences. DONE June 18.
✓ **Settings access redesigned** — Gear button removed. Swipe-right gesture opens Settings. First-boot hint shown on first launch. Voice command also opens Settings. DONE June 18.
✓ **Eye jitter FIXED** — Boot lock (3500ms gaze stabilization), speaking gate, dead zone, and min-delta guard added to ScoutFaceView iris pipeline. A32 iris is now stable. DONE June 18.
✓ **Scout eyebrows and mouth brightened** — Color updated to #9BBEFF (lighter blue, matches iris). DONE June 18.
✓ **TinyLlama startup disabled on A32** — Startup load caused LMKD to kill Scout under memory pressure. Disabled as emergency stabilization. RE-ENABLED June 28 with safe delayed load. DONE June 19.
✓ **Camera bitmap memory leak fixed** — Bitmap objects now properly recycled after all async ML Kit callbacks complete, not prematurely. DONE June 19.
✓ **ML Kit suppressed during Gemini calls** — isThinking flag gates camera analyzer during Gemini API calls. Reduces peak memory usage during AI processing. DONE June 19.
✓ **speak() race condition FIXED** — isSpeaking = true now set immediately at function entry (not 240–650ms later when TTS onStart fires). Closes the window where ML Kit could run unconstrained just as Scout was starting to speak, causing a memory spike and LMKD kill. DONE June 20.

---

## June 15–16, 2026 — What Changed Since Version 31

✓ **TinyLlama rambling fix** — `limitToSentences()` added to MainActivity.kt. Offline replies capped at 2 sentences before TTS. Eliminates garbled continuations like 'I see a cool in an ear.'
✓ **Self-echo guard** — `lastScoutUtteranceNormalized` field added. `onResults()` now checks if mic picked up Scout's own TTS voice and ignores it. Eliminates Scout answering himself.
✓ **MainActivity.kt blank line cleanup** — excessive blank lines removed file-wide (except the TinyLlama system prompt raw string which is intentionally preserved).
✓ **Face recognition Step 1** — MobileFaceNet.tflite (MIT licensed, 5,233,396 bytes) bundled in `app/src/main/assets/`. TensorFlow Lite dependency (`org.tensorflow:tensorflow-lite:2.14.0`) added to build.gradle.kts. `noCompress += "tflite"` added so the model loads correctly. `FaceEmbedder.kt` created: takes a cropped face Bitmap, runs 112x112 / normalize / inference / L2-normalize, returns 192-dim FloatArray.
✓ **Naming phrases expanded** — TeachExtractor.kt updated. "this is X", "I am X", "you see X" now recognized as FactKey.NAME teaching phrases alongside existing "my name is X". NON_NAME_WORDS stoplist guards against false positives.
✓ **Weather switched to NWS** — ScoutWeatherManager.kt fully rewritten to use api.weather.gov. 100% free for commercial use, no API key required. Two-step flow: /points to resolve gridpoint URL (cached), then /forecast for periods. All five query types preserved (current, tonight, tomorrow, specific day, week). U.S. locations only.
✓ **THIRD_PARTY_NOTICES.md created** — MIT attribution for MobileFaceNet.tflite in repo root. Start of Open Source Credits screen.

---

## 1. Who We Are

Patrick Lippy — developer, project owner, creator of Scout. Not a professional programmer. Stroke survivor, blind in right eye, type 1 diabetic, dyslexic. Explain things calmly and at screenshot level.

- Diana: Patrick's wife
- Elijah: Patrick's son, age 9. Scout's biggest fan. Has drawn pictures of Scout.
- Nicolas: The family dog. Elijah has drawn pictures of Scout. Nicolas is why Pet Mode exists.

**Names must NEVER be hardcoded in Scout's code. Always use variables.**

AI Collaborators: Patrick works with both Claude and ChatGPT as project partners. Cross-review between the two is welcome and encouraged. Grok was tried and discontinued.

---

## 2. What Scout Is

Scout is a calm family companion robot running on a Samsung Galaxy phone mounted in landscape mode as a permanent face display. Scout has animated eyes, speaks, listens, sees via camera, and remembers the family.

| Item | Detail |
|------|--------|
| Package | com.example.scoutface |
| Language | Kotlin + C++ NDK |
| Active test device | Samsung Galaxy A32 — current active development and testing as of June 28 |
| Listed primary device | Samsung Galaxy Fold 7 (12GB RAM) — needs dedicated stability testing session |
| Future hardware | KEYESTUDIO Mini Tank Kit V2 chassis via Bluetooth (opt-in) |
| Ship target | Google Play Store — 7-day free trial, then $9.99 one-time purchase. No subscriptions. Ever. |
| Website | https://patevan9.github.io/lippyrobotics.github.io |
| Company name | Lippy Robotics |
| Build method | Android Studio only: Build → Clean Project → Build → Assemble Project. gradlew fails (JAVA_HOME). |

---

## 3. Identity & Purpose

Scout should feel: Calm. Thoughtful. Emotionally subtle. Grounded. Occasionally curious. Sometimes unsure. Quietly alive. Honest. Predictable. Present.

Scout should NOT: Constantly praise the user. Act overly excited. Feel fake or scripted. Behave unpredictably. Constantly force conversation. Use permanent goodbye language.

**Core Philosophy: Stability > Features | Presence > Intelligence | Honest > Fake cheerful | Predictable > Flashy | Local-first > Cloud dependence**

---

## 4. Business Model

**Trial:** 7-day free trial on Google Play. Families get to know Scout, fall in love with him. The $9.99 feels like nothing because they already care about him.

**Purchase:** $9.99 one-time purchase. No automatic charges. No recurring fees. No subscriptions. Ever.

**Post-trial:** After 7 days, advanced features lock but Scout stays installed. Still shows his face. Still greets the family. Trial end message: 'Thank you for spending time with Scout. Scout is still growing and receiving updates. If you'd like to continue the journey, you can unlock the full version at any time.'

**Baseline Brain:** TinyLlama 1.1B Chat Q4_K_M (~669 MB) — default, offline, always included. Re-enabled June 28 with safe delayed load (90s, 800MB RAM guard, nCtx=512).

**Optional Gemini:** Users add their own free Gemini key in Settings. ON by default when a key is saved (fixed June 28 — was always OFF). Scout NEVER ships with a bundled key.

---

## 5. Support Scout (In-App, Optional)

| Tier | Amount | Label |
|------|--------|-------|
| Coffee | $3 | Buy Scout a Coffee |
| More | $5 | Support Scout More |
| Grow | $10 | Help Scout Grow |
| Founding | $25 | Founding Supporter |

Support Scout screen designed and ready. Message: 'You’re not just supporting an app — you’re supporting a companion.'

---

## 6. Architecture — Five-Layer Memory Stack

| Layer | Type | Storage | Status |
|-------|------|---------|--------|
| Working | Sensory | RAM | Done |
| Habit | Patterns | JSON 14-day | Done |
| Truth | Authority | SQLite | Done — FLEXIBLE |
| Relevance | Index | Local Vector | Not yet |
| Reflective | Wisdom | LLM read-only | Not yet |

- Sovereign Rules: SQLite Truth always overrules everything.
- Pet Mode: any animal detected → Scout greets softly by name (or "Well... hello there little one. I hope someone will tell me your name soon." if unnamed). Scout continues operating normally. Future: steer-around Bluetooth command when mobile.
- Privacy Gate: Gemini receives anonymized text only. (Planned — not yet implemented.)
- Guest Mode: unknown face → 'Hello, I am [name]. What is your name?' (Planned — not yet implemented.)
- Flexible Memory: Scout stores and recalls ANY fact.

---

## 7. Current Technical State

### Working:

✓ Animated face (ScoutFaceView) — mouth, iris drift, thinking expression
✓ Eye jitter FIXED — boot lock (3500ms), speaking gate, dead zone, min-delta guard. A32 stable. June 18.
✓ Eyebrows and mouth brightened to #9BBEFF. June 18.
✓ Speech recognition (Android STT) + Text to Speech (TTS)
✓ STT reliability improved — EXTRA_PREFER_OFFLINE, 10s silence window, ERROR_RECOGNIZER_BUSY 600ms delay. June 28.
✓ Camera — face detection (ML Kit), scene labeling — throttled to ~7fps June 21 (memory pressure fix)
✓ Launcher icon fixed — face 68% of canvas, all 5 mipmap densities. Eyes fully inside circular mask. June 29.
✓ Face recognition COMPLETE and RELIABLE — ArcFace upgrade July 3: InsightFace MobileFaceNet (512-dim, 4.8MB) replaces old 192-dim model. PeopleDb v4. Cosine threshold 0.60f (ArcFace scale: same-person ~0.5–0.95, different-person ~0.0–0.4). findBestMatch scans only named rows. embedExecutor runs findBestMatch BEFORE storeEmbedding (self-match fix). Known face recognized consistently. Unknown face → Guest Mode. Nicolas Protocol active.
✓ Secondary face recognition — second-largest face also embedded in same executor job. person_embeddings table (up to 12 per person, threshold 0.55f for secondary crops). lastSecondaryFaceName (@Volatile). VisionAnswerBuilder uses it. June 29 / Diana fix July 3.
✓ Diana (secondary face) fix — pendingFaceIntroName now checked in secondary face block. "This is my wife Diana" with two people in frame now correctly assigns Diana to the secondary face. July 3.
✓ "Scout, forget [name]" command — wipes people table + person_embeddings table for that name. June 29.
✓ Multi-person face introduction — "this is my son Elijah" / "this is my wife Diana" registers family member faces in PeopleDb. Pending face mechanism handles two-person-in-frame introductions. June 21 / fixed July 3.
✓ VisionAnswerBuilder two-person response — "I see Patrick and Elijah" when both faces known; "I see Patrick and someone else" when secondary unrecognized. "I see X" phrasing (not "I see you, X") as of July 3.
✓ Personality phrase pools — Phrases.kt (new July 3). Anti-repeat rolling window (cooldown = pool.size / 2). Varied boot, goodbye, and remember responses. pickNamed() substitutes {name} placeholder.
✓ Adaptive boot greeting — ScoutBootStatus.kt rewritten July 3. Offline boot: BOOT_OFFLINE_FAST (no warming-up line) when TinyLlama loaded < 2s last session; BOOT_OFFLINE otherwise. TinyLlama load time stored in SharedPreferences. Online boot: BOOT_ONLINE (all 6 phrases mention offline backup warming up).
✓ Face greeting fires once per launch — greetedThisSession reset removed. June 28.
✓ Wrong-name teaching with 2 people in frame fixed — handleTeaching() guard prevents "this is my wife Diana" being stored as primary user rename. June 27.
✓ ML Kit label whitelist — OBJECT_WHITELIST in VisionAnswerBuilder.kt. ~80 household objects. Garbage labels gone. June 27.
✓ lastKnownFaceName set immediately on teaching — Scout says your name right away, not 2 seconds later. June 27.
✓ finishThinking() fixed — was empty no-op. Now clears isThinking + faceView state. Fixes permanent stuck-thinking when Gemini blocked. June 27.
✓ Greeting words blocked from name storage — hello/hi/hey/howdy/greetings/sup/yo in blockedNames. June 29.
✓ TTS deafness bug fixed — speak() return check + speakingStartedMs + 45s watchdog. Scout cannot go permanently deaf after idle. June 29.
✓ Voice settings persist across Settings/MainActivity — scout_prefs used by both. onResume() reloads pitch/speed. June 29.
✓ Gemini API — ON by default when key is saved (default fixed June 28). Timeout 10s connect / 20s read. maxOutputTokens=600 (raised June 29), sentence-complete instruction. Activated by 'go online' voice command. Model: gemini-3.5-flash. Daily quota cooldown 1 hour.
✓ Gemini quota/cooldown announced — speakUnavailableIfNeeded() returns Boolean; cooldown check at top of tryTinyLlamaOrFallback(). Repeat gaps: 6h daily quota, 10min rate limit. June 29.
✓ Gemini responses complete — maxOutputTokens=600, MAX_TOKENS trim to sentence boundary. June 29.
✓ TinyLlama 1.1B offline brain — RE-ENABLED June 28 with delayed load (90s), 800MB RAM guard, nCtx=512, nThreads=2. Automatic Gemini fallback via onFailed callback. On-demand load fires when Gemini fails and TinyLlama not yet loaded. CONFIRMED WORKING on A32 and Fold 7, July 7. bootstrapModelFile() auto-copies model from external storage on startup so reinstalls recover automatically.
✓ "Repeat that" intent — isRepeatRequest() + lastMeaningfulResponse cache (4-min TTL). Replays last 5-word+ answer instantly from any brain. June 28.
✓ Brain source Toast — "Gemini (online)" / "TinyLlama (offline)" shown after each answer for testing. June 28.
✓ Settings screen — SettingsActivity with 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Swipe-right gesture + voice command + first-boot hint. June 18.
✓ Hardcoded Gemini API key removed — now in encrypted SharedPreferences. June 18.
✓ Memory layers: TruthDb, ConversationDb, HabitLayer, PeopleDb (with BLOB embeddings), JournalDb
✓ Intent router — time, date, greetings, family facts, downloads, vision, weather, IDENTITY, RECALL_FACT
✓ Flexible teaching — 'my favorite color is teal' → stored permanently
✓ Flexible recall — recalls facts reliably after other questions
✓ Wake word filter — Scout only responds when he hears his name
✓ Conversation window — 30 seconds of open conversation after Scout responds
✓ Boot window — Scout awake and ready immediately after boot
✓ Online / disconnect phrases recognized
✓ Greeting routing — casual greetings route instantly to HOW_ARE_YOU (no TinyLlama wait)
✓ Vision response cleanup — noisy ML Kit labels filtered from spoken responses
✓ Person detection — VisionAnswerBuilder wired to PeopleDb. Scout reports person count cleanly
✓ Weather — current, tonight, tomorrow, 7-day, precipitation % via NWS (api.weather.gov) — free for commercial use
✓ ScoutPresenceDecider — four time-of-day modes
✓ Identity questions hardcoded — routing expanded
✓ Total offline mode — 'go offline' blocks ALL internet features
✓ Thinking-state expression — curious/engaged expression: one brow clearly raised (22px + sine) with questioning arch, other barely moves (5px); right lid subtly more relaxed (+0.08f droop); mouth right corner 3px higher (thoughtful side-smile). Iris glances 35–65px to side + upward (-20px). Head-turn faceGazeDrift 0.32f drives 12–21px visible drift. Redesigned July 7 from Patrick's direction with reference images.
✓ TinyLlama rambling fix — offline replies capped at 2 sentences (limitToSentences)
✓ Self-echo guard — Scout ignores his own TTS voice bleeding back into mic
✓ MainActivity.kt blank line cleanup — complete
✓ Naming phrases expanded — "this is X", "I am X", "you see X" recognized as name-teaching phrases
✓ Three A32 stability fixes — camera bitmap recycle, ML Kit suppression during Gemini, speak() race condition closed. June 19–20.
✓ A32 crash resolved — camera frame throttle (150ms) eliminates delayed LMKD kill after Gemini responses. Confirmed stable June 21.
✓ A32 startup-collision crash resolved — staggered camera (3s) and speech (4.5s) startup avoids colliding with GMS's one-time ML Kit ART verification pass. Root-caused via full logcat capture, not guessed. July 28.
✓ Personal-memory questions structurally gated before Gemini — ScoutMemoryGate.isPossiblePersonalMemoryQuery(), not phrase-list-dependent. July 26.
✓ TinyLlama SIGABRT on long prompts fixed — chunked prefill (kNBatch-sized chunks) instead of one oversized batch. July 26.
✓ Teaching via entity+property extraction — ScoutFactExtractor.kt + ScoutEntityResolver.kt, order-independent, real multi-alias support (TruthDb.addAlias()/getAliases()). July 26.
✓ Presence Layer moments 1 & 2 — idle-silence acknowledgment and a real proactive return greeting, both gated by genuine sustained camera presence, not the always-true isListening flag. July 27–28.
✓ Listening reminder vision-gated — only fires when a face is actually sustained-facing Scout (yaw/size/center + 1.5s sustain), not "any face existed recently." July 28.
✓ API keys encrypted at rest — ScoutSecureKeyStore, Android Keystore-backed AES-256-GCM, versioned format, one-time plaintext migration. July 29.
✓ ScoutLlamaController — process-wide singleton owns TinyLlama's generation executor and owner/generation token, surviving Activity recreation without leaking threads or delivering stale results to a destroyed Activity. July 29.

### Pending — Launch Blockers:

✓ **Startup diagnostics** — DONE July 4. TTS failure Toast + STT unavailability spoken warning at boot.
✓ **Onboarding flow** — DONE July 4. OnboardingActivity.kt, 5 screens, first-boot redirect in MainActivity.
■ **Fold 7 dedicated stability testing** — testing has been on A32. Fold 7 needs its own validation session.
✓ **16KB page size — RESOLVED July 19** — Confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK: all 11 previously-flagged libraries pass individually, "Verification successful" overall. The July 18 dialog only ever fired on debuggable installs, not a real defect. See the July 19 section at the top of this document. No longer a launch blocker.
■ **Play Asset Delivery (PAD) wiring** — ModelDownloadActivity is built and ready. Wiring PAD to trigger the download screen and call updateProgress() is a future session.

✓ **Privacy Policy** — DONE July 11. In-app scrollable dialog (Settings → About Scout).
✓ **Terms of Use** — DONE July 10–11. In-app dialog + terms.html for website.
- Open Source Credits — THIRD_PARTY_NOTICES.md started (MobileFaceNet MIT done). Full screen still needed at launch.
- Play Store listing — description, screenshots, content rating, privacy policy link.
- Proposal Sandbox — 'Want me to remember that?' confirm step.
- Permanent vs temporary memory sorting.
- Caring follow-up loop.
- ScoutFaceView cleanup — 2 dead-code lines.
- Response cleanup layer — post-TinyLlama filter.
- Scout news feed — FUTURE feature.
- Wire in full mood system.
- Offline Brain Delivery (Phase 3) — TinyLlama inference COMPLETE on-device. Still undecided/unbuilt: HOW the ~669MB model reaches a real user's phone (bundled vs. first-launch download).

---

## 7b. b8946 API Discoveries — CRITICAL

**llama_vocab is a separate type:** llama_tokenize and llama_token_to_piece now require `const llama_vocab*`. Get via `llama_model_get_vocab(model)`.

**Functions that crash — hardcode instead:** llama_n_vocab → 32000. llama_token_eos → 2. llama_token_eot → 2.

**KV Cache:** llama_kv_cache_clear() does not exist. Free and recreate context each call.

**Logits:** After prefill, use n_prompt-1. In generation loop, use index 0.

**Backend:** ggml_backend_load_all() returns 0 on Android. Use ggml_backend_load(cpuPath) with explicit .so path.

**Struct padding:** _pad[508] trailing array on params structs. Do NOT remove.

| Tuning (A32) | Value |
|--------------|-------|
| n_threads | 2 (memory-bound) |
| nPredict | 64 (~38 tokens, ~7.6s) |
| History | 2 turns |
| Speed | ~15 tok/s prefill, ~4 tok/s generation |
| Reality | 20–40s per answer — acceptable, Gemini is fast path |

---

## 7b2. Pending Expert Feedback — Mike Forst (Amazon Astro)

Mike Forst — Amazon Astro character director and sound lead (mikeforst.com). Contacted June 30, 2026. Responded positively. Feedback pending — arriving via email or video call.

Mike is an expert in how robots and AI companions feel trustworthy and present through behavioral design and non-verbal cues.

**When feedback arrives, map his insights to:**
- `ScoutFaceView.kt` — animation timing and behavioral micro-expressions
- `ScoutPresenceDecider.kt` — social timing, when Scout speaks vs. stays quiet
- Scout's identity and response philosophy (section 3 of this summary)

Do not act on this area without his input. His expertise is the right lens for these decisions.

---

## 7c. Known Issues — Do Not Touch Without Discussion

| Issue | Notes |
|-------|-------|
| TinyLlama A32 real-world confirmation needed | Re-enabled June 28 with delayed load + RAM guard. Not yet confirmed that LMKD crash does not return under memory pressure. |
| A32 crashes | **RESOLVED June 21** — camera frame throttle (150ms) eliminated the delayed LMKD kill. Patrick confirmed stable. |
| A32 crash — camera/ML Kit/SpeechRecognizer startup collision | **RESOLVED July 28** — root-caused via full on-device logcat capture to a collision with GMS's one-time ML Kit ART verification pass, not a Scout or benchmark-harness bug. Fixed via staggered camera (3s)/speech (4.5s) startup. A different crash class from the June 21 entry above. |
| TinyLlama SIGABRT on prompts near/over 512 tokens | **RESOLVED July 26** — single-batch `llama_decode()` overflow when prefill exceeded `n_batch=512`. Fixed via chunked prefill; a related logit-indexing bug fixed in the same pass. |
| Secondary face bootstrap | First time two people are in frame after a fresh pull, Elijah may show as "someone else" — person_embeddings table starts empty. Once Elijah faces Scout alone once, his embedding populates the table and two-person recognition works. |
| A32 active test device | Patrick confirmed June 28: testing is on A32. Fold 7 listed as primary but needs a dedicated session. |
| TinyLlama slow on A32 | 20-40s per answer. Expected. Hardware limitation. Gemini is fast path when online. |
| Barge-in | Deliberately disabled. Runaway loop. Status: PARKED. |
| STT name recognition | 'Scout' misheard as 'Gal', 'Scott', 'Out'. Partially handled by wake word filter. |
| Live news | Neither brain reads live news. Future news feed needed. |
| ScoutFaceView dead code | Line 1023: doubled condition. Line 709: unused browAsym. Harmless but messy. |
| 16KB page size | ✓ RESOLVED July 19. Confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK — all 11 previously-flagged libraries pass individually, "Verification successful" overall. The July 18 dialog only ever fired on debuggable installs, not a real defect. See July 19 section at top. Play Store submission unblocked. |

---

## 7d. Session Log

- July 29: Two rounds of ChatGPT-reviewed fixes (7 privacy/reliability, 7 mic/camera performance) — offline-brain gate bypass, LlamaEngine.free() race, misleading OpenAI/Claude setup, plaintext API keys + untouched backup templates, ScoutMemoryGate alias mismatch, TruthDb upsert staleness, onEndOfSpeech() restart risk, wake-word "out" false positive, fixed silence timeout, per-frame bitmap allocation, label/face cadence coupling, cameraEverStarted timing. Then: API keys encrypted via Android Keystore (ScoutSecureKeyStore, versioned format, typed encrypt/decrypt results, one-time plaintext migration via commit()); ScoutLlamaController introduced as a process-wide singleton owning TinyLlama's generation executor and owner/generation token, replacing per-Activity-instance state that could leak threads or deliver stale results across a configuration-change recreation; two follow-up corrections after a second review pass (invalidateOwner() on every onDestroy(), discard logging moved off an Activity-owned callback). Commits a348425, 0b3e9bc, 7d030e3, f856bb2, 2ac932e.
- July 28: Listening reminder made vision-led (ML Kit head-yaw gate, sustained-facing streak, reason-based diagnostics), then tightened to conservative thresholds with a vision-staleness check and real measured values logged. Dev-only TinyLlama benchmark harness added (native runGeneration() extraction, perf_context bindings, hidden 7-tap unlock screen), then fixed for thermal run-order bias (Latin-square rotation) and an XML manifest comment bug that had silently broken the previous commit's build. A32 crash fully root-caused via full logcat capture — a camera/ML Kit/SpeechRecognizer startup collision with GMS's one-time ART verification pass, not a Scout or benchmark bug — fixed via staggered camera/speech startup, a startup-settled gate for face embedding, and full startup timing diagnostics. Real proactive return greeting (Presence Layer moment 2) landed at production thresholds after a temporary A32 smoke-test build.
- July 27: "Who is Diana?" now answered by direct TruthDb lookup instead of unreliable TinyLlama inference. Presence Layer moment 1 shipped — idle-silence acknowledgment after long uninterrupted presence with no conversation, plus the return-greeting design that replaced Scout's previously-broken "welcome back" mechanism (shipped as a temporary smoke-test build first).
- July 26: Personal-memory questions structurally gated before Gemini via new ScoutMemoryGate.isPossiblePersonalMemoryQuery(), not just phrase matching. TinyLlama SIGABRT fixed — chunked prefill instead of one oversized batch, plus a related logit-indexing bug. Teaching moved from sentence-template regexes toward entity+property extraction (ScoutFactExtractor.kt, ScoutEntityResolver.kt) with real multi-alias support in TruthDb.
- July 25: Loading-phase visual redesign (solid black background, no fake progress bar/percentage, "Waking Scout up…" replacing "brain" language) plus "Downloading…" label persisting through real byte-progress text. Boot-announcement staleness fixed twice: first the string was captured before the brain was ready and spoken stale later (fixed via boolean flag + fresh bootStatus.build() at actual speak time); then found the fast/slow "warming up" pool selection itself was always wrong since its only timing signal was written 90s after the announcement already fired, and — more fundamentally — the startup gate means the brain is always already loaded by the time this runs, so the fast/slow distinction was removed entirely in favor of always using the ready phrasing. Calendar routing fixed: "when is my next X" was being swallowed by the personal-facts regex exclusion meant only for bare "my" (now scoped to not exclude "my next"), and keyword extraction now strips "my"/"next" filler before matching event titles. New CalendarDateParser.kt adds specific-date lookups ("am I free on July 10th," weekday names, bare "the 10th"). ApiKeySetupActivity's provider picker (Gemini/OpenAI/Claude) wrapped in a ScrollView — Claude was silently cut off below the fold on the locked landscape orientation, same root cause as the earlier download-screen overflow. Mic-hears-itself bug fixed: a queued scheduleListenRestart() Handler callback could survive onPause() and restart the recognizer while SettingsActivity was foregrounded, picking up the voice-tone preview's "My name is {name}" line through the speaker — which is exactly TeachExtractor's user-name-teaching trigger phrase, so renaming Scout and then testing voice tone taught Scout's new name as the user's own name. Fixed with an explicit isForeground guard plus a reworded, collision-safe preview line. New FAMILY_NAMES intent ("what are the names in my family") answers from TruthDb instead of falling through to TinyLlama/Gemini, which have no fact access and were hallucinating names. "Turn on calendar" and "go online" (once connectivity is confirmed) now deep-link into specific Settings screens via a new SettingsActivity.EXTRA_TARGET_SCREEN, addressing feedback that Settings' sections are hard to find by hand. Confirmed (not yet fixed, holding per Patrick): SettingsActivity's robot-rename feature only writes to a `robot_name` pref that nothing else reads — every actual spoken self-identification reads a separate, disconnected TruthDb fact — so renaming Scout currently does nothing outside Settings itself.
- July 19: 16KB alignment CONFIRMED PASS — built a signed release APK (not debug), ran `zipalign -c -P 16 -v 4` against it directly. All 11 previously-flagged native libraries pass individually (OK), overall "Verification successful." Installing the release APK on the Fold 7 no longer triggers the compatibility dialog at all, confirming the dialog was debug-build-specific as hypothesized. Play Store submission unblocked on the 16KB front. Separately diagnosed and documented (not yet fixed): `ModelDownloadActivity`'s `MODEL_DOWNLOAD_URL` is an unfilled placeholder, so its download flow can never complete, and it deletes any locally-staged model file before attempting to download — real TinyLlama delivery for a release build currently requires manually pushing the `.gguf` file into the app's external files directory via `adb push` after every fresh install. Summary updated to version 50.
- June 5: IDENTITY intent + hardcoded responses. Weather offline fix. Total offline mode.
- June 7: TinyLlama A32 crash stabilized. Identity routing expanded. Face direction locked.
- June 8: Thinking expression built. Flexible Memory Planning Document created.
- June 9: Flexible memory foundation built. RECALL_FACT intent added. First autonomous memory recall.
- June 10: Business model updated — 7-day free trial. Wake word and face recognition identified as launch blockers. Launch Checklist created.
- June 12: Wake word filter built. Conversation window (30s) and boot window added. Memory recall bug fixed. Gemini online/offline confirmed. 5-screen onboarding flow approved. Versioning system defined. Legal requirements defined. Website confirmed. PeopleDb.kt updated with getName/setName/isKnown. Weather API licensing question raised with Open-Meteo.
- June 14: Greeting routing fixed. Vision response cleanup. VisionAnswerBuilder wired to PeopleDb. Face-tagging hook added to handleTeaching() but face hash instability found. Rambling/garbled continuations discovered.
- June 15: Rambling fix — limitToSentences() added, offline replies capped at 2 sentences. MainActivity.kt blank line cleanup. Self-echo guard added (lastScoutUtteranceNormalized, onResults() check). Face recognition Step 1: MobileFaceNet.tflite bundled (MIT, ~5MB), TensorFlow Lite dep added, FaceEmbedder.kt created (not yet wired). Naming phrases expanded in TeachExtractor.kt ("this is X", "I am X", "you see X").
- June 16: Weather switched from Open-Meteo to NWS (api.weather.gov) — free for commercial use, no API key, U.S. only. ScoutWeatherManager.kt fully rewritten. THIRD_PARTY_NOTICES.md created. Quick Start, Launch Checklist, and Master Summary updated to v11/v5/v33.
- June 17: Face recognition Steps 2–4 COMPLETE. FaceEmbedder wired into camera pipeline. PeopleDb updated with BLOB embedding column and cosine similarity matching. Naming flow uses embedding identity. Embedding memory pressure and queue overflow fixed. ApiKeySetupActivity.kt wired.
- June 18: SettingsActivity built — all 5 sections. Hardcoded Gemini API key removed from MainActivity.kt. Prism stub removed from Brain & Behavior settings. Eye jitter fixed — boot lock 3500ms, speaking gate, dead zone, min-delta guard. Eyebrows and mouth brightened to #9BBEFF. Gear button replaced with swipe-right gesture + first-boot hint + voice command.
- June 19: TinyLlama startup load disabled on A32 — LMKD crash prevention. Camera bitmap memory leak fixed (recycle after all async ML Kit callbacks complete).
- June 20: ML Kit suppressed during Gemini calls via isThinking gate. speak() race condition fixed — isSpeaking set immediately at function entry, closing 240–650ms gap that allowed ML Kit to spike memory just before Scout spoke. Mouth animation timing fixed — faceView.setSpeaking(true) moved back to TTS onStart callback only.
- June 21: Camera frame throttle added (150ms interval, ~7fps ML Kit). A32 crash eliminated — confirmed stable by Patrick. Face name persistence fixed (lastKnownFaceName, findBestMatch before storeEmbedding, named-rows-only SQL). Face recognition threshold raised 0.65→0.75 (Patrick/Elijah false match fixed). Multi-person introduction added (SON_NAME/WIFE_NAME register face, pendingFaceIntroName mechanism). VisionAnswerBuilder two-person response improved. Gemini maxOutputTokens raised 150→250.
- June 27: Wrong-name teaching bug fixed (2-person frame guard in handleTeaching). ML Kit label blacklist replaced with OBJECT_WHITELIST in VisionAnswerBuilder (~80 household objects). lastKnownFaceName now set immediately after name teaching (not 2s later). finishThinking() fixed — was empty no-op causing permanent stuck-thinking when Gemini blocked. Testing listed as moved to Fold 7.
- June 28: TinyLlama re-enabled — 90s delayed load, 800MB RAM guard, nCtx=512, nThreads=2. tryLoadOfflineBrain() helper added (startup + on-demand path). Gemini timeouts reduced (10s connect / 20s read). onFailed/onAnswered callbacks added to tryGemini(). tryTinyLlamaOrFallback() extracted — TinyLlama now automatic Gemini fallback. "Repeat that" intent added (isRepeatRequest(), lastMeaningfulResponse cache, 4-min TTL). Brain source Toast added ("Gemini (online)" / "TinyLlama (offline)"). Gemini default fixed (isGeminiEnabled() was always false). Daily quota cooldown reduced 6h→1h. Face greeting reset removed — greets once per boot only. STT improved: EXTRA_PREFER_OFFLINE, 10s silence window, ERROR_RECOGNIZER_BUSY 600ms delay. Duplicate prompt now serves cached Gemini reply. speakUnavailableIfNeeded() made public. Testing confirmed on A32.
- June 29: Launcher icon fixed — face 68% of canvas, all 5 mipmap densities regenerated, eyes inside circular mask. Face threshold raised 0.75→0.82 (Patrick/Elijah genetic similarity fix). "Scout, forget [name]" voice command added (clears people + person_embeddings). TTS deafness bug fixed — speak() return check + speakingStartedMs + 45s watchdog. Voice slider now sticks — scout_prefs in both SettingsActivity and MainActivity.onResume(). Greeting words blocked from name storage (hello/hi/hey/howdy/greetings/sup/yo). Gemini maxOutputTokens raised 250→600, "Always end on a complete sentence" added to system prompt, MAX_TOKENS boundary trim. Gemini quota announced — speakUnavailableIfNeeded() returns Boolean, cooldown check at top of tryTinyLlamaOrFallback(). Secondary face recognition — PeopleDb v3 with person_embeddings table, addNamedEmbedding(), findBestMatchName(); secondFace embedded in same executor job, lastSecondaryFaceName (@Volatile); VisionAnswerBuilder uses secondaryFaceName.
- June 30: Dynamic robot name — boot greeting, identity feelings reply, identity fallback, offline brain fallback all read from TruthDb. No hardcoded "Scout" in any spoken response. TeachExtractor.kt: 8 new patterns for "that is my son/wife", "that person is my son/wife", "that is [name]", "that person is [name]", "his name is", "her name is". VisionAnswerBuilder freshness 1800ms→3500ms (camera blocked during TTS). registerFamilyMemberFace() guard prevents overwriting a known face hash with a wrong name. TinyLlama filter: "family friendly companion" + "family companion robot" added. Pet Mode design locked: any animal → soft greeting using stored name or "Well... hello there little one. I hope someone will tell me your name soon." Scout continues normally after greeting. Nicolas Protocol renamed Pet Mode (covers all animals). Settings Architecture and Visual Elements specs restored to summary. Summary updated to version 38.
- July 3: ArcFace upgrade — InsightFace MobileFaceNet (512-dim, 4.8MB) replaces 192-dim model. FaceEmbedder.kt: EMBEDDING_SIZE 192→512, single-batch output. PeopleDb v4: migration clears 192-dim embeddings, preserves names/hashes, threshold 0.60f. "I see X" phrasing replaces "I can see you, X" throughout VisionAnswerBuilder and MainActivity. Diana fix — secondary face block now consumes pendingFaceIntroName. Phrases.kt new file: anti-repeat phrase pools for boot, goodbye, and all remember responses. ScoutBootStatus.kt rewritten: uses Phrases pools, adaptive BOOT_OFFLINE_FAST (< 2s load) vs BOOT_OFFLINE. BOOT_ONLINE phrases all mention offline backup warming up. TinyLlama load time measured and stored in SharedPreferences. Goodbye and remember responses now varied via Phrases pools. Summary updated to version 39.
- July 7 S1: 16KB page alignment fix confirmed working on A32 and Fold 7 (scout_llama.so, CMakeLists.txt). bootstrapModelFile() added — auto-copies TinyLlama model from external storage to filesDir on startup (no permission needed via app-specific external dir; READ_EXTERNAL_STORAGE with maxSdkVersion="32" for root /sdcard/ on Android ≤12). Offline fallback message fixed — "I'm working offline" when Gemini disabled (not "having trouble connecting"). TinyLlama confirmed working on both A32 and Fold 7. Head-turn faceGazeDrift multipliers 0.07/0.06 → 0.32/0.26 (was ±5px virtual = invisible; now ±24px X / ±14px Y, clearly readable). Summary updated to version 42.
- July 10–11: terms.html created (commit b5735f5) — website Terms of Use with acceptance and changes-to-terms clauses for Play Store compliance. ML Kit bumped: face-detection 16.1.6→16.1.7, image-labeling 17.0.7→17.0.9 (both claimed 16KB aligned on arm64, commit 60443f3 — REOPENED July 18, see top of document). LiteRT migration attempted (litert:1.4.0) — failed, version not in Maven, reverted to tensorflow-lite:2.17.0 (commit eb8223e). Privacy Policy and Terms of Use added as in-app scrollable dialogs in SettingsActivity.kt (commit a330b93). openUrl() removed — both dialogs work fully offline. July 16 investigation: TFLite 2.17.0 shows strong on-device evidence of non-compliance (Fold 7 debug popup on Android 15, Google issue tracker) — binary not yet verified with readelf. Correct migration target is litert:2.1.5 (2.1.x alignment confirmed per GitHub issue #6299).
- July 16: LiteRT migration code done — tensorflow-lite:2.17.0 → litert:2.1.5 in build.gradle.kts, FaceEmbedder.kt import updated (commits 9676192). Readelf verification pending (Patrick's task after next Android Studio build). Face recognition 3-bug fix (commit b6c5579): (1) margin check added to findBestMatchName/findBestMatchNameWithScore (minMargin=0.08f — Scout says nothing when top two candidates are within 0.08f); (2) CONFIDENT_EMBED_THRESHOLD=0.72f in MainActivity gates addNamedEmbedding calls on both primary and secondary face paths; (3) addNamedEmbedding at-cap behavior changed from hard-stop to rolling window (replaces most-redundant via maxByOrNull cosine similarity). scoreByPerson() private helper extracted. forgetPerson path clears lastFaceEmbedding. Summary updated to version 46.
- July 17: LiteRT import fix — FaceEmbedder.kt import reverted to org.tensorflow.lite.Interpreter (com.google.ai.edge.litert.Interpreter does not exist at runtime; commit 83ed37f). 16KB readelf run — Patrick ran llvm-readelf.exe -l libLiteRt.so on Windows (NDK 28.2.13676358); all LOAD segments Align 0x4000; libLiteRt.so and libLiteRtClGlAccelerator.so both PASS in that isolated check — but REOPENED July 18: the same libLiteRt.so fails Android's own on-device compatibility check once actually bundled in the built app (see top of document). TeachExtractor double-prefix bug fixed (startsWith("favorite") guard at line 180; commit 9b353a8); keyToHuman() collapses old favorite_favorite_ keys for display. Battery optimization prompt added — checkBatteryOptimization() fires 8s after first boot, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, one-time prefs guard, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in AndroidManifest (commit 1abcee1). Thinking watchdog added — thinkingStartedMs + 120s MAX_THINKING_DURATION_MS in runRecognizerWatchdog() (commit 1abcee1). DB migration migrateDoublePrefixFacts() deletes favorite_favorite_% keys on next launch including TTS self-echo entry (commit e24fad9). TruthDb gains deleteFact() and deleteFactsWithKeyLike() (commit e24fad9). ScoutExportManager updated to accept peopleDb: PeopleDb; adds people (named faces) and face_embeddings (counts) sections to brain export (commit aa10bc9). Brain export JSON confirmed: "Very" not in truth DB — must be in people table. TTS self-echo vulnerability documented (self-echo guard missed "yes, my favorite color is cyan" due to prefix mismatch; TeachExtractor stored it as favorite_favorite_yes_my_favorite_color; cleaned by migration; root fix deferred). Summary updated to version 47.
- July 7 S2: Thinking expression completely redesigned based on Patrick's direction and reference images. Goal: curious/engaged ("Hmm, let me think") not sleepy/tired. thinkGlanceSideX 8–20px → 35–65px (drives visible face drift). Brow: one brow raises 22px + sine with questioning arch; other barely moves (5px); thinkInnerLift reduced 20px → 6px on quiet brow only (was making it look worried). Lid: right eye +0.08f droop during thinking (subtle asymmetry — concentration not sleep). Mouth: corYR 3px higher than corYL (tiny thoughtful side-smile). Summary updated to version 43.
- July 4: PeopleDb threshold raised back to 0.65f (0.60f caused Diana/Elijah cross-contamination at ArcFace scale). cursor.use{} in findBestMatch + findBestMatchName (leak fix). forgetPerson made atomic with transactions. addNamedEmbedding COUNT(*) guard. VisionAnswerBuilder: freshness 3500ms→1800ms, 3+ faces branch gets dogLine, 2-face branch secondaryFaceName arm precedes pendingIntroName arm, new else arm for unknown primary + known secondary. Secondary face path adds findBestMatch fallback after findBestMatchName. Caption persistence fix — onResume() hides caption immediately when captions disabled. Startup diagnostics: TTS failure Toast + STT unavailability spoken warning at boot + JournalDb log. First-boot onboarding redirect at top of MainActivity.onCreate(). OnboardingActivity.kt built — full 5-screen flow, currentPage single source of truth for dots + counter, finishOnboarding() sets offline default. BOOT_NO_KEY phrases replaced with settings slide-right tip. CLAUDE.md created with git commands and critical rules for all future Claude sessions. ModelDownloadActivity.kt built — 39 messages, ObjectAnimator animation, updateProgress() API, portrait-only, AndroidManifest registered. Summary updated to version 40.

---

## 8. Working Rules — Always Apply

- Full paste-ready replacements only, one file at a time. No snippets. No partial files.
- Surgical CTRL-F and CTRL-R approved for large files — always specify which tab first.
- Build: Android Studio only — Build → Clean Project → Build → Assemble Project. gradlew fails on Patrick's machine (JAVA_HOME error).
- Some Scout files have NO indentation. If a search fails, try a shorter unique single-line string.
- Some logic lives in TWO places (e.g. updateLife AND scheduleNextFrame) — change both or Scout flickers.
- One safe change at a time. Build and test before the next change.
- Never touch speech, camera, or download systems without explicit discussion.
- Never touch ScoutFaceView casually — it is Scout's visual heart.
- Both Claude and ChatGPT are active collaborators — cross-review welcome.
- Patrick is not a professional programmer — explain at screenshot level always.
- Patrick is dyslexic, stroke survivor, blind in right eye, T1 diabetic — keep messages clear and concise.

---

## 9. Key Files

| File | Description |
|------|-------------|
| MainActivity.kt | Main app — all logic. Hardcoded API key REMOVED June 18. Wake word filter in onResults(). Self-echo guard (lastScoutUtteranceNormalized). limitToSentences() for rambling fix. handleTeaching() wires name to PeopleDb. isSpeaking set immediately in speak() — race condition fix June 20. tryLoadOfflineBrain() added June 28 (delayed + on-demand TinyLlama load). isRepeatRequest() + lastMeaningfulResponse cache June 28. tryTinyLlamaOrFallback() extracted June 28. pendingBrainSource + brain Toast June 28. greetedThisSession reset removed June 28. STT EXTRA_PREFER_OFFLINE + silence/busy fixes June 28. TTS deafness fix June 29 (speak() return check, speakingStartedMs, 45s watchdog). scoutPrefs reads from scout_prefs June 29 (voice pitch/speed in onInit + onResume). blockedNames includes greeting words June 29. lastSecondaryFaceName + secondFace + capturedSecondBox + secondary embed block June 29. isInCooldown() check + speakUnavailableIfNeeded() call at top of tryTinyLlamaOrFallback() June 29. July 17: checkBatteryOptimization() fires 8s after first boot (commit 1abcee1). thinkingStartedMs + MAX_THINKING_DURATION_MS=120_000L + thinking watchdog in runRecognizerWatchdog() (commit 1abcee1). migrateDoublePrefixFacts() in setupMemory() deletes favorite_favorite_% keys on first run (commit e24fad9). keyToHuman() collapses old double-prefix keys for display (commit 9b353a8). |
| ScoutFaceView.kt | Custom face canvas — all visual animation. Thinking expression updated June 8. Eye jitter fixed June 18 (boot lock, speaking gate, dead zone, min-delta). Eyebrows/mouth #9BBEFF June 18. |
| SettingsActivity.kt | NEW June 18 — 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Gemini key entry, offline toggle, pitch/speed sliders. Opened via swipe-right + voice command + first-boot hint. |
| ScoutIntentRouter.kt | Intent routing — IDENTITY + RECALL_FACT added. Online/disconnect phrases. |
| TeachExtractor.kt | Extracts facts from speech — FLEXIBLE. Updated June 15 with "this is X", "I am X", "you see X" name patterns + NON_NAME_WORDS stoplist. July 17: `startsWith("favorite")` guard prevents double-prefix on "my favorite X is Y" patterns — commit 9b353a8. |
| FactKey.kt | Fact labels — fixed keys kept + FactKey.custom() for any new label. |
| TruthDb.kt | SQLite fact store — fully flexible. July 17: `deleteFact(entity, factKey)` and `deleteFactsWithKeyLike(entity, pattern)` added for targeted fact removal (commit e24fad9). |
| ApiKeySetupActivity.kt | API key wizard — wired to secure storage June 17. |
| GeminiClient.kt | Gemini HTTP wrapper with cooldown discipline. connectTimeout=10s, readTimeout=20s. maxOutputTokens=600 (raised June 29). Daily quota cooldown 1 hour. Single-flight guard. isDailyQuotaExhausted() + isInCooldown() methods. MAX_TOKENS finishReason trim to sentence boundary June 29. |
| ScoutPromptBuilder.kt | Builds Gemini system instruction and unavailable messages. "Always end on a complete sentence" in system prompt June 29. buildOnlineUnavailableMessage() returns daily quota / rate limit / generic variants. |
| brain/ScoutGeminiManager.kt | Gemini orchestration. onAnswered/onFailed callbacks added June 28. lastGeminiReply cache (4-min TTL) — serves duplicate prompts June 28. speakUnavailableIfNeeded() returns Boolean June 29 (true=spoken, false=suppressed). isInCooldown() exposed June 29. Repeat gaps: 6h daily quota, 10min rate limit. |
| ScoutWeatherManager.kt | Live weather via NWS (api.weather.gov) — UPDATED June 16. Free for commercial use. Precip %, offline-aware. U.S. only. |
| ScoutPresenceDecider.kt | Social timing layer. |
| LlamaEngine.kt | Offline brain JNI wrapper — WORKING. Re-enabled June 28: loadAsync called with nCtx=512, nThreads=2. |
| OfflinePromptBuilder.kt | TinyLlama prompt formatter. |
| scout_llama_jni.cpp | C++ JNI bridge — compiled into libscout_llama.so. |
| scout_llama_api.h | Self-contained b8946 declarations. |
| CMakeLists.txt | NDK build config. |
| HabitLayer.kt | Pattern memory — 14-day decay. |
| PeopleDb.kt | People memory — getName(), setName(), isKnown(). BLOB embedding column added June 17. Cosine similarity matching. findBestMatch scans named rows only (June 21). DB version 4 July 3: migration clears 192-dim embeddings (preserves names/hashes). person_embeddings table (addNamedEmbedding(), findBestMatchName(), forgetPerson()). Up to 12 embeddings per person. Threshold 0.65f (raised back July 4 — 0.60f caused Diana/Elijah cross-contamination). cursor.use{} in findBestMatch and findBestMatchName (July 4). forgetPerson atomic with transactions (July 4). Secondary crop threshold 0.55f. July 16: private scoreByPerson() helper aggregates best score per named person. findBestMatchName() adds minMargin=0.08f — returns null when top two candidates are within 0.08f of each other. findBestMatchNameWithScore() new — returns Pair<String, Float>? for confidence gating in MainActivity. addNamedEmbedding(): at cap, replaces the most-redundant embedding (maxByOrNull cosine similarity) in-place via db.update instead of hard-stopping. |
| VisionAnswerBuilder.kt | Builds spoken vision responses. OBJECT_WHITELIST filters noisy ML Kit labels (June 27). Wired to PeopleDb. Uses lastKnownFaceName for reliable name reporting. faceCount==2 uses both knownFaceName and secondaryFaceName. "I see X" phrasing (not "I see you, X") as of July 3. July 4: freshness 3500ms→1800ms; 3+ faces branch gets dogLine; 2-face branch: secondaryFaceName arm precedes pendingIntroName arm, new else arm for unknown primary + known secondary. |
| FaceEmbedder.kt | Created June 15. Wired into camera pipeline June 17. ArcFace upgrade July 3: loads InsightFace MobileFaceNet.tflite (512-dim), EMBEDDING_SIZE=512, single-batch output Array(1){FloatArray(512)}, single-pass buffer fill. Preprocessing unchanged: (px - 127.5f) / 128f. Returns L2-normalized 512-dim embedding. July 17: import corrected to org.tensorflow.lite.Interpreter (com.google.ai.edge.litert.Interpreter does not exist in the LiteRT 2.1.5 AAR at runtime). |
| MobileFaceNet.tflite | Bundled in app/src/main/assets/. InsightFace MobileFaceNet trained with ArcFace loss (July 3). 4.8MB. Input: 112x112 RGB, normalized. Output: 512-dim embedding. Replaces original 192-dim model. |
| Phrases.kt | NEW July 3. Personality phrase pools with anti-repeat rolling window (cooldown = pool.size / 2). pick(key, pool) returns a non-repeating random phrase. pickNamed(key, pool, name) substitutes {name} placeholder. Pools: BOOT_ONLINE, BOOT_OFFLINE_FAST, BOOT_OFFLINE, BOOT_NO_INTERNET, BOOT_NO_KEY, REMEMBER, REMEMBER_NAME, REMEMBER_MY_NAME, REMEMBER_WIFE, REMEMBER_SON, REMEMBER_DOG, GOODBYE. BOOT_NO_KEY phrases replaced July 4 — now tell user to slide right to open settings. |
| OnboardingActivity.kt | NEW July 4. 5-screen onboarding flow: Welcome / Trial / This Is Just The Beginning / Privacy / Ready To Begin. currentPage drives both dots and "X / 5" counter (single source of truth). Scout icon visible screens 1 and 5 only. finishOnboarding() sets PREF_ONBOARDING_DONE=true (scout_prefs) and gemini_enabled=false (scout_memory). |
| ModelDownloadActivity.kt | NEW July 4. Portrait loading screen for TinyLlama model download. 39 humorous messages shuffled and cycled with ObjectAnimator slide-right-in / slide-left-out animation. updateProgress(percent, downloaded, total, timeLeft) for PAD wiring. Layout: activity_model_download.xml. |
| CLAUDE.md | NEW July 4. Repo-root session notes for all future Claude instances: full git pull/push commands (branch name), critical hardcoding rules, architecture quick ref, test devices, master doc list. |
| brain/ScoutBootStatus.kt | REWRITTEN July 3. Uses Phrases pools for all boot greetings. Adaptive offline boot: BOOT_OFFLINE_FAST (skips warming-up) when lastLlamaLoadMs < 2s, BOOT_OFFLINE otherwise. Takes lastLlamaLoadMs: () -> Long lambda (default Long.MAX_VALUE). |
| ScoutExportManager.kt | Exports Scout's memory as a JSON file for sharing. July 17: constructor updated to accept peopleDb: PeopleDb; added "people" section (named faces from people table — face_hash, name, first_met, last_seen, no BLOBs) and "face_embeddings" section (per-name embedding count from person_embeddings). Commit aa10bc9. |
| THIRD_PARTY_NOTICES.md | MIT attribution for MobileFaceNet. Start of Open Source Credits. |

---

## 10. Scout Animation Goal & Mood System

### Visual Elements (ScoutFaceView)
- Background: dark blue-charcoal #1E2B38 (finalized May 18, 2026)
- Virtual canvas: 1920×1080
- Eyes: large ovals, deep blue iris with 28 spoke rays, biased inward 20f toward nose
- Mouth: minimal subtle curve
- Brows: thin and subtle — floating sticker feeling reduced but still readable. Still being refined.
- Wave bars: 22 diamond shapes, teal #00FFD0, visible during listening and speaking
- Idle listening dots: 3 teal pulsing dots when quiet

**Animation tone:** Subtle human animation. Soft emotional transitions. Calm organic motion. Believable presence.
NOT: Pixar-style exaggeration. Cartoon expressions. Hyperactive motion. Fake emotion.

**Design goal:** An AI face with gentle gaze drift, very subtle mouth, and soft thin brows that integrate naturally with the face. Scout is closer to this target than the early versions; brow integration is the largest remaining visual gap.

**Keep forever:** blue iris, white sclera, cartoon style.
**Never add:** tear ducts, skin folds, eyelashes, realistic anatomy.
**Design principle:** 'Scout stays Scout. He just gets a little more alive.'

### Mood States

Scout's face should feel alive and emotionally present, but always calm. Never perfectly still.

- CALM — Eyes center, brows neutral, subtle smile (0.15). Scout's default.
- CURIOUS — Eyes shift right and up, one brow lifts (0.18), slight tilt (0.12).
- HAPPY — Eyes center, both brows lift gently (0.12), smile increases (0.35).
- THINKING — Eyes drift up/around, lids narrow, brows asymmetric. PARTLY BUILT June 8.
- CONCERNED — Eyes look slightly down, inner brows lift (0.05), tilt inward (-0.18).

| Version | What Changes |
|---------|-------------|
| v1.0 | Current eyes. Stable irises. Better identity. Better memory. |
| v1.5 | Eyebrows move per mood. Smooth natural blink every 4–8 seconds. |
| v2.0 | Simple upper eyelids + simple lower eyelids. Still cartoon. Still Scout. |
| v3.0 | Full emotion system. Scout at most expressive. Still obviously Scout. |

- Keep forever: blue iris, white sclera, cartoon style.
- Never add: tear ducts, skin folds, eyelashes, realistic anatomy.
- Design principle: 'Scout stays Scout. He just gets a little more alive.'

---

## 10b. Settings Architecture

The Settings screen is the user's control center for Scout. Defaults are calm and safe — users opt in to more capability rather than opt out.

**10b.1 Identity & Voice**
- Robot Name — default "Scout", users can rename. All spoken responses use the stored name dynamically.
- Voice pitch slider
- Voice speed slider
- Future voice tone options

**10b.2 Brain & Behavior**
- Offline Mode (default ON) — Scout uses TinyLlama by default
- Online Brain Helper — toggle Gemini or a larger local model
- API key entry — user's own free Gemini key, never bundled with the app
- Kid Safe Filter
- Pet Mode — Scout greets pets softly. Future: physical steer-around on robot body.
- Presence Mode (default ON) — Scout actively listens in the room
- Allow Spontaneous Comments
- Privacy Mode toggle

**10b.3 Builder's Workbench**
- Enable Hardware Mode (off by default)
- Bluetooth pairing — for KEYESTUDIO Mini Tank Kit V2 chassis
- Future motor controls

**10b.4 Privacy & Data**
- Memory Export — back up TruthDb and habits
- Memory Import
- Reset Memory Layers — selective reset
- Camera controls
- Voice camera commands

**10b.5 Extras & Support**
- Cosmetics (Backpack) — visual customization
- Support Scout (in-app, optional — see section 5)
- About & Licenses

**10b.6 Connected Services (Future — All Opt-In)**
- Calendar access — add, remove, and announce events. Uses Android Calendar Provider. No external API needed.
- Phone call awareness — Scout announces caller name then steps aside. Normal call behavior untouched.
- Gmail access — read emails and compose when asked. Requires Google OAuth. Planned for a later phase.
- Design principle: Scout announces and helps, but never interferes.

---

## 11. TinyLlama Safety Architecture

- Layer 1: Hard-coded safety rules (app level). Always enforced.
- Layer 2: Scout identity rules (system prompt + hardcoded intents).
- Layer 3: TinyLlama generation (last line of defense only).

TinyLlama knows profanity and scary content. Scout must NOT rely on it for safety decisions.
Future: response cleanup layer after TinyLlama, before TTS.

---

## 12. Device Requirements

| | Android | RAM | Storage | Notes |
|--|---------|-----|---------|-------|
| Minimum | 13+ | 4 GB | ~3–5 GB | Everything works; responses slower. |
| Recommended | 13+ | 8 GB+ | 10 GB+ | Faster responses, smoother animations. |

---

## 13. Brain Upgrade Models

| Tier | Size | Model |
|------|------|-------|
| FREE | 669 MB | TinyLlama 1.1B Q4_K_M — baseline, always included |
| $2.99 | 1.79 GB | Phi-2 2.7B Q4_K_M — Microsoft. Solid first upgrade. |
| $4.99 | 2.02 GB | Llama 3.2 3B Q4_K_M — Meta. THE most important upgrade. |
| $6.99 | ~2.4 GB | Phi-4 Mini Q4_K_M — Microsoft (PRIMARY Pack 3). |
| $6.99 ALT | 2.39 GB | Phi-3.1 Mini 4K Q4_K_M — tested backup Pack 3. |
| $9.99 | 4.92 GB | Llama 3.1 8B Q4_K_M — Meta. Flagship, Fold 7 class. |

---

## 14. Hardware Direction — Optional

Scout works fully without hardware. KEYESTUDIO Mini Tank Kit V2 (Patrick owns one). Hardware Mode is opt-in. The $9.99 baseline must work fully on the phone alone.

---

## 15. Episodic Memory — Planned Phase

Scout's current memory stores facts and habits. The missing layer is **episodic memory** — remembering shared experiences over time, not just isolated facts.

| Type | Example | Status |
|------|---------|--------|
| Facts | "Your wife is Diana." | Done — TruthDb |
| Episodes | "Yesterday we talked about face recognition." | Planned — JournalDb |
| Summaries | "This week we fixed vision and talked about beta testing." | Future |

**How it would work:**
- At the end of a conversation, Scout quietly saves a short journal entry (one or two sentences)
- Teaching moments, recognized events, and notable interactions are logged
- When asked "what did we work on this week?" Scout reads the last several journal entries and summarizes them naturally

**Example journal entries Scout would write:**
- *"July 2, 2026 — Patrick and I talked about face recognition and tested the A32."*
- *"July 3, 2026 — Patrick introduced Diana and Elijah to me."*

**Example recall phrases:**
- "What did we do this week?"
- "What have we been working on?"
- "Do you remember what we talked about yesterday?"

**Why this fits Scout:**
Humans don't remember every sentence — they remember important moments. Scout shouldn't pretend to have perfect recall of every word. A lightweight daily journal gives Scout the feeling of a real shared history without trying to store everything.

**JournalDb** is already listed in Scout's key files — the container exists. What needs to be built is the writing logic (auto-save after conversations) and the reading/summarizing logic (on request).

**Status: Post-launch. Do not build until TruthDb and habit memory are solid and stable.**

---

## 16. Scout Behavior Learning (Scout 1.1+)

**Design approved July 5, 2026.** One of Scout's most unique planned features.

**Public-facing name:** "Scout Behavior Learning"
**Public-facing tagline:** "Scout can learn small preferences with your approval."

Families see friendly first-person suggestions ("I should be quieter at night.") with three buttons: **Approve / Not now / Never suggest this**. No technical language is ever shown to the family. Code proposals are internal only and never surfaced in the UI.

**Two-tier system. Design approved July 5, 2026.**

---

### Tier 1 — Regular Mode (Scout 1.1) — For everyone

Family sees "Scout's Suggestions" in Settings. Scout speaks in first person, warmly. Three buttons: **Approve / Not now / Never suggest this**. No technical language ever shown. Applies immediately to SharedPrefs/behavior flags on approval.

Example suggestions: "I'd like to answer a little faster." · "I noticed you prefer shorter replies." · "I should be quieter at night." · "I should be more careful recognizing [name]."

Triggers: wrong face corrected 3+ times · user says "stop" repeatedly · greeting fires within seconds of last · TTS after 9pm · same fact corrected more than once

---

### Tier 2 — Scout Dev Build (Patrick only — never ships on Play Store)

**Critical architectural decision:** The developer features are NOT hidden in the Play Store APK — they are absent. Android build variants ensure the code is stripped entirely at compile time. Nothing to decompile or discover.

**Build variants (`build.gradle.kts`):**
```kotlin
productFlavors {
    create("standard") { buildConfigField("boolean", "DEVELOPER_MODE", "false") }
    create("dev")      { buildConfigField("boolean", "DEVELOPER_MODE", "true")  }
}
```
`if (BuildConfig.DEVELOPER_MODE)` in release builds compiles to `if (false)` → entire block stripped.

**Scout Dev = telemetry and observations, not code generation.**

Scout surfaces real data from running on Patrick's devices. Patrick (and Claude) decide what to do with it. Scout is an engineering partner, not an autonomous programmer.

Examples:
- "I've had 14 failed face recognitions today."
- "Wake-word detection dropped after yesterday's update."
- "Battery usage increased by 12% compared to last week."
- "Gemini failed 8 times today — mostly between 6 and 7pm."
- "TinyLlama boot time has been averaging 11 seconds this week."

**TelemetryDb** (dev build only — not compiled into standard/release):
```sql
CREATE TABLE telemetry_events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type  TEXT    NOT NULL,  -- "face_fail"|"wake_miss"|"gemini_fail"|"tts_error"|"boot_time" etc.
    value       REAL,
    context     TEXT,
    recorded_at INTEGER NOT NULL
);
```

### Files to build
Tier 1 session: `ProposalDb.kt` · `ProposalDetector.kt` · `ScoutProposal.kt` · Settings "Scout's Suggestions" UI · `ApplyProposal.kt`

Tier 2 session (Scout Dev, 1.5+): `TelemetryDb.kt` · `TelemetryCollector.kt` · Scout Dev dashboard UI · build variant wiring

---

## 16c. Autonomy Direction — Future (Post-Launch)

**Goal:** Scout acts autonomously, but changes himself with permission.

Two kinds of autonomy — both approved:
- **Behavioral autonomy** (Scout 1.x+): Scout decides *how* to act in the moment — when to speak, when to stay quiet, when to notice something and comment without being asked. No approval needed for moment-to-moment behavior.
- **Self-modification autonomy** (requires approval always): Scout changing his own settings, memory rules, or behavior flags. Always requires Approve / Not Now / Never Suggest This Again.

**What true behavioral autonomy looks like for Scout:**
- Noticing the room is quiet and checking in unprompted
- Noticing a pattern in conversations and mentioning it naturally
- Noticing a family member hasn't been seen in a while
- Environmental awareness driving initiated behavior — not just reacting to being called

**The hard part:** Knowing *when not* to speak is what separates a present companion from an annoying one. Timing and presence matter more than capability.

**Status:** Future session. Do not build before launch.

**Reaffirmed July 25, 2026** — Patrick restated this direction in full as a five-layer model: Working Memory (conversation only, cleared on restart) → Habit Store (decaying patterns, already built) → Truth Database (permanent, approved-only facts, already built) → **Proposal Sandbox** (new — a temporary holding area for Scout's own ideas; nothing here affects Scout until approved) → Reflective Layer (future — an LLM that can help generate ideas but never directly changes Scout). Core rule restated explicitly: Scout must never rewrite Kotlin files, modify application logic, generate executable code, edit his own source, or change compiled behavior — ever. He only ever proposes ("I've noticed Patrick usually asks for weather around 7am — want me to offer it after good morning?"), and every proposal requires explicit approval before it becomes real behavior. Immediate scope confirmed for whenever this is actually built: noticing patterns and mentioning them in conversation, and suggesting Settings changes — not self-adjustment. This section's original "do not build before launch" status stands; this is documentation only, not a build session.

---

## 16b. Future Polish Ideas (Post-Launch, Scout 2.0+)

- Voice Recognition (Future) — Optional voice enrollment for family members. Advisory only — does not replace TruthDb or user-confirmed identity. Not for launch or 1.1.
- Fun startup/loading messages — Rotating, self-aware, Scout-voiced lines for the first-launch brain download screen.
- "Test Connection" button in Settings — verify API key without burning quota (add small sentinel request).

---

## 17. Language Support — Planned

**Phase 1 — Early Spanish Support (No new brain model needed)**

- Add a Language setting: English / Español.
- Android STT switches to Spanish when selected.
- Android TTS speaks in Spanish when selected.
- All hardcoded responses translated.

**Phase 2 — Full Spanish Support (Long-Term Future)**

- Evaluate Spanish-capable offline brain models when brain pack infrastructure is mature.
- Priority: Not now. Current roadmap comes first.

---

## 18. Play Store Launch Checklist

| # | Task | Status |
|---|------|--------|
| 1 | Wake word filter | ✓ DONE June 12 |
| 2 | Memory recall bug | ✓ DONE June 12 |
| 3 | Greeting routing | ✓ DONE June 14 |
| 4 | TinyLlama rambling fix | ✓ DONE June 15 |
| 5 | Self-echo guard | ✓ DONE June 15 |
| 6 | MainActivity blank line cleanup | ✓ DONE June 15 |
| 7 | Face recognition Step 1 (foundation) | ✓ DONE June 15 — FaceEmbedder.kt + model bundled |
| 8 | Face recognition Steps 2–4 (wiring) | ✓ DONE June 17 — camera wired, PeopleDb embeddings, naming flow |
| 9 | Remove hardcoded Gemini API key + Settings screen | ✓ DONE June 18 — SettingsActivity + key removed |
| 10 | Eye jitter fix | ✓ DONE June 18 — boot lock, speaking gate, dead zone, min-delta |
| 11 | A32 speak() crash fix | ✓ DONE June 20 — isSpeaking race condition closed |
| 11b | A32 delayed crash fix | ✓ DONE June 21 — camera frame throttle eliminates post-Gemini LMKD kill. Patrick confirmed stable. |
| 12 | TinyLlama re-enable on A32 | ✓ DONE June 28 — 90s delay, 800MB RAM guard, nCtx=512. On-demand Gemini fallback. Needs A32 real-world confirmation. |
| 13 | Startup diagnostics | ✓ DONE July 4 — TTS failure Toast + STT spoken warning at boot |
| 14 | Onboarding flow — OnboardingActivity.kt | ✓ DONE July 4 — 5-screen flow, first-boot redirect, offline default |
| 15 | Fold 7 stability testing | Not started — A32 is current test device |
| 16 | A32 stability testing | Ongoing — no crashes as of June 21. TinyLlama re-enabled June 28, monitoring. |
| 17 | Privacy Policy | ✓ DONE July 11 — in-app scrollable dialog (Settings → About Scout). Website version available at lippy-robotics.gt.tc. |
| 18 | Terms of Use | ✓ DONE July 10–11 — in-app scrollable dialog + terms.html in repo root (commit b5735f5). |
| 19 | Open Source Credits — THIRD_PARTY_NOTICES.md started | In progress |
| 20 | Weather API licensing | ✓ RESOLVED June 16 — switched to NWS, free for commercial use |
| 21 | Play Store listing — description, screenshots, rating | Not started |
| 22 | ✓ 16KB page size — RESOLVED July 19 | Confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK — all 11 previously-flagged libraries pass individually, "Verification successful" overall. See July 19 section at top. Play Store submission unblocked. |

---

## 19. Onboarding Flow — 5 Screens (Designed, Not Yet Built)

Blue color scheme locked in — matches Scout's eye color and visual identity. Designed by ChatGPT. Approved June 12.

IMPORTANT: Screen counter (e.g. '4 / 5') and progress dots must both be driven by the same variable when built in Android. Never hardcode the number in two places.

| Screen | Title | Key Message |
|--------|-------|-------------|
| 1 of 5 | Welcome to Scout | 'I'm Scout. Just say my name and I'll be listening.' No account required. Privacy-first. No subscriptions. |
| 2 of 5 | Try Scout Free for 7 Days | $9.99 one time. Never a subscription. Pay once. Own Scout forever. |
| 3 of 5 | This Is Just The Beginning | Future updates: calendar, news, expressions, languages. Scout is actively growing. |
| 4 of 5 | Your Privacy Matters | No account. Local features stay on device. You decide what Scout remembers. |
| 5 of 5 | Ready To Begin? | Scout's face fills the screen. 'This is just the beginning.' Start Using Scout button. |

The 'What's to come' section also lives in Settings → About Scout → Features & Future Plans so users can revisit it later.
Screen 1 'See & Recognize' description reads: 'I see faces, scenes, and more.' — not 'pets' which is not yet fully implemented.

---

## 20. Versioning System

| Type | Examples | When to Use |
|------|----------|-------------|
| Launch | 1.0 | First public release. |
| Bug Fix | 1.0.1, 1.0.2 | Bug fixes, crash fixes, stability improvements. |
| Feature | 1.1, 1.2, 1.3 | New features — calendar, memory improvements, expressions, language support. |
| Major | 2.0, 3.0 | Full mood system, major memory upgrades, hardware mode, big companion leaps. |

**Scout-Themed Version Names:**
- Scout 1.0 — The Beginning
- Scout 1.1 — Growing Up
- Scout 1.2 — Learning More
- Scout 2.0 — A New Chapter

---

## 21. Legal & Website

**Website:**
- Current address: https://patevan9.github.io/lippyrobotics.github.io
- Future domain options: lippyrobotics.com, scoutcompanion.com, meetscout.ai
- List website on: Facebook page, Google Play listing, About Scout screen, website footer.
- Add a 'What's New' or 'Scout Development Updates' page.

**Required for Launch:**

| Document | Priority | Notes |
|----------|----------|-------|
| Privacy Policy | 1 — ✓ DONE July 11 | In-app scrollable dialog (Settings → About Scout). Covers offline-first design, Gemini optional/user-key-only, NWS weather, no data collected by Lippy Robotics. Website version available. |
| Terms of Use | 2 — ✓ DONE July 10–11 | In-app scrollable dialog + terms.html in repo root. Acceptance clause, service-as-is, third-party services, changes-to-terms. |
| Open Source Credits | 3 — At launch | llama.cpp, TinyLlama, MobileFaceNet (MIT, done in THIRD_PARTY_NOTICES.md), Android libraries. |
| Website link | 1 — Before launch | https://patevan9.github.io/lippyrobotics.github.io in About Scout, Google Play listing, Facebook page. |

**Inside the App — About Scout must contain:**
- Version number
- Privacy Policy link
- Terms of Use link
- Open Source Licenses link
- Website: https://patevan9.github.io/lippyrobotics.github.io
- Update History — every major version and what changed
- Contact / Get in Touch — opens email to lippyroboticslabs@gmail.com

Support email: lippyroboticslabs@gmail.com. Auto-responder confirmed — sets expectations on response time, asks for device model, Android version, and description for technical issues.

**Weather API — RESOLVED June 16:**
Open-Meteo was replaced with NWS (api.weather.gov). Completely free for commercial use, no API key, no licensing concern. U.S. locations only. Open-Meteo attribution no longer required.

---

*Project Scout Master Summary | Last updated: July 29, 2026 | Version 51 | Single source of truth — upload every session*
