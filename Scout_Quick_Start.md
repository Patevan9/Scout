# Project Scout — Quick Start
**Last updated: June 27, 2026 | Version 13**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
For full technical details, use the Scout Master Summary (v35).

---

## June 27, 2026 — What Is New:

✓ **Wrong-name teaching with 2 people in frame FIXED** — "This is my wife Diana" was sometimes stored as the primary user's name. STT drops "my wife" → Scout heard "this is Diana" → FactKey.NAME. Guard added: if primary user already known + different name + 2+ faces → secondary introduction, not primary rename.
✓ **ML Kit label whitelist** — OBJECT_WHITELIST in VisionAnswerBuilder.kt replaces old blacklist. Only ~80 real household objects reach Scout's voice. Garbage labels ("aerospace engineer", "dude", "vacation") gone.
✓ **`lastKnownFaceName` set immediately on name teaching** — "I am Patrick" → Scout says your name right away on next "what do you see?", not 2 seconds later.
✓ **`finishThinking()` fixed — critical bug** — Was a completely empty function. `isThinking` got stuck `true` whenever Gemini was blocked (cooldown, duplicate prompt, quota message already suppressed). Face frozen in thinking mode, camera stopped, mic never restarted. Now actually clears `isThinking = false` + `faceView.setThinking(false)`.
✓ **Primary test device: Fold 7** — Patrick now building and testing on Samsung Galaxy Fold 7 (12GB RAM). A32 remains stable but Fold 7 is now primary.

*(Previous session June 21: A32 crash eliminated, face name persistence fixed, family face introduction, two-person response, Gemini maxOutputTokens 250 — all DONE)*

---

## June 21, 2026 — What Is New:

✓ **A32 NO LONGER CRASHING** — Patrick confirmed stable June 21. Root cause: ML Kit labeler and face detector were running on every camera frame (up to 30fps), exhausting memory until LMKD killed Scout. Fixed by adding `ANALYSIS_MIN_INTERVAL_MS = 150ms` — ML Kit now runs at max ~7fps. Skipped frames are dropped instantly with zero cost.
✓ **Face name persistence FIXED** — Scout now says your name consistently, not just once. Root causes were: (1) `findBestMatch` was scanning unnamed rows that won the similarity race, (2) embedding was stored BEFORE `findBestMatch`, causing a self-match (similarity = 1.0) that always returned the wrong hash. Fixed: SQL now only scans named rows, and `findBestMatch` runs BEFORE `storeEmbedding`. `lastKnownFaceName` caches the result every 2 seconds.
✓ **Face recognition threshold raised** — 0.65 → 0.75. Prevents family members (Patrick/Elijah) with shared facial geometry from being misidentified.
✓ **Family member face introduction** — "this is my son Elijah" / "this is my wife Diana" now registers their face. Pending mechanism handles two-people-in-frame.
✓ **Two-person response improved** — "I can see Patrick and one other person." instead of always "I see two people."
✓ **Gemini maxOutputTokens raised** — 150 → 250. Prevents mid-sentence cutoff on longer answers.
✓ **Mouth timing fix** — Mouth no longer moves before audio starts (faceView.setSpeaking stays in TTS onStart only).

*(Previous sessions June 17–20: Face recognition Steps 2–4, Settings screen, API key removed, eye jitter fix, speak() race condition, three A32 stability fixes — all DONE)*

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
- Primary test device: Samsung Galaxy Fold 7 — as of June 27 (12GB RAM)
- Stress-test device: Samsung Galaxy A32 — stable as of June 21, still used for low-RAM validation
- App: 7-day free trial, then $9.99 one-time. No automatic charges. No subscriptions. Ever.
- Brains: TinyLlama 1.1B (offline, default — temporarily disabled on A32) + user's own free Gemini key (online, opt-in)
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
✓ Camera — face detection, scene labeling (ML Kit) — throttled to ~7fps for A32 stability
✓ Face recognition COMPLETE and RELIABLE — known faces recognized consistently. Threshold 0.75. findBestMatch scans named rows only. Self-match bug fixed. lastKnownFaceName updated every 2 seconds.
✓ Family face introduction — "this is my son Elijah" / "this is my wife Diana" registers face. Pending mechanism for two-people-in-frame.
✓ Two-person response — "I can see Patrick and one other person."
✓ Gemini API — OFF by default. 'Go online' activates. 'Go offline' deactivates. maxOutputTokens=250.
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
✓ TinyLlama 1.1B offline brain — verified working on A32 (temporarily disabled — see Known Issues)
✓ TinyLlama rambling fix — offline replies capped at 2 sentences
✓ Self-echo guard — Scout ignores hearing his own TTS voice through the mic
✓ Weather via NWS (api.weather.gov) — precipitation %, offline-aware, free for commercial use
✓ Total offline mode — 'go offline' blocks ALL internet features
✓ A32 STABLE — no crashes as of June 21. Camera throttle eliminated delayed LMKD kill.
✓ Wrong-name teaching fixed — 2-person frame guard in handleTeaching(). June 27.
✓ ML Kit label whitelist — OBJECT_WHITELIST in VisionAnswerBuilder. Garbage labels gone. June 27.
✓ lastKnownFaceName set immediately on teaching. June 27.
✓ finishThinking() actually clears thinking state — was empty no-op causing stuck-thinking. June 27.

---

## 5. Known Issues — Do Not Touch Without Discussion

⚠ **TinyLlama disabled on A32** — Disabled at startup to prevent LMKD crash under memory pressure. Needs investigation: delayed load, on-demand load, or memory footprint reduction. Gemini is primary brain for now. NOTE: A32 is now stable without TinyLlama — this is purely about re-enabling the offline brain safely.
⚠ **Elijah/Diana face registration requires solo moment** — After "this is my son Elijah", Scout sets a pending flag. Elijah needs to be the primary (largest) face in frame once for the embedding to be captured. Once done, Scout recognizes Elijah reliably.

- Face recognition breaks on slight head turns — position-based hash still used as fallback. Embedding-based matching (findBestMatch) improves over time as embeddings accumulate.
- Elijah/Diana face registration requires solo face moment after introduction.
- STT name recognition — partially handled by wake word filter.
- Live news — future feature.
- Barge-in — deliberately disabled. PARKED.
- ScoutFaceView dead code — 2 lines. Harmless for now.

---

## 6. Current Priority — Launch Checklist Order

1. **TinyLlama re-enable path** — A32 is stable. Now need to safely load TinyLlama without LMKD. Delayed load or on-demand approach.
2. **Startup diagnostics** — friendly message if systems missing at boot.
3. **Onboarding flow** — build 5 approved screens in Android.
4. **Fold 7 stability testing** — now primary device. Ongoing.
5. **Privacy Policy, Terms of Use, Open Source Credits** — write and add to app and website.
6. **Play Store listing** — description, screenshots, content rating.

After launch — Update 1.1 (Scout 1.1 — Growing Up) and beyond:
- Proposal Sandbox — 'Want me to remember that?' confirm step
- Permanent vs temporary memory sorting
- Caring follow-up loop
- Full mood system wired in
- Spanish language support — Phase 1
- Response cleanup layer
- Brain Pack upgrades
- Robot renaming in Settings
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

*Project Scout Quick Start | Last updated: June 27, 2026 | Version 13 | Upload every session | For full details use Master Summary v35*
