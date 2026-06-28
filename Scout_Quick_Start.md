# Project Scout — Quick Start
**Last updated: June 28, 2026 | Version 14**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
For full technical details, use the Scout Master Summary (v36).

---

## June 28, 2026 — What Is New:

✓ **TinyLlama re-enabled with safe delayed load** — `startOfflineBrain()` restored with 90s startup delay, 800MB RAM guard, `nCtx=512`, `nThreads=2`. `tryLoadOfflineBrain()` helper added. On-demand load also fires when Gemini fails. TinyLlama is back as the offline brain. Needs real-world A32 testing to confirm LMKD crash does not return.
✓ **TinyLlama automatic Gemini fallback** — `tryGemini()` now takes `onAnswered` and `onFailed` callbacks. When Gemini times out, 503s, or returns nothing, `onFailed` fires `tryTinyLlamaOrFallback()`. Scout no longer silently fails — TinyLlama picks up the question.
✓ **Gemini timeouts reduced** — `connectTimeout=10s` (was 20s), `readTimeout=12s` (was 30s). Faster fallback to TinyLlama. Was causing 30-second SocketTimeoutException hangs.
✓ **"Repeat that" intent** — `isRepeatRequest()` detects "repeat that", "say that again", "what did you say?", "pardon", and similar phrases. Replays last meaningful answer (5+ words) from a 4-minute cache. Works offline without re-running any brain.
✓ **Brain source Toast** — After each answer, a short Toast says "Gemini (online)" or "TinyLlama (offline)". For testing — helps Patrick see which brain is actually responding.
✓ **Gemini default fixed** — `isGeminiEnabled()` was defaulting to `false`, so Gemini was always blocked on fresh install even with a saved key. Fixed to `true`.
✓ **Gemini daily quota cooldown reduced** — 6 hours → 1 hour. Faster recovery during dev testing.
✓ **Face greeting fires once per launch** — `greetedThisSession` was resetting to `false` every 5 seconds of face absence. Fixed: reset removed. Scout greets once per boot only.
✓ **STT reliability improved** — `EXTRA_PREFER_OFFLINE=true` avoids Samsung's network STT, 10-second silence window (was shorter), `ERROR_RECOGNIZER_BUSY` (error 8) now waits 600ms before restart.
✓ **Duplicate prompt serves cached Gemini answer** — Was saying "I don't want to ask twice." Now replays the cached reply (4-minute TTL) or lets the duplicate through if no cache.
✓ **Testing confirmed on A32** — Patrick is actively building and testing on Samsung Galaxy A32. Fold 7 is listed as primary but A32 is the current active test device.

*(Previous session June 27: Wrong-name teaching fixed, ML Kit label whitelist, finishThinking() fixed, Fold 7 listed as primary — all DONE)*

---

## June 27, 2026 — What Is New:

✓ **Wrong-name teaching with 2 people in frame FIXED** — "This is my wife Diana" was sometimes stored as the primary user's name. STT drops "my wife" → Scout heard "this is Diana" → FactKey.NAME. Guard added: if primary user already known + different name + 2+ faces → secondary introduction, not primary rename.
✓ **ML Kit label whitelist** — OBJECT_WHITELIST in VisionAnswerBuilder.kt replaces old blacklist. Only ~80 real household objects reach Scout's voice. Garbage labels ("aerospace engineer", "dude", "vacation") gone.
✓ **`lastKnownFaceName` set immediately on name teaching** — "I am Patrick" → Scout says your name right away on next "what do you see?", not 2 seconds later.
✓ **`finishThinking()` fixed — critical bug** — Was a completely empty function. `isThinking` got stuck `true` whenever Gemini was blocked (cooldown, duplicate prompt, quota message already suppressed). Face frozen in thinking mode, camera stopped, mic never restarted. Now actually clears `isThinking = false` + `faceView.setThinking(false)`.

*(Previous session June 21: A32 crash eliminated, face name persistence fixed, family face introduction, two-person response, Gemini maxOutputTokens 250 — all DONE)*

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
- Active test device: Samsung Galaxy A32 — current development and testing as of June 28
- Secondary device: Samsung Galaxy Fold 7 (12GB RAM) — listed as primary, needs dedicated stability testing
- App: 7-day free trial, then $9.99 one-time. No automatic charges. No subscriptions. Ever.
- Brains: TinyLlama 1.1B (offline, default — re-enabled June 28 with delayed load) + user's own free Gemini key (online, opt-in, now ON by default when key is saved)
- Website: lippy-robotics.gt.tc | Company: Lippy Robotics

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
✓ Camera — face detection, scene labeling (ML Kit) — throttled to ~7fps for A32 stability
✓ Face recognition COMPLETE and RELIABLE — known faces recognized consistently. Threshold 0.75. findBestMatch scans named rows only. Self-match bug fixed. lastKnownFaceName updated every 2 seconds.
✓ Family face introduction — "this is my son Elijah" / "this is my wife Diana" registers face. Pending mechanism for two-people-in-frame.
✓ Two-person response — "I can see Patrick and one other person."
✓ Face greeting fires once per launch — greetedThisSession no longer resets every 5s. June 28.
✓ Wrong-name teaching fixed — 2-person frame guard in handleTeaching(). June 27.
✓ ML Kit label whitelist — OBJECT_WHITELIST in VisionAnswerBuilder. Garbage labels gone. June 27.
✓ Gemini API — ON by default when key is saved. 'Go online'/'go offline' toggle. maxOutputTokens=250. Timeouts reduced (10s/12s). June 28.
✓ TinyLlama 1.1B offline brain — RE-ENABLED June 28 with delayed load (90s), 800MB RAM guard, nCtx=512. Automatic Gemini fallback wired. Pending A32 real-world confirmation.
✓ TinyLlama rambling fix — offline replies capped at 2 sentences
✓ "Repeat that" intent — replays last meaningful answer from 4-minute cache. Works offline. June 28.
✓ Brain source Toast — shows "Gemini (online)" or "TinyLlama (offline)" after each answer. June 28.
✓ Duplicate prompt now serves cached Gemini reply instead of refusing. June 28.
✓ Self-echo guard — Scout ignores hearing his own TTS voice through the mic
✓ Settings screen — swipe-right to open, API key entry, offline toggle, voice/TTS sliders, About Scout
✓ Hardcoded API key removed — Gemini key now in secure encrypted SharedPreferences
✓ Memory layers: TruthDb, HabitLayer, PeopleDb (with embeddings), JournalDb, ConversationDb
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

⚠ **TinyLlama re-enabled but not yet confirmed on A32** — Re-enabled June 28 with 90s delayed load + 800MB RAM guard + nCtx=512. Needs real-world testing on A32 to confirm LMKD crash does not return under memory pressure.
⚠ **Gemini API quota** — Exhausted during June 28 testing. 1-hour cooldown resets it. Test Gemini the next day by watching for "Gemini (online)" Toast.
⚠ **Elijah/Diana face registration requires solo moment** — After "this is my son Elijah", Scout sets a pending flag. Elijah needs to be the primary (largest) face in frame once for the embedding to be captured. Once done, Scout recognizes Elijah reliably.

- Face recognition breaks on slight head turns — embedding-based matching improves over time as embeddings accumulate.
- STT name recognition — 'Scout' sometimes misheard. Partially handled by wake word filter.
- Live news — future feature.
- Barge-in — deliberately disabled. PARKED.
- ScoutFaceView dead code — 2 lines. Harmless for now.

---

## 6. Current Priority — Launch Checklist Order

1. **✓ TinyLlama re-enable path DONE June 28** — 90s delay, 800MB RAM check, nCtx=512. Now needs A32 confirmation.
2. **Startup diagnostics — NEXT** — friendly message if brain, TTS, or STT missing at boot. (MainActivity.kt)
3. **Onboarding flow** — build 5 approved screens in Android. (OnboardingActivity.kt)
4. **Fold 7 stability testing** — dedicated session needed on Fold 7.
5. **Privacy Policy, Terms of Use, Open Source Credits** — write and add to app and website.
6. **Play Store listing** — description, screenshots, content rating.
7. **16KB page size warning** — ML Kit + TensorFlow Lite version updates required before Play Store submission.

After launch — Update 1.1 (Scout 1.1 — Growing Up) and beyond:
- Proposal Sandbox — 'Want me to remember that?' confirm step
- Permanent vs temporary memory sorting
- Caring follow-up loop
- Full mood system wired in
- Spanish language support — Phase 1
- Response cleanup layer (post-TinyLlama filter)
- Brain Pack upgrades (Phi-2, Llama 3.2, Phi-4, Llama 3.1 8B)
- Robot renaming in Settings
- "Test Connection" button — verify API key without burning quota
- Public roadmap / What's New page on website

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

*Project Scout Quick Start | Last updated: June 28, 2026 | Version 14 | Upload every session | For full details use Master Summary v36*
