# Project Scout — Quick Start
**Last updated: June 16, 2026 | Version 10**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
For full technical details, use the Scout Master Summary (v32).

---

## June 16, 2026 — What Is New:

✓ **TinyLlama rambling fix COMPLETE** — offline replies capped at 2 sentences. No more garbled continuations.
✓ **Self-echo guard COMPLETE** — Scout no longer hears his own TTS voice and treats it as a new question.
✓ **MainActivity.kt blank line cleanup COMPLETE** — file cleaned throughout.
✓ **Face recognition foundation COMPLETE (Step 1 of 4)** — MobileFaceNet.tflite bundled in app (MIT licensed, ~5MB). TensorFlow Lite dependency added. FaceEmbedder.kt created but not yet wired in. App behavior unchanged — foundation only.
✓ **Naming phrases expanded** — "this is Patrick", "I am Patrick", "you see Patrick" now teach Scout a name alongside "my name is Patrick".
✓ **Weather switched to NWS** — api.weather.gov replaces Open-Meteo. 100% free for commercial use, no API key, no licensing issue. U.S. locations only.
✓ **THIRD_PARTY_NOTICES.md created** — MIT credit for MobileFaceNet model. Start of Open Source Credits screen.

*(Previous sessions: Wake word, conversation window, boot window, memory recall, greeting routing, vision cleanup, person counting — all DONE June 12–14)*

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
- Brains: TinyLlama 1.1B (offline, default) + user's own free Gemini key (online, opt-in)
- Website: lippy-robotics.gt.tc | Company: Lippy Robotics

---

## 3. Scout's Core Philosophy

Scout should feel: Calm. Thoughtful. Quietly alive. Emotionally subtle. Occasionally curious.
Scout should NOT feel: Excited. Scripted. Fake. Cartoonish. Hyperactive. Constantly praising.

**Stability > Features | Presence > Intelligence | Honest > Fake cheerful | Local-first > Cloud | Predictable > Flashy**

---

## 4. What Is Working Right Now

✓ Animated face (ScoutFaceView) — thinking expression, iris drift, narrowed lids, asymmetric brows
✓ Speech recognition (STT) + Text-to-Speech (TTS)
✓ Camera — face detection, scene labeling (ML Kit)
✓ Gemini API — OFF by default. 'Go online' activates. 'Go offline' deactivates.
✓ Memory layers: TruthDb, HabitLayer, PeopleDb, JournalDb, ConversationDb
✓ Intent router — weather, time, greetings, family facts, downloads, IDENTITY, RECALL_FACT
✓ Flexible teaching — 'my favorite color is teal' → stored permanently
✓ Flexible recall — recalls facts reliably after other questions
✓ Wake word filter — Scout only responds when he hears his name
✓ Conversation window — 30 seconds open conversation after Scout responds
✓ Boot window — Scout ready immediately after boot, no name needed
✓ Online / disconnect phrases recognized
✓ TinyLlama 1.1B offline brain — VERIFIED on A32 (20-40s per answer, expected)
✓ TinyLlama rambling fix — offline replies capped at 2 sentences
✓ Self-echo guard — Scout ignores hearing his own TTS voice through the mic
✓ Weather via NWS (api.weather.gov) — precipitation %, offline-aware, free for commercial use
✓ Total offline mode — 'go offline' blocks ALL internet features
✓ Face recognition Step 1 — MobileFaceNet.tflite bundled, FaceEmbedder.kt created (not yet wired)
✓ Naming phrases — "this is X", "I am X", "you see X" recognized as name-teaching phrases

---

## 5. Known Issues — Do Not Touch Without Discussion

■ **Face recognition Steps 2–4 — IN PROGRESS.** Foundation done (Step 1). Still needed: wire FaceEmbedder into camera pipeline (Step 2), update PeopleDb schema for embeddings (Step 3), rewire naming flow (Step 4).
■ **Hardcoded Gemini API key** — Patrick's personal key in MainActivity.kt. Removing in Settings session.

- Fold 7 not tested — all testing on A32 via WiFi. Fold 7 needs dedicated session.
- TinyLlama slow on A32 — 20-40s expected. Hardware limitation. Not a bug.
- Iris jitter (A32 idle) — hardware timing. Fold 7 smooth.
- ScoutFaceView dead code — 2 lines. Harmless for now.
- STT name recognition — partially handled by wake word filter.
- Live news — future feature.
- Barge-in — deliberately disabled. PARKED.

---

## 6. Current Priority — Launch Checklist Order

1. **Face recognition Steps 2–4** — wire FaceEmbedder into camera, update PeopleDb, rewire naming flow.
2. **Remove hardcoded API key + Basic Settings screen** — do together.
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
- Full Settings screen
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

*Project Scout Quick Start | Last updated: June 16, 2026 | Version 10 | Upload every session | For full details use Master Summary v32*
