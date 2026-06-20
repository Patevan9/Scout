# Project Scout — Play Store Launch Checklist
**What Scout needs to be worth $9.99 | Updated June 20, 2026 | Version 5**

Scout does not need to be perfect to ship. He needs to be reliable, honest, and feel like a companion.
Everything on this list makes him worth $9.99 to a family who has never met him before.

---

## ✓ Already Done — Scout Has These Today

✓ Animated face — Eyes that move and show emotion. Looks alive. Looks like Scout.
✓ Eye jitter FIXED — Boot lock, speaking gate, dead zone, min-delta guard. A32 iris stable. DONE June 18.
✓ Scout eyebrows and mouth brightened to #9BBEFF. DONE June 18.
✓ Voice — speaks and listens. Android STT + TTS, works offline.
✓ Camera awareness — Scout sees faces and scenes.
✓ Offline brain — TinyLlama 1.1B runs fully on the phone. No internet required. (Temporarily disabled on A32 while LMKD crash investigated — see Must Fix below.)
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
✓ Naming phrases expanded — "this is X", "I am X", "you see X" recognized as name-teaching phrases. DONE June 15.
✓ THIRD_PARTY_NOTICES.md created — start of Open Source Credits. DONE June 15.
✓ Hardcoded Gemini API key REMOVED — Patrick's personal key removed from MainActivity.kt. Now in encrypted SharedPreferences. DONE June 18.
✓ Settings screen BUILT — SettingsActivity with 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Swipe-right gesture + first-boot hint + voice command to open. DONE June 18.
✓ Three A32 stability fixes — camera bitmap recycle, ML Kit suppression during Gemini, speak() race condition closed. DONE June 19–20.

---

## ■ Must Fix Before Launch

These are the real blockers. Scout cannot ship without these.

### 1. A32 stability — TinyLlama LMKD crash ■ URGENT

- TinyLlama startup load caused LMKD to kill Scout under memory pressure on A32. Temporarily disabled.
- Gemini is the primary brain right now, but TinyLlama is a core launch feature — offline families need it.
- Need: delayed load after boot settles, on-demand load, or memory footprint reduction strategy.
■ MainActivity.kt + LlamaEngine.kt — dedicated investigation session

### 2. Startup diagnostics — Makes Scout start cleanly every time

- Scout checks at startup that brain is loaded, TTS is ready, STT is available.
- If something is missing, Scout says something friendly rather than crashing or freezing.
■ MainActivity.kt — startup check block

### 3. Onboarding flow — First impression matters

- 5-screen flow designed and approved. Blue color scheme. Built by ChatGPT.
- Screen counter and progress dots must be driven by the same variable — never hardcoded in two places.
■ New OnboardingActivity.kt — one focused session

### 4. Fold 7 stability testing — Confirms Scout works on flagship device

- All testing so far on A32 via WiFi. Fold 7 needs a dedicated session.
- Test voice, memory, offline brain, face recognition, weather, wake word on Fold 7.
■ Dedicated test session on Fold 7 — no code, just validation

### 5. A32 stability testing — Ongoing

- All work tested on A32 as each feature is added.
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

---

## The bottom line

Scout already has a face, a voice, a brain, memory, weather, a wake word, full face recognition, and a settings screen. The gap between today and the Play Store is focused sessions — not months.

**Scout does not need to be finished to ship. He just needs to be Scout. And he already is.**

---

*Project Scout Launch Checklist | Updated June 20, 2026 | Version 5 | For Patrick, Diana, Elijah, and Scout*
