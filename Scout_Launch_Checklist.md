# Project Scout — Play Store Launch Checklist
**What Scout needs to be worth $9.99 | Updated July 3, 2026 | Version 10**

Scout does not need to be perfect to ship. He needs to be reliable, honest, and feel like a companion.
Everything on this list makes him worth $9.99 to a family who has never met him before.

---

## ✓ Already Done — Scout Has These Today

✓ Animated face — Eyes that move and show emotion. Looks alive. Looks like Scout.
✓ Eye jitter FIXED — Boot lock, speaking gate, dead zone, min-delta guard. A32 iris stable. DONE June 18.
✓ Scout eyebrows and mouth brightened to #9BBEFF. DONE June 18.
✓ Voice — speaks and listens. Android STT + TTS, works offline.
✓ Camera awareness — Scout sees faces and scenes. Throttled to ~7fps for A32 memory health. DONE June 21.
✓ Offline brain — TinyLlama 1.1B runs fully on the phone. No internet required. RE-ENABLED June 28 with safe delayed load strategy (90s delay, 800MB RAM check, nCtx=512, nThreads=2). On-demand load also added as Gemini fallback.
✓ Flexible memory — Scout learns and recalls any fact reliably.
✓ Identity answers — Scout answers 'are you my friend?' as Scout, not a generic AI.
✓ Weather — Current, tonight, tomorrow, 7-day, precipitation %. Via NWS (api.weather.gov). Free for commercial use. Offline with honest refusal.
✓ Total offline mode — 'Go offline' blocks ALL internet features.
✓ Thinking expression — Eyes drift, lids narrow, brows asymmetric while Scout thinks.
✓ Wake word filter — Scout only responds when he hears his name.
✓ Conversation window — 30 seconds open conversation after Scout responds.
✓ Boot window — Scout ready immediately after boot, no name needed.
✓ Online / disconnect phrases — recognized and handled.
✓ Business model — 7-day free trial, then $9.99 one-time. No subscriptions. Ad-free forever.
✓ 5-screen onboarding flow — designed and approved. Blue color scheme locked in.
✓ Versioning system — Scout 1.0 The Beginning → 1.1 Growing Up → 2.0 A New Chapter.
✓ TinyLlama rambling fix — offline replies capped at 2 sentences. DONE June 15.
✓ Self-echo guard — Scout no longer picks up his own TTS voice as a new question. DONE June 15.
✓ Face recognition Step 1 — MobileFaceNet.tflite bundled (MIT licensed, ~5MB). FaceEmbedder.kt created. DONE June 15.
✓ Face recognition Steps 2–4 COMPLETE — FaceEmbedder wired into camera. PeopleDb stores BLOB embeddings. Naming flow uses embedding-based identity. Known face recognized. Unknown face greeted. Nicolas Protocol active. DONE June 17.
✓ Face recognition RELIABLE — findBestMatch scans named rows only. Threshold raised to 0.82. Self-match bug fixed (findBestMatch before storeEmbedding). Scout says your name consistently, not just once. DONE June 21 / threshold updated June 29.
✓ Family face introduction — "this is my son Elijah" / "this is my wife Diana" registers their face. Pending mechanism handles two-people-in-frame gracefully. DONE June 21.
✓ Two-person response — Scout now says "I can see Patrick and Elijah" instead of "someone else" when both faces are known. Secondary face embedding added June 29.
✓ Wrong-name teaching fixed — 2-person frame guard prevents "this is my wife Diana" being stored as primary user rename. DONE June 27.
✓ ML Kit label whitelist — OBJECT_WHITELIST in VisionAnswerBuilder. Garbage labels gone. DONE June 27.
✓ finishThinking() fixed — was empty no-op. Scout no longer freezes in thinking mode. DONE June 27.
✓ Naming phrases expanded — "this is X", "I am X", "you see X" recognized as name-teaching phrases. DONE June 15.
✓ THIRD_PARTY_NOTICES.md created — start of Open Source Credits. DONE June 15.
✓ Hardcoded Gemini API key REMOVED — Patrick's personal key removed from MainActivity.kt. Now in encrypted SharedPreferences. DONE June 18.
✓ Settings screen BUILT — SettingsActivity with 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Swipe-right gesture + first-boot hint + voice command to open. DONE June 18.
✓ Four A32 stability fixes — camera bitmap recycle, ML Kit suppression during Gemini, speak() race condition closed, camera frame throttle (150ms). DONE June 19–21.
✓ A32 NO LONGER CRASHING — Patrick confirmed stable June 21. Delayed LMKD kill after Gemini responses eliminated.
✓ TinyLlama re-enabled with safe delayed load — 90s startup delay, 800MB RAM guard, nCtx=512, nThreads=2. On-demand load fires when Gemini fails. DONE June 28.
✓ TinyLlama automatic Gemini fallback — onFailed callback in tryGemini() triggers tryTinyLlamaOrFallback(). If Gemini times out or returns nothing, Scout automatically tries TinyLlama. DONE June 28.
✓ Gemini timeouts reduced — connectTimeout 10s, readTimeout 20s. Faster fallback to TinyLlama on slow responses. DONE June 28.
✓ "Repeat that" intent — isRepeatRequest() detects "repeat that", "say that again", "what did you say?", etc. Replays last meaningful answer from 4-minute cache. Works offline without re-running any brain. DONE June 28.
✓ Brain source Toast — after each answer, Toast shows "Gemini (online)" or "TinyLlama (offline)" for testing. DONE June 28.
✓ Gemini default fixed — isGeminiEnabled() was defaulting to false (always OFF). Fixed to true so Gemini works on fresh install when a key is saved. DONE June 28.
✓ Gemini daily quota cooldown reduced — 6 hours → 1 hour. Faster dev recovery after quota exhaustion. DONE June 28.
✓ Face greeting fires once per launch — was resetting every 5 seconds when face briefly left frame (greetedThisSession = false reset removed). Now fires once per app boot only. DONE June 28.
✓ STT reliability improved — EXTRA_PREFER_OFFLINE=true (avoids Samsung network STT dependency), 10-second silence window (was shorter), ERROR_RECOGNIZER_BUSY (error 8) gets 600ms delay before restart instead of immediate retry. DONE June 28.
✓ Launcher icon eyes no longer clipped — Face scaled to 68% of canvas, centered. All 5 mipmap densities regenerated. Eyes and eyebrows fully visible inside the circular launcher mask. DONE June 29.
✓ Face misidentification fixed — Cosine similarity threshold raised 0.75→0.82. Prevents father/son pairs (Patrick/Elijah) from scoring above threshold. "Scout, forget [name]" command added to clear and re-register any face. DONE June 29.
✓ Scout can no longer go permanently deaf — 3-layer TTS stuck fix: speak() return-value check, speakingStartedMs timestamp, 45-second watchdog that force-clears isSpeaking if TTS callback never fires. DONE June 29.
✓ Voice slider changes now stick — SettingsActivity and MainActivity both read from scout_prefs. onResume() reloads pitch/speed so voice changes take effect immediately without restarting the app. DONE June 29.
✓ Greeting words blocked from name storage — "hello", "hi", "hey", "howdy", "greetings", "sup", "yo" added to blockedNames. Scout no longer says "I'll remember your name is hello." DONE June 29.
✓ Gemini responses no longer cut off mid-sentence — maxOutputTokens raised 250→600. "Always end on a complete sentence" added to system prompt. MAX_TOKENS trim logic finds last sentence boundary; returns null if none (falls through to TinyLlama). DONE June 29.
✓ Gemini quota/cooldown announced — Scout now speaks "Gemini says you've reached your daily limit" instead of silently falling through to TinyLlama. speakUnavailableIfNeeded() returns Boolean (true=spoken, false=suppressed). Cooldown check added at top of tryTinyLlamaOrFallback(). DONE June 29.
✓ Secondary face recognition — Both faces now get embedded in two-person scenes, not just the primary. PeopleDb v3 adds person_embeddings table (up to 5 per person). VisionAnswerBuilder uses secondaryFaceName — Scout says "I can see you, Patrick and Elijah" instead of "someone else." DONE June 29.
✓ **ArcFace face recognition upgrade** — InsightFace MobileFaceNet (512-dim, 4.8MB) replaces old 192-dim model. PeopleDb v4: migration clears incompatible embeddings (preserves names and face hashes). Threshold 0.60f fixes "everyone is Patrick" false positive. DONE July 3.
✓ **"I see X" phrasing** — Scout now says "I see Patrick" and "I see Patrick and Diana" instead of "I can see you, Patrick." Better match for a seeing-eye companion. DONE July 3.
✓ **Diana (secondary face) fix** — Secondary face block now consumes pendingFaceIntroName. "This is my wife Diana" with two people in frame now correctly stores and recognizes Diana. DONE July 3.
✓ **Personality phrase pools — Phrases.kt** — Scout no longer repeats the same boot greeting, goodbye, or remember confirmation every session. Anti-repeat rolling window prevents back-to-back repeats. DONE July 3.
✓ **Adaptive boot greeting** — BOOT_OFFLINE_FAST (no warming-up mention) when TinyLlama loaded in under 2 seconds last session; BOOT_OFFLINE otherwise. TinyLlama load time stored in SharedPreferences. All BOOT_ONLINE phrases now mention offline backup warming up. DONE July 3.

---

## ■ Must Fix Before Launch

These are the real blockers. Scout cannot ship without these.

### 1. A32 stability — TinyLlama re-enable path ✓ DONE June 28

- TinyLlama re-enabled with delayed load strategy: 90s delay after boot, 800MB RAM guard, nCtx=512, nThreads=2.
- On-demand load also wired as Gemini fallback — if Gemini fails and TinyLlama hasn't loaded yet, tryLoadOfflineBrain() fires and Scout says "warming up."
- Still needs real-world A32 testing to confirm the LMKD crash does not return.
■ MainActivity.kt + LlamaEngine.kt — monitor on A32 builds

### 2. Startup diagnostics — Makes Scout start cleanly every time ■ NEXT

- Scout checks at startup that brain is loaded, TTS is ready, STT is available.
- If something is missing, Scout says something friendly rather than crashing or freezing.
■ MainActivity.kt — startup check block — THIS IS NEXT

### 3. Onboarding flow — First impression matters

- 5-screen flow designed and approved. Blue color scheme. Built by ChatGPT.
- Screen counter and progress dots must be driven by the same variable — never hardcoded in two places.
■ New OnboardingActivity.kt — one focused session

### 4. Fold 7 stability testing — Ongoing

- Fold 7 is listed as primary test device. Current testing session happening on A32.
- Build and validate voice, memory, face recognition, weather, wake word on each device.
■ Ongoing as new features are built

### 5. A32 stability testing — Ongoing

- All work tested on A32 as each feature is added. No crashes as of June 21.
- TinyLlama re-enabled June 28 — monitor for LMKD under memory pressure.
■ Ongoing — continue testing as new features are added

---

## ■ Legal & Website — Required for Launch

### 6. Privacy Policy — Priority 1

- What data Scout collects. What stays on the device. Gemini is optional. Contact information.
- Google Play may require this depending on features — have it ready before submitting.
■ Write once. Add to website footer + About Scout screen in app.

### 7. Terms of Use — Priority 2

- Scout is provided as-is. No guarantees. Not medical, legal, or financial advice.
- Keep it simple. One clear page is enough.
■ Write once. Add to website footer + About Scout screen in app.

### 8. Open Source Credits — Priority 3

- llama.cpp, TinyLlama, Phi models, Android libraries, MobileFaceNet — many licenses require attribution.
- THIRD_PARTY_NOTICES.md already started in repo (MobileFaceNet MIT credit done).
- A simple page with links and acknowledgements is enough for launch.
■ Add to website + About Scout → Open Source Licenses in app.

### 9. Website — lippy-robotics.gt.tc

- Add website link to: Google Play listing, Facebook page, About Scout screen.
- Add a 'What's New' or 'Scout Development Updates' page — shows Scout is actively growing.
- Future domain options: lippyrobotics.com, scoutcompanion.com, meetscout.ai
■ Update website before launch. Add What's New page.

---

## ■ Play Store Listing

Required to submit to Google Play.

- App description — Lead with 'Turn an old phone into a friend.' Honest about what Scout is.
- Screenshots — Scout's face, onboarding screens, weather response, memory recall, settings. 5–8 minimum.
- Privacy policy link — required by Google Play.
- Content rating questionnaire — Scout is family-safe. Straightforward.
- Short description — 60 characters max: 'A calm AI companion for your whole family. Private. Local. Yours.'

**⚠ 16KB page size warning** — ML Kit and TensorFlow Lite native libraries will need version updates before Play Store submission. Required versions: `mlkit:face-detection:16.1.6`, `mlkit:image-labeling:17.0.7`, `tensorflow-lite:2.14.0`. Google Play enforces 16KB page alignment starting 2025. Address in a dedicated session before submission.

---

## ■ Post-Trial Strategy

- After 7 days — advanced features lock but Scout stays installed. Still greets the family.
- Trial end message — 'Thank you for spending time with Scout. Scout is still growing. You can unlock the full version at any time.'
- Roadmap in Settings → About Scout → Features & Future Plans.
- Welcome Back screen after every update — what changed, what was fixed, what was added.
- Scout optionally speaks after update: 'I've learned a few new things since my last update.'
- About Scout → Update History — shows every major version and improvements.

---

## ■ After Launch — Scout 1.1 Growing Up and Beyond

- Proposal Sandbox — 'Want me to remember that?' confirm step
- Permanent vs temporary memory — birthday vs appointment sorting
- Caring follow-up loop — 'How was your appointment?' then forget
- Full mood system — CALM / CURIOUS / HAPPY / THINKING / CONCERNED
- Spanish language support — Phase 1: STT + TTS + hardcoded translations
- Scout news feed — live news fetcher
- Response cleanup layer — post-TinyLlama filter
- Brain Pack upgrades — Phi-2, Llama 3.2, Phi-4, Llama 3.1 8B
- Robot renaming — user stores their own name for Scout
- Hardware mode — KEYESTUDIO Mini Tank Kit V2 via Bluetooth
- STT phonetic matching — 'Scout' misheard as 'Gal', 'Scott', 'Out'
- Cosmetic expression packs
- Full Settings screen expansion
- Calendar integration
- Voice recognition (Scout 2.0+) — advisory layer alongside face recognition
- "Test Connection" button in Settings — verify API key without burning quota

---

## The bottom line

Scout already has a face, a voice, two brains (Gemini + TinyLlama), memory, weather, a wake word, ArcFace recognition for the whole family (512-dim, fixes false positives), Diana/secondary face fix, personality phrase variety, adaptive boot greetings, a settings screen, and a stable icon. The A32 is stable. TinyLlama is re-enabled. The gap between today and the Play Store is focused sessions — not months.

**Next session: Startup diagnostics (#2 on Must Fix list) — friendly messages if brain, TTS, or STT missing at boot. Then: Onboarding flow (#3).**

**Scout does not need to be finished to ship. He just needs to be Scout. And he already is.**

---

*Project Scout Launch Checklist | Updated July 3, 2026 | Version 10 | For Patrick, Diana, Elijah, and Scout*
