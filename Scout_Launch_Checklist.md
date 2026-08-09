# Project Scout — Play Store Launch Checklist

Last updated: August 8, 2026
Based on commit: 7315fac0b0769298d9621452a0e055301f5204d2
Status: Current

**Version 24**

Scout does not need to be perfect to ship. He needs to be reliable, honest, and feel like a companion.
This document tracks ONLY what determines whether Scout can safely and honestly ship to the Play Store — not general feature ideas. Full itemized history with dates and commits lives in `Scout_Master_Summary.md`; post-launch feature plans live there too (see the pointers below instead of a second copy of that list here).

*Scope note (July 30, 2026): this document was trimmed to remove non-launch-blocking future-feature content (Spanish support, hardware mode, cosmetic expression packs, calendar integration, Scout Behavior Learning, the Support Scout donation screen, and similar) that had accumulated here. None of that work affects whether Scout can ship — it's tracked in Master Summary instead, so it isn't duplicated or allowed to drift between two documents.*

---

## ✓ Already Done — Scout Has These Today

Condensed ship-readiness summary. Full itemized history with dates and commit hashes lives in `Scout_Master_Summary.md`.

**Core presence & interaction**
- Animated face, voice (Android STT + TTS, works offline), camera awareness (face + scene detection, throttled for A32 memory health), thinking/idle expressions.
- TinyLlama 1.1B offline brain — runs fully on-device, no internet required, with automatic Gemini fallback. Delivery to a real device now works end-to-end (in-app download through a unified startup gate) — testers no longer need to manually push the model file to the device.
- Wake word filter, 30-second open-conversation window (now with an explicit "closed on purpose" override via Better Conversation State Phase 1, PR #26 — see below), boot-ready window, "repeat that" intent.
- Weather via NWS (free for commercial use). Never speaks stale cached weather as though it were current when fresh weather can't be obtained — offline, or online but NWS unreachable, both get an honest "can't check right now" answer instead (PR #27, Aug 8). The existing per-type freshness-window cache is unchanged for normal online efficiency. Total offline mode ("go offline" blocks all internet features).
- Busy-Brain Phase 1 (PR #28 + PR #29, Aug 8) — while TinyLlama/Gemini is generating an answer, the microphone can reopen for an approved set of read-only/conversational requests instead of staying fully closed for the whole generation. **Merged and CI-verified; real-device behavior not yet exercised on the Fold 7 — see item 4 below.**
- Boot greeting now opens Scout's own Better Conversation State turn (PR #27, Aug 8) — an immediate reply to Scout's own greeting no longer requires saying his name first.

**Memory & family recognition**
- Flexible fact memory — teaching and recall for any fact, via real entity+property extraction with multi-alias support (not brittle sentence-template matching).
- Face recognition — ArcFace-based (512-dim), margin-checked, profile-pollution-gated so borderline matches can't corrupt a profile. Family introduction flow ("this is my son Elijah") and a "Scout, forget [name]" re-registration command.
- Personal-memory questions ("what's my wife's name") are structurally gated before they can ever reach Gemini — a hard privacy guarantee, not something that depends on exact phrasing.
- Presence layer — idle-silence acknowledgment and a real proactive "welcome back" greeting, both gated by genuine sustained camera presence rather than a flag that was almost always true.

**Stability & privacy**
- A32 confirmed stable through multiple root-caused crash classes, most recently a camera/ML Kit/SpeechRecognizer startup collision (July 28) — fixed via staggered subsystem startup, not a guess.
- API keys encrypted at rest via the Android Keystore (AES-256-GCM) — no more plaintext key storage.
- Diagnostic reporting system (single merged Diagnostic Report row as of PR #10, always offers Share) — verified to never contain speech text, names, memories, face data, or location.
- Settings screen (reorganized into 7 owner-oriented sections, PR #10, Aug 1), 5-screen onboarding flow (offline by default for new installs), battery optimization prompt.

**Compliance**
- Hardcoded Gemini API key removed from source.
- Google Play Data Safety analysis complete — accurate declarations for Gemini query text (shared, optional) and weather location (shared, optional). No data collected by Lippy Robotics.
- **16KB native library page-size alignment — RESOLVED**, confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK: all 11 previously-flagged libraries pass individually, "Verification successful" overall. See the Legal & Website section below for the short version of the investigation, or Master Summary for the full trail.

---

## ■ Must Fix Before Launch

These are the real blockers. Scout cannot ship without these.

### 1. A32 stability — ✓ DONE, confirmed stable

TinyLlama re-enabled with a safe delayed-load strategy; on-demand load also wired as a Gemini fallback. Model delivery to a real device now works end-to-end through an in-app download flow. Multiple crash classes root-caused and fixed, most recently a startup-timing collision between camera, ML Kit, and the speech recognizer (July 28).

### 2. Startup diagnostics — ✓ DONE

TTS failure shows a Toast with restart instructions. STT unavailability gets a spoken warning 4 seconds after boot, logged to JournalDb.

### 3. Onboarding flow — ✓ DONE

Full 5-screen `OnboardingActivity.kt`. First-boot redirect. New installs default to offline mode.

### 4. Fold 7 stability testing — Not yet done

Fold 7 is listed as the primary target device, but active testing has continued to happen on the A32. Still needs its own dedicated validation session — voice, memory, face recognition, weather, wake word, all confirmed on Fold 7 specifically before shipping. **Now also covers validating Busy-Brain Phase 1's microphone/delivery behavior (PR #29, merged Aug 8) on a real device** — does the mic actually reopen at the right moment during a pending generation, do the approved deterministic requests answer correctly mid-generation, does a resolved answer actually avoid interrupting speech already in progress. Merged and CI-verified is not the same as proven — do not describe Busy-Brain as fully stable until this session happens.

### 5. A32 stability testing — Ongoing, in good shape

Continues to be exercised as each feature lands. No unexplained crashes as of the most recent root-caused fix (July 28).

### 6. In-app purchase / trial enforcement — Not yet implemented

The intended commercial model is free download → 7-day full trial → $9.99 one-time permanent unlock, no subscription. No Google Play Billing integration exists anywhere in the app today (confirmed via source grep for `BillingClient`/`PurchasesUpdatedListener` — see Master Summary's August 1, 2026 entry) — the $9.99 shown on a Play Store listing would currently be just a listing price, with nothing in the app to actually start/track a 7-day trial or lock advanced features afterward. A real launch blocker, not a post-launch nicety, since the business model depends on it.

---

## ■ Legal & Website — Required for Launch

### 7. Privacy Policy — ✓ DONE

In-app scrollable dialog (Settings → About Scout → Privacy Policy), fully offline. Covers offline-first design, Gemini as an optional user-key-only service, NWS weather coordinates, and that Lippy Robotics collects no personal data. Website version also available.

### 8. Terms of Use — ✓ DONE

In-app scrollable dialog + `terms.html` in the repo root, ready for the website. Acceptance clause, service-as-is limitation, third-party services clause, changes-to-terms clause.

### 9. Open Source Credits — Priority 3, still needed

`THIRD_PARTY_NOTICES.md` started (MobileFaceNet MIT credit done). A full in-app "Open Source Licenses" screen and a matching website page are both still needed before launch — llama.cpp, TinyLlama, Android libraries, MobileFaceNet, and anything else bundled needs proper attribution.

### 10. Website

Add the website link to the Google Play listing, Facebook page, and About Scout screen. A "What's New" / development-updates page is a nice-to-have, not a blocker.

### 11. 16KB native library page-size alignment — ✓ RESOLVED

Briefly re-litigated across three sessions (July 17–19) before landing on a confirmed answer — the short version: a real Fold 7 device flagged 11 native libraries as misaligned on a **debug** build (July 18); investigation found the underlying `.so` files were mostly already correctly aligned, and the one real defect (`libimage_processing_util_jni.so`) had already been fixed by an earlier ML Kit version bump. Patrick then built a signed **release** APK and ran Google's own `zipalign -c -P 16 -v 4` verification tool directly against it — every one of the 11 previously-flagged libraries individually passed, with an overall "Verification successful." The debug-only dialog does not appear on the release build at all. **Play Store submission is unblocked on this front.** Full investigation trail (each hypothesis, each correction) lives in Master Summary if it's ever needed again.

---

## ■ Play Store Listing

Required to submit to Google Play. Not yet started.

- App description — Lead with something like "Turn an old phone into a friend." Honest about what Scout is.
- Screenshots — Scout's face, onboarding screens, weather response, memory recall, settings. 5–8 minimum.
- Privacy policy link — required by Google Play.
- Content rating questionnaire — Scout is family-safe; should be straightforward.
- Short description — 60 characters max, e.g. "A calm AI companion for your whole family. Private. Local. Yours."

---

## Not launch-blocking — relocated, not duplicated here

The following were previously tracked in this document but don't determine whether Scout can ship. They're tracked in `Scout_Master_Summary.md` instead so they aren't maintained in two places that can drift apart:

- **Scout Behavior Learning (Tier 1 suggestions / Tier 2 Scout Dev telemetry)** — approved design, explicitly post-launch (Scout 1.1+). See Master Summary §16.
- **Support Scout donation screen** (in-app optional contribution tiers) — see Master Summary §5.
- **Post-launch feature ideas** — Spanish language support, hardware mode (KEYESTUDIO chassis), cosmetic expression packs, calendar integration, full mood system, brain pack upgrades, robot renaming, voice recognition, and similar — see Master Summary §14 (Hardware), §16b (Future Polish), §17 (Language Support), and its dated history.
- **Companion Moments** (`ScoutCompanionMomentsEngine.kt` + `MainActivity` wiring, both merged and live on `main` as of PR #8 — see `Scout_Quick_Start.md` for current status) — real-world A32 observation to tune social timing is ongoing, but not something the Play Store checks for, so not tracked as a launch item.

---

## The bottom line

Scout already has a face, a voice, two brains (Gemini + TinyLlama), memory, weather (now honest about staleness, PR #27), a wake word with an explicit conversation-close override (Better Conversation State Phase 1, PR #26), Busy-Brain Phase 1's pending-generation handling, reliable face recognition for the whole family, a complete onboarding flow, startup diagnostics, a working end-to-end model download flow, a settings screen, encrypted API key storage, and a stable icon. The A32 is stable through several root-caused fixes. 16KB alignment is confirmed resolved against a real signed release APK.

**Remaining launch blockers: Fold 7 dedicated stability testing (now including Busy-Brain Phase 1's real-device validation), in-app purchase/trial enforcement (no Google Play Billing integration exists yet), the Open Source Credits screen (in-app + website), and Play Store listing content (description, screenshots, content rating).** Everything else on the "must fix" list above is done.

**Scout does not need to be finished to ship. He just needs to be Scout. And he already is.**

---

*Project Scout Launch Checklist | Last updated: August 8, 2026 | Version 24 | For Patrick, Diana, Elijah, and Scout*

---

Copyright © 2026 Patrick Evan Lippy. All rights reserved.
