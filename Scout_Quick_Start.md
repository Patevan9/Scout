# Project Scout — Quick Start

Last updated: July 31, 2026
Based on commit: a85177e95b7873250cde4e37ae7a41c1ba89f638
Status: Current

**Version 28**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
This is the smallest accurate handoff — what a fresh session needs in the next 60 seconds to not break anything and know what's happening right now. For full technical history and architecture, use `Scout_Master_Summary.md` (v55) — that document is the single source of truth and keeps the complete day-by-day record.

---

## Right Now — Current State & Focus

**Workflow:** `main` is now Scout's single source of truth for development (deliberate change — see `CLAUDE.md`). New work happens on short-lived feature branches named `claude/**`, merges into `main` via pull request, and the branch is deleted afterward. CI (`.github/workflows/android-build.yml`) builds on every push to `main`/`claude/**`, runs the actual JVM unit test suite (`testDebugUnitTest`), not just a compile check, and also triggers on pull requests targeting `main`.

**Recently merged PRs (most recent first):**

| PR | Branch (deleted after merge) | What it did | Status |
|----|--------|---------------|--------|
| #8 | `claude/companion-moments-wiring` | Wires `ScoutCompanionMomentsEngine` (PR #5) into `MainActivity`: real call site, `VoiceBank` phrase pools, shared proactive-speech timestamp, `DiagLog` entries, `JournalDb` novelty tracking. All 5 findings from an independent ChatGPT review (arrival-signal latching, entity-aware Memory phrasing, session-scoped conversation flag, executor lifecycle/generation protection, new unit tests) resolved before merge. | **Merged** — commit `a85177e95b7873250cde4e37ae7a41c1ba89f638`. CI green including `testDebugUnitTest`. |
| #4 | `claude/speech-reliability-designs` | `FuzzyNameMatcher.kt` (generic wake-word tolerance for a renamed Scout) + `ScoutSpeechAvailabilityMonitor.kt` (honest warning when speech recognition looks unavailable) | **Merged** — commit `c14671f2592e422c1f4cafe718ecfd3e8a5cfd7e`. Fully wired into `MainActivity` (both pieces touch `onResults()`/`onError()` directly) — this is live behavior, not just an added-but-unused class. |
| #5 | `claude/companion-moments-engine` | `ScoutCompanionMomentsEngine.kt` — the decision-logic engine for the "Companion Moments" system, plus its tests | **Merged** — commit `1b5deb19dfced44529f571b30d27c622e8e12fb3`. Engine only at the time — wiring shipped separately in PR #8 above. |
| #6 | `claude/ci-run-unit-tests` | Expanded CI to actually run `./gradlew testDebugUnitTest` (not just compile) + added the `pull_request` → `main` trigger. Running tests for the first time exposed a real pre-existing bug — see below | **Merged** — commit `d1a56ac8615fd8fa065790d1318bb953f9a79127`, including the bug fix bundled on the same branch. |

**Real bug found by CI actually running tests (now fixed and merged):** `ScoutMemoryGate.SELF_WORDS` only recognized the user talking about *themselves* ("my", "me", "i", "us", "we") — not the user addressing *Scout* directly ("you", "your"), so "what did you learn today" failed. Fix adds "you"/"your", kept narrow so it only matters combined with a real topic word — ordinary commands like "can you set a timer" are unaffected.

**Companion Moments is fully wired and live on `main`.** During over an hour of real-device testing, Scout stayed technically stable but felt too passive and boring — he mostly watched the room and waited to be spoken to. PR #5 built the decision engine; PR #8 wired it into `MainActivity` end to end (call site, phrase pools, shared cooldown timestamp, diagnostics, persisted daily budget) and resolved all 5 findings from an independent code review before merging. **This is explicitly not "just make him talk more"** — restraint (hard gates, cooldowns, a persisted 3/day budget, and silence as the default outcome) is the core design constraint, not an afterthought. Companion Moments coexists with the already-shipped Presence system (`ScoutPresenceDecider` — return greetings, idle-silence remarks) rather than duplicating it: both read/write one shared proactive-speech timestamp, but each still compares it against its own unchanged interval (Presence 20 min, Companion Moments 45 min) — so the user experiences one Scout, not two. **What's left is real-world A32 observation to tune social timing (starting values are deliberately conservative) — that's validation, not unfinished implementation.** Full design and implementation detail lives in Master Summary's July 30 and July 31 entries.

**Recent shipped history (compressed — see Master Summary for full day-by-day detail):** 16KB page-size alignment is resolved and confirmed against a real signed release APK (`zipalign` pass, July 19). TinyLlama model delivery now works end-to-end through a real in-app download + unified startup gate (July 19–24). Personal-memory questions are now structurally gated before Gemini, TinyLlama's long-prompt crash is fixed, teaching moved to real entity/property extraction with alias support, the Presence layer (idle-silence + return greeting) shipped, and API keys are now encrypted via Android Keystore — all July 26–29, and all merged into `main` via PR #1. PR #2 and PR #3 (small CI/speech cleanups) are also merged.

---

## 1. Who Is Patrick

Patrick Lippy — creator and developer of Scout. NOT a professional programmer. Stroke survivor, dyslexic, blind in right eye, type 1 diabetic.

- Explain everything at screenshot level. Keep messages clear and not visually overwhelming.
- Always provide full paste-ready files, one at a time — or exact CTRL-F / CTRL-R surgical edits. No snippets. No partial files.
- Wife: Diana | Son: Elijah (age 9) | Dog: Nicolas. Names must NEVER be hardcoded.
- Build instructions: Android Studio only — Build → Clean Project, then Build → Assemble Project. Do NOT use gradlew in terminal (JAVA_HOME error on Patrick's machine).

**Both Claude and ChatGPT are active collaborators, and they independently cross-review each other's work on Scout.** That's a good thing — but it means a summary either AI writes (including this document) can drift from what the code actually does. If something in these docs matters for the change you're about to make, spot-check it against the real source first rather than trusting the summary at face value. This applies both ways: when reviewing the other AI's work, and when trusting your own past session's notes.

---

## 2. What Scout Is

Scout is a calm family companion robot running on a Samsung Galaxy phone in landscape mode as a permanent face display. Animated eyes, speaks, listens, sees via camera, remembers the family.

- Package: com.example.scoutface | Language: Kotlin + C++ NDK
- Active test device: Samsung Galaxy A32 — primary development and testing device
- Secondary device: Samsung Galaxy Fold 7 (12GB RAM) — listed as primary, still needs dedicated stability testing
- App: 7-day free trial, then $9.99 one-time. No automatic charges. No subscriptions. Ever.
- Brains: TinyLlama 1.1B (offline, default) + user's own free Gemini key (online, opt-in, ON by default when a key is saved)
- Website: https://patevan9.github.io/lippyrobotics.github.io | Company: Lippy Robotics

---

## 3. Scout's Core Philosophy

Scout should feel: Calm. Thoughtful. Quietly alive. Emotionally subtle. Occasionally curious.
Scout should NOT feel: Excited. Scripted. Fake. Cartoonish. Hyperactive. Constantly praising.

**Stability > Features | Presence > Intelligence | Honest > Fake cheerful | Local-first > Cloud | Predictable > Flashy**

Scout must never fake a signal he cannot actually observe — no invented emotion, laughter detection, or "focus" reading. This is a hard, explicit design rule, most recently reaffirmed for Companion Moments (see above).

---

## 4. Known Issues — Do Not Touch Without Discussion

- **Fold 7 stability testing** — still not done as its own dedicated session; all recent testing has been on A32.
- **Open Source Credits** — `THIRD_PARTY_NOTICES.md` started, not a full in-app screen or website page yet.
- **Play Store listing** — description, screenshots, content rating not started.
- **STT name recognition** — "Scout" sometimes misheard. Now generically tolerant for any configured name via `FuzzyNameMatcher.kt` (merged, PR #4, live).
- **Barge-in** — deliberately disabled. PARKED, do not re-enable without discussion.
- **Companion Moments (`ScoutCompanionMomentsEngine.kt` + wiring, PR #5 + PR #8)** — merged and live on `main`, not experimental. Untuned: starting values (45-min shared cooldown, 3/day budget, 0.50 confidence threshold, 2–24h category cooldowns) are deliberately conservative. Needs real-world A32 observation before loosening anything — see Master Summary's July 31 entry.

---

## 5. Working Rules — Always Apply

- Full paste-ready files only, one at a time. No snippets. No partial files.
- Surgical CTRL-F / CTRL-R edits — always specify which file tab to click first.
- Build: Android Studio only → Build → Clean Project → Build → Assemble Project.
- Some Scout files have NO indentation. If search fails, try shorter unique string.
- Some logic lives in TWO places — change both or Scout flickers.
- One safe change at a time. Build and test before the next change.
- Never touch speech, camera, or download systems without explicit discussion.
- Never touch `ScoutFaceView` casually — it is Scout's visual heart.
- New work belongs on a `claude/**` branch merged via PR, not directly on `main`.
- Patrick is not a professional programmer — screenshot-level explanations always.

---

## 6. Versioning Quick Reference

- Scout 1.0 — The Beginning (launch)
- Scout 1.0.1 — bug fixes only
- Scout 1.1 — Growing Up (first feature update)
- Scout 2.0 — A New Chapter (major milestone)
- After each update: Welcome Back screen + optional spoken message + Google Play release notes

---

*Project Scout Quick Start | Last updated: July 31, 2026 | Version 28 | Upload every session | For full details use Master Summary v55*
