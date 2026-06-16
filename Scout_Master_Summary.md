# Project Scout — Master Project Summary
**Last updated: June 16, 2026 | Version 32**

Upload this document at the start of every new Claude or ChatGPT conversation about Scout.
This is the single source of truth.

---

## June 15–16, 2026 — What Changed Since Version 31

✓ **TinyLlama rambling fix** — `limitToSentences()` added to MainActivity.kt. Offline replies capped at 2 sentences before TTS. Eliminates garbled continuations like 'I see a cool in an ear.'
✓ **Self-echo guard** — `lastScoutUtteranceNormalized` field added. `onResults()` now checks if mic picked up Scout's own TTS voice and ignores it. Eliminates Scout answering himself.
✓ **MainActivity.kt blank line cleanup** — excessive blank lines removed file-wide (except the TinyLlama system prompt raw string which is intentionally preserved).
✓ **Face recognition Step 1** — MobileFaceNet.tflite (MIT licensed, 5,233,396 bytes) bundled in `app/src/main/assets/`. TensorFlow Lite dependency (`org.tensorflow:tensorflow-lite:2.14.0`) added to build.gradle.kts. `noCompress += "tflite"` added so the model loads correctly. `FaceEmbedder.kt` created: takes a cropped face Bitmap, runs 112x112 / normalize / inference / L2-normalize, returns 192-dim FloatArray. NOT yet wired into camera pipeline. App behavior unchanged.
✓ **Naming phrases expanded** — TeachExtractor.kt updated. "this is X", "I am X" (covers "I'm X" after normalization), "you see X" now recognized as FactKey.NAME teaching phrases alongside existing "my name is X". NON_NAME_WORDS stoplist guards against false positives like "I am tired" or "this is great."
✓ **Weather switched to NWS** — ScoutWeatherManager.kt fully rewritten to use api.weather.gov. 100% free for commercial use, no API key required, no licensing issue. Two-step flow: /points to resolve gridpoint URL (cached), then /forecast for periods. All five query types preserved (current, tonight, tomorrow, specific day, week). U.S. locations only.
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

**Baseline Brain:** TinyLlama 1.1B Chat Q4_K_M (~669 MB) — default, offline, always included.

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
✓ Speech recognition (Android STT) + Text to Speech (TTS)
✓ Camera — face detection (ML Kit), scene labeling
✓ Gemini API — OFF by default, activated by 'go online' voice command
✓ Memory layers: TruthDb, ConversationDb, HabitLayer, PeopleDb, JournalDb
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
✓ OFFLINE BRAIN COMPLETE — TinyLlama 1.1B on BOTH A32 and Fold 7
✓ Identity questions hardcoded — routing expanded
✓ Total offline mode — 'go offline' blocks ALL internet features
✓ Thinking-state expression — drift, narrowed lids, asymmetric brows
✓ TinyLlama rambling fix — offline replies capped at 2 sentences (limitToSentences)
✓ Self-echo guard — Scout ignores his own TTS voice bleeding back into mic
✓ MainActivity.kt blank line cleanup — complete
✓ Face recognition Step 1 — MobileFaceNet.tflite bundled, FaceEmbedder.kt created (not yet wired)
✓ Naming phrases expanded — "this is X", "I am X", "you see X" recognized as name-teaching phrases

### Pending — Launch Blockers:

■ **Face recognition Steps 2–4 — IN PROGRESS.**
  - Step 2: Wire FaceEmbedder.kt into camera pipeline — crop face from ML Kit bounding box, run getEmbedding().
  - Step 3: Update PeopleDb schema to store embeddings as BLOB, implement similarity-based matching (replace position-hash with embedding distance).
  - Step 4: Rewire "this is X" / "my name is X" naming/teach flow to use embedding-based identity.
  - Do NOT mark complete until known-person identification works reliably frame-to-frame.

■ **Remove hardcoded Gemini API key** — MainActivity.kt. Doing with Settings screen.
  - Basic Settings screen — API key entry, offline toggle, robot name display minimum.

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
| Face recognition Steps 2–4 | Step 1 done. Steps 2–4 still needed: wire into camera, update PeopleDb schema, rewire naming flow. |
| Hardcoded API key | Patrick's personal Gemini key in MainActivity.kt. Removing in Settings session. |
| Fold 7 not tested | All testing on A32 via WiFi. Fold 7 needs dedicated stability session. |
| TinyLlama slow on A32 | 20-40s per answer. Expected. Hardware limitation. Gemini is fast path when online. |
| Iris jitter (A32 idle) | Hardware timing issue. NOT present on Fold 7. |
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
- June 16: Weather switched from Open-Meteo to NWS (api.weather.gov) — free for commercial use, no API key, U.S. only. ScoutWeatherManager.kt fully rewritten. THIRD_PARTY_NOTICES.md created. Quick Start, Launch Checklist, and Master Summary updated to v10/v4/v32.

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
| MainActivity.kt | Main app — all logic. API key — REMOVE BEFORE LAUNCH. Wake word filter in onResults(). Self-echo guard (lastScoutUtteranceNormalized). limitToSentences() for rambling fix. handleTeaching() wires name to PeopleDb. |
| ScoutFaceView.kt | Custom face canvas — all visual animation. Thinking expression updated June 8. |
| ScoutIntentRouter.kt | Intent routing — IDENTITY + RECALL_FACT added. Online/disconnect phrases. |
| TeachExtractor.kt | Extracts facts from speech — FLEXIBLE. Updated June 15 with "this is X", "I am X", "you see X" name patterns + NON_NAME_WORDS stoplist. |
| FactKey.kt | Fact labels — fixed keys kept + FactKey.custom() for any new label. |
| TruthDb.kt | SQLite fact store — fully flexible. No changes needed. |
| ApiKeySetupActivity.kt | API key wizard — built by Claude. Needs wiring to secure storage. |
| GeminiClient.kt | Gemini HTTP wrapper with cooldown discipline. |
| ScoutPromptBuilder.kt | Builds Gemini system instruction. |
| ScoutGeminiManager.kt | Gemini orchestration. |
| ScoutWeatherManager.kt | Live weather via NWS (api.weather.gov) — UPDATED June 16. Free for commercial use. Precip %, offline-aware. U.S. only. |
| ScoutPresenceDecider.kt | Social timing layer. |
| LlamaEngine.kt | Offline brain JNI wrapper — WORKING. |
| OfflinePromptBuilder.kt | TinyLlama prompt formatter. |
| scout_llama_jni.cpp | C++ JNI bridge — compiled into libscout_llama.so. |
| scout_llama_api.h | Self-contained b8946 declarations. |
| CMakeLists.txt | NDK build config. |
| HabitLayer.kt | Pattern memory — 14-day decay. |
| PeopleDb.kt | People memory — getName(), setName(), isKnown(). Will need BLOB embedding column in Step 3 of face recognition. |
| VisionAnswerBuilder.kt | Builds spoken vision responses. Filters noisy ML Kit labels. Wired to PeopleDb. |
| FaceEmbedder.kt | NEW June 15 — loads MobileFaceNet.tflite, returns 192-dim L2-normalized face embedding. NOT YET WIRED INTO CAMERA. |
| MobileFaceNet.tflite | NEW June 15 — bundled in app/src/main/assets/. MIT licensed. 5.2MB. Input: 112x112 RGB, normalized. Output: 192-dim embedding. |
| THIRD_PARTY_NOTICES.md | NEW June 15 — MIT attribution for MobileFaceNet. Start of Open Source Credits. |

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
| 8 | Face recognition Steps 2–4 (wiring) | IN PROGRESS — Step 1 done, Steps 2–4 remain |
| 9 | Remove hardcoded Gemini API key + Basic Settings screen | Not started |
| 10 | Startup diagnostics | Not started |
| 11 | Onboarding flow — build 5 screens in Android | Screen 1 text finalized |
| 12 | Fold 7 stability testing | Not started |
| 13 | A32 stability testing | Ongoing |
| 14 | Privacy Policy | Not started |
| 15 | Terms of Use | Not started |
| 16 | Open Source Credits — THIRD_PARTY_NOTICES.md started | In progress |
| 17 | Weather API licensing | ✓ RESOLVED June 16 — switched to NWS, free for commercial use |
| 18 | Play Store listing — description, screenshots, rating | Not started |

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

*Project Scout Master Summary | Last updated: June 16, 2026 | Version 32 | Single source of truth — upload every session*
