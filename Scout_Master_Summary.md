# Project Scout — Master Project Summary
**Last updated: June 21, 2026 | Version 34**

Upload this document at the start of every new Claude or ChatGPT conversation about Scout.
This is the single source of truth.

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
✓ **TinyLlama startup disabled on A32** — Startup load caused LMKD to kill Scout under memory pressure. Disabled as emergency stabilization. Gemini is primary brain. Re-enable path TBD. DONE June 19.
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
- Nicolas: The family dog. The Nicolas Protocol: Scout stops immediately when the dog is detected.

**Names must NEVER be hardcoded in Scout's code. Always use variables.**

AI Collaborators: Patrick works with both Claude and ChatGPT as project partners. Cross-review between the two is welcome and encouraged. Grok was tried and discontinued.

---

## 2. What Scout Is

Scout is a calm family companion robot running on a Samsung Galaxy phone mounted in landscape mode as a permanent face display. Scout has animated eyes, speaks, listens, sees via camera, and remembers the family.

| Item | Detail |
|------|--------|
| Package | com.example.scoutface |
| Language | Kotlin + C++ NDK |
| Primary device | Samsung Galaxy A32 — stress-test device, all testing via WiFi |
| Dev device | Samsung Galaxy Fold 7 (wireless via WiFi from Android Studio) |
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

**Baseline Brain:** TinyLlama 1.1B Chat Q4_K_M (~669 MB) — default, offline, always included. (Temporarily disabled on A32 for stability — re-enable path TBD.)

**Optional Gemini:** Users add their own free Gemini key in Settings. OFF by default. Scout NEVER ships with a bundled key.

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
- Nicolas Protocol: dog detection → immediate stop.
- Guest Mode: unknown face → 'Hello, I am Scout. What is your name?'
- Flexible Memory: Scout stores and recalls ANY fact.

---

## 7. Current Technical State

### Working:

✓ Animated face (ScoutFaceView) — mouth, iris drift, thinking expression
✓ Eye jitter FIXED — boot lock (3500ms), speaking gate, dead zone, min-delta guard. A32 stable. June 18.
✓ Eyebrows and mouth brightened to #9BBEFF. June 18.
✓ Speech recognition (Android STT) + Text to Speech (TTS)
✓ Camera — face detection (ML Kit), scene labeling — throttled to ~7fps June 21 (memory pressure fix)
✓ Face recognition COMPLETE and RELIABLE — embedding pipeline wired into camera, PeopleDb stores BLOB embeddings with cosine similarity (threshold 0.75), `findBestMatch` scans only named rows, embedExecutor runs findBestMatch BEFORE storeEmbedding (self-match bug fixed June 21). Known face recognized consistently. Unknown face → Guest Mode. Nicolas Protocol active.
✓ Multi-person face introduction — "this is my son Elijah" / "this is my wife Diana" registers family member faces in PeopleDb. Pending face mechanism handles two-person-in-frame introductions. June 21.
✓ VisionAnswerBuilder two-person response — "I can see [Patrick] and one other person." when primary face known. June 21.
✓ Gemini API — OFF by default, activated by 'go online' voice command. maxOutputTokens raised to 250 June 21.
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
✓ OFFLINE BRAIN — TinyLlama 1.1B on-device (temporarily disabled on A32 — LMKD crash prevention)
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

■ **TinyLlama re-enable path on A32 — URGENT.**
  - Disabled at startup to prevent LMKD kill under memory pressure.
  - Options: delayed load after boot settles, on-demand load when needed, memory footprint reduction.
  - TinyLlama is a core feature — offline families without a Gemini key need it.

■ **Remove hardcoded Gemini API key** — DONE June 18. SettingsActivity built and wired.

- Startup diagnostics — friendly message if brain, TTS, or STT missing at startup.
- Fold 7 stability testing — all testing on A32. Fold 7 needs dedicated session.
- Onboarding flow — 5 screens designed, need building in Android.
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

## 7c. Known Issues — Do Not Touch Without Discussion

| Issue | Notes |
|-------|-------|
| TinyLlama disabled on A32 | Startup load causes LMKD kill under memory pressure. Disabled June 19. Re-enable path needed before launch. |
| A32 crashes | **RESOLVED June 21** — camera frame throttle (150ms) eliminated the delayed LMKD kill. Patrick confirmed stable. |
| Elijah/Diana face registration | Requires solo face moment after introduction to capture embedding via pending mechanism. Works once triggered. |
| Fold 7 not tested | All testing on A32 via WiFi. Fold 7 needs dedicated stability session. |
| TinyLlama slow on A32 | 20-40s per answer. Expected. Hardware limitation. Gemini is fast path when online. |
| Barge-in | Deliberately disabled. Runaway loop. Status: PARKED. |
| STT name recognition | 'Scout' misheard as 'Gal', 'Scott', 'Out'. Partially handled by wake word filter. |
| Live news | Neither brain reads live news. Future news feed needed. |
| ScoutFaceView dead code | Line 1023: doubled condition. Line 709: unused browAsym. Harmless but messy. |

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
| MainActivity.kt | Main app — all logic. Hardcoded API key REMOVED June 18. Wake word filter in onResults(). Self-echo guard (lastScoutUtteranceNormalized). limitToSentences() for rambling fix. handleTeaching() wires name to PeopleDb. isSpeaking set immediately in speak() — race condition fix June 20. |
| ScoutFaceView.kt | Custom face canvas — all visual animation. Thinking expression updated June 8. Eye jitter fixed June 18 (boot lock, speaking gate, dead zone, min-delta). Eyebrows/mouth #9BBEFF June 18. |
| SettingsActivity.kt | NEW June 18 — 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Gemini key entry, offline toggle, pitch/speed sliders. Opened via swipe-right + voice command + first-boot hint. |
| ScoutIntentRouter.kt | Intent routing — IDENTITY + RECALL_FACT added. Online/disconnect phrases. |
| TeachExtractor.kt | Extracts facts from speech — FLEXIBLE. Updated June 15 with "this is X", "I am X", "you see X" name patterns + NON_NAME_WORDS stoplist. |
| FactKey.kt | Fact labels — fixed keys kept + FactKey.custom() for any new label. |
| TruthDb.kt | SQLite fact store — fully flexible. No changes needed. |
| ApiKeySetupActivity.kt | API key wizard — wired to secure storage June 17. |
| GeminiClient.kt | Gemini HTTP wrapper with cooldown discipline. 30s timeout. maxOutputTokens=250 (raised June 21). Single-flight guard. Daily quota detection. |
| ScoutPromptBuilder.kt | Builds Gemini system instruction and unavailable messages. |
| ScoutGeminiManager.kt | Gemini orchestration. Calls respond() on success. Catches OOM errors. |
| ScoutWeatherManager.kt | Live weather via NWS (api.weather.gov) — UPDATED June 16. Free for commercial use. Precip %, offline-aware. U.S. only. |
| ScoutPresenceDecider.kt | Social timing layer. |
| LlamaEngine.kt | Offline brain JNI wrapper — WORKING. Startup load disabled on A32 June 19 (LMKD prevention). |
| OfflinePromptBuilder.kt | TinyLlama prompt formatter. |
| scout_llama_jni.cpp | C++ JNI bridge — compiled into libscout_llama.so. |
| scout_llama_api.h | Self-contained b8946 declarations. |
| CMakeLists.txt | NDK build config. |
| HabitLayer.kt | Pattern memory — 14-day decay. |
| PeopleDb.kt | People memory — getName(), setName(), isKnown(). BLOB embedding column added June 17. Cosine similarity matching. findBestMatch scans named rows only (June 21). Threshold 0.75 (raised June 21). |
| VisionAnswerBuilder.kt | Builds spoken vision responses. Filters noisy ML Kit labels. Wired to PeopleDb. Uses lastKnownFaceName for reliable name reporting. faceCount==2 names primary face (June 21). |
| FaceEmbedder.kt | Created June 15. Wired into camera pipeline June 17. Loads MobileFaceNet.tflite, returns 192-dim L2-normalized face embedding. |
| MobileFaceNet.tflite | Bundled in app/src/main/assets/. MIT licensed. 5.2MB. Input: 112x112 RGB, normalized. Output: 192-dim embedding. |
| THIRD_PARTY_NOTICES.md | MIT attribution for MobileFaceNet. Start of Open Source Credits. |

---

## 10. Scout Animation Goal & Mood System

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

## 15. Future Vision

Scout notices patterns in user behavior, generates a suggestion for a new behavior, and asks permission before activating it. Nothing changes without explicit consent. Scout cannot write compiled Kotlin — but CAN generate behavioral scripts, response patterns, routing rules, and habit triggers within the existing safe framework.

**Future Polish Ideas (Post-Launch, Scout 2.0+):**

- Voice Recognition (Future) — Optional voice enrollment for family members. Advisory only — does not replace TruthDb or user-confirmed identity. Not for launch or 1.1.
- Fun startup/loading messages — Rotating, self-aware, Scout-voiced lines for the first-launch brain download screen.

---

## 16. Language Support — Planned

**Phase 1 — Early Spanish Support (No new brain model needed)**

- Add a Language setting: English / Español.
- Android STT switches to Spanish when selected.
- Android TTS speaks in Spanish when selected.
- All hardcoded responses translated.

**Phase 2 — Full Spanish Support (Long-Term Future)**

- Evaluate Spanish-capable offline brain models when brain pack infrastructure is mature.
- Priority: Not now. Current roadmap comes first.

---

## 17. Play Store Launch Checklist

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
| 12 | TinyLlama re-enable on A32 | IN PROGRESS — disabled June 19, re-enable path TBD |
| 13 | Startup diagnostics | Not started |
| 14 | Onboarding flow — build 5 screens in Android | Screen 1 text finalized |
| 15 | Fold 7 stability testing | Not started |
| 16 | A32 stability testing | Ongoing — no crashes as of June 21 |
| 17 | Privacy Policy | Not started |
| 18 | Terms of Use | Not started |
| 19 | Open Source Credits — THIRD_PARTY_NOTICES.md started | In progress |
| 20 | Weather API licensing | ✓ RESOLVED June 16 — switched to NWS, free for commercial use |
| 21 | Play Store listing — description, screenshots, rating | Not started |

---

## 18. Onboarding Flow — 5 Screens (Designed, Not Yet Built)

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

## 19. Versioning System

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

## 20. Legal & Website

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

*Project Scout Master Summary | Last updated: June 21, 2026 | Version 34 | Single source of truth — upload every session*
