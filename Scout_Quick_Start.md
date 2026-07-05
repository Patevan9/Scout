# Project Scout — Quick Start
**Last updated: July 5, 2026 | Version 18**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
For full technical details, use the Scout Master Summary (v40).

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
✓ TTS deafness bug fixed — speak() return check + speakingStartedMs timestamp + 45s watchdog. Scout cannot get stuck deaf after idle. June 29.
✓ Voice slider changes stick — scout_prefs used in both SettingsActivity and MainActivity. onResume() reloads pitch/speed. June 29.
✓ Launcher icon fixed — face 68% of canvas, eyes fully inside circular mask. June 29.
✓ Camera — face detection, scene labeling (ML Kit) — throttled to ~7fps for A32 stability
✓ Face recognition COMPLETE and RELIABLE — ArcFace upgrade July 3: InsightFace MobileFaceNet (512-dim, 4.8MB). PeopleDb v4, threshold 0.60f (ArcFace scale). findBestMatch scans named rows only. Self-match bug fixed. lastKnownFaceName updated every 2 seconds.
✓ Secondary face recognition — both faces in a two-person frame embedded and matched. person_embeddings table (PeopleDb v4). Threshold 0.55f for secondary crops. Diana fix July 3 — pendingFaceIntroName now checked in secondary block.
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

⚠ **TinyLlama re-enabled but not yet confirmed on A32** — Re-enabled June 28 with 90s delayed load + 800MB RAM guard + nCtx=512. Needs real-world testing on A32 to confirm LMKD crash does not return under memory pressure.
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
5. **Privacy Policy, Terms of Use, Open Source Credits** — write and add to app and website.
6. **Play Store listing** — description, screenshots, content rating.
7. **16KB page size warning** — ML Kit + TensorFlow Lite version updates required before Play Store submission.
8. **Play Asset Delivery wiring** — ModelDownloadActivity is ready; PAD integration to trigger it is a future session.

After launch — Update 1.1 (Scout 1.1 — Growing Up) and beyond:
- **Scout Behavior Learning** — "Scout can learn small preferences with your approval." Scout suggests plain-English adjustments ("I should be quieter at night." / "I should use shorter answers."). Family sees Approve / Not now / Never suggest this. Safe architecture behind the scenes: ProposalDb, SharedPrefs, no silent changes, no code shown to family. Schema designed July 5. Build in dedicated session. (Master Summary §16)
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

*Project Scout Quick Start | Last updated: July 5, 2026 | Version 18 | Upload every session | For full details use Master Summary v41*
