# Project Scout — Master Project Summary
**Last updated: July 5, 2026 | Version 41**

Upload this document at the start of every new Claude or ChatGPT conversation about Scout.
This is the single source of truth.

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
| Website | lippy-robotics.gt.tc |
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

Support Scout screen designed and ready. Message: 'You're not just supporting an app — you're supporting a companion.'

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
✓ TinyLlama 1.1B offline brain — RE-ENABLED June 28 with delayed load (90s), 800MB RAM guard, nCtx=512, nThreads=2. Automatic Gemini fallback via onFailed callback. On-demand load fires when Gemini fails and TinyLlama not yet loaded.
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
✓ Thinking-state expression — drift, narrowed lids, asymmetric brows
✓ TinyLlama rambling fix — offline replies capped at 2 sentences (limitToSentences)
✓ Self-echo guard — Scout ignores his own TTS voice bleeding back into mic
✓ MainActivity.kt blank line cleanup — complete
✓ Naming phrases expanded — "this is X", "I am X", "you see X" recognized as name-teaching phrases
✓ Three A32 stability fixes — camera bitmap recycle, ML Kit suppression during Gemini, speak() race condition closed. June 19–20.
✓ A32 crash resolved — camera frame throttle (150ms) eliminates delayed LMKD kill after Gemini responses. Confirmed stable June 21.

### Pending — Launch Blockers:

✓ **Startup diagnostics** — DONE July 4. TTS failure Toast + STT unavailability spoken warning at boot.
✓ **Onboarding flow** — DONE July 4. OnboardingActivity.kt, 5 screens, first-boot redirect in MainActivity.
■ **Fold 7 dedicated stability testing** — testing has been on A32. Fold 7 needs its own validation session.
■ **16KB page size warning** — ML Kit + TensorFlow Lite native libraries need version updates before Play Store submission (`mlkit:face-detection:16.1.6`, `mlkit:image-labeling:17.0.7`, `tensorflow-lite:2.14.0`). Address before submission.
■ **Play Asset Delivery (PAD) wiring** — ModelDownloadActivity is built and ready. Wiring PAD to trigger the download screen and call updateProgress() is a future session.

- Privacy Policy, Terms of Use, Open Source Credits — write and add to app + website.
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
| Secondary face bootstrap | First time two people are in frame after a fresh pull, Elijah may show as "someone else" — person_embeddings table starts empty. Once Elijah faces Scout alone once, his embedding populates the table and two-person recognition works. |
| A32 active test device | Patrick confirmed June 28: testing is on A32. Fold 7 listed as primary but needs a dedicated session. |
| TinyLlama slow on A32 | 20-40s per answer. Expected. Hardware limitation. Gemini is fast path when online. |
| Barge-in | Deliberately disabled. Runaway loop. Status: PARKED. |
| STT name recognition | 'Scout' misheard as 'Gal', 'Scott', 'Out'. Partially handled by wake word filter. |
| Live news | Neither brain reads live news. Future news feed needed. |
| ScoutFaceView dead code | Line 1023: doubled condition. Line 709: unused browAsym. Harmless but messy. |
| 16KB page size | ML Kit + TensorFlow Lite native libraries require version updates before Play Store submission. Address before submission. |

---

## 7d. Session Log

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
| MainActivity.kt | Main app — all logic. Hardcoded API key REMOVED June 18. Wake word filter in onResults(). Self-echo guard (lastScoutUtteranceNormalized). limitToSentences() for rambling fix. handleTeaching() wires name to PeopleDb. isSpeaking set immediately in speak() — race condition fix June 20. tryLoadOfflineBrain() added June 28 (delayed + on-demand TinyLlama load). isRepeatRequest() + lastMeaningfulResponse cache June 28. tryTinyLlamaOrFallback() extracted June 28. pendingBrainSource + brain Toast June 28. greetedThisSession reset removed June 28. STT EXTRA_PREFER_OFFLINE + silence/busy fixes June 28. TTS deafness fix June 29 (speak() return check, speakingStartedMs, 45s watchdog). scoutPrefs reads from scout_prefs June 29 (voice pitch/speed in onInit + onResume). blockedNames includes greeting words June 29. lastSecondaryFaceName + secondFace + capturedSecondBox + secondary embed block June 29. isInCooldown() check + speakUnavailableIfNeeded() call at top of tryTinyLlamaOrFallback() June 29. |
| ScoutFaceView.kt | Custom face canvas — all visual animation. Thinking expression updated June 8. Eye jitter fixed June 18 (boot lock, speaking gate, dead zone, min-delta). Eyebrows/mouth #9BBEFF June 18. |
| SettingsActivity.kt | NEW June 18 — 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Gemini key entry, offline toggle, pitch/speed sliders. Opened via swipe-right + voice command + first-boot hint. |
| ScoutIntentRouter.kt | Intent routing — IDENTITY + RECALL_FACT added. Online/disconnect phrases. |
| TeachExtractor.kt | Extracts facts from speech — FLEXIBLE. Updated June 15 with "this is X", "I am X", "you see X" name patterns + NON_NAME_WORDS stoplist. |
| FactKey.kt | Fact labels — fixed keys kept + FactKey.custom() for any new label. |
| TruthDb.kt | SQLite fact store — fully flexible. No changes needed. |
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
| PeopleDb.kt | People memory — getName(), setName(), isKnown(). BLOB embedding column added June 17. Cosine similarity matching. findBestMatch scans named rows only (June 21). DB version 4 July 3: migration clears 192-dim embeddings (preserves names/hashes). person_embeddings table (addNamedEmbedding(), findBestMatchName(), forgetPerson()). Up to 12 embeddings per person. Threshold 0.65f (raised back July 4 — 0.60f caused Diana/Elijah cross-contamination). cursor.use{} in findBestMatch and findBestMatchName (July 4). forgetPerson atomic with transactions (July 4). addNamedEmbedding COUNT(*) guard — skips INSERT if already at max 12 (July 4). Secondary crop threshold 0.55f. |
| VisionAnswerBuilder.kt | Builds spoken vision responses. OBJECT_WHITELIST filters noisy ML Kit labels (June 27). Wired to PeopleDb. Uses lastKnownFaceName for reliable name reporting. faceCount==2 uses both knownFaceName and secondaryFaceName. "I see X" phrasing (not "I see you, X") as of July 3. July 4: freshness 3500ms→1800ms; 3+ faces branch gets dogLine; 2-face branch: secondaryFaceName arm precedes pendingIntroName arm, new else arm for unknown primary + known secondary. |
| FaceEmbedder.kt | Created June 15. Wired into camera pipeline June 17. ArcFace upgrade July 3: loads InsightFace MobileFaceNet.tflite (512-dim), EMBEDDING_SIZE=512, single-batch output Array(1){FloatArray(512)}, single-pass buffer fill. Preprocessing unchanged: (px - 127.5f) / 128f. Returns L2-normalized 512-dim embedding. |
| MobileFaceNet.tflite | Bundled in app/src/main/assets/. InsightFace MobileFaceNet trained with ArcFace loss (July 3). 4.8MB. Input: 112x112 RGB, normalized. Output: 512-dim embedding. Replaces original 192-dim model. |
| Phrases.kt | NEW July 3. Personality phrase pools with anti-repeat rolling window (cooldown = pool.size / 2). pick(key, pool) returns a non-repeating random phrase. pickNamed(key, pool, name) substitutes {name} placeholder. Pools: BOOT_ONLINE, BOOT_OFFLINE_FAST, BOOT_OFFLINE, BOOT_NO_INTERNET, BOOT_NO_KEY, REMEMBER, REMEMBER_NAME, REMEMBER_MY_NAME, REMEMBER_WIFE, REMEMBER_SON, REMEMBER_DOG, GOODBYE. BOOT_NO_KEY phrases replaced July 4 — now tell user to slide right to open settings. |
| OnboardingActivity.kt | NEW July 4. 5-screen onboarding flow: Welcome / Trial / This Is Just The Beginning / Privacy / Ready To Begin. currentPage drives both dots and "X / 5" counter (single source of truth). Scout icon visible screens 1 and 5 only. finishOnboarding() sets PREF_ONBOARDING_DONE=true (scout_prefs) and gemini_enabled=false (scout_memory). |
| ModelDownloadActivity.kt | NEW July 4. Portrait loading screen for TinyLlama model download. 39 humorous messages shuffled and cycled with ObjectAnimator slide-right-in / slide-left-out animation. updateProgress(percent, downloaded, total, timeLeft) for PAD wiring. Layout: activity_model_download.xml. |
| CLAUDE.md | NEW July 4. Repo-root session notes for all future Claude instances: full git pull/push commands (branch name), critical hardcoding rules, architecture quick ref, test devices, master doc list. |
| brain/ScoutBootStatus.kt | REWRITTEN July 3. Uses Phrases pools for all boot greetings. Adaptive offline boot: BOOT_OFFLINE_FAST (skips warming-up) when lastLlamaLoadMs < 2s, BOOT_OFFLINE otherwise. Takes lastLlamaLoadMs: () -> Long lambda (default Long.MAX_VALUE). |
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
| 17 | Privacy Policy | Not started |
| 18 | Terms of Use | Not started |
| 19 | Open Source Credits — THIRD_PARTY_NOTICES.md started | In progress |
| 20 | Weather API licensing | ✓ RESOLVED June 16 — switched to NWS, free for commercial use |
| 21 | Play Store listing — description, screenshots, rating | Not started |
| 22 | 16KB page size — ML Kit + TF Lite library updates | Not started — required before submission |

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
- Current address: lippy-robotics.gt.tc
- Future domain options: lippyrobotics.com, scoutcompanion.com, meetscout.ai
- List website on: Facebook page, Google Play listing, About Scout screen, website footer.
- Add a 'What's New' or 'Scout Development Updates' page.

**Required for Launch:**

| Document | Priority | Notes |
|----------|----------|-------|
| Privacy Policy | 1 — Before launch | What data Scout collects. What stays on device. Gemini is optional. Contact info. |
| Terms of Use | 2 — Near launch | Scout is as-is. No guarantees. Not medical/legal/financial advice. Keep it simple. |
| Open Source Credits | 3 — At launch | llama.cpp, TinyLlama, MobileFaceNet (MIT, done in THIRD_PARTY_NOTICES.md), Android libraries. |
| Website link | 1 — Before launch | lippy-robotics.gt.tc in About Scout, Google Play listing, Facebook page. |

**Inside the App — About Scout must contain:**
- Version number
- Privacy Policy link
- Terms of Use link
- Open Source Licenses link
- Website: lippy-robotics.gt.tc
- Update History — every major version and what changed
- Contact / Get in Touch — opens email to lippyroboticslabs@gmail.com

Support email: lippyroboticslabs@gmail.com. Auto-responder confirmed — sets expectations on response time, asks for device model, Android version, and description for technical issues.

**Weather API — RESOLVED June 16:**
Open-Meteo was replaced with NWS (api.weather.gov). Completely free for commercial use, no API key, no licensing concern. U.S. locations only. Open-Meteo attribution no longer required.

---

*Project Scout Master Summary | Last updated: July 5, 2026 | Version 41 | Single source of truth — upload every session*
