# Project Scout — Play Store Launch Checklist
**What Scout needs to be worth $9.99 | Updated July 7, 2026 | Version 13**

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
✓ **PeopleDb threshold raised back to 0.65f** — 0.60f (from ArcFace upgrade) caused Diana/Elijah cross-contamination. Raised to 0.65f in both findBestMatch and findBestMatchName. cursor.use{} prevents cursor leaks. forgetPerson is now atomic. addNamedEmbedding skips insert if person already has 12 embeddings. DONE July 4.
✓ **VisionAnswerBuilder fixes** — 3+ faces branch now includes dogLine (was missing). 2-face branch: secondaryFaceName arm precedes pendingIntroName arm; new else arm handles unknown primary + known secondary. Freshness 3500ms→1800ms. DONE July 4.
✓ **Secondary face findBestMatch fallback** — Secondary face recognition now falls back to the single-BLOB people.embedding if person_embeddings has no match. Closes recognition gap on fresh installs. DONE July 4.
✓ **Caption persistence fix** — Last spoken line no longer lingers on screen after captions are turned off in Settings. onResume() hides the caption view immediately. DONE July 4.
✓ **Startup diagnostics** — Scout speaks a friendly STT-unavailable warning at boot; shows a Toast if TTS fails to initialize. Both events logged to JournalDb. DONE July 4.
✓ **Onboarding flow — OnboardingActivity.kt** — Full 5-screen flow built. Screens: Welcome / Trial / This Is Just The Beginning / Privacy / Ready To Begin. currentPage drives both dots and "X / 5" counter. First-boot redirect in MainActivity.onCreate() sends new installs to onboarding. finishOnboarding() defaults new installs to offline mode (gemini_enabled=false). DONE July 4.
✓ **New installs default to offline mode** — finishOnboarding() writes gemini_enabled=false to scout_memory SharedPrefs. Gemini opt-in only after user adds their key in Settings. DONE July 4.
✓ **BOOT_NO_KEY phrases** — Replaced vague "online mode not configured" with actionable tip: "Open settings any time by sliding the screen to the right." DONE July 4.
✓ **CLAUDE.md** — Repo-root file documents git pull/push commands with full branch name, critical no-hardcoding rules, architecture notes. Persists across Claude session compaction. DONE July 4.
✓ **ModelDownloadActivity** — Portrait-only loading screen for TinyLlama model download. 39 humorous loading messages, ObjectAnimator slide animation, updateProgress() API. Ready for Play Asset Delivery wiring in a future session. DONE July 4.
✓ **16KB page alignment fix confirmed** — scout_llama.so built with `-Wl,-z,max-page-size=16384`. Fixes dlopen failure on Samsung Linux 6.x kernels (Android 15). Confirmed working on A32 and Fold 7. DONE July 7.
✓ **bootstrapModelFile() — auto-copy on startup** — Scout copies the TinyLlama model from app-specific external storage to filesDir automatically. No more "model not found" after reinstall. READ_EXTERNAL_STORAGE added to manifest with maxSdkVersion="32" for Android ≤12 fallback. DONE July 7.
✓ **TinyLlama confirmed working on A32 and Fold 7** — Both devices tested with Online Features OFF. TinyLlama answers questions. Primary brain confirmed operational. DONE July 7.
✓ **Offline fallback message fixed** — When user deliberately turns off Online Features, Scout says "I'm working offline" not "having trouble connecting." DONE July 7.
✓ **Thinking expression and head-turn amplitude fixed** — Brow lifts raised to visible levels (26/24px). Head-turn face drift raised from invisible 5px to ±24px X / ±14px Y. DONE July 7.

---

## ■ Must Fix Before Launch

These are the real blockers. Scout cannot ship without these.

### 1. A32 stability — TinyLlama re-enable path ✓ DONE June 28 / CONFIRMED July 7

- TinyLlama re-enabled with delayed load strategy: 90s delay after boot, 800MB RAM guard, nCtx=512, nThreads=2.
- On-demand load also wired as Gemini fallback — if Gemini fails and TinyLlama hasn't loaded yet, tryLoadOfflineBrain() fires and Scout says "warming up."
- bootstrapModelFile() added July 7 — auto-copies model from external storage to filesDir on startup. Model survives reinstalls.
- CONFIRMED WORKING on A32 and Fold 7 July 7.

### 2. Startup diagnostics — ✓ DONE July 4

- TTS failure: Toast shown to user with restart instructions.
- STT unavailable: Scout speaks a friendly warning 4 seconds after boot and logs to JournalDb.

### 3. Onboarding flow — ✓ DONE July 4

- Full 5-screen OnboardingActivity.kt built. First-boot redirect in MainActivity.onCreate().
- currentPage is the single source of truth for dots and counter — not hardcoded in two places.
- finishOnboarding() defaults new installs to offline mode (gemini_enabled=false in scout_memory).

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

### 9. Website — https://patevan9.github.io/lippyrobotics.github.io

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

## ■ Support Scout Screen — Settings

A "Support Scout" section inside Settings with four optional one-time contribution tiers:
- **Buy Scout a Coffee** — $3 (product ID: `support_3`)
- **Support Scout More** — $5 (product ID: `support_5`)
- **Help Scout Grow** — $10 (product ID: `support_10`)
- **Scout Supporter** — $25 (product ID: `support_25`)

All clearly labeled as one-time, optional, and never unlocking features. Messaging: "Scout has no required subscriptions. Support is completely optional and helps fund future improvements." Footer: "Scout is a one-time purchase. Support contributions are completely optional and never unlock core features." Three badges: Ad-Free / Private & Local / Built with Care.

Payment: Google Play In-App Billing. All four products are consumable (so users can give more than once). Create product IDs in Play Console before building. Design mockup approved — final card names confirmed July 4.

■ Build in a future session before or after launch.

---

## ■ Post-Trial Strategy

- After 7 days — advanced features lock but Scout stays installed. Still greets the family.
- Trial end message — 'Thank you for spending time with Scout. Scout is still growing. You can unlock the full version at any time.'
- Roadmap in Settings → About Scout → Features & Future Plans.
- Welcome Back screen after every update — what changed, what was fixed, what was added.
- Scout optionally speaks after update: 'I've learned a few new things since my last update.'
- About Scout → Update History — shows every major version and improvements.

---

## ■ Scout Behavior Learning — Two Tiers

---

### Tier 1 — Regular Mode (Scout 1.1) — For everyone

**Public tagline:** "Scout can learn small preferences with your approval."

Scout notices patterns and suggests simple behavior adjustments in plain, friendly English. The family taps one button. Nothing ever changes without approval. No technical language, no file names, no risk levels shown.

**Settings → "Scout's Suggestions"** — Scout speaks warmly in first person:

> *"I'd like to answer a little faster."*
> *"I noticed you prefer shorter replies."*
> *"Would you like me to stop announcing battery percentage?"*
> *"I should be quieter at night."*
> *"I should not greet you every time you walk by."*
> *"I should be more careful recognizing [name]."*

Three buttons: **Approve** · **Not now** · **Never suggest this**

- Approve → Scout writes the change to SharedPrefs/behavior flag immediately. No build needed.
- Not now → dismissed, may resurface after cooldown
- Never suggest this → `suppressed` status, ProposalDetector skips permanently

**What triggers a suggestion:**
- Same wrong face corrected 3+ times → "I should be more careful recognizing [name]."
- User says "stop" / "that's enough" frequently → "I noticed you prefer shorter replies."
- Greeting fires within seconds of last greeting → "I should not greet you every time you walk by."
- TTS fires after 9pm frequently → "I should be quieter at night."
- Same fact corrected more than once → "I should ask before remembering new things."

---

### Tier 2 — Scout Dev Build (Patrick only — never on Play Store)

**This feature does not ship in the Play Store APK at all.** Not hidden — literally absent from the compiled release build. Android build variants ensure the code is stripped at compile time.

**Two build variants:**
- `standard` (Play Store) — Tier 1 behavior suggestions only. No developer code present.
- `dev` (Patrick's devices only, sideloaded) — Full telemetry + engineering observations.

**Compile-time flag in `build.gradle.kts`:**
```kotlin
productFlavors {
    create("standard") {
        buildConfigField("boolean", "DEVELOPER_MODE", "false")
    }
    create("dev") {
        buildConfigField("boolean", "DEVELOPER_MODE", "true")
    }
}
```
In release builds, `if (BuildConfig.DEVELOPER_MODE)` compiles to `if (false)` — the entire block is stripped. Nothing to decompile or discover.

**What Scout Dev shows — telemetry and observations, not code proposals:**

Scout surfaces real data from running on Patrick's devices. Patrick (and Claude) decide what to do with it.

> *"I've had 14 failed face recognitions today."*
> *"Wake-word detection dropped after yesterday's update."*
> *"Battery usage increased by 12% compared to last week."*
> *"Gemini failed 8 times today — mostly between 6 and 7pm."*
> *"TinyLlama boot time has been averaging 11 seconds this week."*
> *"The same face has been mis-identified 4 times today."*

These are observations, not commands. Scout never decides what to fix. Patrick sees the data, starts a Claude session, and says "Scout noticed X — let's fix it." Scout is an engineering partner, not an autonomous programmer.

**Scout Dev telemetry to collect:**
- Face recognition: success rate, failure rate, per-person accuracy
- Wake word: detection rate, false positive rate, post-update drops
- Gemini: call count, failure count, timeout rate, time-of-day patterns
- TinyLlama: boot time trends, memory usage
- TTS/STT: failure counts, error codes seen
- Battery: usage trend week-over-week
- Memory: correction frequency per fact type

**ProposalDb schema (Tier 1 shared only — Tier 2 uses a separate TelemetryDb):**

```sql
-- Tier 1 (standard build)
CREATE TABLE proposals (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    category        TEXT    NOT NULL,  -- "parameter" | "behavior" | "phrase"
    suggestion_text TEXT    NOT NULL,  -- First-person family-friendly text
    change_json     TEXT    NOT NULL,  -- What to write to SharedPrefs on approval
    status          TEXT    NOT NULL,  -- "pending"|"approved"|"dismissed"|"suppressed"|"applied"|"reverted"
    trigger_reason  TEXT,
    created_at      INTEGER NOT NULL,
    reviewed_at     INTEGER,
    applied_at      INTEGER
);

-- Tier 2 (dev build only — not compiled into standard/release)
CREATE TABLE telemetry_events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type  TEXT    NOT NULL,  -- "face_fail"|"wake_miss"|"gemini_fail"|"tts_error"|"boot_time" etc.
    value       REAL,              -- Numeric value where applicable (ms, count, %)
    context     TEXT,              -- Extra detail (person name, error code, etc.)
    recorded_at INTEGER NOT NULL
);
```

### Files needed (future sessions)

Tier 1 session: `ProposalDb.kt` · `ProposalDetector.kt` · `ScoutProposal.kt` · Settings "Scout's Suggestions" UI · `ApplyProposal.kt`

Tier 2 session (dev build, Scout 1.5+): `TelemetryDb.kt` · `TelemetryCollector.kt` (hooks into existing fail/miss points) · Scout Dev dashboard UI (observations list) · build variant wiring in `build.gradle.kts`

■ Tier 1 design approved July 5, 2026 — build post-launch Scout 1.1.
■ Tier 2 design approved July 5, 2026 — Scout Dev build, Patrick only, never Play Store.

---

## ■ After Launch — Scout 1.1 Growing Up and Beyond

- **Support Scout in-app donation screen** — Google Play Billing integration. Four one-time tiers: Buy Scout a Coffee ($3), Support Scout More ($5), Help Scout Grow ($10), Scout Supporter ($25). Scrollable screen with Scout face image, "Keep Scout by your side" copy, and the four tappable cards. Requires app to be live on Play Store first so products can be registered in Play Console. Design mockup approved July 13, 2026.
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

Scout already has a face, a voice, two brains (Gemini + TinyLlama), memory, weather, a wake word, ArcFace recognition for the whole family (512-dim, threshold 0.65f), a complete onboarding flow, startup diagnostics, a download loading screen, personality phrase variety, adaptive boot greetings, a settings screen, and a stable icon. The A32 is stable. TinyLlama is confirmed working on both A32 and Fold 7. New installs default to offline mode. The gap between today and the Play Store is focused sessions — not months.

**Next session: Privacy Policy + Terms of Use, Play Store listing, and 16KB page size ML Kit library updates.**

**Scout does not need to be finished to ship. He just needs to be Scout. And he already is.**

---

*Project Scout Launch Checklist | Updated July 7, 2026 | Version 13 | For Patrick, Diana, Elijah, and Scout*
