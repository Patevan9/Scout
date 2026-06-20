# Project Scout — Quick Start
**Last updated: June 20, 2026 | Version 11**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
For full technical details, use the Scout Master Summary (v33).

---

## June 17–20, 2026 — What Is New:

✓ **Face recognition Steps 2–4 COMPLETE** — FaceEmbedder wired into camera pipeline (Step 2). PeopleDb updated with BLOB embedding column + cosine similarity matching (Step 3). "This is X" / "My name is X" naming flow uses embedding-based identity (Step 4). Scout now recognizes known faces frame-to-frame. DONE June 17.
✓ **Settings screen BUILT** — SettingsActivity with 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Gemini API key entry wired to secure encrypted SharedPreferences. DONE June 18.
✓ **Hardcoded Gemini API key REMOVED** — Patrick's personal key removed from MainActivity.kt entirely. Now lives in encrypted SharedPreferences via SettingsActivity. DONE June 18.
✓ **Settings access via swipe-right** — Gear button replaced with swipe-right gesture. First-boot hint shown on first launch. Voice command also opens Settings. DONE June 18.
✓ **Eye jitter FIXED** — Boot lock, speaking gate, dead zone, and min-delta guard added to ScoutFaceView. A32 iris is now stable. DONE June 18.
✓ **Scout's eyebrows and mouth brightened** — Color updated to #9BBEFF (lighter blue, matches iris). DONE June 18.
✓ **Three A32 stability fixes** — Camera bitmap memory leak fixed (recycle after all ML Kit callbacks). ML Kit suppressed during Gemini calls (isThinking gate). speak() race condition closed (isSpeaking set immediately at function entry, not 240–650ms later). DONE June 19–20.
⚠ **TinyLlama temporarily disabled on A32** — Startup load caused LMKD to kill Scout under memory pressure. Disabled to stabilize. Gemini is the primary brain until a safe re-enable path is found.

*(Previous sessions June 12–16: Wake word, memory recall, weather NWS, face recognition foundation, rambling fix, self-echo guard — all DONE)*

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
- Primary test device: Samsung Galaxy A32 — all testing via WiFi through Android Studio
- Dev device: Samsung Galaxy Fold 7 — not yet stability tested
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
✓ Speech recognition (STT) + Text-to-Speech (TTS)
✓ Camera — face detection, scene labeling (ML Kit)
✓ Face recognition COMPLETE (Steps 1–4) — known faces recognized by embedding; unknown faces greeted; Nicolas Protocol active
✓ Gemini API — OFF by default. 'Go online' activates. 'Go offline' deactivates.
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
✓ Three memory stability fixes — bitmap recycle, ML Kit suppression during Gemini, speak() race condition closed

---

## 5. Known Issues — Do Not Touch Without Discussion

⚠ **TinyLlama disabled on A32** — Disabled at startup to prevent LMKD crash under memory pressure. Needs investigation: delayed load, on-demand load, or memory footprint reduction. Gemini is primary brain for now.
■ **A32 crash investigation ongoing** — Three stability fixes pushed. Main target was the speak() race condition (isSpeaking gap). Testing needed to confirm fix holds after Gemini response.

- Fold 7 not tested — all testing on A32 via WiFi. Fold 7 needs dedicated session.
- STT name recognition — partially handled by wake word filter.
- Live news — future feature.
- Barge-in — deliberately disabled. PARKED.
- ScoutFaceView dead code — 2 lines. Harmless for now.

---

## 6. Current Priority — Launch Checklist Order

1. **A32 stability confirmed** — pull and test the speak() race condition fix. Confirm Scout does not crash after Gemini responses.
2. **TinyLlama re-enable path** — figure out safe way to load TinyLlama without LMKD.
3. **Startup diagnostics** — friendly message if systems missing at boot.
4. **Onboarding flow** — build 5 approved screens in Android.
5. **Fold 7 stability testing** — dedicated session.
6. **Privacy Policy, Terms of Use, Open Source Credits** — write and add to app and website.
7. **Play Store listing** — description, screenshots, content rating.

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

*Project Scout Quick Start | Last updated: June 20, 2026 | Version 11 | Upload every session | For full details use Master Summary v33*
