# Project Scout — Quick Start

Last updated: August 7, 2026
Based on commit: 1367e0572083e40962bcdbc0cae2c2149b447091
Status: Current

**Version 30**

Upload this at the start of EVERY Claude or ChatGPT session about Scout.
This is the smallest accurate handoff — what a fresh session needs in the next 60 seconds to not break anything and know what's happening right now. For full technical history and architecture, use `Scout_Master_Summary.md` (v58) — that document is the single source of truth and keeps the complete day-by-day record.

---

## Right Now — Current State & Focus

**Workflow:** `main` is now Scout's single source of truth for development (deliberate change — see `CLAUDE.md`). New work happens on short-lived feature branches named `claude/**`, merges into `main` via pull request, and the branch is deleted afterward. CI (`.github/workflows/android-build.yml`) builds on every push to `main`/`claude/**`, runs the actual JVM unit test suite (`testDebugUnitTest`), not just a compile check, and also triggers on pull requests targeting `main`.

**Recently merged PRs (most recent first):**

| PR | Branch (deleted after merge) | What it did | Status |
|----|--------|---------------|--------|
| #24 | `claude/mic-restart-computed-delay` | Computed post-TTS mic-restart delay — targets the actual latest of three cooldown deadlines instead of a flat 150ms poll that was overshooting to ~750ms real-world restart time. No threshold values changed. Also adds `DiagLog.logSelfEchoDiscarded()` (char count + timing only, never the recognized text). | **Merged** — commit `ed9f85d3b971f17474336e2d582e4c45109e03fe`. CI green. |
| #23 | `claude/deterministic-self-knowledge-fixes` | Fixes two `ScoutIntentRouter` phrasing gaps (`DATE`: "what day is today"/"what day is it"; `ASK_SCOUT_NAME`: "what should/do I call you") plus two brand-new deterministic intents (`LANGUAGE`, `TIME_OF_DAY`) that previously had no coverage at all and fell through to TinyLlama. See "TinyLlama misroute investigation" below. | **Merged** — commit `5a465e0600a255e69f0e4a78df4356402f4f2b58`. CI green. |
| #22 | `claude/remove-outdated-pdf-exports` | Removed 33 stale PDF export snapshots (old `Scout Quick Start/`, `Scout_Launch_Checklist/`, `Scout_Master_Project_Summary's/` folders) that duplicated and lagged these canonical `.md` docs. | **Merged** — commit `cc3e23c8371a180848b74aedc2aaace3ead5522d`. |
| #20 | `claude/courtesy-layer-phase1` | Courtesy Layer Phase 1 — a small fixed set of everyday phrases ("hi", "thank you", "good night", ...) work without saying Scout's name and never reach `ScoutIntentRouter`/TinyLlama/Gemini at all. | **Merged** — commit `fc2233e919218c16cf5da5958d9478752ee060de`. From a parallel session; confirmed intentional. |
| #18 | `claude/gaze-symmetry-fix` | Fixed a real horizontal gaze-tracking bias (traced to the repo's first commit, no documented reason) — one shared `GAZE_TRACKING_GAIN` (1.25×) instead of a 1.15×/1.35× split, and the "thinking" glance now picks its side 50/50 instead of always left. | **Merged** — commit `a829892d0b24cead8ae39898e5affbe01e1de7f2`. Owner confirmed the original bug on-device; **the fix itself hasn't been re-tested on the A32 yet.** |
| #17 | `claude/awareness-phase1-foundation` | Awareness Layer Phase 1 — `AwarenessState` (live signals), `AwarenessHistoryDb` (rolling history), `AwarenessResolver` (charging + connectivity transitions from existing sensors). Zero consumers read from it yet, by design. | **Merged** — commit `ff796bd2ea53a1ae36769f38929dab5c0e42ad2b`. **A32 logging trial (Tests A–D) to confirm no false events and size the retention cap hasn't been run yet.** |
| #10 | `claude/settings-reorganization` | Reorganizes Settings from five implementation-oriented sections into seven owner-oriented ones (My Household, Companion, AI, Connected Services, Privacy & Data, Builder's Workbench, Advanced & Support). Cuts five confirmed-dead rows, merges View/Share Diagnostic Report into one, adds a Donate to Scout screen reusing the website's existing Stripe Payment Links. Relocation/presentation change, not a behavioral rewrite — see below and Master Summary's August 1 entry. | **Merged** — commit `cf1f695b619bf9d861f5da371450ea7c32d053c6`. CI green including `testDebugUnitTest`. |
| #8 | `claude/companion-moments-wiring` | Wires `ScoutCompanionMomentsEngine` (PR #5) into `MainActivity`: real call site, `VoiceBank` phrase pools, shared proactive-speech timestamp, `DiagLog` entries, `JournalDb` novelty tracking. All 5 findings from an independent ChatGPT review (arrival-signal latching, entity-aware Memory phrasing, session-scoped conversation flag, executor lifecycle/generation protection, new unit tests) resolved before merge. | **Merged** — commit `a85177e95b7873250cde4e37ae7a41c1ba89f638`. CI green including `testDebugUnitTest`. |

**TinyLlama misroute investigation (traced, mostly fixed).** A real-world fully-offline conversation test (Wi-Fi off, Airplane Mode on) found several deterministic-sounding questions getting poor TinyLlama-generated answers instead of Scout's existing handlers. Traced against source before any code changed: "what day is today" and "what should I call you" were genuine `ScoutIntentRouter` phrasing gaps next to already-correct handlers (**fixed, PR #23**); "what language are we speaking" and "is it morning or night"/"what time of day is it" had *no* deterministic coverage at all (**fixed, PR #23** — new `LANGUAGE`/`TIME_OF_DAY` intents, the latter reusing `HabitLayer.TIME_SLOTS`' hour boundaries after `ScoutPresenceDecider.PresenceMode` was traced and explicitly rejected as the source). "Can you hear me?" routes correctly in code (`IDENTITY` intent, deterministic) — the leading hypothesis is an STT mismatch under degraded fully-offline recognition, **still unconfirmed, routing left untouched**, pending an adb/sqlite3 evidence pull already requested. "How long have we been talking?" and "what were we talking about?" are a genuine missing capability (no conversation-start timestamp exists anywhere) — **deliberately left unimplemented**, needs a real Working Memory design, not more prompt-stuffing. Full per-question detail in Master Summary's August 6–7 entry.

**Mic-restart timing fixed (PR #24).** Prompted by Patrick reporting clipped sentence-starts when speaking right after Scout finishes. Traced the full post-TTS gate chain and found three independent cooldown checks, each rescheduling on a flat 150ms poll, overshooting the real 650ms floor to ~750ms in practice — a scheduling inefficiency, not a wrong threshold (all three threshold values are unchanged). Also confirmed as current, correct behavior: `isThinking` fully blocks the mic from opening at all during a TinyLlama/Gemini generation (not a capture-then-discard path), and every proactive-speech system already self-blocks on `isThinking` too. Self-echo discards are now logged for the first time (char count/timing only) — closes a real diagnostic blind spot, though the self-echo guard's own known normalization-mismatch bug (see Master Summary) is still unfixed, untouched by this pass.

**Roadmap sequencing traced, nothing built yet.** Patrick's proposed next-priority order — Busy-Brain deterministic pass-through → better conversation-state window → Awareness-based direct address — was checked against the repo. The Awareness spec's own Appendix already independently mandates direct-address last, confirming that part of the order. A real, current gap was found relevant to the middle item: inside the existing 30s/40s follow-up windows, *no* wake-word or vision check runs at all today, so 2+-word TV/background speech would be treated as real conversation. Sequencing between the first two items is still an open call — nothing here was implemented.

**Settings reorganized into seven owner-oriented sections, fully merged and live on `main`.** After living with Scout day-to-day, the previous five-section Settings screen (Identity & Voice, Brain & Behavior, Builder's Workbench, Privacy & Data, Extras & Support) — organized around implementation — was replaced with seven sections organized around what an owner actually wants to do: **My Household** (memory export/import/reset), **Companion** (name, voice, captions, presence, spontaneous comments), **AI** (online features, API key management), **Connected Services** (calendar awareness), **Privacy & Data** (camera behavior, privacy policy, terms), **Builder's Workbench** (unchanged — still the only section allowed to show not-yet-active hardware rows, since it's Patrick's own long-term build workspace, not a family-facing surface), and **Advanced & Support** (donations, support, about, licenses, diagnostics). Five confirmed-dead rows were cut (Kid Safe Filter and Voice Tone toggled nothing at all; Online Brain Helper duplicated AI's real rows; Camera Controls and Cosmetics were empty "coming soon" toasts — verified via grep that none of their preference keys were read anywhere outside `SettingsActivity.kt` before removing them). View/Share Diagnostic Report merged into one row. A new Donate to Scout screen reuses the website's existing five Stripe Payment Links (fixed tiers, live-selected, external hand-off) — deliberately not Google Play Billing, since these are voluntary donations that unlock nothing; Play Billing stays reserved for future purchases that actually unlock something (premium brain tiers, paid cosmetics). The two voice-command deep links ("go online", calendar prompts) were repointed to the new screens. **No preference keys were renamed anywhere in this change.** **Not yet verified on a real device** — CI confirms `assembleDebug` and `testDebugUnitTest` both pass, but the actual on-screen behavior (all seven cards, both repointed voice deep-links, the Donate tier buttons and their Stripe links, and the hidden dev-benchmark unlock still surfacing in its new location) hasn't been exercised on the A32 yet. Full design discussion and rationale lives in Master Summary's August 1 entry.

**Real bug found by CI actually running tests (now fixed and merged):** `ScoutMemoryGate.SELF_WORDS` only recognized the user talking about *themselves* ("my", "me", "i", "us", "we") — not the user addressing *Scout* directly ("you", "your"), so "what did you learn today" failed. Fix adds "you"/"your", kept narrow so it only matters combined with a real topic word — ordinary commands like "can you set a timer" are unaffected.

**Companion Moments is fully wired and live on `main`.** During over an hour of real-device testing, Scout stayed technically stable but felt too passive and boring — he mostly watched the room and waited to be spoken to. PR #5 built the decision engine; PR #8 wired it into `MainActivity` end to end (call site, phrase pools, shared cooldown timestamp, diagnostics, persisted daily budget) and resolved all 5 findings from an independent code review before merging. **This is explicitly not "just make him talk more"** — restraint (hard gates, cooldowns, a persisted 3/day budget, and silence as the default outcome) is the core design constraint, not an afterthought. Companion Moments coexists with the already-shipped Presence system (`ScoutPresenceDecider` — return greetings, idle-silence remarks) rather than duplicating it: both read/write one shared proactive-speech timestamp, but each still compares it against its own unchanged interval (Presence 20 min, Companion Moments 45 min) — so the user experiences one Scout, not two. **What's left is real-world A32 observation to tune social timing (starting values are deliberately conservative) — that's validation, not unfinished implementation.** Full design and implementation detail lives in Master Summary's July 30 and July 31 entries.

**Recent shipped history (compressed — see Master Summary for full day-by-day detail):** 16KB page-size alignment is resolved and confirmed against a real signed release APK (`zipalign` pass, July 19). TinyLlama model delivery now works end-to-end through a real in-app download + unified startup gate (July 19–24). Personal-memory questions are now structurally gated before Gemini, TinyLlama's long-prompt crash is fixed, teaching moved to real entity/property extraction with alias support, the Presence layer (idle-silence + return greeting) shipped, and API keys are now encrypted via Android Keystore — all July 26–29, and all merged into `main` via PR #1. PR #2 and PR #3 (small CI/speech cleanups), PR #4 (`FuzzyNameMatcher.kt` generic wake-word tolerance + `ScoutSpeechAvailabilityMonitor.kt`), PR #5 (Companion Moments engine, see below), and PR #6 (CI now runs unit tests, exposing and fixing a real `ScoutMemoryGate` bug — see below) are also merged. Copyright standardized across both the app and the website (`Copyright © 2026 Patrick Evan Lippy. All rights reserved.` — Lippy Robotics stays a brand name only) via PRs #14/#16, early August.

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
- **Settings reorganization (`SettingsActivity.kt`, PR #10)** — merged and live on `main`. CI-verified (compiles, unit tests pass) but not yet exercised on a real device — confirm all seven section cards, both repointed voice deep-links, the Donate to Scout tier buttons/Stripe links, and the hidden dev-benchmark unlock before treating it as fully verified. See Master Summary's August 1 entry.
- **Awareness Layer Phase 1 (`AwarenessState`/`AwarenessHistoryDb`/`AwarenessResolver`, PR #17)** — merged and live, but zero consumers by design — nothing reads from it yet. The on-device A32 logging trial (Tests A–D, already specified in `Scout_Awareness_Layer_Spec.md`) hasn't been run — needed to confirm no false events and to size the history retention cap before this is considered complete.
- **Gaze symmetry fix (PR #18)** — merged. Patrick physically confirmed the original rightward bias on-device before the fix; the fix itself hasn't been re-tested on the A32 yet.
- **"Can you hear me?" routing — do not touch until evidence returns.** Traced as provably correct in code for the literal phrase (deterministic `IDENTITY` intent); leading hypothesis is an STT mismatch under fully-offline on-device recognition. An adb/sqlite3 evidence-pull method (timestamp-isolated, non-destructive) was already given to Patrick to confirm the exact recognized text — not yet returned. Do not change identity/routing code until it comes back. See Master Summary's August 6–7 entry.
- **"How long have we been talking?" / "What were we talking about?"** — genuinely unimplemented, not a bug. No conversation-start timestamp exists anywhere in the app today. Needs a real Working Memory / conversation-state design before either can be answered — do not paper over with more TinyLlama prompt-stuffing.
- **Self-echo guard normalization bug** — still open, unfixed (see Master Summary's TTS self-echo entry). The substring match against `lastScoutUtteranceNormalized` can miss a real echo when a prefix word changes the normalized comparison, risking a self-heard phrase getting taught back in as a fact. Self-echo discards are now at least logged (PR #24 — char count/timing only) so this is finally observable, but the matching logic itself hasn't been touched.
- **Busy-Brain vs. conversation-window sequencing — open, not decided.** Traced but not built: which of "let a deterministic intent interrupt a pending TinyLlama generation" or "close the TV/background-speech gap in the 30s/40s follow-up window" should come first. The Awareness Layer spec's own Appendix already commits to direct-address coming last after both — see Master Summary's August 6–7 entry before starting either.

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

*Project Scout Quick Start | Last updated: August 7, 2026 | Version 30 | Upload every session | For full details use Master Summary v58*

---

Copyright © 2026 Patrick Evan Lippy. All rights reserved.
