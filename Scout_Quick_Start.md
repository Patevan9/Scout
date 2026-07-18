# Project Scout — Quick Start
**Last updated: July 18, 2026 | Version 25**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
For full technical details, use the Scout Master Summary (v49).

---

## July 18, 2026 (Later Same Day) — 16KB Root Cause Refined:

⚠ **Correction to the entry directly below: 5 of the 6 llama.cpp/ggml libraries are already ELF-aligned.** Running `readelf -lW` directly against the actual files checked into `app/src/main/jniLibs/arm64-v8a/` in this repo (not a doc claim — the real binaries) shows `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, and `libggml-cpu-android_armv8.2_2.so` all have `Align 0x4000` (16384 bytes = 16KB) on every LOAD segment. These libraries were almost certainly pulled from an official [ggml-org/llama.cpp release](https://github.com/ggml-org/llama.cpp/releases) — their naming matches llama.cpp's own Android CI output exactly, which builds via NDK 29.0.14206865 (well past NDK r28, where Google's docs confirm 16KB alignment is the compiled-in default) — so the upstream build is already compliant. This also explains why the Fold 7 dialog tags `libimage_processing_util_jni.so` alone with the specific "LOAD segment not aligned" message while every other library (including these five) gets a generic "Unknown error" — two different failure classes. **Revised hypothesis:** the real suspect for the 10 "Unknown error" failures is APK packaging (whether `.so` entries are stored uncompressed and page-aligned in the installed zip) or something specific to how a **debuggable** build installs from Android Studio — not source-level misalignment. `libscout_llama.so`'s own alignment is still unverified (nobody has run `readelf` on a fresh compiled build). **Revised next steps:** (1) build a **release** APK, not a debug install, and re-test on the Fold 7; (2) if it still fails, run `zipalign -c -P 16 -v 4` against the built APK to check zip-level alignment; (3) `libimage_processing_util_jni.so` needs an actual ML Kit version bump — that's the one confirmed real defect; (4) check `libscout_llama.so`'s alignment on a fresh build. **Do not spend a session rebuilding llama.cpp from source for 16KB — the evidence says that's not the fix.**

---

## July 18, 2026 — What Is New:

⚠ **16KB page size — REOPENED, contradicted by real Fold 7 device evidence** — Android's own "Android App Compatibility" warning fired on Patrick's Fold 7 (Android 15) at app launch, listing **11 native libraries** as NOT 16KB aligned: `libLiteRt.so`, `libLiteRtClGlAccelerator.so`, `libface_detector_v2_jni.so`, `libimage_processing_util_jni.so`, `libmlkitcommonpipeline.so` (ML Kit), `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so` (llama.cpp/ggml), and `libscout_llama.so`. This directly contradicts the July 17 "readelf VERIFIED PASS" claim below (for the exact file, `libLiteRt.so`, that check supposedly verified), the July 10 "ML Kit DONE" claim, and the July 7 "scout_llama.so confirmed working" claim. **Root cause, confirmed by reading `CMakeLists.txt` directly:** the `-Wl,-z,max-page-size=16384` linker flag from the July 7 fix only applies to the `scout_llama` build target — Scout's own thin JNI wrapper. `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, and `libggml-cpu-android_armv8.2_2.so` are pre-built binaries checked directly into `app/src/main/jniLibs/arm64-v8a/` — CMakeLists.txt only links against them, it never compiles them, so the flag never reached them. Even `libscout_llama.so` itself is still failing on the real device, so the July 7 fix may never have actually taken effect (a stale native build cache is the leading suspect). The ML Kit and LiteRT "done" statuses were both based on checking an isolated artifact rather than the actual built and installed APK. **Real remaining work:** source or rebuild 16KB-aligned versions of the five prebuilt llama.cpp/ggml libraries, do a full clean rebuild to check whether the scout_llama.so flag is even taking effect, and re-verify ML Kit/LiteRT against the real built APK. **Play Store submission is NOT unblocked on the 16KB front** — every "FULLY DONE"/"PASS" claim below dated July 7 through July 17 regarding 16KB alignment is superseded by this entry.

*(Previous session July 17: LiteRT import fix, "favorite favorite" bug fix, battery optimization prompt, thinking watchdog, people DB export — all still valid; only the 16KB claims below are affected)*

---

## July 17, 2026 — What Is New:

✓ **LiteRT import corrected — build was broken** — July 16's change set the import to `com.google.ai.edge.litert.Interpreter` (matching the Maven artifact name), but that class does not exist inside the LiteRT 2.1.5 AAR. LiteRT rebrands the Maven coordinates but the internal Java package is still `org.tensorflow.lite`. Fixed: `FaceEmbedder.kt` import reverted to `org.tensorflow.lite.Interpreter`. Build confirmed successful. Commit 83ed37f.

✓ **16KB readelf verification COMPLETE — PASS** — Patrick ran `llvm-readelf.exe -l libLiteRt.so` on Windows (NDK 28.2.13676358). All LOAD segments show `Align 0x4000` (16KB). Also verified `libLiteRtClGlAccelerator.so` — same result. Both files PASS. 16KB compliance for LiteRT is now fully confirmed. Play Store submission is unblocked on the 16KB front. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the July 18 entry at the top of this file for the full correction.

✓ **"Favorite favorite" double-prefix bug fixed** — `TeachExtractor.kt` was unconditionally prepending `"favorite_"` to all `"my X is Y"` teaching patterns. When X was already "favorite color", the stored key became `"favorite_favorite_color"`. Fixed: `startsWith("favorite")` guard prevents double-prefix. Now "my favorite color is cyan" → key `"favorite_color"`. Commit 9b353a8.

✓ **Display fix for old double-prefix keys** — `keyToHuman()` in `handleWhatYouLearnedQuery()` now strips one `"favorite_"` prefix from any key that begins with `"favorite_favorite_"`. Scout reads back "your favorite color is cyan" correctly even for facts stored under the old bug. Same commit 9b353a8.

✓ **Battery optimization prompt added** — `checkBatteryOptimization()` fires 8 seconds after first boot. Uses Android's `PowerManager.isIgnoringBatteryOptimizations()` + `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to take users directly to the system setting. Fires once only (prefs guard). `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission added to AndroidManifest. Note: Samsung's additional "never sleeping apps" list is a second layer that this standard Android API cannot reach — users still need to add Scout there manually. Commit 1abcee1.

✓ **Thinking watchdog added** — `thinkingStartedMs` field records when `isThinking` turns true. 120-second watchdog in `runRecognizerWatchdog()` force-clears stuck thinking: `isThinking = false`, `wantListening = true`, `scheduleListenRestart(immediate = true)`, logged to JournalDb. Prevents Scout going silent with eyes still moving when TinyLlama hangs. Commit 1abcee1.

✓ **DB migration cleans up double-prefix facts** — `migrateDoublePrefixFacts()` in `setupMemory()` deletes all `"favorite_favorite_%"` keys from TruthDb on next launch (one-time, prefs-guarded). Cleans up both the TeachExtractor bug pollution and the TTS self-echo entry `"favorite_favorite_yes_my_favorite_color"`. Commit e24fad9.

✓ **TruthDb gains `deleteFact()` and `deleteFactsWithKeyLike()`** — Two new methods. `deleteFact(entity, factKey)` removes a single fact. `deleteFactsWithKeyLike(entity, pattern)` removes all matching facts using SQL LIKE syntax. Used by the DB migration. Commit e24fad9.

✓ **People DB added to brain export** — `ScoutExportManager` now takes `peopleDb: PeopleDb` and exports two new sections: `"people"` (named faces: face_hash, name, first_met, last_seen — no BLOBs) and `"face_embeddings"` (per-name embedding count from `person_embeddings` table). "Scout, export your brain" now shows the full people picture. Commit aa10bc9.

⚠ **"Very" — in people DB, not truth DB** — Confirmed via brain export JSON: "Very" is not in truth DB. Must be in the people table (likely stored from an early hash-based recognition session). Patrick will run "Scout, export your brain" and share the new JSON (which now includes the people sections) to identify and fix this.

⚠ **TTS self-echo vulnerability noted** — `"favorite_favorite_yes_my_favorite_color"` in the DB showed Scout re-teaching himself from his own TTS output ("yes, my favorite color is cyan" echoed back through the mic within the 30-second conversation window). The self-echo guard in `onResults()` failed (normalization mismatch on the prefix "yes,"). DB migration cleaned it up. Root vulnerability needs future tightening.

*(Previous session July 16: LiteRT migration code done, face recognition 3-bug fix — see below)*

---

## July 16, 2026 — What Is New:

✓ **LiteRT migration — code done, readelf pending** — `app/build.gradle.kts` + `FaceEmbedder.kt`. `tensorflow-lite:2.17.0` replaced with `litert:2.1.5` (drop-in replacement, same Interpreter API, no logic changes). FaceEmbedder.kt import changed `org.tensorflow.lite.Interpreter` → `com.google.ai.edge.litert.Interpreter`. Commits 9676192. **Readelf step still required** — after next Android Studio build, run `readelf -l liblitert_jni.so | grep -A1 LOAD` on the `.so` inside the unpacked AAR from `~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litert/litert/2.1.5/`. Look for `p_align: 0x4000` (pass) vs `p_align: 0x1000` (fail).
✓ **Face recognition accuracy — 3 root-cause bugs fixed** — Root cause of the repeated Diana/Elijah confusion found and fixed in PeopleDb.kt and MainActivity.kt. Commit b6c5579.
  - **Margin check** — `findBestMatchName` now requires the top candidate to lead by ≥ 0.08f. If two people score too close, Scout says nothing rather than guessing wrong.
  - **Profile pollution gate** — `CONFIDENT_EMBED_THRESHOLD = 0.72f` in MainActivity. A match must score ≥ 0.72f (well above the 0.65f floor) before its embedding is added to a person's profile. Borderline matches no longer corrupt profiles.
  - **Rolling window at cap** — When a person's 12 embeddings are full, the most-redundant one (highest cosine similarity to the new one) is replaced rather than hard-stopping. Profiles stay diverse as conditions change.
  - `forgetPerson` now also clears `lastFaceEmbedding` for a clean slate on re-introduction.
  **Action required:** Run "Scout, forget [name]" for Diana and Elijah before re-introducing them — existing profiles may already be polluted.

*(Previous session July 13: Diagnostic reporting, Settings DIAGNOSTICS section, support button, reset red styling, NDK fix, Gradle OOM fix — all DONE)*

---

## July 13, 2026 — What Is New:

✓ **Diagnostic reporting system complete (Steps 4–6)** — `DiagReportActivity.kt` built. Reads from DiagnosticDb and displays: Privacy Notice (verbatim policy wording), System Information (4 fields), Event Log (last 7 days, newest first), Crash Log. Report never contains speech text, names, memories, face data, location, API keys, stack traces, or file paths. Activity registered in AndroidManifest.
✓ **View/Share mode differentiation** — All sharing controls (notes field, warnings, Share button) grouped in `llShareControls` LinearLayout. `EXTRA_SHOW_SHARE` boolean extra controls visibility — hidden in View mode, shown in Share mode. Share flow writes `filesDir/diag/diag_report.txt` and opens the system share sheet via FileProvider.
✓ **Settings DIAGNOSTICS section** — Three navRows: "View Diagnostic Report" (read-only), "Share Diagnostic Report" (full sharing UI), "Clear Diagnostic History" (removes events, crash log, report file — does not touch memories or model files).
✓ **Support button now opens browser** — `showSupport()` fires an ACTION_VIEW intent to `lippy-robotics.gt.tc/support.html`. Fallback dialog "Unable to open the Scout Support Center" shown if no browser is available. Previously showed a dead-end dialog with no action.
✓ **Reset Memory Layers destructive red styling** — navRow title rendered in `#FF4D4D`. Confirmation dialog "Reset" button colored red via `getButton(BUTTON_POSITIVE).setTextColor()` after `.show()`. Standard Android UX for irreversible data deletion.
✓ **NDK 28.2 build fix** — `keepDebugSymbols += "*/x86_64/*.so"` in `app/build.gradle.kts` packaging block. Prevents NDK llvm-strip STATUS_ILLEGAL_INSTRUCTION crash on x86_64 ML Kit ELFs. Does not affect ARM64.
✓ **Gradle daemon OOM fix** — `org.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC` in `gradle.properties`. SerialGC avoids G1's large virtual address reservation that was exhausting the Windows page file on Patrick's machine.
✓ **Google Play Data Safety analysis complete** — Scout sends no data to Lippy Robotics servers (no collection). Must declare: Gemini query text as "App interactions → User-generated content" Shared/Optional; weather coordinates as "Location → Approximate location" Shared/Optional.

*(Previous session July 7: 16KB fix, bootstrapModelFile, head-turn amplitude, thinking expression redesign — all DONE)*

---

## July 10–11, 2026 — What Is New:

✓ **Privacy Policy — in-app dialog** — `showPrivacyPolicy()` in SettingsActivity. Scrollable AlertDialog with full policy text. Covers: offline-first design, Gemini optional/user-key-only, NWS weather, no data collected by Lippy Robotics. Settings → About Scout → Privacy Policy. Fully offline. DONE July 11.
✓ **Terms of Use — in-app dialog** — `showTermsOfUse()` in SettingsActivity. Scrollable dialog with acceptance clause, service-as-is, third-party (Gemini), changes-to-terms. Settings → About Scout → Terms of Use. DONE July 11.
✓ **terms.html added to repo root** — Website Terms of Use for lippy-robotics.gt.tc. Play Store compliance clauses: acceptance block + changes-to-terms block. Commit b5735f5. DONE July 10.
✓ **ML Kit 16KB alignment — DONE** — face-detection 16.1.6 → 16.1.7 (arm64 confirmed aligned, ML Kit issue #986 Dec 2025). image-labeling 17.0.7 → 17.0.9 (fixed vision-common). Commit 60443f3. DONE July 10.
✓ **LiteRT migration — FULLY DONE** — `build.gradle.kts` + `FaceEmbedder.kt` updated July 16–17. `litert:2.1.5` in build.gradle.kts; `FaceEmbedder.kt` uses `org.tensorflow.lite.Interpreter` (the correct internal package). Readelf COMPLETE July 17 — all LOAD segments `Align 0x4000` (PASS). Play Store submission unblocked. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the July 18 entry at the top of this file for the full correction.

*(Previous session July 7: 16KB scout_llama.so fix, bootstrapModelFile, head-turn amplitude, thinking expression — all DONE)*

---

## July 7, 2026 — What Is New:

✓ **16KB page alignment fix confirmed** — `scout_llama.so` now builds with `-Wl,-z,max-page-size=16384`. Fixes dlopen failure on Samsung Linux 6.x kernels. Confirmed working on both A32 and Fold 7. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the July 18 entry at the top of this file for the full correction.
✓ **bootstrapModelFile() added** — Scout auto-copies the TinyLlama model from external storage to filesDir on startup. Checks app-specific external dir first (no permission needed), then root /sdcard/ if READ_EXTERNAL_STORAGE is granted (Android ≤12). READ_EXTERNAL_STORAGE added to manifest with maxSdkVersion="32". Model file survives reinstalls automatically.
✓ **TinyLlama confirmed working on A32 and Fold 7** — Both devices tested with Online Features OFF. TinyLlama answers questions from local model. Primary brain confirmed.
✓ **Offline fallback message fixed** — When Online Features are deliberately OFF, Scout no longer says "having trouble connecting." Now says "I'm working offline right now, so that one's a bit beyond me."
✓ **Thinking expression amplitude increased** — thinkingLift raised 18/16px → 26/24px, thinkInnerLift 12px → 20px. Previous values were too subtle to see against idle micro-animation.
✓ **Head-turn amplitude fixed** — faceGazeDrift multipliers were 0.07/0.06 (max ±5px = ~2 physical pixels, invisible). Raised to 0.32/0.26 (max ±24px X / ±14px Y). Now clearly readable as a neck turn when Scout follows someone with his gaze.

*(Previous session July 4: Onboarding, startup diagnostics, PeopleDb 0.65f threshold, CLAUDE.md, ModelDownloadActivity — all DONE)*

---

## July 4, 2026 — What Is New:

✓ **PeopleDb threshold raised back to 0.65f** — ArcFace upgrade (July 3) set threshold to 0.60f, which caused Diana's face to match Elijah's stored embeddings. Raised back to 0.65f. cursor.use{} leak fix added. forgetPerson is now atomic. addNamedEmbedding skips insert if person already at max 12 embeddings.
✓ **VisionAnswerBuilder fixes** — 3+ faces branch gets dogLine (was missing). 2-face branch: secondaryFaceName arm now comes before pendingIntroName arm; new else arm for unknown primary + known secondary. Freshness 3500ms → 1800ms.
✓ **Secondary face findBestMatch fallback** — Secondary face path now also tries the single-BLOB people.embedding if person_embeddings returns no match.
✓ **Caption persistence fix** — Last caption line no longer stays on screen after captions are turned off in Settings. onResume() hides it immediately.
✓ **Startup diagnostics** — TTS failure shows a Toast; STT unavailability triggers a spoken warning 4 seconds after boot. Both logged to JournalDb.
✓ **Onboarding flow built — OnboardingActivity.kt** — Full 5-screen flow. First-boot redirect in MainActivity.onCreate(). currentPage is the single source of truth for both dots and "X / 5" counter. finishOnboarding() defaults new installs to offline mode (gemini_enabled=false).
✓ **BOOT_NO_KEY phrases replaced** — Now tells users to slide right to open Settings instead of vague "online mode not configured" message.
✓ **CLAUDE.md created** — Repo-root file with full git pull/push commands, critical rules, architecture notes — so future Claude sessions always have the branch name and key context.
✓ **ModelDownloadActivity built** — Portrait loading screen for TinyLlama model download. 39 humorous messages, ObjectAnimator slide-right-in / slide-left-out animation. updateProgress() method ready for Play Asset Delivery wiring.

*(Previous session July 3: ArcFace upgrade 512-dim, Diana secondary face fix, Phrases.kt, adaptive boot, BOOT_ONLINE offline-backup mentions — all DONE)*

---

## July 3, 2026 — What Is New:

✓ **ArcFace upgrade** — InsightFace MobileFaceNet (512-dim, 4.8MB) replaces old 192-dim model. FaceEmbedder.kt: EMBEDDING_SIZE 192→512, single-batch output. PeopleDb v4: migration clears incompatible embeddings (names and face hashes preserved — everyone needs one re-introduction). New threshold 0.60f (ArcFace scale: same-person ~0.5–0.95, different-person ~0.0–0.4). Fixes "everyone is Patrick" false positive bug.
✓ **"I see X" (not "I see you, X")** — Scout now says "I see Patrick" and "I see Patrick and Diana" instead of "I can see you, Patrick." Sounds like a description, not an address.
✓ **Diana (secondary face) fix** — Secondary face block now consumes `pendingFaceIntroName`. "This is my wife Diana" with two people in frame now correctly stores and recognizes Diana as the secondary face.
✓ **Personality phrase pools — Phrases.kt (new)** — Scout no longer repeats the same boot greeting, goodbye, or remember confirmation every time. Anti-repeat rolling window (cooldown = half the pool). Pools: BOOT_ONLINE (6), BOOT_OFFLINE_FAST (5), BOOT_OFFLINE (6), BOOT_NO_INTERNET (4), BOOT_NO_KEY (3), REMEMBER (9), REMEMBER_NAME/MY_NAME/WIFE/SON/DOG, GOODBYE (7).
✓ **Adaptive boot greeting** — If TinyLlama loaded in under 2 seconds last session, Scout uses the short fast boot greeting (no warming-up mention). Otherwise uses the full offline greeting with warming-up. TinyLlama load time now stored in SharedPreferences.
✓ **Online boot phrases mention offline backup** — All 6 BOOT_ONLINE phrases now include "My offline backup is warming up in the background." Previously said nothing about this when online.

*(Previous session June 30: Dynamic robot name, 8 new TeachExtractor patterns, VisionAnswerBuilder freshness extended, registerFamilyMemberFace() guard, Pet Mode design locked — all DONE)*

---

## June 29, 2026 — What Is New:

✓ **Launcher icon fixed** — Face scaled to 68% of canvas. Eyes and eyebrows now fully visible inside the circular launcher mask. All 5 mipmap densities regenerated. Patrick confirmed: "icon looks good 👍"
✓ **Face threshold raised 0.75→0.82** — Prevents father/son false matches (Patrick/Elijah scored 0.76–0.79 which was above the old 0.75 threshold). Genuine same-person matches still score 0.80+. "Scout, forget [name]" command added to wipe and re-register any face.
✓ **TTS deafness bug fixed** — Scout can no longer go permanently deaf after long idle. 3-layer fix: (1) speak() return-value check clears isSpeaking immediately if TTS returns ERROR, (2) speakingStartedMs timestamp tracks when TTS starts, (3) 45-second watchdog in the watchdog loop force-clears isSpeaking/wantListening if TTS callback never fires.
✓ **Voice slider now sticks** — SettingsActivity saves pitch/speed to scout_prefs. MainActivity reads from scout_prefs in both onInit() and onResume(). Voice changes take effect without restarting the app. Patrick confirmed: "voice is fixed."
✓ **Greeting words blocked from name storage** — "hello", "hi", "hey", "howdy", "greetings", "sup", "yo" added to blockedNames. Scout no longer says "I'll remember your name is hello."
✓ **Gemini responses longer and complete** — maxOutputTokens raised 250→600. "Always end on a complete sentence" added to Gemini system prompt. MAX_TOKENS trim logic: cuts to last `.`/`!`/`?` boundary; falls through to TinyLlama if no boundary found.
✓ **Gemini quota/cooldown announced** — Scout now says "Gemini says you've reached your daily limit, but I can do my best locally to help" instead of silently falling to TinyLlama. speakUnavailableIfNeeded() returns Boolean: true = spoken (caller returns), false = suppressed (TinyLlama answers). Repeat gap: 6 hours for daily quota, 10 minutes for rate limit. Patrick confirmed via logcat: "E Blocked: cooldown active, 3315s remaining."
✓ **Secondary face recognition** — The second face in a two-person frame is now embedded and matched too. PeopleDb v3 adds a person_embeddings table (up to 5 embeddings per person, threshold 0.80 for secondary crops). VisionAnswerBuilder uses secondaryFaceName — Scout now says "I can see you, Patrick and Elijah" instead of "I can see you, Patrick and someone else."

*(Previous session June 28: TinyLlama re-enabled, Gemini fallback, repeat intent, brain Toast, voice prefs fixed, quota cooldown 1 hour — all DONE)*

---

## June 28, 2026 — What Is New:

✓ **TinyLlama re-enabled with safe delayed load** — `startOfflineBrain()` restored with 90s startup delay, 800MB RAM guard, `nCtx=512`, `nThreads=2`. `tryLoadOfflineBrain()` helper added. On-demand load also fires when Gemini fails. TinyLlama is back as the offline brain. Needs real-world A32 testing to confirm LMKD crash does not return.
✓ **TinyLlama automatic Gemini fallback** — `tryGemini()` now takes `onAnswered` and `onFailed` callbacks. When Gemini times out, 503s, or returns nothing, `onFailed` fires `tryTinyLlamaOrFallback()`. Scout no longer silently fails — TinyLlama picks up the question.
✓ **Gemini timeouts reduced** — `connectTimeout=10s`, `readTimeout=20s`. Faster fallback to TinyLlama. Was causing long SocketTimeoutException hangs.
✓ **"Repeat that" intent** — `isRepeatRequest()` detects "repeat that", "say that again", "what did you say?", "pardon", and similar phrases. Replays last meaningful answer (5+ words) from a 4-minute cache. Works offline without re-running any brain.
✓ **Brain source Toast** — After each answer, a short Toast says "Gemini (online)" or "TinyLlama (offline)". For testing.
✓ **Gemini default fixed** — `isGeminiEnabled()` was defaulting to `false`, so Gemini was always blocked on fresh install even with a saved key. Fixed to `true`.
✓ **Gemini daily quota cooldown reduced** — 6 hours → 1 hour. Faster recovery during dev testing.
✓ **Face greeting fires once per launch** — `greetedThisSession` was resetting to `false` every 5 seconds of face absence. Fixed: reset removed. Scout greets once per boot only.
✓ **STT reliability improved** — `EXTRA_PREFER_OFFLINE=true` avoids Samsung's network STT, 10-second silence window (was shorter), `ERROR_RECOGNIZER_BUSY` (error 8) now waits 600ms before restart.
✓ **Duplicate prompt serves cached Gemini answer** — Was saying "I don't want to ask twice." Now replays the cached reply (4-minute TTL) or lets the duplicate through if no cache.

*(Previous session June 27: Wrong-name teaching fixed, ML Kit label whitelist, finishThinking() fixed — all DONE)*

---

## 1. Who Is Patrick

Patrick Lippy — creator and developer of Scout. NOT a professional programmer. Stroke survivor, dyslexic, blind in right eye, type 1 diabetic.

- Explain everything at screenshot level. Keep messages clear and not visually overwhelming.
- Always provide full paste-ready files, one at a time — or exact CTRL-F / CTRL-R surgical edits. No snippets. No partial files.
- Wife: Diana | Son: Elijah (age 9) | Dog: Nicolas. Names must NEVER be hardcoded.
- Both Claude and ChatGPT are active collaborators. Cross-review welcome.
- Build instructions: Android Studio only — Build → Clean Project, then Build → Assemble Project. Do NOT use gradlew in terminal (JAVA_HOME error on Patrick's machine).

---

## 2. What Scout Is

Scout is a calm family companion robot running on a Samsung Galaxy phone in landscape mode as a permanent face display. Animated eyes, speaks, listens, sees via camera, remembers the family.

- Package: com.example.scoutface | Language: Kotlin + C++ NDK
- Active test device: Samsung Galaxy A32 — current development and testing as of June 29
- Secondary device: Samsung Galaxy Fold 7 (12GB RAM) — listed as primary, needs dedicated stability testing
- App: 7-day free trial, then $9.99 one-time. No automatic charges. No subscriptions. Ever.
- Brains: TinyLlama 1.1B (offline, default — re-enabled June 28 with delayed load) + user's own free Gemini key (online, opt-in, ON by default when key is saved)
- Website: https://patevan9.github.io/lippyrobotics.github.io | Company: Lippy Robotics

---

## 3. Scout's Core Philosophy

Scout should feel: Calm. Thoughtful. Quietly alive. Emotionally subtle. Occasionally curious.
Scout should NOT feel: Excited. Scripted. Fake. Cartoonish. Hyperactive. Constantly praising.

**Stability > Features | Presence > Intelligence | Honest > Fake cheerful | Local-first > Cloud | Predictable > Flashy**

---

## 4. What Is Working Right Now

✓ Animated face (ScoutFaceView) — thinking expression, iris drift, narrowed lids, asymmetric brows
✓ Eye jitter FIXED — boot lock, speaking gate, dead zone, min-delta guard. A32 iris stable.
✓ Eyebrows and mouth brightened to #9BBEFF
✓ Mouth timing FIXED — mouth moves only when audio actually starts (TTS onStart)
✓ Speech recognition (STT) + Text-to-Speech (TTS)
✓ STT reliability improved — offline preference, 10s silence window, busy-error 600ms delay. June 28.
✓ TTS deafness bug fixed — speak() return check + speakingStartedMs timestamp + 45s watchdog. Scout cannot get stuck deaf after idle. June 29.
✓ Voice slider changes stick — scout_prefs used in both SettingsActivity and MainActivity. onResume() reloads pitch/speed. June 29.
✓ Launcher icon fixed — face 68% of canvas, eyes fully inside circular mask. June 29.
✓ Camera — face detection, scene labeling (ML Kit) — throttled to ~7fps for A32 stability
✓ Face recognition COMPLETE and RELIABLE — ArcFace upgrade July 3: InsightFace MobileFaceNet (512-dim, 4.8MB). PeopleDb v4, threshold 0.60f (ArcFace scale). findBestMatch scans named rows only. Self-match bug fixed. lastKnownFaceName updated every 2 seconds.
✓ Secondary face recognition — both faces in a two-person frame embedded and matched. person_embeddings table (PeopleDb v4). Threshold 0.55f for secondary crops. Diana fix July 3 — pendingFaceIntroName now checked in secondary block.
✓ Face recognition accuracy — 3 root-cause bugs fixed July 16: margin check (0.08f gap required between top two candidates), profile pollution gate (CONFIDENT_EMBED_THRESHOLD = 0.72f), rolling window at cap (replaces most-redundant). forgetPerson clears lastFaceEmbedding. New: findBestMatchNameWithScore(), scoreByPerson(). See July 16 section above for action steps.
✓ Family face introduction — "this is my son Elijah" / "this is my wife Diana" registers face. Pending mechanism for two-people-in-frame now works correctly for secondary face.
✓ "Scout, forget [name]" command — clears face embedding and name from both tables. June 29.
✓ Two-person response — "I see Patrick and Elijah" when both faces are known (July 3: "I see X" not "I see you, X").
✓ Personality phrase pools — Phrases.kt (July 3). Varied boot greetings, goodbye, and remember responses. Anti-repeat rolling window.
✓ Adaptive boot greeting — BOOT_OFFLINE_FAST (no warming-up) if TinyLlama loaded fast last session; BOOT_OFFLINE_FAST otherwise. BOOT_ONLINE all mention offline backup warming up. July 3.
✓ Face greeting fires once per launch — greetedThisSession no longer resets every 5s. June 28.
✓ Wrong-name teaching fixed — 2-person frame guard in handleTeaching(). June 27.
✓ ML Kit label whitelist — OBJECT_WHITELIST in VisionAnswerBuilder. Garbage labels gone. June 27.
✓ Greeting words blocked from name storage — hello/hi/hey/howdy/greetings/sup/yo. June 29.
✓ Gemini API — ON by default when key is saved. 'Go online'/'go offline' toggle. maxOutputTokens=600, sentence-complete instruction. Timeouts 10s/20s. June 28–29.
✓ Gemini quota/cooldown announced — Scout speaks the unavailable message; doesn't silently fall to TinyLlama. June 29.
✓ Gemini responses no longer truncated mid-sentence — 600 tokens, MAX_TOKENS boundary trim, "Always end on a complete sentence." June 29.
✓ TinyLlama 1.1B offline brain — RE-ENABLED June 28 with delayed load (90s), 800MB RAM guard, nCtx=512. Automatic Gemini fallback wired. Pending A32 real-world confirmation.
✓ TinyLlama rambling fix — offline replies capped at 2 sentences
✓ "Repeat that" intent — replays last meaningful answer from 4-minute cache. Works offline. June 28.
✓ Brain source Toast — shows "Gemini (online)" or "TinyLlama (offline)" after each answer. June 28.
✓ Duplicate prompt now serves cached Gemini reply instead of refusing. June 28.
✓ Self-echo guard — Scout ignores hearing his own TTS voice through the mic
✓ Settings screen — swipe-right to open, API key entry, offline toggle, voice/TTS sliders, About Scout
✓ Hardcoded API key removed — Gemini key now in secure encrypted SharedPreferences
✓ Memory layers: TruthDb, HabitLayer, PeopleDb (with embeddings + person_embeddings), JournalDb, ConversationDb
✓ Intent router — weather, time, greetings, family facts, downloads, IDENTITY, RECALL_FACT
✓ Flexible teaching — 'my favorite color is teal' → stored permanently
✓ Flexible recall — recalls facts reliably after other questions
✓ Wake word filter — Scout only responds when he hears his name
✓ Conversation window — 30 seconds open conversation after Scout responds
✓ Boot window — Scout ready immediately after boot, no name needed
✓ Online / disconnect phrases recognized
✓ Weather via NWS (api.weather.gov) — precipitation %, offline-aware, free for commercial use
✓ Total offline mode — 'go offline' blocks ALL internet features
✓ A32 STABLE — no crashes as of June 21. Camera throttle eliminated delayed LMKD kill.
✓ finishThinking() actually clears thinking state — was empty no-op causing stuck-thinking. June 27.
✓ lastKnownFaceName set immediately on teaching. June 27.

---

## 5. Known Issues — Do Not Touch Without Discussion

⚠ **16KB page size — REOPENED July 18, root cause refined** — 11 native libraries fail Android's own alignment check on Patrick's real Fold 7, but a same-day follow-up check found 5 of the 6 llama.cpp/ggml libraries are actually already ELF-aligned — the real suspect is APK packaging on debug installs, not source rebuilds. See the "Later Same Day" entry at the top of this file. Play Store submission is still blocked on this pending a release-build test.
✓ **LiteRT import fix** — `FaceEmbedder.kt` uses `org.tensorflow.lite.Interpreter` (correct internal package for litert:2.1.5). Build confirmed. July 17.
✓ **"Favorite favorite" bug fixed** — TeachExtractor double-prefix eliminated. New facts stored correctly as `"favorite_color"` not `"favorite_favorite_color"`. keyToHuman() collapses old keys for readback. DB migration cleans up existing bad entries on next launch. July 17.
✓ **Battery optimization prompt** — Fires 8 seconds after first boot, takes user to system setting to exclude Scout from battery optimization. One-time only. July 17.
✓ **Thinking watchdog** — 120-second timeout clears stuck `isThinking` state if TinyLlama hangs. Scout cannot stay frozen with eyes moving and no speech. July 17.
✓ **People DB in brain export** — "Scout, export your brain" now includes named faces (face_hash, name, first_met, last_seen) and per-name embedding counts. July 17.
✓ **TruthDb `deleteFact()` + `deleteFactsWithKeyLike()`** — New delete methods for targeted fact removal. July 17.
✓ **Diagnostic reporting system** — DiagReportActivity with View and Share modes, Privacy Notice, System Info, Event Log, Crash Log. July 13.
✓ **Settings DIAGNOSTICS section** — View Report, Share Report, Clear Diagnostic History. Support button opens browser. Reset Memory Layers red styling. July 13.
✓ **TinyLlama confirmed working on A32 and Fold 7** — Confirmed July 7. bootstrapModelFile() auto-copies model from external storage. Both devices tested with Online Features OFF.
⚠ **Gemini daily quota** — 1-hour cooldown after daily limit hit. Scout now announces it instead of going silent. Test Gemini the next day by watching for "Gemini (online)" Toast.
⚠ **Secondary face bootstrap** — The first time Patrick and Elijah are in frame together after a pull, Elijah may still show as "someone else." Once Elijah faces Scout alone once (so his embedding is added to person_embeddings), subsequent two-person scenes should name him correctly.

- STT name recognition — 'Scout' sometimes misheard. Partially handled by wake word filter.
- Live news — future feature.
- Barge-in — deliberately disabled. PARKED.
- ScoutFaceView dead code — 2 lines. Harmless for now.

---

## 6. Current Priority — Launch Checklist Order

1. **✓ TinyLlama re-enable path DONE June 28** — 90s delay, 800MB RAM check, nCtx=512.
2. **✓ Startup diagnostics DONE July 4** — TTS Toast + STT spoken warning at boot.
3. **✓ Onboarding flow DONE July 4** — 5-screen OnboardingActivity.kt + first-boot redirect + offline default.
4. **Fold 7 stability testing** — dedicated session needed on Fold 7.
5. **✓ Privacy Policy** — DONE July 11. In-app dialog (Settings → About Scout). Offline, no website needed.
   **✓ Terms of Use** — DONE July 10–11. In-app dialog + terms.html for website.
   **Open Source Credits** — Still needed. THIRD_PARTY_NOTICES.md started (MobileFaceNet done). Full in-app screen + website page required at launch.
6. **Play Store listing** — description, screenshots, content rating.
7. **⚠ 16KB page size — REOPENED July 18, root cause refined, still the top blocker** — Real Fold 7 testing showed 11 native libraries failing Android's own alignment check, contradicting the July 7/10/17 "done" claims. A same-day follow-up found 5 of the 6 llama.cpp/ggml libraries are already ELF-aligned (verified with `readelf` against the actual files in the repo) — the real suspect is now APK packaging on debug installs, not a source rebuild. Needs a dedicated session: build a release APK, test on Fold 7, `zipalign -c` the result, and check `libscout_llama.so`'s own alignment fresh — see the "Later Same Day" entry above. `libimage_processing_util_jni.so` is the one confirmed real ELF defect and needs an ML Kit version bump.
8. **Play Asset Delivery wiring** — ModelDownloadActivity is ready; PAD integration to trigger it is a future session.

After launch — Update 1.1 (Scout 1.1 — Growing Up) and beyond:
- **Scout Behavior Learning — two tiers** — Tier 1 (Scout 1.1): "Scout can learn small preferences with your approval." Family sees first-person suggestions + Approve/Not now/Never suggest this. No technical language. Tier 2 (Scout Dev build, 1.5+): NOT in the Play Store APK — absent, not hidden. Android build variants (`standard` vs `dev`, `BuildConfig.DEVELOPER_MODE`). Scout Dev is a telemetry/observation dashboard: "I've had 14 failed face recognitions today." / "Wake-word detection dropped after yesterday's update." Scout surfaces the data; Patrick and Claude decide the fix. TelemetryDb + TelemetryCollector. (Master Summary §16)
- Permanent vs temporary memory sorting
- Caring follow-up loop
- Full mood system wired in
- Spanish language support — Phase 1
- Response cleanup layer (post-TinyLlama filter)
- Brain Pack upgrades (Phi-2, Llama 3.2, Phi-4, Llama 3.1 8B)
- Robot renaming in Settings
- "Test Connection" button — verify API key without burning quota
- Public roadmap / What's New page on website
- Support Scout screen (Google Play Billing, 4 tiers: $3/$5/$10/$25, product IDs support_3/5/10/25, consumable)

---

## 7. Versioning Quick Reference

- Scout 1.0 — The Beginning (launch)
- Scout 1.0.1 — bug fixes only
- Scout 1.1 — Growing Up (first feature update)
- Scout 2.0 — A New Chapter (major milestone)
- After each update: Welcome Back screen + optional spoken message + Google Play release notes

---

## 8. Working Rules — Always Apply

- Full paste-ready files only, one at a time. No snippets. No partial files.
- Surgical CTRL-F / CTRL-R edits — always specify which file tab to click first.
- Build: Android Studio only → Build → Clean Project → Build → Assemble Project.
- Some Scout files have NO indentation. If search fails, try shorter unique string.
- Some logic lives in TWO places — change both or Scout flickers.
- One safe change at a time. Build and test before the next change.
- Never touch speech, camera, or download systems without explicit discussion.
- Never touch ScoutFaceView casually — it is Scout's visual heart.
- Patrick is not a professional programmer — screenshot-level explanations always.

---

*Project Scout Quick Start | Last updated: July 18, 2026 | Version 25 | Upload every session | For full details use Master Summary v49*
