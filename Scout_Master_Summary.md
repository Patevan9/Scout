# Project Scout — Master Project Summary

Last updated: August 19, 2026
Based on commit: 4b1c979d5ab6f4d28f27bcbc2527e1cd007efdec
Status: Current. This pass catches up PRs #41–52 (all merged) — memory-integrity backstops, a gaze-drift amplitude tune, two Companion Moments refinements, widened family/vision routing, a self-identity guard, a TeachExtractor question-vs-statement fix, the thinking-face lifecycle fix, the courtesy acknowledgment extension, the Companion-Moment-vs-return-greeting priority fix, and correlated TTS lifecycle diagnostics. **PR #53** (`CourtesyIntent.WELCOME_BACK`) **is open for review, not yet merged.** A second fix — the `pendingAiAnswer` lifecycle design (expiry, a presence-completion guard, supersede-on-new-question) — has an **approved design**, but its only implementation exists uncommitted in a different session's local sandbox, has **not been independently reviewed or approved**, and must not be pushed, opened as a PR, or merged without that review; see "What's left" below. PR #38's calendar-follow-up flow and PR #35's TTS fix (Diana's S24) both remain separately pending real-device validation, unchanged by this pass.

**Version 62**

Upload this document at the start of every new Claude or ChatGPT conversation about Scout.
This is the single source of truth.

---

## August 16–19, 2026 — PRs #41–52 Merged, PR #53 Open: Memory-Integrity Backstops, Gaze Amplitude, Companion Moments Refinements, Family/Vision Routing, Self-Identity Guard, TeachExtractor Fix, Thinking-Face Lifecycle, Courtesy Extensions, Return-Greeting Priority, TTS Diagnostics

✓ **Context.** A long, single continuous session covering twelve merged PRs (#41–52) plus one open for review (#53), almost all driven by real-device findings on the Fold 7 (a handful on the A32), each traced against actual source before any code changed, per this document's standing discipline. Two independent reviewers were used throughout — Claude implementing, ChatGPT independently re-reviewing several of the trickier diffs (the TTS-diagnostics correlation fixes in particular) before merge.

### PR #40 — docs sync only (already reflected)

✓ **PR #40 merged** (branch `claude/docs-sync-pr37-39`, merge commit `5aef7c51df45f3a62d284315495b822e1691fe48`) — the routine documentation sync for PRs #37–39, already fully reflected in the August 12–15 entry below and this document's own prior header. No code change.

### Memory-integrity backstops (PR #41)

✓ **PR #41 merged** (branch `claude/memory-integrity-capability-reminder`, merge commit `f36b289b885f50d1c4554e9148e6a47985ef63a3`) — two refined integrity backstops on top of PR #39's memory-confirmation work. **Layer 1 (deterministic, before either model):** new `ScoutFactExtractor.looksLikeMemoryCapabilityQuestion()` detects self-referential questions like "Can you learn?"/"Do you have a memory?", gated so a specific referent right after the verb ("do you remember my birthday," "can you remember to grab milk") is excluded and keeps routing normally; `ScoutIntentRouter` routes a match to `IDENTITY`, answered truthfully from real TruthDb state summed across every known entity (not just the primary user). **Layer 2 (after either model, unconditional):** new `containsCapabilityDenial()` catches global capability denial ("I cannot learn," "I don't retain information") while preserving truthful specific-fact-absence answers ("I don't have a memory of that"); new `containsReminderPromise()` catches future reminder-scheduling offers and completion claims ("I'll remind you on January 27th," "I've set a reminder") while preserving ordinary recollection language ("I can remind you what you told me earlier") — Scout has no reminder system, so this only stops false promises about one, not builds one. Both folded into a combined `applyMemoryIntegrityGuards()` alongside PR #39's existing (unchanged) `applyRetentionClaimGuard()`, replacing both of `MainActivity`'s generative-output call sites. 25 new tests; 99/99 in the touched suite passed locally (kotlinc + JUnit, no Android SDK in this environment).

### Face: gaze-drift amplitude tune (PR #42)

✓ **PR #42 merged** (branch `claude/face-gaze-drift-amplitude-tune`, merge commit `c03d834f918ecd25647a51227dd8c93953b094c6`) — a conservative first-pass amplitude increase on the existing gaze-driven whole-face drift in `ScoutFaceView.kt`: `faceGazeDriftX` `lookX * 0.32f → lookX * 0.60f`, `faceGazeDriftY` `lookY * 0.26f → lookY * 0.38f`. At max deflection the old coefficients capped whole-face travel at ~26/14px on a 1920×1080 canvas — under 1.5% of screen width, effectively unreadable even though the eyes (which travel at `lookX * 1.10`) were clearly moving. New coefficients raise travel to ~48/21px, still well inside the ~340px margin to the canvas edge — no clipping risk. Every feature keyed off `faceCx`/`faceCy` (eye sockets, brows, mouth) now follows for free. Explicitly unchanged: gaze timing, spring physics, iris travel amplitude, frame scheduling, breathing/blink/micro-motion/saccades. No pure-Kotlin test surface (`ScoutFaceView` draws via `Canvas`) — **real-device judgment needed on the Fold 7/A32, not yet re-tested.**

### Companion Moments: two more real-device refinements (PRs #43, #44)

✓ **PR #43 merged** (branch `claude/companion-memory-eligibility-filters`, merge commit `dc466e0a6095b8afeec3454c2e71bfbaa7fbab05`) — real-device finding: startup often produced trivial/repetitive Memory callbacks ("...my name is Scout," "...your name is Patrick") and, separately, a person-ranking statement ("...your favorite son is Elijah"). Traced to `MainActivity.buildCompanionSignals()` feeding every TruthDb row from every entity into Memory scoring with zero eligibility filtering. Four filters added, confined to that one function: (1) exclude `ENTITY_SCOUT`'s own facts; (2) exclude bootstrap/identity keys (`name`, `aliases`) via new `ScoutCompanionMemoryEligibility.isCompanionMemoryEligible()`; (3) widen the JournalDb novelty lookback from 48 hours to 30 days, closing a gap where the "don't repeat what was recently spoken" reset was quietly expiring and letting the same stale fact re-win indefinitely; (4) exclude `favorite_` keys shaped like a person-ranking statement (`favorite_son`, `favorite_daughter`, ...) — the read-side safety net for a real `TeachExtractor` write-path bug (documented here, **not fixed in this PR** — see PR #47 below, which fixed it directly). 8 new tests; 26/26 in the touched file, 53/53 including the untouched sibling engine test suite.

✓ **PR #44 merged** (branch `claude/companion-environment-live-facecount-recheck`, merge commit `1085c3769c7438a4a12e1a566409e82426f40eb6`) — real-device finding: Scout could say "It's nice having you both around" while only one person was actually present, since the `ENVIRONMENT` category's `secondFaceJustAppeared` signal is a 5-minute-latched event with nothing re-checking whether the second person was still there by the time Scout actually spoke. Fix: one more condition added to `speakCompanionMoment()`'s existing last-mile `blockReason` re-validation (already re-checks `isSpeaking`/`isThinking`/etc. right before `respond()`) — if `lastFaceCount < 2` for an `ENVIRONMENT` candidate at that final moment, it's discarded silently via the same existing pattern (nothing consumed; cooldown/budget/novelty untouched, so the next eligible check can still try). `SECOND_FACE_ARRIVAL_MAX_PENDING_MS` deliberately left at 5 minutes, not touched in this pass. One file, +11/−0. No unit-test surface (reads an `Activity`-level field) — **on-device verification needed**: have a second person join then leave, confirm the line stops once they're gone.

### Family/vision routing widened + a real vision capability guard (PR #45)

✓ **PR #45 merged** (branch `claude/family-vision-routing-capability-guard`, merge commit `9f75050b7e6c82cae752d20da93a801d4ce6b2c1`) — two real-device findings, both gaps in deterministic routing that let a fact-blind/vision-blind generative model answer instead of Scout's real subsystems. **Family:** 5 more literal `FAMILY_NAMES` phrasings ("What names do you know in my family?," "Who in my family do you know?," ...) that previously reached TinyLlama with a flat fact dump and produced a hallucinated generic answer, instead of the deterministic `handleFamilyNamesQuery()`. **Vision, three layers** (deliberately kept separate from `ScoutMemoryGate`, per explicit correction): (1) 4 more literal `VISION` router phrasings; (2) new standalone `ScoutVisionGate.kt` — a small pure topic-word + self-word gate checked in `handleUnknownIntent()` before Gemini is ever considered, routing a match straight to the existing deterministic `handleVisionIntent()` — deliberately excludes bare "look"/"looking"/"watching" (too common as unrelated filler); (3) new `ScoutFactExtractor.containsVisionCapabilityDenial()`, a Layer-2 output backstop catching global camera denial ("I don't have a camera") while preserving truthful current-state reports ("I don't see anything I recognize right now") — the first output guard Gemini's replies have ever had for vision specifically, since its system prompt carried zero vision-capability confirmation or scene data. `applyMemoryIntegrityGuards()` (PR #41) renamed to `applyScoutCapabilityIntegrityGuards()` now that it covers memory, reminders, and vision together. 152/152 tests passed locally across the four touched suites. **On-device verification needed** for the vision-gate wiring specifically (no pure-Kotlin surface in `handleUnknownIntent()` itself).

### Self-identity guard (PR #46)

✓ **PR #46 merged** (branch `claude/self-identity-third-person-guard`, merge commit `050b79958e3a4c921291e96bb0221d7b542c473c`) — real-device finding: a self-referential family-belonging question ("Scout, are you part of the family?"/"You're one of us") reaching a fact-blind Gemini or an empty-TruthDb TinyLlama fallback could produce a reply that treated Scout's own name as an unrelated third party ("Scout is not mentioned... may have moved or passed away"). Two layers: `ScoutFactExtractor.looksLikeSelfFamilyBelongingStatement()` (Layer 1, subject-anchored so it doesn't swallow a real `FAMILY_NAMES` question like "who is a part of my family," whose subject is "who," not Scout) routes to a deterministic `IDENTITY` answer; `containsSelfThirdPersonConfusion()` (Layer 2) is a narrow, subject-anchored output backstop for the third-person-confusion pattern specifically, replacing it with "That's me you're asking about — I'm right here, I haven't gone anywhere."

### TeachExtractor: stop inventing "favorite_" for plain statements (PR #47)

✓ **PR #47 merged** (branch `claude/teach-extractor-favorite-prefix-fix`, merge commit `a23e41939fda7b382d4e357c09e61aaba58731e9`) — the write-path fix for the mislabeling PR #43 had already documented and filtered around on the read side. The generic `"my X is Y"` fallback used to auto-prepend `"favorite_"` to any label that didn't already start with the literal word "favorite," so "my son is Elijah" silently became `favorite_son = "Elijah"` instead of `son_name = "Elijah"`, and "my mentor is Sam" became `favorite_mentor = "Sam"` even though Scout has no such concept. Fix, two parts: (1) the fallback now stores `rawLabel` exactly as spoken via `FactKey.custom()` — a `favorite_` key is created **only** when the user's own words literally begin with "favorite" (no category allowlist, which would have missed exactly the "mentor" case above); (2) bare "my wife/son/dog is X" (no "'s name is," no "this is") now match the existing dedicated `WIFE_NAME`/`SON_NAME`/`DOG_NAME` blocks directly, guarded by the same `NON_NAME_WORDS` check the other bare "is X" patterns already use. PR #43's read-side `ScoutCompanionMemoryEligibility` filter is left in place as a safety net for any other route that could produce the same key shape, not made redundant by this fix.

### Thinking-face lifecycle fix (PR #48)

✓ **PR #48 merged** (branch `claude/thinking-face-lifecycle-refresh`, merge commit `2a90010cc142982f7a2db77cc39daa2600e08ea2`) — traced why Scout's face showed no thinking expression at all for most of a real generation wait. `finishThinking()` (which clears the face's thinking visual) is called at generation-**dispatch** time, deliberately early — a Busy-Brain PR #29 design choice so `maybeStartListening()`'s `isThinking` gate doesn't block the mic reopening for a follow-up while a generation is still running. That left the face with no thinking cue for the actual wait the user perceives, since `busyBrainState.isPending` — which already spans that whole window by design — was never consulted for the face at all. Fix: new `refreshThinkingFaceState()` derives the face's thinking visual from `isThinking || busyBrainState.isPending` (not from `isThinking` alone), wired into all ~17 call sites touching either signal, with no change to either signal's own timing or meaning. Verified before opening the PR that every transition into/out of `busyBrainState.isPending` (success, failure, discard, timeout, conversation close, model-unavailable fallthrough) leaves the face able to clear, not stuck.

### Courtesy layer: acknowledgment closers + lead-in tolerance (PR #49)

✓ **PR #49 merged** (branch `claude/courtesy-acknowledgment-leadins`, merge commit `f0483c5743ec960af7c04f2fab472bc2d4c03529`) — real-device finding (A32): bare conversational closers ("okay"/"alright"/"sounds good"/"you're welcome"/"got it") and any courtesy phrase led in with "okay,"/"alright,"/"got it," had no deterministic handling anywhere and fell through to Gemini/TinyLlama like a real open-ended question — slow enough on the A32 to trigger a Busy-Brain filler phrase in reply to a bare "thank you." Fix: new `CourtesyIntent.ACKNOWLEDGE` matched via `ScoutCourtesyMatcher`'s existing exact-string table; new lead-in stripping (`LEAD_INS = ["okay","ok","alright","got it"]`) strips **exactly one** recognized filler lead-in from the **start only**, tried once, no recursion — if the remainder isn't an exact hit, the original untouched string still reaches `ScoutIntentRouter` normally, so "Okay, what is the weather?" is completely unaffected. 16 new tests including adversarial negatives ("I thanked Diana yesterday," "okay, what is the weather?" surviving unmatched).

### Companion Moments vs. return greeting: priority race fixed (PR #50)

✓ **PR #50 merged** (branch `claude/companion-moment-return-greeting-priority`, merge commit `a48d473db0f8718bdaf1c36a26e613e1fbcc58d5`) — real-device finding, investigated as a two-part report. The "Scout said 'My name is Scout' as a Companion Moment on arrival" half was traced and found **structurally impossible** on current `main` — PR #43's bootstrap-key/`ENTITY_SCOUT` filters are intact and would prevent that exact candidate; leading hypothesis was a build predating PR #43, left for Patrick to confirm/reinstall rather than "fixed" speculatively. The second, confirmed-reproducible half: Companion Moments has no stabilization wait the way the return greeting does (`RETURN_STABILIZATION_MS`), so its background evaluation can complete and speak **before** the return greeting is even attempted — and once it does, it stamps the same shared proactive-remark cooldown clock the return greeting's own cooldown reads, suppressing "welcome back" outright for up to 20 minutes rather than just delaying it. Fix: new pure `ScoutReturnGreetingGate.isStabilizing(genuineAbsenceMarked, returnStabilizingSinceMs)` gates `maybeMakeCompanionMoment()` — while a genuine return is still stabilizing, Companion Moments must not produce or speak a candidate, so the return greeting always gets first opportunity. Scoped to exactly this one condition; Companion Moment scoring, cooldowns, PR #43's eligibility filters, and the return greeting's own wording/thresholds are all untouched. 5 new tests. CI initially failed on a missing `import` (caught via job logs, fixed, re-verified green).

### TeachExtractor "you see X" vision-question guard (PR #51)

✓ **PR #51 merged** (branch `claude/teach-extractor-vision-question-guard`, merge commit `351f6d5de93b5ca8620ea34e70612a0280c88ff7`) — Fold 7 finding: "Do/Can you see colors?" was mistaught as a person's name ("I'll hang on to Colors") instead of answered as a vision-capability question. Root cause: `TeachExtractor`'s `\byou see ([a-z]+)\b` pattern, written for a camera-introduction statement ("you see Patrick"), also matches a capability question, since the question keeps "you see X" intact as a contiguous in-order substring — a collision `this is X`/`i am X`/`that is X` don't share, since a genuine question inverts those to "is this"/"am i"/"is that." Fix: a local `YOU_SEE_QUESTION_LEAD` guard (question-auxiliary immediately before "you see") checked right next to that one pattern only — anchored to the phrase, not the start of the utterance, so a wake-word prefix ("Scout, can you see colors?") can't defeat it. No denylist of "colors"/"color." Empirically verified (compiled + run, not just traced) against all 8 reported phrasings — exactly the 4 containing "you see X" were broken, confirmed fixed; the other 4 were already safe via `ScoutVisionGate`/direct router match. 6 new tests, including a wake-word-prefixed case and a regression that bare "You see Patrick" still teaches the name.

### Correlated TTS lifecycle diagnostics (PR #52)

✓ **PR #52 merged** (branch `claude/tts-lifecycle-diagnostics`, merge commit `4b1c979d5ab6f4d28f27bcbc2527e1cd007efdec`) — instrumentation-only, for a separate Fold 7 report: a generated reply displayed via captions but was never spoken, the mic stayed off, and only the ~45s `MAX_SPEAKING_DURATION_MS` watchdog eventually recovered it. Traced to the one path that can produce a re-entrant `speak()` call — `deliverAiResult()` holding an answer in `pendingAiAnswer` while Scout was busy, later drained by a *different* utterance's own `onDone()`/`onError()`. New `DiagLog` "TTS" tag with five correlated events (`requested`/`speak_call`/`started`/`done`/`error`), each carrying a per-dispatch id and a `NORMAL`/`DRAINED_PENDING_ANSWER` source — no speech text, ever. Two correctness rounds from independent review before merge: (1) source was initially read from a single mutable "last dispatch" field, which is racy for back-to-back/re-entrant dispatches — fixed by storing source per-dispatch-id in a `ConcurrentHashMap` (also needed since `TextToSpeech`'s callbacks aren't guaranteed to run on the main thread, unlike the plain scalar fields elsewhere in the class); (2) the dispatch-id counter itself was a non-atomic `++Int`, also racy for the same reason — fixed with `AtomicInteger`. The Android `utteranceId` passed to `tts.speak()` changed from a shared constant `"scout"` (never read by any callback before this) to the per-dispatch id — confirmed via repo-wide search to be inert for existing behavior, flagged explicitly rather than done silently. No watchdog/Busy-Brain/mic/TTS-timing change of any kind — diagnostics only.

### PR #53 — open for review, not merged

**PR #53** (branch `claude/courtesy-welcome-back`) — `CourtesyIntent.WELCOME_BACK`, closing a real-device gap found during PR #52's own investigation: Scout said its boot greeting, Patrick replied "Welcome back!", and it fell through `ScoutCourtesyMatcher`/`ScoutIntentRouter` entirely (no deterministic route existed for it), reaching Gemini/TinyLlama like an open question and entering Busy-Brain for a two-word acknowledgment — made easy to hit since the presence-reply window Scout's own boot/return greeting opens lets it through without the wake name. Matched via the existing exact-string table only (`"welcome back"`, `"welcome back {name}"`, `"glad you are back"`, `"good to have you back"`, `"nice to have you back"`) — no substring/`contains()` matching, with adversarial regression tests confirming "welcome back to the show"/"welcome back everyone to the meeting" stay unmatched. 11 new tests, 57/57 in the touched file passing locally. **Open for review — awaiting merge approval.**

### `pendingAiAnswer` lifecycle fix — design approved, implementation not reviewed/approved

A second, independent bug traced from the same real-device report: once `CourtesyIntent.WELCOME_BACK` (PR #53) exists, "Welcome back!" itself will stop reaching the generative brain at all — but the report also surfaced a deeper structural gap that would still exist for *any* real, slow-to-resolve question. `pendingAiAnswer` (the Busy-Brain PR 2 holding slot for an answer that resolved while Scout was mid-utterance) has no expiry and no relevance check — it can drain onto **any** subsequent Scout utterance, including a fully unrelated proactive remark (a presence/return greeting, a Companion Moment), with a misleading "and about your earlier question" prefix, however much time had actually passed. The **design** below is fully approved. An **implementation** of it exists, but only as uncommitted work in a different Claude session's local sandbox — it has **not been independently reviewed or approved**, and must not be pushed, opened as a PR, or merged until that review happens — see "What's left."

Approved design:
- New pure `ScoutPendingAnswerGate.decide(hasQueuedAnswer, wasPresenceInitiated, queuedAtMs, nowMs, maxAgeMs): Decision` (`NONE`/`DELIVER`/`HOLD`/`EXPIRED`) — a presence-initiated TTS completion (boot greeting, idle-silence remark, return greeting, Companion Moment — confirmed the only 5 call sites using `isPresenceInitiated=true` in the file) never drains or discards; it's held for the next non-presence completion, still subject to a standalone `PENDING_AI_ANSWER_MAX_AGE_MS = 30_000L` constant (timer starts when the answer is actually queued, not when generation began — deliberately not derived from `PRESENCE_REPLY_WINDOW_MS`, a different concept).
- `onDone()`/`onError()` share one `handlePendingAnswerAfterTts()` call so the two callback paths can't drift into different rules.
- A new successfully-started generative request (the shared `tryBegin()`-guarded dispatch point both Gemini and TinyLlama funnel through) supersedes and discards an older undelivered answer — never a courtesy/deterministic reply, which never calls `tryBegin()` at all.
- Explicit conversation close (goodbye/stop listening/good night) also discards it — closing a real, confirmed gap: `ScoutBusyBrainState.discard()` is a no-op once an answer has already resolved into `pendingAiAnswer` (`isPending` is already false by then), so a stale answer could previously survive an explicit "goodbye."
- A single `clearPendingAiAnswer()` helper clears the answer and its queued timestamp together everywhere, so the two fields can never desync.
- The good Busy-Brain case (a real question → filler → the answer resolves during that same filler → drains right after it, prefixed) is unchanged by design, verified by trace.
- Verified this doesn't touch PR #52's TTS diagnostics (the new decision paths that don't deliver never call `respond()`/`speak()`).

**All 12 PRs merged in this pass keep every prior PR's stated scope discipline** — no TruthDb schema changes, no `ScoutMemoryGate`/`ScoutIntentRouter`-widening beyond what's documented above, no touch to Companion Moments' core scoring/cooldowns, PR #38's calendar code, or `PeopleDb`.

**What's left:** merging PR #53 (open, awaiting review); independently reviewing the `pendingAiAnswer` lifecycle implementation that currently exists only uncommitted in another session's local sandbox (design approved; implementation not yet reviewed or approved — must not be pushed, opened as a PR, or merged before that review) and then opening/merging its PR; on-device verification for PR #42 (gaze amplitude — not yet re-tested), PR #44 (environment face-count re-check), and PR #45 (vision-gate wiring) specifically, none of which have a pure-Kotlin test surface; everything still outstanding from the August 12–15 entry below (PR #38's calendar-follow-up real-device validation, PR #37's disclosed paraphrase-layer gap) and the August 9–11 entry (Diana's S24 voice validation, the "Can you hear me?" STT evidence pull, a Working Memory design, the 30s/40s follow-up window's missing wake-word/vision check) — all unchanged by this pass.

---

## August 12–15, 2026 — PRs #37–39 Merged: Family-Names Routing Widened, Calendar Follow-up, Memory-Confirmation Integrity

✓ **Context.** Three more real-device- and review-driven fixes, all following this document's standing trace-first discipline. PR #37 came from a Fold 7 finding of TinyLlama hallucinating fictional family members. PR #38 built the approved "Meaningful Calendar Follow-up" companion behavior, discussed across several planning rounds before any code changed, then had two real bugs caught and fixed by an independent ChatGPT review **before it was ever merged** — not a follow-up PR, folded into the same branch and the same single merge. PR #39 came from investigating a broader concern Patrick raised after seeing PR #38's own calendar clarification work up close: could Scout ever claim to remember something it hadn't actually written down?

✓ **PR #37 merged** (branch `claude/family-names-phrase-expansion`, merge commit `da5e67e6be86ea4ab32039f22a9d1fbb6cc52970`) — widens `FAMILY_NAMES` routing so natural phrasings like "Who is a part of my family?" no longer fall through to TinyLlama. Traced end-to-end first: the phrase missed every literal check in `ScoutIntentRouter`, correctly tripped `ScoutMemoryGate` (routing away from Gemini, exactly as designed), then TinyLlama — even grounded with real TruthDb facts and an explicit "do not invent" instruction — still fabricated fictional family members ("mother: Mrs. Johnson, father: Dr."). Fixed by adding 7 more natural phrasings to the router's existing literal `.contains()` list, still gated on the word "family" plus a clear question/request word so an unrelated mention of "family" elsewhere doesn't get swept in — no new abstraction, no regex, no change to `ScoutMemoryGate` or the fallback prompt itself. 7 new unit tests, including a negative test confirming "what is my wife's name" still routes to `ASK_WIFE_NAME`, not the widened check. **Disclosed, not fixed:** this is a phrase-list expansion, not a general solution — a lightweight offline paraphrase-understanding layer to close this class of gap more generally was investigated as a design exercise during the same session and **deliberately deferred, not built.**

✓ **PR #38 merged** (branch `claude/calendar-followup-birthday-anniversary-doctor`, merge commit `79139a6ae850ef0af340edfe4ad30918955af921`) — the approved "Meaningful Calendar Follow-up" design: notice → ask → learn → remember, layered on top of the already-shipped, read-only Calendar Awareness (a different, separate item from the still-unbuilt §10b.6 write-access calendar item below, untouched by this PR). A generic **"Birthday"/"Anniversary"** event triggers at most one spoken follow-up question ("Whose birthday is that?"), only when TruthDb doesn't already know and only from a direct calendar question that named exactly one unambiguous event — never a background scan. A generic **"Doctor"/"Dr."** event triggers a one-off caring question ("Is everything okay?") with no data path at all — a deliberate full early return that never reaches `handleTeaching()`, Gemini, or TinyLlama, so a reply like "my blood pressure is high" can never be mis-stored. The resolved birthday/anniversary answer is written durably to TruthDb — anniversary stored as a participant-scoped compound key (e.g. `anniversary_with_diana`) so it never silently implies "whoever the current wife is" — and later "whose birthday/anniversary is [date]" questions resolve **deterministically from TruthDb, never Gemini or TinyLlama.** New pure `brain`-package files: `PendingCalendarFollowup.kt` (one sealed pending-state type, so a Clarification and a DoctorCheckIn can never accidentally coexist) and `CalendarFollowupMatcher.kt` (title matching, UTC/local-safe date derivation, reply resolution, the durable-fact reverse lookup).

✓ **Two real bugs caught and fixed before PR #38 ever merged**, via an independent ChatGPT review Patrick requested before approving it: (1) **Alias resolution was being lost.** `tryResolveCalendarClarification()` was reducing the full alias map (`ScoutEntityResolver.buildAliasMap()`, alias → entity) down to just its distinct entity-slug values before handing it to `CalendarFollowupMatcher.resolveClarificationReply()`, so a taught nickname like "Nick" (mapped to "Nicolas") was silently dropped — a reply like "That's Nick's birthday" would have failed to resolve even though Scout already knew Nick was Nicolas. Fixed by passing the full alias map through and matching alias keys directly, resolving each match to its entity. (2) **Already-known ownership wasn't being used.** `appendCalendarFollowupIfWarranted()`'s already-known path was a bare early return — it correctly stopped Scout from asking again, but still spoke only the event's generic title even when TruthDb already knew whose it was. New `CalendarFollowupMatcher.describeKnownOwner()` incorporates that knowledge into the spoken answer ("That's Elijah's birthday," "That's your anniversary with Diana") when exactly one owner is on record, falling back to the plain generic answer — never guessing — when the match is ambiguous (multiple owners) or the date is known with no recorded participant. Both fixes verified locally (33/33 tests passing) before the PR was approved and merged as a single commit history.

✓ **PR #38 is merged and CI-verified — real-device validation has not happened yet.** Same disclosure standard as PR #35's TTS fix above: nothing about the calendar follow-up flow (the clarification question, the alias-aware reply resolution, the TruthDb write, or the deterministic recall) has been exercised on a real device yet. Not a blocker to being merged, but not something to treat as confirmed-working in practice until it is.

✓ **PR #39 merged** (branch `claude/memory-confirmation-integrity`, merge commit `92c6d57f84ef8f0f651a6a89937532fcaa954d42`) — traced and closed a broader trust gap Patrick raised: Scout could say "I'll remember that" / "Got it, I'll keep that in mind" for things that were never actually written to TruthDb, whenever an unrecognized teaching attempt silently fell through to a fact-blind Gemini or an ungrounded TinyLlama (e.g. "This is my friend, Janice" — "friend" isn't one of `findSubject()`'s 9 hardcoded relation words, so it had no structured write path *and* no existing clarification fallback). Two independent, additive layers — deliberately not a redesign of TruthDb, HabitLayer, or PR #38's calendar system, confirmed unnecessary before implementation:
- **Layer 1 — stop it before either model ever sees it.** `ScoutFactExtractor.looksLikeUnrecognizedTeaching()` (built July 26, see that entry below) is broadened with a new `looksLikeUnknownPersonIntroduction()` check for first-time introductions using a relation word outside that narrow 9-word vocabulary — gated on a finite `PERSON_RELATION_HINTS` list (friend, neighbor, coworker, sibling, etc.), not "any noun after my," so "this is my favorite show"/"this is my house" are never affected. Only ever evaluated after structured extraction has already found nothing, so it can't regress any currently-working write.
- **Layer 2 — contextual output backstop, not a blanket filter.** New `applyRetentionClaimGuard()` substitutes the same honest clarification only when *both* the original utterance looked teaching-shaped *and* the model's reply contains an explicit retention-claim phrase ("I'll remember," "I've saved that," "I'll make a note," etc.) — ordinary acknowledgments ("Got it," "Noted," "Okay") never trigger it alone, on any input. Wired into both the Gemini path (which had zero output filtering before this) and the TinyLlama path (after the existing `cleanOfflineReply()` identity-leak cleanup, not instead of it).

All existing write-then-confirm teaching paths, and all of PR #38's calendar clarification code, are explicitly untouched. 88/88 tests pass locally (20 new), including PR #38's own `CalendarFollowupMatcherTest` re-confirmed unaffected.

**What's left:** PR #38's real-device validation (calendar clarification flow, alias-aware replies, deterministic recall — none of it exercised yet); PR #37's disclosed paraphrase-layer gap remains open, deliberately deferred; everything still outstanding from the August 9–11 entry below (Diana's S24 voice validation, the "Can you hear me?" STT evidence pull, a Working Memory design, the 30s/40s follow-up window's missing wake-word/vision check) is unchanged by this pass.

---

## August 9–11, 2026 — PRs #31–35 Merged: Busy-Brain Filler Polish, Primary-Name Leak Fix + Cleanup, Mic-Chime Fix, TTS Default Voice

✓ **Context.** Living with Scout day-to-day on real devices (Fold 7, A32, and — for the first time — Diana's Galaxy S24) surfaced four separate real-world issues, each traced against actual source before any code changed, per this document's standing discipline: the Busy-Brain filler line firing immediately instead of only when actually needed; Scout using Patrick's name in ordinary conversation with Diana or Elijah even though he can't know who's speaking; an audible microphone start/stop chime on the Fold 7 that got noticeably worse once Busy-Brain started cycling the recognizer more often; and — the most consequential finding — Scout speaking with an unexpected female voice on Diana's S24 because Scout has never explicitly chosen a voice, only ever inherited whatever the device's default TTS engine happened to default to.

✓ **PR #31 merged** (branch `claude/busy-brain-delayed-filler`, merge commit `d4838e5d0910ce88a07d55153d7100d9345ec945`) — Busy-Brain polish. The "Let me think about that for a moment" filler previously spoke immediately every time a generation was dispatched; now delayed `BUSY_BRAIN_FILLER_DELAY_MS` (2000ms) and only spoken if the generation is still genuinely pending at that point via a new `scheduleBusyBrainFiller()`, decoupled from `isThinking`/mic-reopen timing (still cleared immediately, unchanged). The single fixed filler line was replaced with a small randomized pool (`BUSY_BRAIN_FILLERS`) so it doesn't feel robotic on repeat. "Hmm" was also dropped from time-of-day responses, an unrelated small wording fix bundled into the same polish pass.

✓ **PR #32 merged** (branch `claude/fix-primary-name-leak`, merge commit `8854836cc4e31e54441de1181daa17778d2fc2c2`) — traced first with no code changed, per Patrick's explicit request, then fixed. Root cause: `ENTITY_USER_PRIMARY` (Patrick, the app's registered household owner) was being conflated with "the person currently speaking," which are not the same thing. Two confirmed leak points, both fixed narrowly: (1) `tryTinyLlamaOrFallback()`'s generic conversational `factsLine` unconditionally included `ENTITY_USER_PRIMARY`'s NAME fact via `getAllKnownFacts()` — now filtered out of that one generic prompt only; `getAllKnownFacts()` itself and the personal-memory-gate call site that also depends on it (needed for "who is Patrick" to keep routing correctly) are untouched. (2) The face-detection frame callback's `HabitLayer.logPersonSeen()` call was passing Patrick's name unconditionally for whichever face ML Kit detected first in a frame, regardless of whether that face was ever actually verified as him — now looks up the real, already-known name for that specific face hash via `PeopleDb.getName()` (the same identity system used for family introductions), falling back to blank when Scout hasn't actually identified that face. Scout can still use Patrick's name for genuinely relevant questions ("what's my name," "who is Patrick") — those already answer directly from TruthDb, unaffected by this fix.

✓ **PR #33 merged** (branch `claude/habitlayer-primary-name-cleanup`, merge commit `8fbf7010dc9b9e3c967d0658fac3cf5d03d11d17`) — one-time cleanup follow-up to PR #32, kept as a separate PR per Patrick's explicit request. The `logPersonSeen()` bug existed since the project's first commit, so any face hash detected as "primary" before Patrick's own face was ever seen could have been permanently mislabeled with his name in `HabitLayer`'s persisted data. New `HabitLayer.clearUnverifiedPersonName()` clears only the `name` field for any person entry matching the current primary-user name (read fresh from TruthDb each time — never a hardcoded literal) whose face hash `PeopleDb` does *not* independently confirm — sighting counts, timestamps, and time-of-day patterns are all preserved, no entry is ever deleted, and `PeopleDb` itself is never altered. Guarded by a one-time `SharedPreferences` flag (`habit_primary_user_name_cleanup_done_v1`, set before the work runs, mirroring the existing `migrateDoublePrefixFacts()` migration pattern) so it can never re-run and erase a name `HabitLayer` has since genuinely re-learned. No hardcoded personal names were introduced — checked and confirmed before merging.

✓ **PR #34 merged** (branch `claude/mic-chime-mute-stop-side`, merge commit `319ce79c6ec9d249403cf87bb548647b49bcdd60`) — extends Scout's existing (day-one, previously start-side-only) `TRY_MUTE_BEEP` mechanism to also cover `stopListeningSafe()`'s `speechRecognizer.cancel()` call, not just `maybeStartListening()`'s `startListening()` call. Traced first: every spoken Scout utterance (via TTS `onStart()` → `stopListeningSafe()`) triggered an unmuted `cancel()` — the "stop" side of the recognizer cycle was never covered — and Busy-Brain's mic-reopening-while-pending design (PR #29/#31) increased how often that stop side fires per query, which is what made the long-standing gap newly noticeable on the Fold 7. Extending the mute to the stop side required replacing the old single-nullable-Int mute tracking with a proper reference-counted guard (new `ScoutBeepMuteGuard`, `brain` package, unit-tested) so the now-two independent mute windows (start-side and stop-side) can safely overlap without one's restore clobbering the other's still-outstanding saved volume — plus a new `forceRestoreSystemBeep()` used only at shutdown, since `shutdownSystems()` purges pending Handler callbacks before any scheduled restore could otherwise fire. Reuses the existing 380ms mute window; still only ever calls `cancel()`, never `stopListening()`.

✓ **Busy-Brain has now been exercised on the Fold 7.** Corrects the August 8 entry below, which still described Fold 7 real-device validation as not yet done — it has since happened. That real-device testing is what directly surfaced two of the issues fixed in this pass: the filler line speaking immediately and too often (**fixed, PR #31**), and the increased microphone start/stop cycling that made the long-standing chime gap newly audible (**fixed, PR #34**). This confirms Busy-Brain's mic-availability/delivery mechanism is being genuinely exercised on real Fold 7 hardware, not just CI-verified. **Longer-term, dedicated Fold 7 stability validation (voice, memory, face recognition, weather, wake word, per Launch Checklist item 4) may still be ongoing — this is not a claim that item is fully closed, only that Busy-Brain specifically has now seen real Fold 7 use.**

✓ **PR #35 merged** (branch `claude/tts-default-voice-selection`, merge commit `ee00d9bf27b21530e12c4bc0f9fb8e7da725bb7a`) — the TTS default-voice fix. Built on a real-device finding: Scout speaks with the expected male voice on the Fold 7 and A32 (both default to Google TTS) but with an unwanted female voice on Diana's Galaxy S24 — confirmed only through Android Settings (default engine: Samsung TTS, English (US), "Voice 1"), not through the diagnostic tool below — enough of a first-use problem that Diana stopped testing Scout over it. Traced first (Scout has never called `TextToSpeech.setVoice()` anywhere, ever, in the project's history — only `tts.language = Locale.US`, inheriting whatever the bound engine's own default voice happens to be), then investigated via a standalone, read-only TTS-voice diagnostic tool (built and run outside this repo, never touching Scout or any device's TTS settings) to inspect real Locale.US voice data — name, quality, latency, network-requirement, install status. **The diagnostic tool's voice enumeration was run on the Fold 7 and A32 only — it was not run on Diana's S24**, which was not readily available for that additional testing. `en-us-x-iom-local` was identified as the correct voice by a human actually listening to it on real Fold 7 and A32 hardware, confirmed present on both with `quality=HIGH`, `latency=LOW`, `networkRequired=false`, installed — never inferred from the name string, since Android's public `Voice` API has no gender field (confirmed against AOSP source). `setupTts()` now requests `com.google.android.tts` explicitly, falling back to the device default exactly like today's behavior if that fails outright. A real correctness issue was caught and fixed before implementation: the 3-arg constructor's `SUCCESS` status does not by itself prove Google TTS actually bound (it can silently fall back internally); rather than make an unprovable engine-identity claim, new `applyPreferredVoice()`/`ScoutVoiceSelector` (pure, unit-tested) search whatever the actually-bound engine's own voices report for the verified name, restricted to offline/installed candidates. If no offline en-US candidate exists at all, the selector returns nothing and Scout simply keeps whatever voice the bound engine had already defaulted to — **not a guarantee that every possible fallback voice on every Android device is offline**, only that the selector itself never actively picks a network-dependent or not-yet-installed voice. A new diagnostic log line reports the *resolved voice's own name* (read back via the public `getVoice()`) as ground truth — never an assumed engine identity, so a silent fallback to Samsung TTS can never be mislabeled "Google." `voice_pitch` (0.98) and `voice_speed` (0.88) are explicitly untouched, confirmed via a direct diff grep before opening the PR. This PR **addresses** the S24 finding and is expected to fix it, but **has not yet been installed or tested on Diana's S24 — real-device validation there is still pending, not yet confirmed to fix that specific device.** Merged without further changes; Diana's S24 was not blocked on for this merge — her device is covered by the quality-ranked offline fallback, and a verified name for her device can be appended to the preference list later without disturbing this one, once she's able to test again.

**What's left:** installing and testing the now-merged PR #35 on Diana's S24 specifically once she's available — **this is the one piece of PR #35 still outstanding, real-device validation on her device has not happened yet**; a verified preferred-voice name for her device if the current fallback doesn't sound right; the still-outstanding items from the August 8 entry below not related to Busy-Brain (the "Can you hear me?" STT evidence pull, a Working Memory design, the 30s/40s follow-up window's missing wake-word/vision check), plus longer-term dedicated Fold 7 stability validation per Launch Checklist item 4.

---

## August 8, 2026 — PRs #27–29 Merged: Weather Freshness + Boot-Greeting Fixes, and Busy-Brain Phase 1 (Foundation + Microphone/Delivery)

✓ **Context.** Patrick reported two real-world issues on the current build: Scout could speak stale cached weather as though it were current when fresh weather wasn't available, and the boot greeting ("Hello... good to be back") didn't let Patrick reply immediately without saying "Scout" first, even though Scout had just spoken to *him* first. Both traced against actual source before any code changed, per this document's standing discipline. Separately, Patrick approved building Busy-Brain Phase 1 — the roadmap item traced but left unbuilt in the August 6–7 entry below — in two explicitly scoped PRs.

✓ **PR #27 merged** (branch `claude/weather-freshness-and-boot-greeting`, merge commit `d00639910db552d4749cd263296de3b9ebe3b50d`) — two independently-traced fixes on one small PR:
1. **Weather freshness.** Every cached-weather fallback path in `ScoutWeatherManager.kt` was traced before any code changed, per Patrick's explicit instruction. Three sites were found, all sharing the same anti-pattern: an apologetic prefix ("I'm offline right now, but as of 3:45pm...", "I couldn't get a fresh reading...") followed by old cached conditions spoken as if current — the offline branch at the top of `dispatch()`, the NWS forecast-fetch-failure branch, and the parse-failure branch. All three now speak one of two honest, non-cache messages instead: `"I need to be online to check the weather."` (offline) or `"I wasn't able to reach the weather service right now."` (online, NWS unreachable or unparseable) — new named constants `ScoutWeatherManager.MSG_NEED_INTERNET`/`MSG_SERVICE_UNREACHABLE`, deliberately non-private so a new `ScoutWeatherManagerTest` can assert neither message ever grows a cached-data caveat again. The one legitimate cache-reuse path (still online, cache from today, younger than its per-type freshness window) was confirmed and left completely untouched, exactly as instructed — that's normal efficiency, not the bug. `formatCacheTime()` removed as dead code now that nothing speaks a cache timestamp.
2. **Boot greeting.** Traced the exact boot-greeting path in `MainActivity.kt` and found both boot-announcement call sites (the immediate path in `onInit()`, and the deferred `pendingBootAnnouncement` path in `startSystems()`) called `speak()`/`convoDb.logTurn()` directly instead of going through `respond()` — so the boot greeting never called `conversationState.openFromScoutInitiated()` (Better Conversation State Phase 1, PR #26, see below) and never opened the presence-reply window. A genuine spoken greeting from Scout simply wasn't being treated as Scout-initiated. Both call sites now call `respond(out, isPresenceInitiated = true)` — the same existing mechanism already used for idle-silence remarks, return greetings, and Companion Moments, so no new mechanism was introduced. The separate STT-unavailable warning spoken 4s after boot is untouched — it's a system diagnostic, not a greeting intended for the user, so it deliberately stays outside conversation-opening.
3. Both fixes shipped with focused unit tests (`ScoutWeatherManagerTest.kt`, new; `ScoutConversationStateTest.kt`, one added test modeling the boot-greeting mechanism end-to-end at the state level — `MainActivity` itself has no unit coverage in this codebase).

✓ **Busy-Brain Phase 1 merged in two explicitly scoped PRs**, per Patrick's approved split. Goal: while TinyLlama or Gemini is generating an answer, Scout stays available for safe deterministic requests instead of fully shutting off the microphone for the whole generation.

- **PR #28 merged — foundation/correctness only** (branch `claude/busy-brain-pr1-foundation`, merge commit `6b71cce1948fbee0c7041fb25d89ca7874c611df`). New `ScoutBusyBrainState` (RAM-only, `brain` package, mirrors `ScoutConversationState`'s established shape — explicit `nowMs` params, no clock reads inside, private setters) tracks whether a real Gemini/TinyLlama generation is currently pending, independent of `isThinking`. Fixed a real, previously-undetected bug found during the trace: `ScoutLlamaController.newGeneration()` was bumped unconditionally at the top of *every* `handleQuery()` call — deterministic or AI-bound — which meant a deterministic question asked while a TinyLlama generation was in flight would silently invalidate it (its result would return, find `token != currentToken`, and vanish with only a `GENERATION_DISCARDED` log, never delivered). The bump now happens exactly once, only at the point a TinyLlama generation is actually dispatched, gated so it's never reached while one is already pending. A second AI-style question asked while one is already pending gets *"I'm still thinking about your last question"* instead of starting a second generation or touching the first one's token — gated at the two real "this is a genuinely new question" entry points (`handleUnknownIntent()`, `handlePersonalMemoryQuery()`), deliberately not inside `tryTinyLlamaOrFallback()` itself, since that function is also reached as Gemini's own internal same-question fallback and must not be blocked. Explicit conversation close (goodbye/stop listening/good night) now also discards a still-pending generation's eventual answer — `ScoutBusyBrainState.discard()` frees the pending-gate immediately (so a genuinely new question in a freshly-reopened conversation is never told Scout is "still thinking" about one already abandoned) while independently marking that specific generation's result unspeakable via a `discardReason` that survives until the next `tryBegin()`. The generation itself is never cancelled — `LlamaEngine`'s native call and Gemini's network thread finish on their own; only the spoken delivery is suppressed, logged via new `DiagLog.logBusyBrainDiscarded()` (reason + which backend, no speech text). `ScoutGeminiManager.tryGemini()` gained one small, targeted `shouldDiscardResult`/`onDiscarded` hook for this, consulted only at its one delivery point. A new watchdog, independent from the existing `isThinking` watchdog (kept separate since PR #29 would go on to decouple their timing), discards a generation stuck longer than `MAX_THINKING_DURATION_MS`. **This PR alone made no microphone-availability change — mic-off-while-thinking was fully preserved**, by explicit design, so the correctness fix could be reviewed independently of the behavioral change.
- **PR #29 merged — microphone availability + AI result delivery** (branch `claude/busy-brain-pr2-mic-delivery`, merge commit `7315fac0b0769298d9621452a0e055301f5204d2`). `isThinking` now clears the moment a generation is actually dispatched (Gemini's `REQUEST_STARTED`, or TinyLlama's `generateAsync()` call) instead of staying true until the answer arrives — `maybeStartListening()` itself is completely untouched; the mic reopens through the same existing `speak()`→`onDone()`→`scheduleListenRestart()` path every other Scout utterance already uses. A filler line ("Let me think about that for a moment.") is spoken exactly once per question — `ScoutBusyBrainState.tryBegin()` only returns `true` the first time, so Gemini failing over to TinyLlama for the same question doesn't re-speak it. `respond()` gained a new `isStatusOnly` parameter (also retrofitted onto the "still thinking" message above, plus a new "I'll get to that once I've finished my last thought" for blocked intents) that skips `ConversationDb` logging and the "repeat that" cache — status feedback, not conversation content — while still extending conversation state and protecting the self-echo guard exactly like any other `respond()` call. New `ScoutBusyBrainPolicy` (unit-tested, `brain` package) allows only an explicitly approved read-only/conversational intent set to be answered while a generation is pending — `TIME, DATE, LANGUAGE, TIME_OF_DAY, CONNECTIVITY, WEATHER, CALENDAR, VISION, IDENTITY, ASK_SCOUT_NAME, ASK_MY_NAME, ASK_WIFE_NAME, ASK_SON_NAME, ASK_DOG_NAME, FAMILY_NAMES, RECALL_FACT, GREET, HOW_ARE_YOU, PRAISE, AFFECTION`, plus `GOODBYE`/`STOP_LISTENING` (the explicit-close control mechanism PR #28's discard behavior depends on staying reachable through a normal conversation) — while `GO_ONLINE`/`GO_OFFLINE`, `EXPORT_BRAIN`, `OPEN_CALENDAR_SETTINGS`, screen navigation ("settings"), and teaching/forgetting/corrections all stay blocked with the new deferral message. `UNKNOWN` is deliberately excluded from this generic check since it already has its own, more specific arbitration from PR #28. New `ScoutBusyBrainDelivery` (unit-tested) makes sure a resolved answer (or a final failure message) never uses `TextToSpeech.QUEUE_FLUSH` over something already being said — if Scout is speaking or mid-dispatch of another accepted request, the answer is held (`pendingAiAnswer`) and delivered from the TTS `onDone()`/`onError()` drain check once Scout is free, framed as *"And about your earlier question—"* only when it actually had to wait; delivered immediately with no bridge if Scout was genuinely idle. One narrow, disclosed gap: `ScoutGeminiManager.speakUnavailableIfNeeded()`'s rare "nothing worked at all" message still speaks via its own internally-injected `respond()`, not the new delivery arbitration — flagged rather than fixed silently, to keep this PR's scope narrow.

✓ **Merged and CI-verified; as of the August 9–11 entry above, also exercised on the Fold 7.** As of this entry's original writing (Aug 8), PR #29's microphone-availability change had not yet been tried on real Fold 7 hardware. **Update:** it has since been exercised there — that real-device use is what surfaced the filler-timing issue fixed in PR #31 and the mic-cycling/chime issue fixed in PR #34 (see the August 9–11 entry above). Busy-Brain's mic-availability/delivery mechanism should no longer be described as merely CI-verified-but-untested; longer-term, dedicated Fold 7 stability validation (Launch Checklist item 4) may still be ongoing separately.

**What's left:** widening the approved-intent list, if warranted, now that Busy-Brain has real Fold 7 use behind it; the still-outstanding items from the August 6–7 entry below (the "Can you hear me?" STT evidence pull, a Working Memory design for conversation-duration questions, the 30s/40s follow-up window's missing wake-word/vision check — not addressed by either Busy-Brain PR).

---

## August 7, 2026 (Later Same Day) — PR #25 Merged: Documentation Sync; PR #26 Merged: Better Conversation State Phase 1

✓ **PR #25 merged** — documentation-only sync of all three canonical handoff docs (this document, `Scout_Quick_Start.md`, `Scout_Launch_Checklist.md`) catching up PRs #12–24, itemized in the August 6–7 entry below. Superseded by this same document's own header once the present pass landed.

✓ **PR #26 merged — Better Conversation State Phase 1** (branch `claude/conversation-state-phase1`, merge commit `e956f2223756452258fe5deff888baebdc99bad4`). Design-traced first with no code changed, then implemented per Patrick's explicit adjustments (exact courtesy-category behavior, the exact explicit-ending phrase list, RAM-only state requirements, an explicit non-negotiable separation from audio-safety mechanisms). New `ScoutConversationState` (RAM-only, `brain` package) wraps — deliberately does not replace — the existing `CONVO_WINDOW_MS` (30s) and `PRESENCE_REPLY_WINDOW_MS` (40s) timers with one thing neither timer alone could express: an explicit "this conversation was closed on purpose" signal that overrides a still-recent timer, so saying "goodbye"/"stop listening" stops wake-word-free follow-ups immediately instead of only after the full window happens to elapse on its own. Tracks `isActive`/`startedAt`/`lastUserTurnAt`/`lastScoutTurnAt`/`endedAt`/`endReason` via `openFromUserTurn()`, `openFromScoutInitiated()` (the presence-initiated-remark path — idle-silence acknowledgment, return greeting, Companion Moments), `extend()` (an EXTEND-only "thanks" turn — keeps an already-active conversation open but never opens one from idle), `onScoutTurn()`, `closeExplicitly()`, and `evaluate()` (the single "next evaluation" point that performs the actual active→inactive transition itself, not a stale read, so a silence-timeout end gets logged exactly once). Explicit-ending phrases ("goodbye"/"bye"/"good night"/"that's all"/"that will be all"/"stop listening"/"you can stop listening") close the conversation right after Scout's reply, in `handleGoodbyeIntent()`/`handleStopListeningIntent()`/`handleCourtesy()`. Deliberately does not touch `lastSpeechDoneMs`, `ttsLockoutUntilMs`, the mic-restart cooldown math (`ScoutMicRestartTiming`), or the self-echo guard — those audio-safety mechanisms (PR #24) live entirely outside this class and keep working exactly as before, an explicit, non-negotiable separation Patrick required before implementation. **Merged and active** — this is the mechanism PR #27's boot-greeting fix and every explicit-close discard check in Busy-Brain (PR #28) build on directly.

---

## August 6–7, 2026 — PRs #22–24 Merged: TinyLlama Misroute Investigation, Deterministic Self-Knowledge Fixes, and Mic-Restart Timing

✓ **Context.** Patrick ran a real-world offline conversation test (Wi-Fi off, Airplane Mode on) and found several spoken questions — "Can you hear me?", "what day is today," and others — got poor/wrong TinyLlama-generated answers instead of Scout's existing deterministic handlers. Traced against actual source per this document's own standing discipline, not assumed, before any code changed. A separate adb/sqlite3 evidence-pull method (timestamp-isolated, non-destructive) was given to Patrick earlier to confirm the exact recognized text and diagnostic brain-source for the one question the trace couldn't resolve from code alone — **not yet returned as of this writing.**

✓ **Per-question findings, classified by root cause:**
- **"What day is today?" — router phrase gap, not a TinyLlama or grounding problem.** `ScoutIntentRouter`'s `DATE` branch already had a fully correct, existing handler (`handleDateIntent()`) — it just didn't match this exact wording (only `"what date is it"` / `"what is today"` / bare `"date"` were recognized). **Fixed in PR #23.**
- **"What should I call you?" — same class of bug.** `ASK_SCOUT_NAME` only matched `"what is your name"` / `"who are you"` and near variants — not this phrasing, despite the existing name handler being correct. **Fixed in PR #23.**
- **"What language are we speaking?" — no deterministic coverage existed at all.** The recognizer's language (`Locale.US`) was a bare literal inside `buildRecognizerIntent()`, never surfaced anywhere else in the app. **Fixed in PR #23** — see below.
- **"Is it morning or night?" / "What time of day is it?" — same: no deterministic coverage existed.** Two existing subsystems (`HabitLayer.TimeSlot`, `ScoutPresenceDecider.PresenceMode`) each classify time-of-day for unrelated purposes, but neither was ever reachable as a spoken answer. **Fixed in PR #23** — see below.
- **"Can you hear me?" — code path is provably correct for the literal phrase; leading hypothesis is an STT mismatch, unconfirmed.** `ScoutIntentRouter` already routes this deterministically to `IDENTITY` (`handleIdentityIntent()` → "I hear you. I'm right here.") — it cannot reach TinyLlama unless Android's recognizer returned something that didn't literally contain the phrase. Plausible specifically because this was the first test explicitly confirmed fully offline — on-device offline STT (forced by `EXTRA_PREFER_OFFLINE=true` with no network) is materially worse than the cloud recognizer used in earlier testing. **Not touched — routing left exactly as-is, pending the still-outstanding evidence pull.**
- **"How long have we been talking?" / "What were we talking about?" — genuine missing capability, not a bug.** No conversation-start timestamp exists anywhere in the app; `ConversationDb` is a flat, unscoped log of every turn ever, and the only related timestamp (`lastScoutResponseMs`) is a rolling "extend the follow-up window" marker, reset every turn, not a session start. **Deliberately left unimplemented** — needs a real Working Memory / conversation-state design, not more TinyLlama prompt-stuffing.
- **Structural note, applies to every fallback-routed question above:** TinyLlama's offline prompt (built inline in `tryTinyLlamaOrFallback()`, a completely separate hand-rolled path from Gemini's `ScoutPromptBuilder`) never included current date, time, day-of-week, time-of-day, or the recognizer's language — a grounding gap, not a model-quality issue, for every one of these except "how long"/"what were we talking about," which are missing-capability rather than missing-grounding.

✓ **PR #23 merged** (branch `claude/deterministic-self-knowledge-fixes`, deleted after merge; final commit `5a465e0`; merged into `main` at `1367e05`). Four narrow, individually-traced fixes, each following the same "reuse a real deterministic source, never fabricate a separate answer" discipline this document has enforced since the July 30 Companion Moments entry:
1. `ScoutIntentRouter`'s `DATE` branch extended to also match `"what day is today"` / `"what day is it"`.
2. `ASK_SCOUT_NAME` extended to also match `"what should I call you"` / `"what do I call you"`.
3. New `LANGUAGE` intent, answered by new `ScoutSpeechLanguage.RECOGNITION_LOCALE` — a single named constant that `buildRecognizerIntent()` now also builds `EXTRA_LANGUAGE` from (same `Locale.US` value as before, just centralized instead of duplicated). Explicitly designed so Scout's spoken answer can never drift from what the recognizer is actually configured to do — not a separately hardcoded string, and never TinyLlama.
4. New `TIME_OF_DAY` intent, answered by new `ScoutTimeOfDay`, which reuses `HabitLayer.TIME_SLOTS`' existing hour boundaries rather than adding a third independent hour-bucketing scheme. `ScoutPresenceDecider.PresenceMode` was traced and explicitly rejected as the source — its four modes are named for Scout's own social-engagement posture, not the time of day a person would say out loud (`SLEEP` spans midnight–6am; `ACTIVE` alone spans 9am–7pm). Ships two separate spoken vocabularies over the same boundaries after a review refinement: `spokenCategory()` (always exactly morning/afternoon/evening/night, for "is it morning or night") and `descriptiveLabel()` (finer-grained — early morning/midday/late night — for "what time of day is it"), rather than the first version's single `TIME_SLOTS.label` spoken verbatim (which could answer "it's quiet hours right now" — deterministic, but not a direct answer to the question asked).
5. All four fixes shipped with focused unit tests, including explicit regression tests confirming pre-existing phrasing still routes identically.

✓ **Mic-restart timing trace**, prompted by Patrick reporting that speaking immediately after Scout finishes sometimes clips the start of the sentence — the microphone hadn't restarted yet. Traced the complete post-TTS timeline: `TTS_LOCKOUT_MS` (600ms), `MIC_RESUME_COOLDOWN_MS` (650ms), and `BOOT_LISTEN_EXTRA_DELAY_MS` (250ms — misleadingly named; it's checked on every restart forever, not just at boot) are three independent gates in `maybeStartListening()`, each keyed to the same `lastSpeechDoneMs` anchor but each separately rescheduling on a flat 150ms poll (`LISTEN_RESTART_DELAY_MS`) when it fails. Modeled the actual timeline: because the three gates don't coordinate, the real restart landed around ~750ms after TTS finished, not the intended 650ms floor — a polling-overshoot inefficiency, not a wrong threshold. Also confirmed, and worth recording as current protective behavior: `isThinking` fully gates the microphone — `maybeStartListening()` refuses to start listening at all while `isThinking == true`, so during a TinyLlama/Gemini generation the mic never opens in the first place (not a capture-then-discard path), and all three proactive-speech call sites (idle-silence remark, return greeting, Companion Moments) already self-block on `isThinking` too — no code path today lets two `respond()` calls collide. Self-echo protection (substring match against `lastScoutUtteranceNormalized`, 2+ words required) still carries the known, previously-documented normalization-mismatch bug from earlier in this document (the "yes, my favorite color is cyan" entry) — root fix still deferred, untouched by this pass. Diagnostics were confirmed able to reconstruct TTS-done/listen-restart timing from existing `DiagLog` entries, but self-echo discards were completely unlogged before this pass — a real blind spot.

✓ **PR #24 merged** (branch `claude/mic-restart-computed-delay`, deleted after merge; final commit `ed9f85d`; merged into `main` at `81cac76`, before #23). Two fixes from the trace above, deliberately kept separate from any Busy-Brain/conversation-window work (see roadmap note below):
1. New `ScoutMicRestartTiming.computeRestartDelayMs()` (pure function) computes the exact remaining wait to the *latest* of the three active deadlines instead of flat-polling past it — `scheduleListenRestart()` gained an optional `delayMsOverrideMs` parameter so the three cooldown branches can target it precisely. No threshold value changed; only how precisely the wait targets them.
2. New `DiagLog.logSelfEchoDiscarded(charCount, gapAfterResponseMs)`, closing the diagnostic blind spot above — character count and timing only, never the recognized text (the method's signature structurally can't accept it). Self-echo matching logic itself untouched.

✓ **Roadmap sequencing traced, nothing built.** Patrick's proposed order — Busy-Brain deterministic pass-through → Better conversation-state window → Awareness-based direct address — was checked against the repo rather than taken on faith. Confirmed the Awareness Layer spec's own Appendix (Direct-Address Confidence) is independently labeled "Future Phase — Not Built Now," explicitly "last, after every other phase is stable" — the spec itself already commits to that item coming third, not just Patrick's instinct. Also surfaced a real, current gap directly relevant to the second item: inside the existing 30-second conversation window / 40-second presence-reply window, no wake-word or vision/direct-address check runs at all today — any 2+-word recognized speech that isn't a self-echo match is treated as real conversation, which is exactly the TV/background-speech risk the Awareness Appendix's Tier 3 ("active conversation-window follow-up") is eventually meant to corroborate against. Sequencing between Busy-Brain and the conversation-window fix remains an open call — the trace didn't settle it either way, and nothing here was implemented.

✓ **PR #22 merged** (branch `claude/remove-outdated-pdf-exports`, deleted after merge; commit `cc3e23c`). Removed 33 stale PDF export snapshots (`Scout Quick Start/`, `Scout_Launch_Checklist/`, `Scout_Master_Project_Summary's/` folders) that had accumulated across many past sessions and duplicated/lagged the canonical `.md` docs — a real confusion risk since the folder names closely echoed the current document names. The three canonical files (`Scout_Quick_Start.md`, `Scout_Launch_Checklist.md`, and this document) are unaffected and remain the single source of truth.

✓ **GitHub Actions historic outage explains an unrelated CI delay on PR #23 — not a code problem.** GitHub Actions had its second-worst major outage in the service's history on August 6 (15:22–02:04 UTC, 7h26m — 45 minutes short of the May 2021 record), independently confirmed after the fact via a published third-party writeup cross-checked against the observed symptoms. This is why a PR #23 CI check sat at "queued, 0s" for roughly 22 hours and several job attempts failed with "Service Unavailable" resolving action downloads before ever reaching checkout — confirmed via actual job-step logs, not assumed. Resolved operationally, once the outage cleared, by updating PR #23's branch with current `main` (which also resolved a real but trivial 3-line import-block merge conflict created by PR #24 merging first) — not by any code change. Both PRs' final commits were independently re-verified with real, complete `assembleDebug` + `testDebugUnitTest` job logs before merging.

**What's left:** the adb/sqlite3 evidence pull for "Can you hear me?" (STT-mismatch hypothesis still unconfirmed — do not touch identity/routing code until it comes back); a real Working Memory / conversation-state design before "how long have we been talking" or "what were we talking about" can be answered; the Busy-Brain-vs-conversation-window sequencing decision; and the still-outstanding A32 validation items from the entries below (Awareness Phase 1's logging trial, the gaze-fix re-test, Companion Moments/Settings-reorg on-device checks).

---

## August 2–5, 2026 — PRs #12–#21: Copyright Standardization, Awareness Layer Spec + Phase 1, Gaze Symmetry Fix, Courtesy Layer Phase 1

Catching up the backlog this document's own header had flagged as incomplete since the August 5 pass. Verified against actual commit history (previously a shallow clone in the session doing this pass; unshallowed specifically to check this backlog against real source rather than trust secondhand summary).

✓ **Copyright standardized across both repos.** PR #14 (`e0d698f`) added it to the in-app Privacy Policy, Terms of Use, and About Scout dialogs, plus `Scout_Awareness_Layer_Spec.md`; PR #16 (`839a28f`) added it to `README.md`; the website repo separately added it to the footer across all pages. Standard line: **Copyright © 2026 Patrick Evan Lippy. All rights reserved.** — Lippy Robotics remains a brand name only, never the legal copyright holder, everywhere it appears. A separate, duplicate copyright PR from a parallel session was closed without merging — no effect on `main`.

✓ **PR #13 merged** (`a69d096`) — added `Scout_Awareness_Layer_Spec.md`, the design document Phase 1 below implements.

✓ **PR #17 merged — Awareness Layer Phase 1** (`ff796bd`). Implements only what the spec's Phase 1 scope requires: `AwarenessState` (live, in-memory-only snapshot), `AwarenessHistoryDb` (a physically separate rolling-history store, retention modeled on `DiagnosticDb`'s existing pattern), and `AwarenessResolver` (publishes charging start/stop and connectivity lost/restored transitions from existing sensors into both). **Zero consumers read from Awareness yet, by design** — Presence, Companion Moments, speech routing, microphone handling, `HabitLayer`, `JournalDb`, and the camera pipeline are all untouched. Two follow-up fixes landed on the same PR: charging/connectivity are now seeded via plain state setters *before* any listener registers, so the initial reading is never itself misread as a transition; and the history row-count ceiling is now enforced after every insert, not just before the first insert per session, so the cap is a real hard limit. **Pending, not yet run:** the on-device A32 logging-only trial (Tests A–D) needed to confirm no false events and to size the retention count cap — see §4/§9 of the spec.

✓ **PR #18 merged — horizontal gaze bias fixed** (`a829892`). Traced both asymmetries to the repo's very first commit, with no documented reason for either. Face-tracking gain was a two-branch 1.15×/1.35× split depending on direction — replaced with one shared `GAZE_TRACKING_GAIN` (1.25×) applied regardless of side (`IRIS_MAX_X`/`IRIS_MAX_Y` still govern max travel, unchanged). The "thinking" glance always favored left before this fix — now a 50/50 coin flip per episode (magnitude range, vertical component, micro-drift, and timing all unchanged). Patrick physically confirmed the original bug on a real device before the fix; **the fix itself has not yet been re-tested on the A32.**

✓ **PR #20 merged — Courtesy Layer Phase 1** (`fc2233e`), from a parallel session; confirmed by Patrick as intentional, already reviewed, no further action needed. Wake-name-free deterministic courtesy phrases — new `ScoutCourtesyMatcher.kt`, new `Phrases.kt` pools, `MainActivity` wiring, own test suite (`ScoutCourtesyMatcherTest.kt`, 138 lines). Answered directly via `respond()`, never through `handleQuery()`/`ScoutIntentRouter` — these never reach TinyLlama or Gemini.

✓ **PR #21 merged — documentation only, already reflected in §16c/§16d of this document**, no new edit needed here. Captured the Owner Remote View concept as a future Builder's Workbench design note (§16d — off by default, owner-authenticated, local-only, no recording, reuses the existing camera frame rather than a second camera session) and clarified the autonomy/Proposal Sandbox boundary in §16c (isolated prototype-code generation stays a possible future policy question, not something current rules already permit — Scout still never generates code, modifies source, or merges/deploys anything, ever).

✓ **PR #12 merged** (`9067cf4`) — `Scout_Quick_Start.md`/`Scout_Launch_Checklist.md` updated for PR #10's Settings reorganization. This document itself was not caught up at the time — hence this backlog pass.

**Not merged:** PR #19 (an unrelated, docs-only About-screen idea from the same parallel session as PR #20) remains open — can wait indefinitely, no urgency.

---

## August 1, 2026 — PR #10 Merged: Settings Reorganized into Seven Owner-Oriented Sections

✓ **Product context.** With the recent architecture work (Companion Moments, speech reliability, memory improvements) complete and merged, Patrick shifted focus from feature work to living with Scout day-to-day and observing him in the home. Out of that came a standalone design discussion — explicitly design-only for several turns before any code was touched — to rethink Settings from the perspective of a normal owner rather than a developer. The stated goal: the menu should answer "what do I want Scout to do?" rather than "where did we put this setting?"

✓ **Seven sections replace the previous five.** The prior structure (Identity & Voice, Brain & Behavior, Builder's Workbench, Privacy & Data, Extras & Support) was organized around implementation. The new structure:
- **My Household** — Memory Export, Import Memory, Reset Memory Layers. Deliberately sparse — there's no "browse what Scout knows" screen yet, only bulk operations, and the docs say so rather than papering over the gap.
- **Companion** — the entire former Identity & Voice screen (Robot Name, Voice Pitch, Voice Speed, Reset Voice, Closed Captions) plus Presence Mode and Allow Spontaneous Comments, moved from the old Brain & Behavior screen. Answers "how does Scout feel to live with?"
- **AI** — Online Features toggle and Online Services (API key/provider management). Scoped tightly to thinking-systems plumbing, not personality.
- **Connected Services** — Calendar Awareness, moved out of the old Privacy & Data screen. The direct jump into Android's own Calendar settings is unchanged.
- **Privacy & Data** — trimmed to Voice Camera Commands, Privacy Policy, and Terms of Use after Memory moved to My Household and Calendar moved to Connected Services. Kept the name (a mid-discussion rename to "Privacy & Safety" was reverted once nothing "Safety"-labeled was left in the section).
- **Builder's Workbench** — unchanged content-wise (Enable Hardware Mode, Motor Controls, Bluetooth Pairing, Pet Awareness). Explicitly the one section exempt from the no-placeholder rule below, since Patrick framed it as his own long-term physical-robot workspace — real hardware he already owns (a KEYESTUDIO Mini Tank Kit V2: Bluetooth/app control, IR control, ultrasonic obstacle avoidance, light/ultrasonic following, an 8×16 LED panel) — not a family-facing discovery surface.
- **Advanced & Support** — Donate to Scout (new, see below), Support, About Scout (with its hidden 7-tap dev unlock), Licenses, a merged Diagnostic Report row, Clear Diagnostic History, and the hidden Performance Benchmark row.

✓ **Five rows cut after being confirmed dead via code inspection, not assumption.** `kid_safe_filter` and `pet_safety`'s Workbench-era read/write were each grepped across the full app source: `kid_safe_filter` was read and written only inside `SettingsActivity.kt` — nothing else in the app ever checked it, so toggling it changed no real behavior. Voice Tone, Online Brain Helper, Camera Controls, and Cosmetics were all pure "coming in a future update!" toasts with no backing logic at all. Online Brain Helper was additionally confirmed redundant, not just unbuilt — a grep for any brain-selection pref key or routing logic found nothing; the automatic TinyLlama-default/Gemini-opt-in behavior documented elsewhere already covers what the row claimed to do. All five were cut from the visible menu rather than relocated. Pet Awareness was **not** cut — Patrick reframed it as the seed of a future physical-safety system (his dog Nicolas is older and hard of hearing; the goal is cautious movement around pets once Scout has a mobile chassis) and it stayed in Builder's Workbench, where it already lived.

✓ **One mislabeling caught before it shipped.** Mid-discussion, Patrick proposed renaming "Allow Spontaneous Comments" to "Companion Moments" since that better reflects the feature's intent. Tracing the pref key (`spontaneous_enabled` / `PREF_SPONTANEOUS_ENABLED`) through the code showed it's wired only into `ScoutPresenceDecider` (idle-silence remarks, return greetings) — `ScoutCompanionMomentsEngine` has no reference to it anywhere, and Companion Moments itself has no user-facing toggle at all today. The rename was dropped; the row kept its existing, accurate label. If a real Companion Moments toggle gets built later, it needs its own new pref key, not this one.

✓ **View + Share Diagnostic Report merged into one row.** Both always pointed at the same `DiagReportActivity`, differing only by a boolean extra (`EXTRA_SHOW_SHARE`) that toggled whether the notes field and Share button were visible. The merged row always shows both.

✓ **New Donate to Scout screen, reusing the website's existing Stripe infrastructure rather than building anything new.** Mirrors `donate.html`'s actual mechanics (read directly from source, not assumed): five fixed tiers ($5/$10/$25/$50/$100), each backed by its own pre-created Stripe Payment Link — no free-text custom-amount entry exists on the website either, contrary to an initial assumption. Tapping a tier live-updates a single CTA button's label before the user commits; tapping the CTA opens the matching Payment Link externally via `ACTION_VIEW`, the same hand-off pattern `showSupport()` already used. Deliberately **not** routed through Google Play Billing, despite that being the original plan: grepped the app for `BillingClient`/`PurchasesUpdatedListener` and confirmed zero Play Billing integration exists anywhere in the app today (the $9.99 app price is a Play Store listing price, not an in-app purchase), so "through Google" would have meant building real new payment infrastructure from scratch. Google Play's standard commission (roughly 15–30%) would also take a meaningfully larger cut of a small donation than Stripe's flat ~2.9%+30¢. Google Play Billing requirements apply to purchases that unlock app content or functionality — a donation that unlocks nothing doesn't require it, the same pattern other apps (Wikipedia, Twitch's "support the streamer" links) already use externally. **Play Billing stays the intended path for anything that actually unlocks something later** — a better/premium brain tier, paid cosmetics (eye colors, emotes, holiday themes) — since those do trigger Play's requirement.

✓ **Two voice-command deep links repointed.** `MainActivity`'s "go online" handler and its two calendar-prompt call sites used to jump straight to the old `S_BRAIN`/`S_PRIVACY` screens via `openSettingsScreen()`. Repointed to the new `S_AI`/`S_CONNECTED` screen keys so voice commands still land where the content actually lives now. Two explanatory comments referencing "Brain & Behavior" were updated to say "AI." Two on-screen instructional strings in `LlamaBenchmarkActivity.kt` pointing users to "Extras & Support > Share Diagnostic Report" were also updated to the new path.

✓ **No preference keys renamed anywhere in this change.** Every row move is a relocation to a different screen-building function; the underlying `SharedPreferences` keys are untouched. Confirmed this was achievable without exception before starting the edit.

✓ **This was a relocation and presentation change, not a behavioral rewrite** — Patrick set that constraint explicitly partway through. `onCalendarToggleChanged()`, `confirmReset()`, `confirmDeleteDiagLogs()`, `showPrivacyPolicy()`, `showTermsOfUse()`, `showLicenses()`, `showAbout()`, `showSupport()`, and the 7-tap dev-unlock logic in `onAboutScoutTapped()` are all untouched function bodies — only which screen calls them changed. The two exceptions (the Diagnostic Report merge and the voice deep-link repoint) were both explicitly requested, not incidental.

✓ **CI confirmed green on both the `push` and `pull_request` triggers** — `assembleDebug` and `testDebugUnitTest` both passed on commit `6e1da3b`, no Android SDK was available in the session doing the work so this was the only real build verification, and PR #10 merged clean with `mergeable_state: "clean"` and zero review comments.

**What's left is on-device verification, not unfinished implementation.** CI confirms the app compiles and the unit test suite passes; it does not confirm the seven section cards actually open the right screens, that the two repointed voice deep-links actually land correctly, that the Donate tier buttons visually update and open the correct Stripe checkout page per tier, or that the hidden dev-benchmark unlock still surfaces correctly in its new location. All four are called out explicitly as pending A32 checks, matching how Companion Moments' "validation, not unfinished implementation" note was framed on July 31.

---

## July 31, 2026 — PR #8 Merged: Companion Moments Fully Wired and Live on `main`

✓ **PR #8 merged — Companion Moments wiring** (branch `claude/companion-moments-wiring`, deleted after merge). Merge commit `a85177e95b7873250cde4e37ae7a41c1ba89f638`. Adds the real `MainActivity` call site (`maybeMakeCompanionMoment()`/`speakCompanionMoment()`/`buildCompanionSignals()`/`resolveCompanionMomentText()`, evaluated from the same face-detection frame callback and 30-second throttle as the existing presence checks), four new `VoiceBank` phrase pools (`COMPANION_ENVIRONMENT`, `COMPANION_CURIOSITY`, `COMPANION_MEMORY_INTRO`, `COMPANION_OBSERVATION_FALLBACK`), `JournalDb.getEntriesSince()` for novelty tracking (daily budget and per-category/per-content-key last-fired history are both derived fresh from `JournalDb` on every check, never an in-memory counter), and `DiagLog.logCompanionMoment()` (category/confidence/contribution-key diagnostics only — no facts, names, or spoken text). **Companion Moments is now fully wired and live on `main` — not just an engine sitting unused.**

✓ **Shared proactive-speech timestamp is live, exactly as designed.** `ScoutPresenceDecider` gained `msSinceLastPresenceRemark()`/`onExternalProactiveRemark()` — one shared timestamp both systems read and write, each still compared against its own unchanged interval: Presence keeps its existing 20-minute `PRESENCE_GLOBAL_COOLDOWN_MS`; Companion Moments compares that same shared timestamp against its own 45-minute `SHARED_PROACTIVE_COOLDOWN_MS`. Either system speaking suppresses the other only for the other's own interval — Presence's existing behavior was not retuned or shortened to match Companion Moments.

✓ **Companion Moments alone carries the persisted three-per-day budget** (`DAILY_MOMENT_BUDGET = 3`, an engine-side constant, independent of and in addition to the shared cooldown above). Derived fresh from `JournalDb` `'companion_moment'` entries against the local calendar day (`java.time.LocalDate`) on every check, so a process restart doesn't reset the day's count.

✓ **All five findings from an independent ChatGPT review of the actual PR #8 diff were resolved before merge, not after:**
1. **Arrival-event latching.** The second-face-arrival signal (Environment category) was a one-frame boolean the 30-second throttle would almost always miss before the next camera frame overwrote it back to `false`. Replaced with a latched pending timestamp (`secondFaceArrivalPendingSinceMs`), consumed exactly once via the new `ScoutArrivalLatch.consume()`, with a bounded 5-minute staleness window so a very late consumption doesn't speak a no-longer-honest "someone just joined."
2. **Entity-aware Memory phrasing.** The spoken sentence previously hardcoded "your ..." regardless of which entity a fact actually belonged to — a fact about Diana could have been spoken as if it were the user's own. New `ScoutMemoryPhraser` (in the new `brain/ScoutCompanionMomentsWiring.kt`) resolves `user_primary`→"your", `scout`→"my", any other known entity→its own possessive display name (e.g. "Diana's"), and aborts the moment entirely — rather than guessing — for a blank/unresolved entity.
3. **Session-scoped conversation flag.** `hasHadConversationThisSession` previously latched `true` once and stayed there for the Activity's entire process lifetime. It now resets whenever the tolerant continuous-presence streak it's scoped to itself restarts — the same streak `CURIOSITY_MIN_PRESENCE_MS` is measured against — via the newly extracted `ScoutPresenceStreakTracker`.
4. **Executor lifecycle protection.** `companionMomentsExecutor` gained a generation token (`companionMomentsGeneration`), mirroring `ScoutLlamaController`'s existing owner/generation-token pattern: bumped in `onDestroy()`, checked before the background-to-UI-thread hop and again before actually speaking (since a posted `Runnable` can still run after the Activity is destroyed). `execute()` is now guarded against `RejectedExecutionException`, and `shutdown()` was replaced with `shutdownNow()` so queued/in-flight work is dropped rather than left to finish against a destroyed Activity.
5. **Test coverage.** New `brain/ScoutCompanionMomentsWiringTest.kt` (18 tests) covers all four fixes above as small, pure, unit-tested helpers — independent of `ScoutCompanionMomentsEngine`'s own existing test suite.

✓ **CI confirmed green on the merge commit**, including the `Run JVM unit tests` step (not just `assembleDebug`), on both the `push` and `pull_request` triggers.

**What's left is validation and tuning, not unfinished implementation.** Companion Moments' code is complete and merged into `main`. Its starting values — the 45-minute shared cooldown (on top of Presence's own unchanged 20-minute interval), the 3/day budget, the 0.50 confidence threshold, and the 2–24 hour per-category cooldowns — are deliberately conservative and untuned by design (see the July 30 entry below). Real-world A32 observation is the next step, to see whether Scout's social timing feels right in day-to-day use, not to finish building the feature. Findings from that observation belong in a new dated entry when they happen, not folded into this one.

---

## July 30, 2026 — Workflow Change to `main`; PRs #3–#6 Merged; Companion Moments Approved as a Major Priority

**Workflow change — `main` is now Scout's single source of truth.** Documented in `CLAUDE.md`. New work uses short-lived feature branches (naming convention `claude/**`), merges into `main` via pull request, and the branch is deleted once merged — no more long-lived development branches. CI (`.github/workflows/android-build.yml`) triggers on push to `main` and `claude/**`, and — as of PR #6, merged — also on pull requests targeting `main`.

✓ **PR #3 merged — speech-listening cleanup.** Removed a redundant no-op `scheduleListenRestart()` call in `requestSpeechStartup()`. Merge commit `ff26a367ef005213a36918ed789c8886fc79896c`.

✓ **PR #4 merged — speech reliability designs** (branch `claude/speech-reliability-designs`, deleted after merge). Merge commit `c14671f2592e422c1f4cafe718ecfd3e8a5cfd7e`. Two independent pieces, both fully wired into `MainActivity` (confirmed via the merge diff: `MainActivity.kt` itself changed, not just new standalone files) — this is live behavior on `main` today, not just added-but-dormant code: (a) `FuzzyNameMatcher.kt` — generic edit-distance wake-word tolerance, so a renamed Scout gets the same mishearing tolerance the default name already had, replacing a hardcoded list of alternate spellings that only covered "Scout" itself; (b) `ScoutSpeechAvailabilityMonitor.kt` — Tier-1-only detection of a sustained pattern of network-dependent recognizer failures, so Scout can honestly warn about a possible speech-recognition-unavailable situation instead of silently retrying forever. Both ship with their own unit test suites, and — since CI now actually runs tests (see PR #6) — both suites are confirmed passing in CI, not just review-verified.

✓ **PR #5 merged — Companion Moments decision engine** (branch `claude/companion-moments-engine`, deleted after merge). Merge commit `1b5deb19dfced44529f571b30d27c622e8e12fb3`. Adds `ScoutCompanionMomentsEngine.kt` and its test suite: the pure decision-logic engine for a new "Companion Moments" system (design detailed below). Confirmed via the merge diff that this PR touched only the two new engine/test files — **engine only, not wired in**: no `MainActivity` call site, no `VoiceBank` phrase pools, no `DiagLog` entries, no `JournalDb` reads/writes yet. Wiring is explicitly a separate, later PR (see design note below) — do not assume Companion Moments is live behavior.

✓ **PR #6 merged — CI now runs unit tests, not just a compile check** (branch `claude/ci-run-unit-tests`, deleted after merge). Merge commit `d1a56ac8615fd8fa065790d1318bb953f9a79127`. Expanded `.github/workflows/android-build.yml` to run `./gradlew testDebugUnitTest` after `assembleDebug`, and added the `pull_request` → `main` trigger mentioned above. Running tests for the first time immediately exposed a real, pre-existing bug (not introduced by this PR) — see the `ScoutMemoryGate` item directly below, fixed and merged on this same branch.

✓ **Real pre-existing bug found by CI actually running tests — `ScoutMemoryGate.SELF_WORDS` didn't recognize the user addressing Scout directly.** `ScoutMemoryGateTest`'s "what did you learn today" case was failing: `SELF_WORDS` only matched the user referring to *themselves* (`my`/`me`/`i`/`us`/`we`), not the user addressing *Scout* (`you`/`your`) — both are legitimate personal-memory phrasings. Fix (merged via PR #6): added `"you"`/`"your"` to `SELF_WORDS`. Kept deliberately narrow — `SELF_WORDS` only matters when paired with a real `TOPIC_WORD`, so ordinary commands like "can you set a timer" are unaffected. Confirmed deterministic intents (e.g. `WEATHER`) are matched before the memory gate is ever reached, so routing order is safe.

**Companion Moments — approved direction, design locked, engine merged, wiring not yet built.**

✓ **Product-priority context.** During over an hour of continuous real-device testing, Scout remained technically stable but felt too passive and boring — he mostly watches the room and waits to be spoken to. Improving Scout's sense of meaningful initiative is now a major product priority. This is explicitly **not** the same thing as "just make him talk more" — restraint is the core design constraint of Companion Moments, not a side concern.

✓ **Relationship to the existing Presence system.** `ScoutPresenceDecider` (already shipped, July 27–28) continues to own return greetings and idle-silence courtesy remarks — unrelated to and unchanged by Companion Moments. Companion Moments is scoped to coexist with Presence, not duplicate or replace it: its own Environment category only covers a second person joining, not return-from-absence or a quiet room, which stay Presence's job. The two systems share **one** proactive-speech cooldown/budget — the user experiences one Scout, not two independently-timed subsystems talking over each other.

✓ **Design constraint: Scout must never fake a signal he cannot observe.** No audio-tone/sentiment signal exists anywhere in the app today. Companion Moments generates candidates only from what's actually measurable (camera presence, taught facts, conversation cadence, habit patterns) — never invented emotion, laughter detection, or focus.

✓ **Restraint gates come first, unconditionally, before any scoring.** `ScoutCompanionMomentsEngine.evaluate()` checks, in order: a situational safety gate, the shared proactive-speech cooldown (with Presence), a daily moment budget (3/day in the current design), and each category's own cooldown — all hard gates, checked before any candidate is even generated, and none of them can be overridden by a high-scoring candidate. Only after all of them pass does the engine generate grounded candidates across four categories (Environment, Memory, Observation, Curiosity) and score them **additively** — each contributing factor adds to a 0–1 confidence score, capped at 1.0, never multiplicative.

✓ **Silence is the intended, common default outcome**, not a fallback or failure state — most evaluations are expected to return no candidate at all.

✓ **The engine carries no literal spoken text.** It returns a category plus a stable content key; actual wording stays `VoiceBank`'s job (the existing, separate phrase-pool system), which keeps the decision layer privacy-safe and diagnostic-safe by construction — nothing it logs can ever contain a spoken sentence, a name, or a fact's value.

✓ **Deterministic tie-breaking** when multiple candidates are simultaneously eligible: confidence score first, then a time-sensitivity rank, then least-recently-used content, then a fixed category order as the final fallback.

✓ **MainActivity wiring is explicitly a separate, later PR** — the real call site, `VoiceBank` phrase pools for the four categories, `DiagLog` diagnostic entries, and `JournalDb` novelty-tracking reads/writes are all intentionally out of scope for PR #5. When that follow-up PR happens, it is intentionally scoped to only that wiring work — no new sensors, no emotion assumptions, no expanded categories.

**Update (July 31):** that wiring PR (#8) shipped and merged — see the July 31, 2026 entry above. Companion Moments is no longer engine-only.

---

## July 29, 2026 (Documentation Consistency Pass) — Four-Document System Cross-Checked

Per Patrick's request, a final pass across all four documents (`Scout_Master_Summary.md`, `MAIN BUILD PATH - ACTIVE.md`, `MainActivity Cleanup.md`, `Architecture.md`) before treating them as the new source of truth. No code changed — documentation only. Findings:

✓ **Fixed a real factual error in `Architecture.md`** — it claimed "six SQLite databases" in two places; a direct repo-wide check found exactly five `SQLiteOpenHelper` subclasses (`TruthDb`, `PeopleDb`, `JournalDb`, `ConversationDb`, `DiagnosticDb`). Corrected both occurrences. Also aligned its `MainActivity.kt` line-count figure with `MainActivity Cleanup.md`'s exact count (4,923) instead of a rounded approximation.

✓ **Removed cross-document duplication of the presence-layer temporary-value TODO** — the exact same three smoke-test values (with the same restore targets) were independently listed in both `MAIN BUILD PATH - ACTIVE.md` and `MainActivity Cleanup.md`, risking the two silently drifting apart. `MainActivity Cleanup.md` §6 now points to `MAIN BUILD PATH - ACTIVE.md` instead of restating the values.

✓ **Removed an unverified, non-actionable item from `MainActivity Cleanup.md`** — a bullet speculating that "some other file might still have a stale startup-gate comment" without pointing to an actual file, cut since it wasn't a concrete finding.

✓ **Refreshed `MAIN BUILD PATH - ACTIVE.md`'s PR #1 statistics** — they were captured before this documentation pass's own commits landed on the PR's branch and were already one commit stale (254→255 commits, 90→93 files, +10,400→+10,835/−2,727→−2,729 lines).

✓ **Addressed a structural duplication between this document and `MAIN BUILD PATH - ACTIVE.md`** — this document's own "Pending — Launch Blockers" list (§7) and "Known Issues" table (§7c) were tracking several of the same still-open items `MAIN BUILD PATH - ACTIVE.md` now also tracks (Fold 7 testing, Play Asset Delivery, Open Source Credits, Barge-in, Scout news feed, `ScoutFaceView` dead code, and others). Rather than delete this document's history (against its own stated policy), added a note at the top of each section: both are now frozen as historical snapshots going forward, with `MAIN BUILD PATH - ACTIVE.md` (behavioral/product) and `MainActivity Cleanup.md` (code-level) as the live trackers for any still-open item. New "DONE"/"RESOLVED" annotations can still be added here when something ships; new open items should not be added to §7/§7c anymore.

✓ **Verified all header commit hashes** — updated across all four documents to `5867c54ba29de4e86ddbd3eadf7ac21cdef2d86f`, the commit whose source tree every specific claim in all four documents was checked against (this pass changes only documentation, so the underlying Kotlin/Gradle/manifest source is unchanged from that commit).

**Not changed**: no contradictions were found in `Architecture.md`'s §19 "Future Architecture Notes" (already correctly distinguishes built-today from planned/proposed throughout); no duplicate active tasks were found between `Architecture.md` and either `MAIN BUILD PATH - ACTIVE.md` or `MainActivity Cleanup.md` (`Architecture.md` consistently cross-references rather than restates, e.g. §11's presence-threshold mention).

---

## July 19–24, 2026 (Previously Undocumented) — Model Delivery Fixed; Full Startup Gate Built

Found and reconciled while auditing the codebase directly against this document (per Patrick's request to base documentation on the current code, not older notes) — this entire body of work happened but was never given its own dated entry here. Only a fragment of it was mentioned in passing inside the July 25 Session Log line below. Filling the gap now, in commit order.

✓ **`MODEL_DOWNLOAD_URL` placeholder fixed** — `ModelDownloadActivity.MODEL_DOWNLOAD_URL` now points at the actual TinyLlama model hosted as a GitHub Release asset (`github.com/Patevan9/Scout/releases/download/model-v1/...`), replacing the unfilled placeholder that the July 19 16KB-alignment entry (further down this document, from earlier that same day) had flagged as blocking real TinyLlama delivery on a release build. July 19.

✓ **Model download debugged end-to-end from a real on-device stall** — `setDestinationUri(Uri.fromFile(...))` pointed Android's `DownloadManager` at a raw `file://` path inside Scout's app-specific external directory; under scoped storage the `DownloadManager` system process can't write there via a raw file path, so the request was silently accepted but never progressed (confirmed: the identical URL downloaded fine in Chrome, which writes to the public Downloads folder instead). Fixed via `setDestinationInExternalFilesDir`, the API Android provides specifically for this case. A second, quieter failure mode surfaced once the first was fixed (0% progress, zero notifications, zero crashes) — three rounds of on-device Logcat testing on both A32 and Fold 7 narrowed it down and added full diagnostic logging at every previously-silent branch of the download flow (enqueue, storage checks, poll loop, DownloadManager row lookups), plus on-screen Toast checkpoints around the `modelDownloadLauncher.launch()` boundary specifically because Logcat/adb capture was itself unreliable during this exact window (USB reconnects colliding with high log volume from camera HAL/TTS). July 20–21.

✓ **Full unified startup gate built** — `ModelDownloadActivity` became the single gate `MainActivity` always waits on before showing its face, asking any permission, or starting camera/mic — not just when the model file happens to be missing. Three phases in order: Downloading (only if the model isn't present locally), Loading offline brain (triggers `LlamaEngine.loadAsync()` itself — the only way to avoid a load-ordering race with `MainActivity`'s own boot), and a brief Preparing beat. Explanatory text and rotating, already-shipped-feature tips added below the progress bar, with the tip rotation deliberately starting on the explanatory line first so first-time users understand what's happening. July 23–24.

✓ **Three startup-gate bugs fixed after first on-device test pass** — (1) TTS's `onInit()` callback was speaking the boot status announcement unconditionally the moment the TTS engine itself initialized, completely independent of the new gate, and TTS almost always finishes before the offline brain does — Scout was heard speaking while the loading screen was still up. Deferred via `pendingBootAnnouncement`, spoken only once `startSystems()` confirms the brain is ready. (2) The Downloading phase showed nothing explaining what was happening once the explanatory tip line was moved to the Loading-only phase. (3) A third, related bug (see below). July 24.

✓ **Stale boot announcement fixed properly; Loading phase restyled** — `pendingBootAnnouncement` originally stored the boot line's *text*, captured at TTS-init time, before the offline brain was necessarily ready — so a stale "still warming up" message could get spoken later even after the brain had actually finished loading. Changed to a boolean flag; the line is now built fresh (`bootStatus.build()`) at the moment it's actually spoken. The Loading phase visually looked like a continuation of Downloading (same background, same bar, a percentage with nothing real behind it) — switched to a solid black background matching Scout's own face screen and a single bold status line instead of a fake progress bar. "Loading offline brain..." reworded to "Waking Scout up…" — friendlier, consistent with the rest of the loading copy. July 24.

**Play Store submission note**: this closes the delivery gap the July 19 16KB-alignment entry flagged — TinyLlama now actually reaches a real device through the app itself, not via manual `adb push`. Play Asset Delivery (mentioned in that same entry as a future alternative) remains unimplemented and is not currently a blocker, since the GitHub-Release-asset + `DownloadManager` path is functional end-to-end.

---

## July 26–29, 2026 — What Changed Since Version 50

✓ **Personal-memory questions now stop before Gemini — structural guarantee, not phrasing-dependent** — New `ScoutMemoryGate.isPossiblePersonalMemoryQuery()`, checked at the top of `handleUnknownIntent()` before `tryGemini()` is ever called. Deliberately biased toward over-triggering (a false positive just costs a wasted TruthDb check; a false negative would leak a personal question to fact-blind Gemini). Two independent signals: a self-reference word + personal-topic word (whole-word regex, not `.contains()`), or mention of a name Scout already knows. `handlePersonalMemoryQuery()` gives a hard "I don't know" when TruthDb is empty, otherwise reuses `tryTinyLlamaOrFallback()` (already grounds every reply in facts, never calls Gemini). `ScoutIntentRouter`'s wife/son/dog blocks now only fire for a single-relation query — compound mentions ("my wife and son's names") fall through to the gate instead of silently answering only one. July 26.

✓ **TinyLlama SIGABRT fixed — chunked prefill, not a single oversized batch** — Confirmed from on-device logcat: a 533-token prompt exceeded `n_batch=512` in one `llama_batch_init()`/`llama_decode()` call, and llama.cpp aborts (`ggml_abort` → SIGABRT) the instant that happens. The personal-memory gate's fact grounding could easily push a prompt past 512 tokens once a dozen facts plus history are folded in. `nativeGenerate()` now prefills in chunks of at most `kNBatch` (512) tokens, with absolute token positions preserved across chunks. Also fixed a related logit-indexing bug (`llama_get_logits_ith` indexes into the *last* decode call's batch, not a global position) and added a guard that refuses and logs instead of ever submitting an oversized batch again. July 26.

✓ **Teaching moved from sentence templates to entity+property extraction** — Root-caused two bugs: "Diana's birthday is November 27th" was never stored (`TeachExtractor` only recognized "my ___ is ___"), and nickname clauses ("we call him Nick") were silently dropped — no alias concept existed anywhere. New `ScoutFactExtractor.kt` extracts (subject, property, value) anchored on property keywords and known entity names, order-independent for dates ("Diana's birthday is Nov 27" / "Nov 27 is Diana's birthday" / "Diana was born on Nov 27" all extract the same fact). New `ScoutEntityResolver.kt` resolves "my wife"/"diana" to the entity slug its facts live under — no `wife_birthday`-style key needed, scales as Scout learns more people/pets. `TruthDb.addAlias()`/`getAliases()` store a real comma-joined alias list per entity (Nicolas/Nick/etc. all resolve together); `ScoutMemoryGate` and TinyLlama grounding both updated to recognize aliases. Teaching statements never reach TinyLlama/Gemini — `handleTeaching()` tries the extractor first and always confirms exactly what was learned, or asks for a rephrase via `looksLikeUnrecognizedTeaching()` (hint-word confidence signal, never required). `TeachExtractor.kt` (face-recognition identity teaching) left untouched. July 26.

✓ **"Who is Diana?" answered by direct TruthDb lookup, not TinyLlama inference** — On-device confirmed TinyLlama had the correct fact in its prompt but didn't reliably connect "wife's name: Diana" to "who is Diana" — answered with generic name trivia instead. `handlePersonalMemoryQuery()` now checks "who is/who's <name>" directly against `wife_name`/`son_name`/`dog_name` (and aliases) before ever falling back to TinyLlama, the same pattern as the existing `ASK_WIFE_NAME`-style handlers. July 27.

✓ **Presence Layer, moment 1 — idle-silence acknowledgment** — Per Patrick's real-world testing: four hours of continuous silent operation with zero acknowledgment reads as a camera watching the room, not a companion. `ScoutPresenceDecider` gains `canMakeIdleSilenceRemark()`/`onIdleSilenceRemarkMade()` — reuses the existing time-of-day mode (QUIET/SLEEP excluded), a global cross-moment cooldown, and a longer category-specific cooldown; presence threshold ~75 min (deliberately conservative for a first test). Fixed two real correctness issues found during design before shipping: `isListening` is true almost continuously while idle (recognizer sessions just cycle) — new `isCapturingSpeech` flag (set only between `onBeginningOfSpeech()` and session end) is the real gate instead; and `faceAppearanceMs` resets on any single missed frame, which would make a 75-minute uninterrupted timer nearly unreachable — new gap-tolerant streak (`presencePresentSinceMs`/`presenceLastSeenMs`, 2-min grace) used only by this feature. `respond()` gained `isPresenceInitiated` (default false) so a presence-initiated remark doesn't misread itself as a long-absence return. New 40-second presence reply window opens when Scout *finishes* speaking (TTS `onDone`, not `onStart`). Also fixed two hardcoded `"scout"` string checks (`looksLikeDirectAddress()`, `handleTeaching()`'s background-speech guard) to read the TruthDb-configured name instead. New `PRESENCE_IDLE_SILENCE` phrase pool. July 27.

✓ **Real proactive return greeting, replacing a broken one** — Confirmed via code inspection that Scout never had a working "welcome back": the vision first-contact greeting fires at most once per process and shared the `!isListening` bug above; `consumeLongAbsenceGreeting()` measured gaps between Scout's *own* responses (not the camera at all), set its pending flag too late to ever fire on the first post-absence utterance, and only worked if that utterance happened to parse as GREET. Removed entirely. New genuine-absence + stabilized-return tracking driven by actual face presence (reuses `presenceLastSeenMs`): `CAMERA_GAP_TOLERANCE_MS` (15s, absorbs missed frames), `MIN_GENUINE_ABSENCE_MS` (10 min production), `RETURN_STABILIZATION_MS` (3s) before Scout actually speaks. Gated the same way as the idle-silence remark (shared global cooldown + its own 30-min category cooldown). New `PRESENCE_RETURN_GREETING` phrase pool. Diagnostic logging added throughout (tag `ScoutPresenceDebug`). A temporary smoke-test build (lowered thresholds, extra logging) shipped first for A32 verification, then the presence-layer commits above were built at production values. July 27–28.

✓ **Listening reminder made vision-led, not just "a face existed"** — Root cause: the "say my name first" reminder fired off any face detected as frame-largest within 3 seconds, with zero regard for whether it was oriented toward Scout — almost certainly why Scout interrupted Diana talking to Elijah. New per-frame gate using ML Kit's existing head-yaw output (no detector-config change needed): a face only counts as "facing Scout" within a yaw tolerance, minimum face-height fraction, and center-offset bound, sustained continuously for 1.5s (any disqualifying frame resets the streak). Reminder decision is reason-based (no face / not oriented / not sustained / cooldown / busy / eligible) for diagnostics. Thresholds tightened to conservative test values in a follow-up pass (yaw 25°→18°, face height 12%→18%, offset 0.55→0.40), plus a `VISION_FRESHNESS_MS` staleness check so a stalled vision pipeline can't leave a stale "qualifying" streak sitting untouched, and real measured yaw/height/offset values logged (not just the pass/fail category) for evidence-based tuning. `isSpeaking`/`isThinking` folded into the same reason chain so a logged "eligible" always matches the real decision. July 28.

✓ **Dev-only on-device TinyLlama benchmark harness** — Instrumentation only, zero change to production generation behavior/thread config. Native: exposed `n_threads_batch` as a real struct field (was landing unread in ABI padding), added `llama_perf_context()` bindings, extracted a shared `runGeneration()` helper so the benchmark path and production `nativeGenerate()` use identical code with different parameters. Measures prefill time, true wall-clock time-to-first-token, generation time, and total duration; returns performance metrics only, never the generated reply text (matches DiagnosticDb's existing invariant). New hidden dev screen (7-tap unlock on "About Scout," mirroring Android's own build-number convention) runs 4 fixed synthetic prompts across 6 thread combinations. A follow-up fix replaced the harness's sequential run order (which biased results toward low-thread combos always running while the device was coolest) with a deterministic Latin-square rotation, plus a brief cooldown pause between runs and a `runIndex` field so results can be cross-referenced against thermal load. Fixed an XML manifest comment containing a literal `--` (illegal mid-comment, broke Gradle's manifest merge and silently prevented the prior commit's code from compiling at all). July 28.

✓ **A32 crash root-caused and fixed — startup collision, not a Scout or benchmark bug** — Confirmed from a full 12,078-message on-device logcat capture: camera + ML Kit face detection + SpeechRecognizer were all starting in the same instant, colliding with a one-time multi-second ART bytecode-verification pass over Google Play Services' ML Kit classes; the resulting memory pressure killed GMS's own persistent process, and Android killed Scout as a side effect of depending on a GMS content provider in that dying process — not Scout itself being heavy. Confirmed the new benchmark harness played no role (zero related log lines; native benchmark method only resolves on first call). `requestCameraStartup()`/`requestSpeechStartup()` now stagger camera (3s) and speech (4.5s) startup after the existing `LlamaEngine.isReady` gate opens, idempotent and lifecycle-safe (re-checks `isFinishing`/`isDestroyed`/`isForeground`/permission before firing; only protects cold start, steady-state restarts unaffected). New `startupSettled` flag (6s after camera starts) additionally gates face-embedding specifically. New `ListenAttemptReason` enum + deduped diagnostic logging across every `maybeStartListening()` branch, plus wall-clock startup timing markers. July 28.

✓ **Seven ChatGPT-reviewed privacy/reliability fixes** — Hard offline-brain gate could be bypassed via `launchLoadingGate()`'s catch block (now shows a non-cancelable retry dialog instead of silently starting systems). `LlamaEngine.free()` discarded `awaitTermination()`'s return value (fixed, later fully superseded by `ScoutLlamaController`, see below). OpenAI/Claude key setup was misleading — keys could be saved but nothing in Scout used them (now flagged via `Provider.isAvailable`, hidden from the picker). API keys were plain SharedPreferences strings, and the Android Studio sample backup-rules templates were untouched (real device-transfer exclusion rules written; keys later encrypted, see below). `ScoutMemoryGate` alias handling fixed for a plural-key mismatch. `TruthDb.upsertFact()` only updated `value`/`updated_at` on conflict, silently leaving `confidence`/`source`/`last_confirmed` stale (fixed). `TruthDb` schema migration reviewed — `onUpgrade()` is empty but there's no schema change yet to migrate, confirmed as a non-issue, not a bug. July 29.

✓ **Seven ChatGPT-reviewed mic/camera fixes** — `onEndOfSpeech()` no longer prematurely calls `scheduleListenRestart()` (real `ERROR_RECOGNIZER_BUSY` risk). Wake-word bare `"out"` substring match (and the same risk for short custom names via `.contains()`) replaced with a whole-word `containsWholeWord()` check. Silence timeout is now mode-aware (5s/4s wake-word listening vs. 10s/7s open-conversation listening) instead of one fixed value regardless of mode. `ImageAnalysis` now sets an explicit 640×480 target resolution instead of allocating a full-size bitmap every analyzed frame. Scene labeling (`ImageLabeling`) now throttled independently from face detection (1.5s minimum interval) instead of running at the same ~7fps cadence. `cameraEverStarted` now only sets after `bindToLifecycle()` actually succeeds, not immediately after calling `safeStartCamera()`. Forcing `EXTRA_PREFER_OFFLINE` with no fallback was flagged as a bigger feature/privacy decision and deliberately left unimplemented pending explicit direction. July 29.

✓ **API keys encrypted via Android Keystore; TinyLlama lifecycle race fixed** — New `ScoutSecureKeyStore` (AES-256-GCM, versioned `"v1:<iv>:<ciphertext>"` format) replaces plain-string SharedPreferences storage — deliberately not `androidx.security-crypto`'s `EncryptedSharedPreferences`/`MasterKey` (both deprecated since 1.1.0-alpha07 over real reliability problems, confirmed via research, not just API churn); uses only platform Keystore APIs, no new Gradle dependency. Encryption may create the Keystore key; decryption only ever looks up an existing one and fails cleanly (typed `Available`/`Unavailable` results for both directions) rather than risk decrypting old ciphertext with a mismatched fresh key. One-time migration encrypts any pre-existing plaintext beta key on first read. Separately, diagnosed a deeper lifecycle-concurrency issue: `MainActivity`'s old per-Activity-instance `llamaExecutor` and generation-counter meant a configuration-change recreation could either leak the old instance's executor thread or leave a stale generation able to deliver its result to a destroyed Activity's callback. New `ScoutLlamaController` (process-wide singleton) now owns the single generation executor and a unified owner/generation token for the app's entire lifetime; `shutdownSystems()` only frees the ~800MB model on a genuine close (`isChangingConfigurations()`-aware), via a bounded `tryLock`-based `LlamaEngine.freeIfIdle()` rather than blocking the main thread indefinitely. Two follow-up corrections after a second review pass: added `invalidateOwner()`, called unconditionally on every `onDestroy()` (not just real closes) so a generation finishing after a real close can never reach a destroyed Activity's callback; moved discard-event logging inside `ScoutLlamaController` itself (an application-Context-scoped `DiagLog` it owns) instead of a caller-supplied lambda that captured `MainActivity`'s Activity-scoped `diagLog`. Also gave `encrypt()` a typed failure result (`EncryptResult`) instead of throwing, and switched the plaintext-key migration write from `apply()` to `commit()` so a failed persist doesn't get silently treated as done. July 29.

---

## July 19, 2026 — 16KB Alignment CONFIRMED PASS on Real Release APK: What Changed Since Version 49

✓ **16KB page size — RESOLVED, verified against the actual built release APK.** Patrick built a signed **release** APK (not a debug build) and ran Google's own `zipalign -c -P 16 -v 4` verification tool directly against it — the authoritative local check for this requirement. Full itemized result: `libLiteRt.so`, `libLiteRtClGlAccelerator.so`, `libface_detector_v2_jni.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so`, `libggml.so`, `libimage_processing_util_jni.so`, `libllama-common.so`, `libllama.so`, `libmlkitcommonpipeline.so`, and `libscout_llama.so` — all 11 previously-flagged libraries — each individually listed **`(OK)`**, with an overall result of **"Verification successful."** Separately, installing this release APK on the Fold 7 no longer triggers the "Android App Compatibility" 16KB dialog at all.

**What this confirms about the July 18 investigation:** the dialog seen on July 18 was specific to the **debuggable** build — the dialog's own text says as much ("This warning is showing because this is a debuggable app which is currently being tested"). The five llama.cpp/ggml prebuilt libraries were correctly ELF-aligned all along (confirmed via `readelf` on July 18, later same day). `libimage_processing_util_jni.so`'s alignment was genuinely fixed by the July 10 ML Kit version bump — this zipalign pass is the first real confirmation that fix landed correctly in an actual build, not just an isolated AAR. The root cause really was the debug-build install path, exactly as hypothesized in the July 18 "Later Same Day" correction below.

**Play Store submission is unblocked on the 16KB front.** This is the first claim anywhere in this entire 16KB investigation verified against the real shipped artifact using Google's own tool — not an isolated file, not a debug-only dialog, not an inference. Every prior "REOPENED"/"blocked" entry below (dated July 18) is superseded by this entry; those entries are left in place as a historical record of the investigation rather than deleted.

---

## July 18, 2026 — What Changed Since Version 47

⚠ **16KB page size — REOPENED, contradicted by real Fold 7 device evidence** — Patrick's Samsung Fold 7 (Android 15) shows Android's own "Android App Compatibility" dialog at app launch — a live OS-level ELF alignment check. It lists **11 native libraries** as NOT 16KB aligned: `libLiteRt.so`, `libLiteRtClGlAccelerator.so` (LiteRT), `libface_detector_v2_jni.so`, `libimage_processing_util_jni.so`, `libmlkitcommonpipeline.so` (ML Kit), `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so` (the llama.cpp/ggml stack), and `libscout_llama.so`. This is every native library in the app, and it directly contradicts three separate "DONE"/"FULLY DONE" claims made across this document: the July 7 `scout_llama.so` alignment fix, the July 10 ML Kit alignment claim, and the July 17 "readelf VERIFIED PASS" for LiteRT (`libLiteRt.so` — the exact file that check reported as passing is the same file failing on the real device).

**Root cause, confirmed by reading `app/src/main/cpp/CMakeLists.txt` directly:** the `-Wl,-z,max-page-size=16384` linker flag added July 7 is applied only to the `scout_llama` build target — Scout's own thin JNI wrapper, the only native code this project actually compiles. `libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, and `libggml-cpu-android_armv8.2_2.so` are **pre-built binaries checked directly into `app/src/main/jniLibs/arm64-v8a/`**. `CMakeLists.txt` only links against them (`-lllama -lllama-common -lggml -lggml-base -lggml-cpu-android_armv8.2_2`) — it never compiles them, so the flag has no mechanism to reach them. Even `libscout_llama.so` itself is still failing on the real device, meaning the July 7 fix may never have actually taken effect (a stale native build cache is the leading suspect — the flag is present in source but the `.so` may not have been rebuilt since).

The ML Kit and LiteRT "done"/"verified" statuses were both based on checking an isolated artifact (a Maven AAR, an extracted library) rather than the actual built and installed APK. This real-device dialog is the first check in this entire 16KB investigation that has actually looked at what ships.

**Real remaining work, not yet started:**
1. Source or rebuild 16KB-aligned versions of the five prebuilt llama.cpp/ggml libraries — either a newer upstream llama.cpp release built with alignment support, or a from-source NDK rebuild with the linker flag applied throughout the whole dependency chain.
2. Do a full clean rebuild and re-check `libscout_llama.so` specifically, to rule out a stale build artifact before assuming the flag itself is insufficient.
3. Re-verify ML Kit and LiteRT against the real built APK's bundled `.so` files, not an isolated AAR or Maven artifact.

**Play Store submission is NOT unblocked on the 16KB front.** Every prior "FULLY DONE"/"PASS" claim regarding 16KB alignment elsewhere in this document (dated July 7 through July 17) is superseded by this entry — those claims are left in place below as a historical record, each flagged inline, rather than deleted, so the investigation trail stays intact.

---

## July 18, 2026 (Later Same Day) — 16KB Root Cause Refined: Version 48's Diagnosis Was Partly Wrong

⚠ **Correction to the entry directly above.** The claim that the five prebuilt llama.cpp/ggml libraries (`libllama.so`, `libllama-common.so`, `libggml.so`, `libggml-base.so`, `libggml-cpu-android_armv8.2_2.so`) are unaligned at the source level does not hold up. Running `readelf -lW` directly against the actual files checked into `app/src/main/jniLibs/arm64-v8a/` in this repo — the real binaries, not documentation — shows every one of them already has `Align 0x4000` (16384 bytes = 16KB) on every LOAD segment:

```
libllama.so                      LOAD ... Align 0x4000
libllama-common.so               LOAD ... Align 0x4000
libggml.so                       LOAD ... Align 0x4000
libggml-base.so                  LOAD ... Align 0x4000
libggml-cpu-android_armv8.2_2.so LOAD ... Align 0x4000
```

These five are already ELF-aligned. Their naming (`libggml-cpu-android_armv8.2_2.so` matches the `GGML_CPU_ALL_VARIANTS=ON` output pattern) matches llama.cpp's official Android CI (`build-android.yml`) exactly, which builds arm64 via NDK 29.0.14206865 — well past the NDK r28 threshold where [Google's own docs](https://developer.android.com/guide/practices/page-sizes) confirm 16KB alignment is the compiled-in default with zero extra flags. This strongly indicates these five files were pulled from an official [ggml-org/llama.cpp GitHub release](https://github.com/ggml-org/llama.cpp/releases) (asset pattern `llama-bNNNNN-bin-android-arm64.tar.gz`), not hand-built for Scout, and that upstream build is already compliant.

This also explains a detail in the Fold 7 dialog that the July 18 entry above glossed over: `libimage_processing_util_jni.so` is the *only* library the dialog tags with the specific message "LOAD segment not aligned" — a real, confirmed ELF `p_align` failure. Every other library in the list of 11, including these five, gets a generic "Unknown error" — a different failure class that an aligned ELF file can still trigger.

**Revised hypothesis:** since the ELF files themselves check out, the remaining 10 "Unknown error" failures most likely trace to **APK packaging** — whether `.so` entries are stored uncompressed and page-aligned inside the installed APK's zip container, a property distinct from each library's own internal `p_align` — or to something specific about how a **debuggable** build installs from Android Studio. (The dialog itself says: "This warning is showing because this is a debuggable app which is currently being tested.") `libscout_llama.so`'s own alignment is still unverified — nobody has run `readelf` against a freshly compiled build; that remains genuinely open.

**Revised remaining work — replaces item 1 from the July 18 entry above:**
1. Build a clean **release** APK (not a debug install from Android Studio) and side-load it onto the Fold 7. Check whether the same 10 "Unknown error" libraries clear once it isn't a debug build.
2. If they still fail on a release build, run `zipalign -c -P 16 -v 4` against the actual built APK — this checks zip-level page alignment, separate from each library's internal ELF alignment.
3. `libimage_processing_util_jni.so` is the one library with a confirmed real ELF alignment defect (despite the ML Kit version bump on July 10) — that one needs a further ML Kit dependency version check, not a packaging fix.
4. Run `readelf -lW` on the freshly compiled `libscout_llama.so` after a clean build — this is the one native-stack library that has never actually been checked directly.

**Do not spend a session sourcing or rebuilding llama.cpp/ggml from source for 16KB alignment — the evidence here shows that specific fix isn't needed.** Start with a release-build install test instead. Play Store submission remains blocked pending that test.

---

## July 17, 2026 — What Changed Since Version 46

✓ **LiteRT import corrected — build was broken** — July 16's session changed `FaceEmbedder.kt`'s import to `com.google.ai.edge.litert.Interpreter` (matching the Maven artifact name), but this class does not exist inside the LiteRT 2.1.5 AAR at runtime. LiteRT rebrands the Maven coordinates while keeping `org.tensorflow.lite` as the internal Java package. Fixed: import reverted to `org.tensorflow.lite.Interpreter`. Build confirmed successful. Commit `83ed37f`.

✓ **16KB readelf verification COMPLETE — PASS** — Patrick ran `llvm-readelf.exe -l libLiteRt.so` on Windows (NDK 28.2.13676358). Steps: copied the AAR from `~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litert/litert/2.1.5/` to `.zip`, extracted, located `libLiteRt.so` inside `jni/arm64-v8a/`. All LOAD segments show `Align 0x4000`. Also verified `libLiteRtClGlAccelerator.so` — same result. Both PASS. 16KB compliance for LiteRT is now fully binary-verified. Play Store submission unblocked on the 16KB front. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the new July 18 section at the top of this file for the full correction and root cause.

✓ **"Favorite favorite" double-prefix bug fixed** — `TeachExtractor.kt` was unconditionally prepending `"favorite_"` to all `"my X is Y"` patterns, including cases where X already began with "favorite" (e.g., "my favorite color is cyan" → key `"favorite_favorite_color"`). Root cause confirmed by brain export JSON showing `favorite_favorite_color = Cyan` and `favorite_favorite_yes_my_favorite_color = Cyan` in the truth DB. Fixed: `startsWith("favorite")` guard at line 180 of `TeachExtractor.kt` — if `rawLabel` already starts with "favorite", it is passed directly to `FactKey.custom()` without prepending the prefix. Now "my favorite color is cyan" → key `"favorite_color"`. Commit `9b353a8`.

✓ **Display fix for old double-prefix keys in `keyToHuman()`** — `handleWhatYouLearnedQuery()` in `MainActivity` now preprocesses each key before rendering: if it starts with `"favorite_favorite_"`, one `"favorite_"` prefix is stripped before display. Scout now reads back "your favorite color is cyan" correctly for facts stored under the old bug, without needing to re-teach them. Same commit `9b353a8`.

✓ **Battery optimization prompt added** — `checkBatteryOptimization()` private method added to `MainActivity`. Called from `startSystems()` with an 8-second delay. Uses `PowerManager.isIgnoringBatteryOptimizations(packageName)` to check whether Scout is excluded from battery optimization, then fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (via URI `"package:$packageName"`) to navigate the user directly to the system setting. One-time only (prefs key `"battery_opt_shown"`). `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission added to `AndroidManifest.xml`. Note: Samsung maintains a second layer ("never sleeping apps") that the standard Android API does not reach — users on Samsung devices still need to add Scout to that list manually. Commit `1abcee1`.

✓ **Thinking watchdog added** — `thinkingStartedMs: Long` field added to `MainActivity` (near `speakingStartedMs`). Set to `System.currentTimeMillis()` when `isThinking = true` in `handleQuery()`; cleared to `0L` in `speak()` (alongside `isThinking = false`). Watchdog condition added to `runRecognizerWatchdog()`: if `isThinking && !isSpeaking && thinkingStartedMs > 0L && now - thinkingStartedMs > MAX_THINKING_DURATION_MS (120_000L)`, force-clears thinking state: `isThinking = false`, `thinkingStartedMs = 0L`, `wantListening = true`, `faceView.setThinking(false)`, `scheduleListenRestart(immediate = true)`, JournalDb log. Prevents Scout staying frozen with eyes still moving when TinyLlama hangs indefinitely. Commit `1abcee1`.

✓ **DB migration cleans up double-prefix fact keys** — `migrateDoublePrefixFacts()` private method in `MainActivity`. Called from `setupMemory()` on first run (prefs key `"migrated_double_prefix_facts"`). Calls `truthDb.deleteFactsWithKeyLike(ENTITY_USER_PRIMARY, "favorite_favorite_%")` to remove all double-prefix pollution, including the TTS self-echo entry `"favorite_favorite_yes_my_favorite_color"`. Commit `e24fad9`.

**TTS self-echo entry explained:** When Scout spoke "yes, my favorite color is cyan" (a Gemini reply), the TTS audio bled back through the mic within the 30-second conversation window. `onResults()` received the transcript "yes my favorite color is cyan." The self-echo guard checked `lastScoutUtteranceNormalized` — but the stored normalized text did not match due to the leading "yes" prefix. TeachExtractor then processed it as a new teaching phrase ("my favorite color is cyan" extracted) and stored it as `"favorite_favorite_yes_my_favorite_color"` (TeachExtractor tried to use the full matched string including "yes" as the label). This is a two-part vulnerability: (1) self-echo guard normalization mismatch, (2) TeachExtractor over-broad match of prefix fragments. Cleaned up by migration. Root fix deferred to future session.

✓ **TruthDb gains `deleteFact()` and `deleteFactsWithKeyLike()`** — Two new public methods added to `TruthDb.kt`:
- `deleteFact(entity, factKey)` — removes a single specific fact row.
- `deleteFactsWithKeyLike(entity, pattern)` — removes all facts for an entity where `fact_key LIKE ?` (SQL LIKE syntax, e.g. `"favorite_favorite_%"`).
Both use `entity.lowercase()` for case-insensitive matching, consistent with other TruthDb methods. Commit `e24fad9`.

✓ **People DB added to brain export** — `ScoutExportManager` constructor updated to accept `peopleDb: PeopleDb` (and `MainActivity` updated to pass it). Two new sections exported:
- `"people"` — named faces from the `people` table (`face_hash`, `name`, `first_met`, `last_seen`; no BLOB embeddings). Ordered by `last_seen DESC`. Only rows where name is not null and not empty.
- `"face_embeddings"` — per-name embedding count from `person_embeddings` table (`SELECT name, COUNT(*) GROUP BY name ORDER BY name`).
"Scout, export your brain" now gives a complete picture of both the truth DB and the people DB. Commit `aa10bc9`.

⚠ **"Very" — confirmed NOT in truth DB; must be in people DB** — Brain export JSON (sent by Patrick) contained no entry for "Very" in the truth section. "Very" is stored in the `people` table (face_hash-keyed, likely from an early hash-based recognition session before ArcFace). Patrick will run "Scout, export your brain" with the updated export (which now includes `people` and `face_embeddings` sections) and share the new JSON to identify the entry and fix it.

---

## July 16, 2026 — What Changed Since Version 45

✓ **LiteRT migration — code done** — `app/build.gradle.kts`: `org.tensorflow:tensorflow-lite:2.17.0` replaced with `com.google.ai.edge.litert:litert:2.1.5`. `FaceEmbedder.kt` line 5: import initially changed to `com.google.ai.edge.litert.Interpreter` (July 16), then corrected to `org.tensorflow.lite.Interpreter` (July 17, commit `83ed37f`) — LiteRT rebrands the Maven coordinates but the internal Java package is still `org.tensorflow.lite`. Build confirmed successful. Commits `9676192`, `83ed37f`. Alignment confirmed in 2.1.x line per GitHub issue #6299; Scout does not use GPU/OpenCL delegates. Readelf verification COMPLETE July 17 — see July 17 section above.

✓ **Face recognition accuracy — 3 root-cause bugs fixed** — Root cause of the repeated Diana/Elijah confusion isolated through code review. Three independent bugs in `PeopleDb.kt` and `MainActivity.kt` were each capable of causing person misidentification. All fixed in commit `b6c5579`. Patrick must run "Scout, forget [name]" for each affected person before re-introducing them — existing profiles may already be polluted from prior teach/forget cycles and must be cleared for clean re-training.

**Bug 1 — No margin check:** `findBestMatchName` returned a name whenever the top score exceeded the threshold, even when the second-place candidate scored nearly as high — a coin-flip situation where Scout would confidently name the wrong person. Fixed: `minMargin = 0.08f` parameter added to `findBestMatchName`. If the gap between the top two candidates is < 0.08f, the function returns `null` (Scout says nothing) rather than guessing. New function `findBestMatchNameWithScore` also added — returns `Pair<String, Float>?` so `MainActivity` can gate `addNamedEmbedding` calls on the winning confidence score.

**Bug 2 — Profile pollution:** `addNamedEmbedding` was called whenever a face matched above the recognition threshold (0.65f), including borderline and ambiguous frames. Any frame where two people's scores were close and the wrong name was returned as the winner would add the wrong person's embedding to the named profile. Fixed: `CONFIDENT_EMBED_THRESHOLD = 0.72f` constant added to `MainActivity`. Both the primary face path and the secondary face path now only call `addNamedEmbedding` when the match score is ≥ 0.72f — well above the 0.65f floor. Borderline matches contribute nothing to profiles.

**Bug 3 — Cap-and-stop / profile stagnation:** `addNamedEmbedding` hard-stopped at `MAX_EMBEDDINGS_PER_PERSON = 12` with an early `return`. Once a profile was full, it was frozen from whatever 12 samples were captured earliest — possibly all under uniform conditions. Fixed: at cap, `maxByOrNull` identifies the most redundant existing embedding (highest cosine similarity to the incoming one) and replaces it in-place via `db.update`. The stored set stays diverse as lighting, angle, and distance change over time. A private `scoreByPerson` helper was also extracted for cleaner score aggregation.

**Additional fix:** `handleTeaching()` in `MainActivity` — the `forgetPerson` code path now also sets `lastFaceEmbedding = null`. Previously, the last embedded frame persisted in memory across a forget/re-introduce cycle and could pre-seed the new profile with a stale embedding from before the forget.

---

## July 10–13, 2026 — What Changed Since Version 43

✓ **Privacy Policy — in-app dialog** — `SettingsActivity.kt`: `showPrivacyPolicy()` builds a scrollable `AlertDialog` with full policy text. Covers: Scout's offline-first design, Gemini as optional user-key-only service (governed by Google's own policies), NWS `api.weather.gov` receives device coordinates for weather (no Lippy Robotics involvement), no personal data collected or retained by Lippy Robotics. Accessible: Settings → About Scout → Privacy Policy. Fully offline — no browser required. DONE July 11.

✓ **Terms of Use — in-app dialog** — `SettingsActivity.kt`: `showTermsOfUse()` builds scrollable dialog with: acceptance clause ("By downloading or using Scout, you agree to these Terms"), service-as-is limitation, third-party services clause (Gemini governed by Google's own policies), changes-to-terms clause (continued use = acceptance of updates). Accessible: Settings → About Scout → Terms of Use. DONE July 11.

✓ **terms.html added to repo root** — Full Terms of Use HTML page for `lippy-robotics.gt.tc` website. Two Google Play compliance clauses added beyond the original design: (1) acceptance block at top; (2) changes-to-terms block before Limitation of Liability. Commit `b5735f5`. DONE July 10.

✓ **ML Kit updated for 16KB page alignment** — `face-detection` 16.1.6 → 16.1.7: arm64-v8a confirmed 16KB aligned (ML Kit issue #986, resolved Dec 2025; Scout is arm64-only so 32-bit ABI gap does not apply). `image-labeling` 17.0.7 → 17.0.9: pulls in fixed `vision-common`, resolving `libimage_processing_util_jni.so` alignment on arm64. Commit `60443f3`. DONE July 10. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the new July 18 section at the top of this file for the full correction and root cause.

✓ **LiteRT migration — code done (readelf pending)** — `app/build.gradle.kts`: `tensorflow-lite:2.17.0` replaced with `litert:2.1.5`. `FaceEmbedder.kt` import changed `org.tensorflow.lite.Interpreter` → `com.google.ai.edge.litert.Interpreter`. Drop-in replacement — same API surface, no logic changes. Commits `9676192`. Alignment confirmed in 2.1.x line per GitHub issue #6299; Scout does not use GPU/OpenCL delegates so `libLiteRtOpenClAccelerator.so` is irrelevant. Prior failed attempt (`litert:1.4.0` — not in Maven, reverted commit `eb8223e`) documented for history. **Readelf verification still pending** — Patrick runs `readelf -l liblitert_jni.so | grep -A1 LOAD` after next Android Studio build. `p_align: 0x4000` = pass; `p_align: 0x1000` = fail. Required before Play Store submission. DONE July 16 (code only).

✓ **DiagReportActivity.kt built (diagnostic reporting Step 4–6)** — New activity reads from `DiagnosticDb` and displays a formatted plain-text report in a monospace ScrollView. Four sections: Privacy Notice (verbatim policy disclosure wording), System Information (generated timestamp, Scout version, Android version + API level, device model), Event Log (last 7 days newest-first from `db.getAll()`), Crash Log (`db.crashFile` contents). Report is privacy-safe: no speech text, user names, family names, memories, photos, face data, location, API keys, exception messages, stack traces, URLs, or file paths. `EXTRA_SHOW_SHARE` boolean Intent extra determines launch mode. Registered in AndroidManifest. DONE July 13.

✓ **View/Share mode differentiation** — `activity_diag_report.xml` wraps all sharing controls (notes-field label, `EditText etNotes`, two guidance TextViews, Share button) inside a single `LinearLayout android:id="@+id/llShareControls"`. `DiagReportActivity.onCreate()` reads `EXTRA_SHOW_SHARE` (default false) and sets `llShareControls.visibility = VISIBLE / GONE`. View mode: clean read-only display. Share mode: full UI visible. Share flow: writes user notes + report to `filesDir/diag/diag_report.txt`, obtains URI via `FileProvider.getUriForFile()`, fires `ACTION_SEND` intent with `EXTRA_STREAM`, `EXTRA_EMAIL`, `EXTRA_SUBJECT`, `EXTRA_TEXT`, `ClipData` (Android 10+ read permission), and `FLAG_GRANT_READ_URI_PERMISSION`. DONE July 13.

✓ **Settings DIAGNOSTICS section wired** — Three `navRow()` entries in `SettingsActivity.kt`: (1) "View Diagnostic Report" → `DiagReportActivity` with `EXTRA_SHOW_SHARE=false`; (2) "Share Diagnostic Report" → with `EXTRA_SHOW_SHARE=true`; (3) "Clear Diagnostic History" → `confirmDeleteDiagLogs()`. Confirmation dialog lists what is removed (events, crash log, generated report file) and explicitly states memories, settings, and model files are unaffected. Delete call: `DiagnosticDb(this).use { db -> db.deleteAll() }` — `.use {}` guarantees DB close even if `deleteAll()` throws. DONE July 13.

✓ **Support button opens browser** — `showSupport()` in `SettingsActivity` replaced the old dead-end "Contact Us" AlertDialog. Now fires `Intent(Intent.ACTION_VIEW, Uri.parse("https://lippy-robotics.gt.tc/support.html"))`. Fallback AlertDialog with title "Unable to open the Scout Support Center" shown if no browser app handles the intent. Import `android.net.Uri` added. DONE July 13.

✓ **Reset Memory Layers destructive styling** — `private val DESTRUCTIVE = Color.parseColor("#FF4D4D")` added after `TXT_MUTE`. `navRow()` gains optional `titleColor: Int = TXT` parameter — label rendered in that color; all other callsites unaffected (default value). "Reset Memory Layers" passes `DESTRUCTIVE`. Confirmation dialog's positive "Reset" button colored red via `dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(DESTRUCTIVE)` — called after `.show()` so the button view already exists. Standard Android pattern for irreversible data-deletion actions. DONE July 13.

✓ **NDK 28.2 llvm-strip build fix** — `packaging { jniLibs { keepDebugSymbols += "*/x86_64/*.so" } }` added to `app/build.gradle.kts`. NDK 28.2's `llvm-strip` crashes with `STATUS_ILLEGAL_INSTRUCTION` when stripping x86_64 ELF binaries from the ML Kit bundle. AGP `keepDebugSymbols` tells the build system to skip stripping those ABIs entirely. Does not affect ARM64 (the production ABI for all Scout test devices). DONE July 13.

✓ **Gradle daemon OOM fix (Windows page file exhaustion)** — `org.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -Dfile.encoding=UTF-8` in `gradle.properties`. Root cause: G1GC (JVM default) reserves very large virtual address space upfront (~4× heap), exhausting the Windows page file on Patrick's machine. SerialGC reserves only what it immediately needs. `1024m` is sufficient for Scout's build. Secondary cause: antivirus background RAM consumption (uninstalled). Git pack memory limits also added (`pack.windowMemory 64m`, etc.) to prevent OOM during `git pull`. DONE July 13.

✓ **Google Play Data Safety analysis** — Full source code review completed. Conclusions: (1) Scout sends no data to Lippy Robotics servers → "No data collected" is correct, no collection box needed. (2) Gemini API call sends user query text to Google → declare "App interactions → User-generated content" as Shared / Optional. (3) Weather API (`api.weather.gov/points`) sends device coordinates to NWS → declare "Location → Approximate location" as Shared / Optional. Data Safety Step 2 answer: "Yes, my app shares user data with third parties." DONE July 13.

---

## July 7, 2026 (Session 2) — What Changed Since Version 42

✓ **Thinking glance amplitude raised** — `thinkGlanceSideX` raised from `8–20px` to `35–65px`. Previous range drove only 3–6px of face drift (invisible). New range drives 12–21px with the 0.32f faceGazeDriftX multiplier — clearly visible as a side glance. DONE July 7.

✓ **Thinking expression redesigned — curious and engaged** — Patrick provided clear direction: expression should read "Hmm, let me think" not "I'm tired." Four changes made:
- **Brow asymmetry**: One brow (side > 0) lifts 22px + gentle sine oscillation with questioning arch (thinkTilt -10f retained). Other brow (side < 0) barely moves (5px). Was both brows lifting nearly equally (24/26px) — that read as surprised, not curious.
- **thinkInnerLift reduced**: Was 20px on both sides (made quiet brow look worried/furrowed). Now 6px on side < 0 only — relaxed, natural.
- **Lid asymmetry**: Right eye target gains +0.08f droop during thinking (total ~0.15f vs left's 0.07f). Still mostly open — concentration, not sleepiness.
- **Mouth corner asymmetry**: Right corner sits 3px higher than left when thinking (corYR = cy - 3f vs corYL = cy + 2f). Barely perceptible thoughtful side-smile. Friendly and warm.
DONE July 7.

---

## July 7, 2026 (Session 1) — What Changed Since Version 41

✓ **16KB page alignment fix confirmed** — `target_link_options(scout_llama PRIVATE -Wl,-z,max-page-size=16384)` added to CMakeLists.txt. Fixes `dlopen` failure on Samsung devices running Linux 6.x kernels (Android 15 / Galaxy A32, Fold 7). Logcat confirmed: "scout_llama native library loaded successfully." DONE July 7. ⚠ **REOPENED July 18** — contradicted by real Fold 7 device evidence. See the new July 18 section at the top of this file for the full correction and root cause.

✓ **bootstrapModelFile() added to MainActivity** — On every startup, Scout checks `filesDir` for the TinyLlama model. If absent, copies it from two source locations: (1) app-specific external dir `/sdcard/Android/data/com.example.scoutface/files/` (no permission needed, any Android version); (2) root `/sdcard/` (requires READ_EXTERNAL_STORAGE, Android ≤12 only). `READ_EXTERNAL_STORAGE` added to manifest with `maxSdkVersion="32"` — not requested on Android 13+. Copy runs in a background thread at startup; the 90-second TinyLlama load delay gives it ample time. After first successful copy, subsequent launches skip it. DONE July 7.

✓ **Offline fallback message fix** — When Online Features are deliberately turned OFF by the user, Scout no longer says "I'm having trouble connecting." Now says "I'm working offline right now, so that one's a bit beyond me." `speakUnavailableIfNeeded()` is only called when `isGeminiEnabled()` is true. DONE July 7.

✓ **TinyLlama confirmed working on A32 and Fold 7** — Model file pushed to both devices via adb. `bootstrapModelFile()` successfully copies from external to internal storage on both. TinyLlama answers questions with Online Features OFF. DONE July 7.

✓ **Head-turn amplitude fixed** — `faceGazeDriftX` multiplier was `0.07f` (max ±5px on 1920px canvas = ~2 physical pixels, completely invisible). Raised to `0.32f` for X and `0.26f` for Y — max ±24px X / ±14px Y. Now clearly readable as a neck turn when Scout looks toward someone. DONE July 7.

---

## July 4, 2026 — What Changed Since Version 39

✓ **PeopleDb threshold raised back to 0.65f** — ArcFace upgrade (July 3) lowered threshold to 0.60f, but this caused Diana/Elijah cross-contamination (Diana's face scored above 0.60f against Elijah's stored embeddings — root cause of "I see Elijah" when only Diana was present). Threshold raised back to 0.65f in both `findBestMatch` and `findBestMatchName`. `cursor.use {}` added to both methods (leak fix). `forgetPerson` made atomic with `beginTransaction()`/`setTransactionSuccessful()`/`endTransaction()`. `addNamedEmbedding` now checks `COUNT(*)` first and skips INSERT if already at `MAX_EMBEDDINGS_PER_PERSON (12)`. DONE July 4.

✓ **VisionAnswerBuilder dogLine + 2-face branch fix** — 3+ faces branch was missing `dogLine` (asymmetric with the 1- and 2-face branches); added. 2-face branch reorganized: `secondaryFaceName` arm now precedes `pendingIntroName` arm; new `else` arm handles case where primary is unknown but secondary is known. Freshness window 3500ms → 1800ms (line 196). DONE July 4.

✓ **Secondary face `findBestMatch` fallback** — Secondary face path now tries `findBestMatchName` first (person_embeddings table, threshold 0.55f), then falls back to `findBestMatch` (people.embedding BLOB, also 0.55f) + `getName()`. Closes the recognition gap when only the single-BLOB embedding exists for a person. DONE July 4.

✓ **Caption persistence fix** — When closed captions are turned off in Settings, `onResume()` now immediately hides the caption TextView and removes the pending hide Runnable. Previously the last spoken caption line lingered on screen after toggling captions off. DONE July 4.

✓ **Startup diagnostics** — At boot, Scout checks STT and TTS availability. TTS failure: Toast shown ("Scout's voice isn't working. Please restart the app…"). STT unavailable: Scout speaks a friendly warning 4 seconds after boot and logs to JournalDb. DONE July 4.

✓ **First-boot onboarding redirect** — Top of `MainActivity.onCreate()` checks `OnboardingActivity.PREF_ONBOARDING_DONE` in `scout_prefs`. If false, starts OnboardingActivity and finishes MainActivity immediately. New installs never reach the main UI until onboarding is complete. DONE July 4.

✓ **OnboardingActivity.kt (new)** — Full 5-screen onboarding flow. Screens: Welcome / Trial / This Is Just The Beginning / Privacy / Ready To Begin. `currentPage` is the single source of truth driving both navigation dots and the "X / 5" counter. Scout icon on screens 1 and 5 only. Colors: `#0D1728` bg, `#9BBEFF` active dot/button, `#2A3A5C` inactive dot, `#B0C4E8` body text. `finishOnboarding()` sets `PREF_ONBOARDING_DONE=true` in `scout_prefs` AND `gemini_enabled=false` in `scout_memory`. DONE July 4.

✓ **New installs default to offline mode** — `finishOnboarding()` writes `gemini_enabled=false` to `scout_memory` SharedPrefs. Gemini opt-in via Settings after adding a key. Prevents new users from being in "online mode not configured" state on first launch. DONE July 4.

✓ **BOOT_NO_KEY phrases replaced** — Old vague phrases replaced with actionable settings-access tip: "Open settings any time by sliding the screen to the right." / "Slide the screen to the right any time to open settings." / "You can open settings any time by sliding right." DONE July 4.

✓ **CLAUDE.md created** — New file in repo root. Documents full `git pull origin claude/test-coverage-analysis-hsp9lt` and `git push` commands, critical hardcoding rules, architecture quick reference, test devices, master doc list. Persists across session compaction so all future Claude instances have the context. DONE July 4.

✓ **ModelDownloadActivity.kt (new)** — Portrait-only loading screen for TinyLlama model download. All 39 humorous loading messages from Patrick's approved list. ObjectAnimator animation: message slides in from the right (320ms), holds for 3.8s, slides out left (280ms), next enters from the right. Messages shuffled at startup and reshuffled on each full cycle. `updateProgress(percent, downloaded, total, timeLeft)` method ready for Play Asset Delivery wiring. Layout: "SCOUT" wordmark + "AI COMPANION APP" subtitle, 220dp Scout face icon, animated message frame, `#9BBEFF` progress bar, downloaded/total/time row. Registered in AndroidManifest as portrait. DONE July 4.

---

## July 3, 2026 — What Changed Since Version 38

✓ **ArcFace face recognition upgrade** — MobileFaceNet (192-dim) replaced with InsightFace MobileFaceNet trained with ArcFace Additive Angular Margin Loss (512-dim, 4.8MB). Input: 112×112 RGB, preprocessing `(px - 127.5f) / 128f` unchanged. FaceEmbedder.kt: EMBEDDING_SIZE 192→512, output array `Array(1) { FloatArray(512) }`, input buffer single-batch (removed the repeat(2) loop). PeopleDb upgraded to v4; migration clears incompatible 192-dim embeddings (preserves names and face hashes — everyone re-introduces once). New cosine similarity threshold: 0.60f (ArcFace same-person range ~0.5–0.95, different-person ~0.0–0.4; 0.40f caused "everyone is Patrick" false positives). DONE July 3.

✓ **"I see you, X" → "I see X"** — VisionAnswerBuilder and MainActivity greeting path both updated. Scout now says "I see Patrick" and "I see Patrick and Diana" instead of "I can see you, Patrick." Sounds like a description, not an address — better match for what Patrick wanted. DONE July 3.

✓ **Diana (secondary face) fix** — Secondary face processing block now also consumes `pendingFaceIntroName`. Previously, introducing "this is my wife Diana" with two people in frame stored the pending name but the secondary face block never checked it — Diana was always "someone else." Fixed: if secondary face embedding doesn't match anyone AND `pendingFaceIntroName` is set, the pending name is assigned to the secondary face and stored via `addNamedEmbedding()`. DONE July 3.

✓ **Personality phrase pools — Phrases.kt (new file)** — New `Phrases` object with anti-repeat rolling window (cooldown = pool.size / 2; chosen phrase blocked until half the pool has been used). Scout no longer repeats the same line back-to-back. Pools: BOOT_ONLINE (6), BOOT_OFFLINE_FAST (5), BOOT_OFFLINE (6), BOOT_NO_INTERNET (4), BOOT_NO_KEY (3), REMEMBER (9), REMEMBER_NAME (6), REMEMBER_MY_NAME (5), REMEMBER_WIFE (5), REMEMBER_SON (5), REMEMBER_DOG (4), GOODBYE (7). `{name}` placeholder substituted via `pickNamed()`. DONE July 3.

✓ **Adaptive boot greeting — ScoutBootStatus.kt rewritten** — Offline boot greeting is now adaptive: if TinyLlama loaded in under 2 seconds last session (`llama_last_load_ms` in SharedPreferences), Scout picks from BOOT_OFFLINE_FAST (skips warming-up line). Otherwise picks from BOOT_OFFLINE (includes warming-up). TinyLlama load time measured and stored in SharedPreferences inside `tryLoadOfflineBrain()`. ScoutBootStatus now takes a `lastLlamaLoadMs: () -> Long` lambda (default Long.MAX_VALUE). DONE July 3.

✓ **Online boot phrases mention offline backup warming up** — All 6 BOOT_ONLINE phrases now include a line about the offline backup warming up in the background (e.g., "Online mode is on. My offline backup is warming up in the background."). Previously said nothing about warming up when online. DONE July 3.

✓ **Goodbye and Remember responses now varied** — `respond("Okay. I'll see you later.")` replaced with `Phrases.pick("goodbye", Phrases.GOODBYE)`. All remember confirmation responses replaced with Phrases pool calls. Scout no longer says the same goodbye or confirmation line every session. DONE July 3.

---

## June 30, 2026 — What Changed Since Version 37

✓ **Dynamic robot name — all spoken responses fixed** — Boot greeting, identity feelings reply, identity fallback, and offline brain fallback reply all now read the robot name from TruthDb at runtime (`truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"`). Renaming Scout in Settings is now fully reflected in every spoken line. No more hardcoded "Scout" in any spoken response. DONE June 30.
✓ **TeachExtractor.kt — 8 new teaching patterns** — "that person is my son/wife [name]", "that is my son/wife [name]", "his name is [name]", "her name is [name]", "that is [name]", "that person is [name]" all now recognized and stored. Root cause of "I see one person" after teaching a family member's face — TeachExtractor returned null → fell to Gemini → Gemini said "I'll remember" but stored nothing. DONE June 30.
✓ **VisionAnswerBuilder freshness extended 1800ms → 3500ms** — Camera is blocked during TTS (`isThinking || isSpeaking` gate). If Scout speaks for more than 1.8s before Patrick asks "what do you see?", face data was stale → "VISION_STALE" or "I see one person." 3500ms covers most TTS utterances. DONE June 30.
✓ **registerFamilyMemberFace() guard** — If the largest face's position-hash already carries a DIFFERENT person's name (i.e. primary user recognized by position but below embedding threshold), the incoming name is stored in `pendingFaceIntroName` instead of overwriting. Prevents the A32 misidentification where Scout called Patrick "Elijah." DONE June 30.
✓ **TinyLlama filter additions** — "family friendly companion" and "family companion robot" added to bad-response filter in `cleanOfflineReply()`. Stops TinyLlama from saying "and my name is Scout, a family friendly companion." DONE June 30.
✓ **Pet Mode design locked** — Nicolas Protocol renamed to Pet Mode. Covers ALL animals (dog, cat, bird, rabbit, etc.). When a pet first appears in frame: if pet name is stored in TruthDb → Scout says "Hello [name]." softly. If no name stored → Scout says "Well... hello there little one. I hope someone will tell me your name soon." Once per appearance (2-minute cooldown). Scout continues operating normally after the greeting — does NOT go silent. Future robot body: steer-around Bluetooth command when Scout is mobile. NOT YET CODED — design locked, implementation next.

---

## June 29, 2026 — What Changed Since Version 36

✓ **Launcher icon eyes fixed** — Face was at 100% of the foreground canvas; eyebrows at ~14% from top were being clipped by the circular launcher mask (safe zone = inner 66.7%). Scaled face to 68% of canvas with dark navy #0D1728 background. All 5 mipmap densities regenerated. Patrick confirmed: "icon looks good 👍"
✓ **Face threshold raised 0.75→0.82** — Father/son genetic similarity (Patrick/Elijah) caused cosine scores of 0.76–0.79, above the old 0.75 threshold. Genuine same-person matches score 0.80+. Threshold raised to 0.82 in `PeopleDb.findBestMatch()`. DONE June 29.
✓ **"Scout, forget [name]" command** — `forgetPerson(name)` now wipes both the `people` table (sets name='', embedding=NULL) and the new `person_embeddings` table (DELETE). Voice command parsed in `handleTeaching()`. DONE June 29.
✓ **TTS deafness bug fixed** — `speak()` sets `isSpeaking=true` and `wantListening=false`. If Android kills the TTS engine during idle and `tts.speak()` fails silently (no onDone/onError callback), Scout goes permanently deaf. Three-layer fix: (1) `speak()` checks return value — if `TextToSpeech.ERROR`, immediately clears `isSpeaking`; (2) `speakingStartedMs` timestamp set when speaking begins, cleared in `onDone`/`onError`; (3) 45-second watchdog in the recognizer watchdog loop force-clears `isSpeaking`, `isThinking`, sets `wantListening=true` if TTS is stuck. DONE June 29.
✓ **Voice slider changes now stick** — SettingsActivity was saving pitch/speed to `scout_prefs` but MainActivity was reading from `scout_memory` (different SharedPreferences file) and hardcoding defaults in `onInit()`. Fixed: `MainActivity.scoutPrefs` reads from `"scout_prefs"`. `onInit()` and `onResume()` both call `scoutPrefs.getFloat("voice_pitch", 0.98f)` / `getFloat("voice_speed", 0.88f)`. Patrick confirmed: "voice is fixed." DONE June 29.
✓ **Greeting words blocked from name storage** — "hello", "hi", "hey", "howdy", "greetings", "sup", "yo" added to `blockedNames` in `handleTeaching()`. Scout no longer says "I'll remember your name is hello." DONE June 29.
✓ **Gemini responses no longer truncated mid-sentence** — `maxOutputTokens` raised 250→600 in `GeminiClent.kt`. "Always end on a complete sentence — never stop mid-sentence." added to Gemini system prompt in `ScoutPromptBuilder.kt`. When Gemini returns `finishReason=MAX_TOKENS`: trims to last `.`/`!`/`?` boundary; returns null (falls through to TinyLlama) if no sentence boundary found. DONE June 29.
✓ **Gemini quota/cooldown announced to user** — Previously `tryTinyLlamaOrFallback()` only called `speakUnavailableIfNeeded()` after TinyLlama also failed. Now: `isInCooldown()` exposed on `ScoutGeminiManager`, cooldown check added at top of `tryTinyLlamaOrFallback()` — if in cooldown, `speakUnavailableIfNeeded()` is called immediately. `speakUnavailableIfNeeded()` returns `Boolean`: `true` = message spoken (caller returns), `false` = suppressed within repeat gap (TinyLlama answers normally). Repeat gaps: 6 hours for daily quota, 10 minutes for rate limit. Scout says: "Gemini says you've reached your daily limit, but I can do my best locally to help any way I can." DONE June 29.
✓ **Secondary face recognition** — Previously only the largest (primary) face got embedded per frame. The second face (e.g. Elijah when Patrick and Elijah are both in frame) was never processed — hence "someone else." Fix: (1) `PeopleDb` upgraded to v3, adds `person_embeddings` table (stores up to 5 embeddings per named person via `addNamedEmbedding()`; `findBestMatchName()` scans it and returns the name directly). (2) `MainActivity`: computes `secondFace` = second-largest face; captures `capturedSecondBox`; in the same `embedExecutor.submit` block, after primary face processing, also crops/embeds the secondary face and calls `findBestMatchName(emb2, threshold=0.80f)` → stores in `lastSecondaryFaceName` (@Volatile). Clears `lastSecondaryFaceName` when `faces.size < 2`. All name-confirmation paths also call `addNamedEmbedding()` to accumulate embeddings for future matching. (3) `VisionAnswerBuilder.build()` gets new `secondaryFaceName` param — used in `faceCount==2` branch. Scout now says "I can see you, Patrick and Elijah." DONE June 29.

---

## June 28, 2026 — What Changed Since Version 35

✓ **TinyLlama re-enabled with safe delayed load** — `startOfflineBrain()` restored. `tryLoadOfflineBrain()` helper added: 90-second startup delay, 800MB RAM guard (`availMem < 800MB → skip`), `nCtx=512` (reduced KV cache), `nThreads=2`. Model loaded from `filesDir/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf`. TinyLlama is back as the offline brain. Needs A32 real-world confirmation that LMKD crash does not return. DONE June 28.
✓ **TinyLlama automatic Gemini fallback** — `tryGemini()` now takes `onAnswered: (() -> Unit)?` and `onFailed: (() -> Unit)?` callbacks. When Gemini times out, 503s, or returns nothing, `onFailed` fires `tryTinyLlamaOrFallback(qNorm)`. Extracted helper shared by: direct path (Gemini disabled/no key/no internet) AND the Gemini `onFailed` path. Scout no longer silently fails when Gemini is down. DONE June 28.
✓ **Gemini timeouts reduced** — `connectTimeout = 10_000` (was 20,000), `readTimeout = 12_000` (was 30,000) in `GeminiClient.kt`. Was causing 30-second `SocketTimeoutException` hangs before TinyLlama fallback could kick in. DONE June 28.
✓ **"Repeat that" / "what did you say?" intent** — `isRepeatRequest()` added just before `handleQuery()`. Detects "repeat that", "say that again", "what did you say", "what was that", "pardon", "sorry what", and similar. `respond()` now caches the last meaningful answer (5+ words, `lastMeaningfulResponse`, 4-minute TTL `REPEAT_CACHE_TTL_MS`). Intent routed early in `handleQuery()` before any brain call — works offline instantly. DONE June 28.
✓ **Brain source Toast** — `pendingBrainSource` variable set before `respond()` ("Gemini (online)" or "TinyLlama (offline)"). Toast shown inside `respond()` after each answer. For testing — helps Patrick identify which brain is actually responding. DONE June 28.
✓ **Gemini default fixed** — `isGeminiEnabled()` was using `getBoolean(PREF_GEMINI_ENABLED, false)`. Default `false` meant Gemini was always blocked on fresh install even with a valid key saved. Fixed to `getBoolean(PREF_GEMINI_ENABLED, true)`. Note: Settings "Offline Mode" toggle correctly inverts `gemini_enabled` in `scout_memory` SharedPrefs. DONE June 28.
✓ **Gemini daily quota cooldown reduced** — `DAILY_QUOTA_COOLDOWN_MS = 60L * 60L * 1000L` (1 hour, was 6 hours) in `GeminiClent.kt`. Faster dev recovery after quota exhaustion from testing. DONE June 28.
✓ **Face greeting fires once per launch** — `greetedThisSession` was being reset to `false` every 5 seconds of face absence (when `GREET_RESET_ABSENCE_MS` elapsed). This caused the greeting to fire again every time the face briefly left frame. Fixed by removing the `greetedThisSession = false` reset block. Now only `faceAppearanceMs` is reset on absence. Scout greets once per boot only. DONE June 28.
✓ **STT reliability improved** — `RecognizerIntent` now includes `EXTRA_PREFER_OFFLINE = true` (avoids Samsung network STT dependency), `SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 10_000L` (longer silence window), `SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 7_000L`. `onError()` handles `ERROR_RECOGNIZER_BUSY` (error 8) with a 600ms delay before restart instead of immediate retry. DONE June 28.
✓ **Duplicate prompt serves cached Gemini answer** — Was saying "I heard that. I don't want to ask online twice." Now: on duplicate within the `duplicatePromptWindowMs` window, checks `lastGeminiReply` (4-minute TTL) and serves it if available. Allows through if no cache (resets `lastPromptMs = 0L` to bypass the guard). DONE June 28.
✓ **speakUnavailableIfNeeded() made public** — Needed so the fallback chain in `tryTinyLlamaOrFallback()` can call it from `MainActivity` when neither brain is available. DONE June 28.
✓ **Testing confirmed on A32** — Patrick confirmed active development and testing is on Samsung Galaxy A32. Fold 7 is listed as primary but A32 is the current working device.

---

## June 27, 2026 — What Changed Since Version 34

✓ **Wrong-name teaching with 2 people in frame FIXED** — Saying "this is my wife Diana" was sometimes stored as the primary user's name (Scout replied "I'll remember your name is Diana"). STT occasionally drops "my wife", making it sound like "this is Diana" → FactKey.NAME. Fixed by guard in `handleTeaching()`: if primary user already known AND incoming name differs AND 2+ faces in frame → treat as secondary person introduction, not primary user rename. DONE June 27.
✓ **ML Kit label whitelist** — Replaced old blacklist approach with OBJECT_WHITELIST in VisionAnswerBuilder.kt (~80 real household objects). Old blacklist couldn't block labels like "aerospace engineer", "dude", "vacation". Now only known household objects reach Scout's voice. DONE June 27.
✓ **`lastKnownFaceName` set immediately after teaching** — Previously set only by the embedExecutor background cycle (2s interval). If Patrick said "what do you see?" within 2 seconds of "I am Patrick", Scout still said "I see one person." Fixed by setting `lastKnownFaceName = value` immediately inside handleTeaching(). DONE June 27.
✓ **`finishThinking()` was empty no-op — FIXED** — Critical bug: `isThinking` was set to `true` in `handleQuery()` but never cleared when Gemini was blocked (cooldown, duplicate, quota message already suppressed). Face locked in thinking mode permanently. Camera dropped all frames (`isThinking || isSpeaking` gate at analyzer). Mic never restarted. Fixed by making `finishThinking()` actually call `isThinking = false` + `faceView.setThinking(false)`. DONE June 27.
✓ **Testing moved to Fold 7** — Listed in docs as primary device switch. Patrick is currently actively testing on A32 (confirmed June 28).

---

## June 21, 2026 — What Changed Since Version 33

✓ **A32 no longer crashing — CONFIRMED** — Scout ran through Gemini responses, face recognition, and extended idle without crashing. Patrick confirmed: "he is not crashing anymore." DONE June 21.
✓ **Camera frame throttle** — `ANALYSIS_MIN_INTERVAL_MS = 150ms` added to camera analyzer. ML Kit labeler and face detector now run at max ~7fps instead of up to 30fps. Reduces bitmap allocation and ML Kit memory pressure by ~4x. Root cause of the delayed LMKD kill after Gemini responses. DONE June 21.
✓ **Face name persistence fixed** — `lastKnownFaceName` volatile field added. VisionAnswerBuilder now uses this embedding-based name cache instead of the per-frame fingerprint hash (which changed every frame). Name refreshed every 2 seconds by embedExecutor, cleared when no face visible. Scout now says your name consistently, not just once. DONE June 21.
✓ **`findBestMatch` only scans named rows** — Changed SQL from `WHERE embedding IS NOT NULL` to `WHERE embedding IS NOT NULL AND name IS NOT NULL AND name != ''`. Unnamed hash rows accumulated from prior frames can no longer win the cosine similarity race. DONE June 21.
✓ **embedExecutor self-match bug fixed** — `findBestMatch` is now called BEFORE `storeEmbedding`. Previously the embedding was stored first; `findBestMatch` then found the just-stored embedding with similarity 1.0, always returning the current frame's unnamed hash. Reordering eliminated the self-match entirely. DONE June 21.
✓ **Face recognition threshold raised** — Raised from 0.65 to 0.75. Prevents family members with shared facial geometry (Patrick/Elijah) from being misidentified. Same-person genuine matches score 0.80+. DONE June 21.
✓ **Multi-person face introduction** — `registerFamilyMemberFace()` added. Patrick can say "this is my son Elijah" or "this is my wife Diana" while the person is visible and Scout stores their face. DONE June 21.
✓ **Pending face mechanism** — When a family member is introduced while two people are in frame (Patrick is the primary face), Scout sets `pendingFaceIntroName` and speaks "I'll remember [name]. When [name] faces me alone, I'll learn to recognize them." The next unknown face automatically gets the pending name. DONE June 21.
✓ **VisionAnswerBuilder two-person response** — `faceCount == 2` now says "I can see [Patrick] and one other person." instead of always "I see two people." DONE June 21.
✓ **Gemini maxOutputTokens raised** — Raised from 150 to 250 in GeminiClient. Prevents responses being cut off mid-sentence (e.g., "Snoopy is..." truncation). DONE June 21.
⚠ **Elijah/Diana face recognition** — Needs one solo introduction: family member faces Scout alone (or becomes the primary face in frame) after "this is my son Elijah" so the pending face embedding is captured. Works correctly once triggered.

---

## June 17–20, 2026 — What Changed Since Version 32

✓ **Face recognition Steps 2–4 COMPLETE** — FaceEmbedder.kt wired into camera pipeline. Face crops taken from ML Kit bounding boxes, embeddings computed per detected face (Step 2). PeopleDb schema updated with BLOB embedding column, cosine similarity matching replaces position-hash (Step 3). "This is X" / "My name is X" naming flow uses embedding-based identity; known face greets by name, unknown face triggers Guest Mode (Step 4). DONE June 17.
✓ **Embedding memory pressure fix** — Memory management and queue overflow prevention added to embedding pipeline. A32 freeze/force-close eliminated. DONE June 17.
✓ **ApiKeySetupActivity.kt wired** — Optional AI provider setup wizard now fully connected to secure storage. DONE June 17.
✓ **SettingsActivity BUILT — all 5 sections** — AI Provider (Gemini key entry, online/offline toggle), Voice & TTS (pitch/speed sliders), Behavior, Brain & Behavior, About Scout (version, licenses, contact). DONE June 18.
✓ **Hardcoded Gemini API key REMOVED** — Patrick's personal key removed from MainActivity.kt entirely. Now lives in encrypted SharedPreferences. DONE June 18.
✓ **Settings access redesigned** — Gear button removed. Swipe-right gesture opens Settings. First-boot hint shown on first launch. Voice command also opens Settings. DONE June 18.
✓ **Eye jitter FIXED** — Boot lock (3500ms gaze stabilization), speaking gate, dead zone, and min-delta guard added to ScoutFaceView iris pipeline. A32 iris is now stable. DONE June 18.
✓ **Scout eyebrows and mouth brightened** — Color updated to #9BBEFF (lighter blue, matches iris). DONE June 18.
✓ **TinyLlama startup disabled on A32** — Startup load caused LMKD to kill Scout under memory pressure. Disabled as emergency stabilization. RE-ENABLED June 28 with safe delayed load. DONE June 19.
✓ **Camera bitmap memory leak fixed** — Bitmap objects now properly recycled after all async ML Kit callbacks complete, not prematurely. DONE June 19.
✓ **ML Kit suppressed during Gemini calls** — isThinking flag gates camera analyzer during Gemini API calls. Reduces peak memory usage during AI processing. DONE June 19.
✓ **speak() race condition FIXED** — isSpeaking = true now set immediately at function entry (not 240–650ms later when TTS onStart fires). Closes the window where ML Kit could run unconstrained just as Scout was starting to speak, causing a memory spike and LMKD kill. DONE June 20.

---

## June 15–16, 2026 — What Changed Since Version 31

✓ **TinyLlama rambling fix** — `limitToSentences()` added to MainActivity.kt. Offline replies capped at 2 sentences before TTS. Eliminates garbled continuations like 'I see a cool in an ear.'
✓ **Self-echo guard** — `lastScoutUtteranceNormalized` field added. `onResults()` now checks if mic picked up Scout's own TTS voice and ignores it. Eliminates Scout answering himself.
✓ **MainActivity.kt blank line cleanup** — excessive blank lines removed file-wide (except the TinyLlama system prompt raw string which is intentionally preserved).
✓ **Face recognition Step 1** — MobileFaceNet.tflite (MIT licensed, 5,233,396 bytes) bundled in `app/src/main/assets/`. TensorFlow Lite dependency (`org.tensorflow:tensorflow-lite:2.14.0`) added to build.gradle.kts. `noCompress += "tflite"` added so the model loads correctly. `FaceEmbedder.kt` created: takes a cropped face Bitmap, runs 112x112 / normalize / inference / L2-normalize, returns 192-dim FloatArray.
✓ **Naming phrases expanded** — TeachExtractor.kt updated. "this is X", "I am X", "you see X" now recognized as FactKey.NAME teaching phrases alongside existing "my name is X". NON_NAME_WORDS stoplist guards against false positives.
✓ **Weather switched to NWS** — ScoutWeatherManager.kt fully rewritten to use api.weather.gov. 100% free for commercial use, no API key required. Two-step flow: /points to resolve gridpoint URL (cached), then /forecast for periods. All five query types preserved (current, tonight, tomorrow, specific day, week). U.S. locations only.
✓ **THIRD_PARTY_NOTICES.md created** — MIT attribution for MobileFaceNet.tflite in repo root. Start of Open Source Credits screen.

---

## 1. Who We Are

Patrick Lippy — developer, project owner, creator of Scout. Not a professional programmer. Stroke survivor, blind in right eye, type 1 diabetic, dyslexic. Explain things calmly and at screenshot level.

- Diana: Patrick's wife
- Elijah: Patrick's son, age 9. Scout's biggest fan. Has drawn pictures of Scout.
- Nicolas: The family dog. Elijah has drawn pictures of Scout. Nicolas is why Pet Mode exists.

**Names must NEVER be hardcoded in Scout's code. Always use variables.**

AI Collaborators: Patrick works with both Claude and ChatGPT as project partners. Cross-review between the two is welcome and encouraged. Grok was tried and discontinued.

---

## 2. What Scout Is

Scout is a calm family companion robot running on a Samsung Galaxy phone mounted in landscape mode as a permanent face display. Scout has animated eyes, speaks, listens, sees via camera, and remembers the family.

| Item | Detail |
|------|--------|
| Package | com.example.scoutface |
| Language | Kotlin + C++ NDK |
| Active test device | Samsung Galaxy A32 — current active development and testing as of June 28 |
| Listed primary device | Samsung Galaxy Fold 7 (12GB RAM) — needs dedicated stability testing session |
| Future hardware | KEYESTUDIO Mini Tank Kit V2 chassis via Bluetooth (opt-in) |
| Ship target | Google Play Store — 7-day free trial, then $9.99 one-time purchase. No subscriptions. Ever. |
| Website | https://patevan9.github.io/lippyrobotics.github.io |
| Company name | Lippy Robotics |
| Build method | Android Studio only: Build → Clean Project → Build → Assemble Project. gradlew fails (JAVA_HOME). |

---

## 3. Identity & Purpose

Scout should feel: Calm. Thoughtful. Emotionally subtle. Grounded. Occasionally curious. Sometimes unsure. Quietly alive. Honest. Predictable. Present.

Scout should NOT: Constantly praise the user. Act overly excited. Feel fake or scripted. Behave unpredictably. Constantly force conversation. Use permanent goodbye language.

**Core Philosophy: Stability > Features | Presence > Intelligence | Honest > Fake cheerful | Predictable > Flashy | Local-first > Cloud dependence**

---

## 4. Business Model

**Implementation status — pre-release item, not yet built.** Everything below describes the *intended* model: free download → 7-day full trial → $9.99 one-time permanent unlock, no subscription. No Google Play Billing integration exists anywhere in the app today — confirmed via source grep for `BillingClient`/`PurchasesUpdatedListener` while investigating the Support Scout donation screen (see the August 1, 2026 entry above). The $9.99 shown on a Play Store listing today would be just a listing price, with nothing in the app to actually start/track a 7-day trial or lock advanced features afterward. Tracked as a real launch blocker in `Scout_Launch_Checklist.md`, not a completed feature.

**Trial:** 7-day free trial on Google Play. Families get to know Scout, fall in love with him. The $9.99 feels like nothing because they already care about him.

**Purchase:** $9.99 one-time purchase. No automatic charges. No recurring fees. No subscriptions. Ever.

**Post-trial:** After 7 days, advanced features lock but Scout stays installed. Still shows his face. Still greets the family. Trial end message: 'Thank you for spending time with Scout. Scout is still growing and receiving updates. If you'd like to continue the journey, you can unlock the full version at any time.'

**Baseline Brain:** TinyLlama 1.1B Chat Q4_K_M (~669 MB) — default, offline, always included. Re-enabled June 28 with safe delayed load (90s, 800MB RAM guard, nCtx=512).

**Optional Gemini:** Users add their own free Gemini key in Settings. ON by default when a key is saved (fixed June 28 — was always OFF). Scout NEVER ships with a bundled key.

---

## 5. Support Scout (In-App, Optional)

| Tier | Amount | Label |
|------|--------|-------|
| Coffee | $3 | Buy Scout a Coffee |
| More | $5 | Support Scout More |
| Grow | $10 | Help Scout Grow |
| Founding | $25 | Founding Supporter |

Support Scout screen designed and ready. Message: 'You’re not just supporting an app — you’re supporting a companion.'

---

## 6. Architecture — Five-Layer Memory Stack

| Layer | Type | Storage | Status |
|-------|------|---------|--------|
| Working | Sensory | RAM | Done |
| Habit | Patterns | JSON 14-day | Done |
| Truth | Authority | SQLite | Done — FLEXIBLE |
| Relevance | Index | Local Vector | Not yet |
| Reflective | Wisdom | LLM read-only | Not yet |

- Sovereign Rules: SQLite Truth always overrules everything.
- Pet Mode: any animal detected → Scout greets softly by name (or "Well... hello there little one. I hope someone will tell me your name soon." if unnamed). Scout continues operating normally. Future: steer-around Bluetooth command when mobile.
- Privacy Gate: Gemini receives anonymized text only. (Planned — not yet implemented.)
- Guest Mode: unknown face → 'Hello, I am [name]. What is your name?' (Planned — not yet implemented.)
- Flexible Memory: Scout stores and recalls ANY fact.

---

## 7. Current Technical State

### Working:

✓ Animated face (ScoutFaceView) — mouth, iris drift, thinking expression
✓ Eye jitter FIXED — boot lock (3500ms), speaking gate, dead zone, min-delta guard. A32 stable. June 18.
✓ Eyebrows and mouth brightened to #9BBEFF. June 18.
✓ Speech recognition (Android STT) + Text to Speech (TTS)
✓ STT reliability improved — EXTRA_PREFER_OFFLINE, 10s silence window, ERROR_RECOGNIZER_BUSY 600ms delay. June 28.
✓ Camera — face detection (ML Kit), scene labeling — throttled to ~7fps June 21 (memory pressure fix)
✓ Launcher icon fixed — face 68% of canvas, all 5 mipmap densities. Eyes fully inside circular mask. June 29.
✓ Face recognition COMPLETE and RELIABLE — ArcFace upgrade July 3: InsightFace MobileFaceNet (512-dim, 4.8MB) replaces old 192-dim model. PeopleDb v4. Cosine threshold 0.60f (ArcFace scale: same-person ~0.5–0.95, different-person ~0.0–0.4). findBestMatch scans only named rows. embedExecutor runs findBestMatch BEFORE storeEmbedding (self-match fix). Known face recognized consistently. Unknown face → Guest Mode. Nicolas Protocol active.
✓ Secondary face recognition — second-largest face also embedded in same executor job. person_embeddings table (up to 12 per person, threshold 0.55f for secondary crops). lastSecondaryFaceName (@Volatile). VisionAnswerBuilder uses it. June 29 / Diana fix July 3.
✓ Diana (secondary face) fix — pendingFaceIntroName now checked in secondary face block. "This is my wife Diana" with two people in frame now correctly assigns Diana to the secondary face. July 3.
✓ "Scout, forget [name]" command — wipes people table + person_embeddings table for that name. June 29.
✓ Multi-person face introduction — "this is my son Elijah" / "this is my wife Diana" registers family member faces in PeopleDb. Pending face mechanism handles two-person-in-frame introductions. June 21 / fixed July 3.
✓ VisionAnswerBuilder two-person response — "I see Patrick and Elijah" when both faces known; "I see Patrick and someone else" when secondary unrecognized. "I see X" phrasing (not "I see you, X") as of July 3.
✓ Personality phrase pools — Phrases.kt (new July 3). Anti-repeat rolling window (cooldown = pool.size / 2). Varied boot, goodbye, and remember responses. pickNamed() substitutes {name} placeholder.
✓ Adaptive boot greeting — ScoutBootStatus.kt rewritten July 3. Offline boot: BOOT_OFFLINE_FAST (no warming-up line) when TinyLlama loaded < 2s last session; BOOT_OFFLINE otherwise. TinyLlama load time stored in SharedPreferences. Online boot: BOOT_ONLINE (all 6 phrases mention offline backup warming up).
✓ Face greeting fires once per launch — greetedThisSession reset removed. June 28.
✓ Wrong-name teaching with 2 people in frame fixed — handleTeaching() guard prevents "this is my wife Diana" being stored as primary user rename. June 27.
✓ ML Kit label whitelist — OBJECT_WHITELIST in VisionAnswerBuilder.kt. ~80 household objects. Garbage labels gone. June 27.
✓ lastKnownFaceName set immediately on teaching — Scout says your name right away, not 2 seconds later. June 27.
✓ finishThinking() fixed — was empty no-op. Now clears isThinking + faceView state. Fixes permanent stuck-thinking when Gemini blocked. June 27.
✓ Greeting words blocked from name storage — hello/hi/hey/howdy/greetings/sup/yo in blockedNames. June 29.
✓ TTS deafness bug fixed — speak() return check + speakingStartedMs + 45s watchdog. Scout cannot go permanently deaf after idle. June 29.
✓ Voice settings persist across Settings/MainActivity — scout_prefs used by both. onResume() reloads pitch/speed. June 29.
✓ Gemini API — ON by default when key is saved (default fixed June 28). Timeout 10s connect / 20s read. maxOutputTokens=600 (raised June 29), sentence-complete instruction. Activated by 'go online' voice command. Model: gemini-3.5-flash. Daily quota cooldown 1 hour.
✓ Gemini quota/cooldown announced — speakUnavailableIfNeeded() returns Boolean; cooldown check at top of tryTinyLlamaOrFallback(). Repeat gaps: 6h daily quota, 10min rate limit. June 29.
✓ Gemini responses complete — maxOutputTokens=600, MAX_TOKENS trim to sentence boundary. June 29.
✓ TinyLlama 1.1B offline brain — RE-ENABLED June 28 with delayed load (90s), 800MB RAM guard, nCtx=512, nThreads=2. Automatic Gemini fallback via onFailed callback. On-demand load fires when Gemini fails and TinyLlama not yet loaded. CONFIRMED WORKING on A32 and Fold 7, July 7. bootstrapModelFile() auto-copies model from external storage on startup so reinstalls recover automatically.
✓ "Repeat that" intent — isRepeatRequest() + lastMeaningfulResponse cache (4-min TTL). Replays last 5-word+ answer instantly from any brain. June 28.
✓ Brain source Toast — "Gemini (online)" / "TinyLlama (offline)" shown after each answer for testing. June 28.
✓ Settings screen — SettingsActivity with 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Swipe-right gesture + voice command + first-boot hint. June 18.
✓ Hardcoded Gemini API key removed — now in encrypted SharedPreferences. June 18.
✓ Memory layers: TruthDb, ConversationDb, HabitLayer, PeopleDb (with BLOB embeddings), JournalDb
✓ Intent router — time, date, greetings, family facts, downloads, vision, weather, IDENTITY, RECALL_FACT
✓ Flexible teaching — 'my favorite color is teal' → stored permanently
✓ Flexible recall — recalls facts reliably after other questions
✓ Wake word filter — Scout only responds when he hears his name
✓ Conversation window — 30 seconds of open conversation after Scout responds
✓ Boot window — Scout awake and ready immediately after boot
✓ Online / disconnect phrases recognized
✓ Greeting routing — casual greetings route instantly to HOW_ARE_YOU (no TinyLlama wait)
✓ Vision response cleanup — noisy ML Kit labels filtered from spoken responses
✓ Person detection — VisionAnswerBuilder wired to PeopleDb. Scout reports person count cleanly
✓ Weather — current, tonight, tomorrow, 7-day, precipitation % via NWS (api.weather.gov) — free for commercial use
✓ ScoutPresenceDecider — four time-of-day modes
✓ Identity questions hardcoded — routing expanded
✓ Total offline mode — 'go offline' blocks ALL internet features
✓ Thinking-state expression — curious/engaged expression: one brow clearly raised (22px + sine) with questioning arch, other barely moves (5px); right lid subtly more relaxed (+0.08f droop); mouth right corner 3px higher (thoughtful side-smile). Iris glances 35–65px to side + upward (-20px). Head-turn faceGazeDrift 0.32f drives 12–21px visible drift. Redesigned July 7 from Patrick's direction with reference images.
✓ TinyLlama rambling fix — offline replies capped at 2 sentences (limitToSentences)
✓ Self-echo guard — Scout ignores his own TTS voice bleeding back into mic
⚠ MainActivity.kt blank line cleanup — previously marked complete; a direct code audit on July 29 found the file still has a blank line after most individual statements throughout, roughly doubling its apparent length versus its real code density. Either the cleanup was partial or later edits reintroduced the pattern. See `MainActivity Cleanup.md` §5 — real cleanup target, not resolved.
✓ Naming phrases expanded — "this is X", "I am X", "you see X" recognized as name-teaching phrases
✓ Three A32 stability fixes — camera bitmap recycle, ML Kit suppression during Gemini, speak() race condition closed. June 19–20.
✓ A32 crash resolved — camera frame throttle (150ms) eliminates delayed LMKD kill after Gemini responses. Confirmed stable June 21.
✓ A32 startup-collision crash resolved — staggered camera (3s) and speech (4.5s) startup avoids colliding with GMS's one-time ML Kit ART verification pass. Root-caused via full logcat capture, not guessed. July 28.
✓ Personal-memory questions structurally gated before Gemini — ScoutMemoryGate.isPossiblePersonalMemoryQuery(), not phrase-list-dependent. July 26.
✓ TinyLlama SIGABRT on long prompts fixed — chunked prefill (kNBatch-sized chunks) instead of one oversized batch. July 26.
✓ Teaching via entity+property extraction — ScoutFactExtractor.kt + ScoutEntityResolver.kt, order-independent, real multi-alias support (TruthDb.addAlias()/getAliases()). July 26.
✓ Presence Layer moments 1 & 2 — idle-silence acknowledgment and a real proactive return greeting, both gated by genuine sustained camera presence, not the always-true isListening flag. July 27–28.
✓ Listening reminder vision-gated — only fires when a face is actually sustained-facing Scout (yaw/size/center + 1.5s sustain), not "any face existed recently." July 28.
✓ API keys encrypted at rest — ScoutSecureKeyStore, Android Keystore-backed AES-256-GCM, versioned format, one-time plaintext migration. July 29.
✓ ScoutLlamaController — process-wide singleton owns TinyLlama's generation executor and owner/generation token, surviving Activity recreation without leaking threads or delivering stale results to a destroyed Activity. July 29.

### Pending — Launch Blockers:

*As of Version 52 (July 29, 2026), the still-open items in this list are tracked live in `MAIN BUILD PATH - ACTIVE.md` instead — that document is now the single current source for priorities/blockers/in-progress/parked work, so the same open item is never independently tracked in two places that could drift apart. This list is frozen from here forward as the historical record of what was pending as of each version; new "DONE" entries should still be added here when something ships, but new open items belong in `MAIN BUILD PATH - ACTIVE.md`, not here.*

✓ **Startup diagnostics** — DONE July 4. TTS failure Toast + STT unavailability spoken warning at boot.
✓ **Onboarding flow** — DONE July 4. OnboardingActivity.kt, 5 screens, first-boot redirect in MainActivity.
■ **Fold 7 dedicated stability testing** — testing has been on A32. Fold 7 needs its own validation session.
✓ **16KB page size — RESOLVED July 19** — Confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK: all 11 previously-flagged libraries pass individually, "Verification successful" overall. The July 18 dialog only ever fired on debuggable installs, not a real defect. See the July 19 section at the top of this document. No longer a launch blocker.
■ **Play Asset Delivery (PAD) wiring** — ModelDownloadActivity is built and ready. Wiring PAD to trigger the download screen and call updateProgress() is a future session.

✓ **Privacy Policy** — DONE July 11. In-app scrollable dialog (Settings → About Scout).
✓ **Terms of Use** — DONE July 10–11. In-app dialog + terms.html for website.
- Open Source Credits — THIRD_PARTY_NOTICES.md started (MobileFaceNet MIT done). Full screen still needed at launch.
- Play Store listing — description, screenshots, content rating, privacy policy link.
- Proposal Sandbox — 'Want me to remember that?' confirm step.
- Permanent vs temporary memory sorting.
- Caring follow-up loop.
- ScoutFaceView cleanup — 2 dead-code lines.
- Response cleanup layer — post-TinyLlama filter.
- Scout news feed — FUTURE feature.
- Wire in full mood system.
✓ **Offline Brain Delivery (Phase 3) — RESOLVED July 19–21.** TinyLlama inference complete on-device, and delivery is now solved too: the ~669MB model downloads via Android's `DownloadManager` from a GitHub Release asset on first launch, wired through the full startup gate (see the July 19–24 section near the top of this document). No longer undecided/unbuilt — Play Asset Delivery remains a possible future alternative (see Play Asset Delivery item above) but is not blocking.

---

## 7b. b8946 API Discoveries — CRITICAL

**llama_vocab is a separate type:** llama_tokenize and llama_token_to_piece now require `const llama_vocab*`. Get via `llama_model_get_vocab(model)`.

**Functions that crash — hardcode instead:** llama_n_vocab → 32000. llama_token_eos → 2. llama_token_eot → 2.

**KV Cache:** llama_kv_cache_clear() does not exist. Free and recreate context each call.

**Logits:** After prefill, use n_prompt-1. In generation loop, use index 0.

**Backend:** ggml_backend_load_all() returns 0 on Android. Use ggml_backend_load(cpuPath) with explicit .so path.

**Struct padding:** _pad[508] trailing array on params structs. Do NOT remove.

| Tuning (A32) | Value |
|--------------|-------|
| n_threads | 2 (memory-bound) |
| nPredict | 64 (~38 tokens, ~7.6s) |
| History | 2 turns |
| Speed | ~15 tok/s prefill, ~4 tok/s generation |
| Reality | 20–40s per answer — acceptable, Gemini is fast path |

---

## 7b2. Pending Expert Feedback — Mike Forst (Amazon Astro)

Mike Forst — Amazon Astro character director and sound lead (mikeforst.com). Contacted June 30, 2026. Responded positively. Feedback pending — arriving via email or video call.

Mike is an expert in how robots and AI companions feel trustworthy and present through behavioral design and non-verbal cues.

**When feedback arrives, map his insights to:**
- `ScoutFaceView.kt` — animation timing and behavioral micro-expressions
- `ScoutPresenceDecider.kt` — social timing, when Scout speaks vs. stays quiet
- Scout's identity and response philosophy (section 3 of this summary)

Do not act on this area without his input. His expertise is the right lens for these decisions.

---

## 7c. Known Issues — Do Not Touch Without Discussion

*As of Version 52, still-open rows below are now tracked live in `MAIN BUILD PATH - ACTIVE.md` (behavioral/product issues) or `MainActivity Cleanup.md` (code-level issues — e.g. the `ScoutFaceView dead code` row is covered there, currently verified as one confirmed item, `browAsym`, not necessarily both originally listed). This table is preserved as the historical record and still gets new "RESOLVED" annotations when something ships, but new open issues should be added to those two documents instead.*

| Issue | Notes |
|-------|-------|
| TinyLlama A32 real-world confirmation needed | Re-enabled June 28 with delayed load + RAM guard. Not yet confirmed that LMKD crash does not return under memory pressure. |
| A32 crashes | **RESOLVED June 21** — camera frame throttle (150ms) eliminated the delayed LMKD kill. Patrick confirmed stable. |
| A32 crash — camera/ML Kit/SpeechRecognizer startup collision | **RESOLVED July 28** — root-caused via full on-device logcat capture to a collision with GMS's one-time ML Kit ART verification pass, not a Scout or benchmark-harness bug. Fixed via staggered camera (3s)/speech (4.5s) startup. A different crash class from the June 21 entry above. |
| TinyLlama SIGABRT on prompts near/over 512 tokens | **RESOLVED July 26** — single-batch `llama_decode()` overflow when prefill exceeded `n_batch=512`. Fixed via chunked prefill; a related logit-indexing bug fixed in the same pass. |
| Secondary face bootstrap | First time two people are in frame after a fresh pull, Elijah may show as "someone else" — person_embeddings table starts empty. Once Elijah faces Scout alone once, his embedding populates the table and two-person recognition works. |
| A32 active test device | Patrick confirmed June 28: testing is on A32. Fold 7 listed as primary but needs a dedicated session. |
| TinyLlama slow on A32 | 20-40s per answer. Expected. Hardware limitation. Gemini is fast path when online. |
| Barge-in | Deliberately disabled. Runaway loop. Status: PARKED. |
| STT name recognition | 'Scout' misheard as 'Gal', 'Scott', 'Out'. Partially handled by wake word filter. |
| Live news | Neither brain reads live news. Future news feed needed. |
| ScoutFaceView dead code | Line 1023: doubled condition. Line 709: unused browAsym. Harmless but messy. |
| 16KB page size | ✓ RESOLVED July 19. Confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK — all 11 previously-flagged libraries pass individually, "Verification successful" overall. The July 18 dialog only ever fired on debuggable installs, not a real defect. See July 19 section at top. Play Store submission unblocked. |

---

## 7d. Session Log

- July 30: Workflow change documented — `main` is now the single source of truth, short-lived `claude/**` feature branches merge in via PR and get deleted. PRs #4, #5, and #6 all merged: PR #4 (speech reliability — `FuzzyNameMatcher.kt`, `ScoutSpeechAvailabilityMonitor.kt`, fully wired into `MainActivity`, live behavior), PR #5 (`ScoutCompanionMomentsEngine.kt` — pure decision-logic engine, no wiring yet), PR #6 (CI now runs `testDebugUnitTest` and adds a `pull_request` trigger; exposed and fixed a real pre-existing `ScoutMemoryGate.SELF_WORDS` gap missing "you"/"your"). Companion Moments approved as a major product-priority direction after real-device testing showed Scout stable but passive; design and restraint gates documented in the July 30 entry above. Final commit after all three merges: `1b5deb19dfced44529f571b30d27c622e8e12fb3`.
- July 29 (documentation): Established the four-document system (Scout_Master_Summary.md, Architecture.md, MainActivity Cleanup.md, MAIN BUILD PATH - ACTIVE.md), all verified against the codebase directly and header-versioned (date/commit/status). Follow-up consistency pass found and fixed a real factual error in Architecture.md ("six" vs. the actual five SQLite databases), removed cross-document duplication of the presence-layer temporary-value TODO, and added redirect notes so this document's own Pending/Known-Issues lists stop being independently tracked in parallel with MAIN BUILD PATH - ACTIVE.md going forward.
- July 29: Two rounds of ChatGPT-reviewed fixes (7 privacy/reliability, 7 mic/camera performance) — offline-brain gate bypass, LlamaEngine.free() race, misleading OpenAI/Claude setup, plaintext API keys + untouched backup templates, ScoutMemoryGate alias mismatch, TruthDb upsert staleness, onEndOfSpeech() restart risk, wake-word "out" false positive, fixed silence timeout, per-frame bitmap allocation, label/face cadence coupling, cameraEverStarted timing. Then: API keys encrypted via Android Keystore (ScoutSecureKeyStore, versioned format, typed encrypt/decrypt results, one-time plaintext migration via commit()); ScoutLlamaController introduced as a process-wide singleton owning TinyLlama's generation executor and owner/generation token, replacing per-Activity-instance state that could leak threads or deliver stale results across a configuration-change recreation; two follow-up corrections after a second review pass (invalidateOwner() on every onDestroy(), discard logging moved off an Activity-owned callback). Commits a348425, 0b3e9bc, 7d030e3, f856bb2, 2ac932e.
- July 28: Listening reminder made vision-led (ML Kit head-yaw gate, sustained-facing streak, reason-based diagnostics), then tightened to conservative thresholds with a vision-staleness check and real measured values logged. Dev-only TinyLlama benchmark harness added (native runGeneration() extraction, perf_context bindings, hidden 7-tap unlock screen), then fixed for thermal run-order bias (Latin-square rotation) and an XML manifest comment bug that had silently broken the previous commit's build. A32 crash fully root-caused via full logcat capture — a camera/ML Kit/SpeechRecognizer startup collision with GMS's one-time ART verification pass, not a Scout or benchmark bug — fixed via staggered camera/speech startup, a startup-settled gate for face embedding, and full startup timing diagnostics. Real proactive return greeting (Presence Layer moment 2) landed at production thresholds after a temporary A32 smoke-test build.
- July 27: "Who is Diana?" now answered by direct TruthDb lookup instead of unreliable TinyLlama inference. Presence Layer moment 1 shipped — idle-silence acknowledgment after long uninterrupted presence with no conversation, plus the return-greeting design that replaced Scout's previously-broken "welcome back" mechanism (shipped as a temporary smoke-test build first).
- July 26: Personal-memory questions structurally gated before Gemini via new ScoutMemoryGate.isPossiblePersonalMemoryQuery(), not just phrase matching. TinyLlama SIGABRT fixed — chunked prefill instead of one oversized batch, plus a related logit-indexing bug. Teaching moved from sentence-template regexes toward entity+property extraction (ScoutFactExtractor.kt, ScoutEntityResolver.kt) with real multi-alias support in TruthDb.
- July 25: Loading-phase visual redesign (solid black background, no fake progress bar/percentage, "Waking Scout up…" replacing "brain" language) plus "Downloading…" label persisting through real byte-progress text. Boot-announcement staleness fixed twice: first the string was captured before the brain was ready and spoken stale later (fixed via boolean flag + fresh bootStatus.build() at actual speak time); then found the fast/slow "warming up" pool selection itself was always wrong since its only timing signal was written 90s after the announcement already fired, and — more fundamentally — the startup gate means the brain is always already loaded by the time this runs, so the fast/slow distinction was removed entirely in favor of always using the ready phrasing. Calendar routing fixed: "when is my next X" was being swallowed by the personal-facts regex exclusion meant only for bare "my" (now scoped to not exclude "my next"), and keyword extraction now strips "my"/"next" filler before matching event titles. New CalendarDateParser.kt adds specific-date lookups ("am I free on July 10th," weekday names, bare "the 10th"). ApiKeySetupActivity's provider picker (Gemini/OpenAI/Claude) wrapped in a ScrollView — Claude was silently cut off below the fold on the locked landscape orientation, same root cause as the earlier download-screen overflow. Mic-hears-itself bug fixed: a queued scheduleListenRestart() Handler callback could survive onPause() and restart the recognizer while SettingsActivity was foregrounded, picking up the voice-tone preview's "My name is {name}" line through the speaker — which is exactly TeachExtractor's user-name-teaching trigger phrase, so renaming Scout and then testing voice tone taught Scout's new name as the user's own name. Fixed with an explicit isForeground guard plus a reworded, collision-safe preview line. New FAMILY_NAMES intent ("what are the names in my family") answers from TruthDb instead of falling through to TinyLlama/Gemini, which have no fact access and were hallucinating names. "Turn on calendar" and "go online" (once connectivity is confirmed) now deep-link into specific Settings screens via a new SettingsActivity.EXTRA_TARGET_SCREEN, addressing feedback that Settings' sections are hard to find by hand. Confirmed (not yet fixed, holding per Patrick): SettingsActivity's robot-rename feature only writes to a `robot_name` pref that nothing else reads — every actual spoken self-identification reads a separate, disconnected TruthDb fact — so renaming Scout currently does nothing outside Settings itself.
- July 24: Full unified startup gate built — `ModelDownloadActivity` is now the single gate `MainActivity` always waits on (Downloading → Loading offline brain → Preparing) before showing its face or starting any system, not just when the model file happens to be missing. Three on-device test bugs fixed the same day: TTS speaking the boot announcement before the gate closed (deferred via `pendingBootAnnouncement`, now a boolean, spoken fresh from `startSystems()`), the Downloading phase showing nothing explanatory once the tip text moved to Loading-only, and the Loading phase visually looking like a stalled Downloading phase (restyled to a solid black background + single bold status line, "Loading offline brain..." softened to "Waking Scout up…"). See the July 19–24 section near the top of this document for full detail — this whole body of work had no dated entry until this pass reconciled the document against the actual codebase.
- July 20–21: Model download debugged from a real on-device stall to fully working — `setDestinationUri` couldn't write into scoped storage from the DownloadManager system process (fixed via `setDestinationInExternalFilesDir`), then a second silent 0%-progress failure mode was traced and fixed via new diagnostic logging at every previously-silent branch of the download flow, confirmed via on-screen Toast checkpoints since Logcat capture was itself unreliable in this exact window.
- July 19: 16KB alignment CONFIRMED PASS — built a signed release APK (not debug), ran `zipalign -c -P 16 -v 4` against it directly. All 11 previously-flagged native libraries pass individually (OK), overall "Verification successful." Installing the release APK on the Fold 7 no longer triggers the compatibility dialog at all, confirming the dialog was debug-build-specific as hypothesized. Play Store submission unblocked on the 16KB front. Separately diagnosed and documented (not yet fixed): `ModelDownloadActivity`'s `MODEL_DOWNLOAD_URL` is an unfilled placeholder, so its download flow can never complete, and it deletes any locally-staged model file before attempting to download — real TinyLlama delivery for a release build currently requires manually pushing the `.gguf` file into the app's external files directory via `adb push` after every fresh install. **Superseded the same week** — see the July 19–24 section near the top of this document: the placeholder was filled and the whole download flow debugged to working order. Summary updated to version 50.
- June 5: IDENTITY intent + hardcoded responses. Weather offline fix. Total offline mode.
- June 7: TinyLlama A32 crash stabilized. Identity routing expanded. Face direction locked.
- June 8: Thinking expression built. Flexible Memory Planning Document created.
- June 9: Flexible memory foundation built. RECALL_FACT intent added. First autonomous memory recall.
- June 10: Business model updated — 7-day free trial. Wake word and face recognition identified as launch blockers. Launch Checklist created.
- June 12: Wake word filter built. Conversation window (30s) and boot window added. Memory recall bug fixed. Gemini online/offline confirmed. 5-screen onboarding flow approved. Versioning system defined. Legal requirements defined. Website confirmed. PeopleDb.kt updated with getName/setName/isKnown. Weather API licensing question raised with Open-Meteo.
- June 14: Greeting routing fixed. Vision response cleanup. VisionAnswerBuilder wired to PeopleDb. Face-tagging hook added to handleTeaching() but face hash instability found. Rambling/garbled continuations discovered.
- June 15: Rambling fix — limitToSentences() added, offline replies capped at 2 sentences. MainActivity.kt blank line cleanup. Self-echo guard added (lastScoutUtteranceNormalized, onResults() check). Face recognition Step 1: MobileFaceNet.tflite bundled (MIT, ~5MB), TensorFlow Lite dep added, FaceEmbedder.kt created (not yet wired). Naming phrases expanded in TeachExtractor.kt ("this is X", "I am X", "you see X").
- June 16: Weather switched from Open-Meteo to NWS (api.weather.gov) — free for commercial use, no API key, U.S. only. ScoutWeatherManager.kt fully rewritten. THIRD_PARTY_NOTICES.md created. Quick Start, Launch Checklist, and Master Summary updated to v11/v5/v33.
- June 17: Face recognition Steps 2–4 COMPLETE. FaceEmbedder wired into camera pipeline. PeopleDb updated with BLOB embedding column and cosine similarity matching. Naming flow uses embedding identity. Embedding memory pressure and queue overflow fixed. ApiKeySetupActivity.kt wired.
- June 18: SettingsActivity built — all 5 sections. Hardcoded Gemini API key removed from MainActivity.kt. Prism stub removed from Brain & Behavior settings. Eye jitter fixed — boot lock 3500ms, speaking gate, dead zone, min-delta guard. Eyebrows and mouth brightened to #9BBEFF. Gear button replaced with swipe-right gesture + first-boot hint + voice command.
- June 19: TinyLlama startup load disabled on A32 — LMKD crash prevention. Camera bitmap memory leak fixed (recycle after all async ML Kit callbacks complete).
- June 20: ML Kit suppressed during Gemini calls via isThinking gate. speak() race condition fixed — isSpeaking set immediately at function entry, closing 240–650ms gap that allowed ML Kit to spike memory just before Scout spoke. Mouth animation timing fixed — faceView.setSpeaking(true) moved back to TTS onStart callback only.
- June 21: Camera frame throttle added (150ms interval, ~7fps ML Kit). A32 crash eliminated — confirmed stable by Patrick. Face name persistence fixed (lastKnownFaceName, findBestMatch before storeEmbedding, named-rows-only SQL). Face recognition threshold raised 0.65→0.75 (Patrick/Elijah false match fixed). Multi-person introduction added (SON_NAME/WIFE_NAME register face, pendingFaceIntroName mechanism). VisionAnswerBuilder two-person response improved. Gemini maxOutputTokens raised 150→250.
- June 27: Wrong-name teaching bug fixed (2-person frame guard in handleTeaching). ML Kit label blacklist replaced with OBJECT_WHITELIST in VisionAnswerBuilder (~80 household objects). lastKnownFaceName now set immediately after name teaching (not 2s later). finishThinking() fixed — was empty no-op causing permanent stuck-thinking when Gemini blocked. Testing listed as moved to Fold 7.
- June 28: TinyLlama re-enabled — 90s delayed load, 800MB RAM guard, nCtx=512, nThreads=2. tryLoadOfflineBrain() helper added (startup + on-demand path). Gemini timeouts reduced (10s connect / 20s read). onFailed/onAnswered callbacks added to tryGemini(). tryTinyLlamaOrFallback() extracted — TinyLlama now automatic Gemini fallback. "Repeat that" intent added (isRepeatRequest(), lastMeaningfulResponse cache, 4-min TTL). Brain source Toast added ("Gemini (online)" / "TinyLlama (offline)"). Gemini default fixed (isGeminiEnabled() was always false). Daily quota cooldown reduced 6h→1h. Face greeting reset removed — greets once per boot only. STT improved: EXTRA_PREFER_OFFLINE, 10s silence window, ERROR_RECOGNIZER_BUSY 600ms delay. Duplicate prompt now serves cached Gemini reply. speakUnavailableIfNeeded() made public. Testing confirmed on A32.
- June 29: Launcher icon fixed — face 68% of canvas, all 5 mipmap densities regenerated, eyes inside circular mask. Face threshold raised 0.75→0.82 (Patrick/Elijah genetic similarity fix). "Scout, forget [name]" voice command added (clears people + person_embeddings). TTS deafness bug fixed — speak() return check + speakingStartedMs + 45s watchdog. Voice slider now sticks — scout_prefs in both SettingsActivity and MainActivity.onResume(). Greeting words blocked from name storage (hello/hi/hey/howdy/greetings/sup/yo). Gemini maxOutputTokens raised 250→600, "Always end on a complete sentence" added to system prompt, MAX_TOKENS boundary trim. Gemini quota announced — speakUnavailableIfNeeded() returns Boolean, cooldown check at top of tryTinyLlamaOrFallback(). Secondary face recognition — PeopleDb v3 with person_embeddings table, addNamedEmbedding(), findBestMatchName(); secondFace embedded in same executor job, lastSecondaryFaceName (@Volatile); VisionAnswerBuilder uses secondaryFaceName.
- June 30: Dynamic robot name — boot greeting, identity feelings reply, identity fallback, offline brain fallback all read from TruthDb. No hardcoded "Scout" in any spoken response. TeachExtractor.kt: 8 new patterns for "that is my son/wife", "that person is my son/wife", "that is [name]", "that person is [name]", "his name is", "her name is". VisionAnswerBuilder freshness 1800ms→3500ms (camera blocked during TTS). registerFamilyMemberFace() guard prevents overwriting a known face hash with a wrong name. TinyLlama filter: "family friendly companion" + "family companion robot" added. Pet Mode design locked: any animal → soft greeting using stored name or "Well... hello there little one. I hope someone will tell me your name soon." Scout continues normally after greeting. Nicolas Protocol renamed Pet Mode (covers all animals). Settings Architecture and Visual Elements specs restored to summary. Summary updated to version 38.
- July 3: ArcFace upgrade — InsightFace MobileFaceNet (512-dim, 4.8MB) replaces 192-dim model. FaceEmbedder.kt: EMBEDDING_SIZE 192→512, single-batch output. PeopleDb v4: migration clears 192-dim embeddings, preserves names/hashes, threshold 0.60f. "I see X" phrasing replaces "I can see you, X" throughout VisionAnswerBuilder and MainActivity. Diana fix — secondary face block now consumes pendingFaceIntroName. Phrases.kt new file: anti-repeat phrase pools for boot, goodbye, and all remember responses. ScoutBootStatus.kt rewritten: uses Phrases pools, adaptive BOOT_OFFLINE_FAST (< 2s load) vs BOOT_OFFLINE. BOOT_ONLINE phrases all mention offline backup warming up. TinyLlama load time measured and stored in SharedPreferences. Goodbye and remember responses now varied via Phrases pools. Summary updated to version 39.
- July 7 S1: 16KB page alignment fix confirmed working on A32 and Fold 7 (scout_llama.so, CMakeLists.txt). bootstrapModelFile() added — auto-copies TinyLlama model from external storage to filesDir on startup (no permission needed via app-specific external dir; READ_EXTERNAL_STORAGE with maxSdkVersion="32" for root /sdcard/ on Android ≤12). Offline fallback message fixed — "I'm working offline" when Gemini disabled (not "having trouble connecting"). TinyLlama confirmed working on both A32 and Fold 7. Head-turn faceGazeDrift multipliers 0.07/0.06 → 0.32/0.26 (was ±5px virtual = invisible; now ±24px X / ±14px Y, clearly readable). Summary updated to version 42.
- July 10–11: terms.html created (commit b5735f5) — website Terms of Use with acceptance and changes-to-terms clauses for Play Store compliance. ML Kit bumped: face-detection 16.1.6→16.1.7, image-labeling 17.0.7→17.0.9 (both claimed 16KB aligned on arm64, commit 60443f3 — REOPENED July 18, see top of document). LiteRT migration attempted (litert:1.4.0) — failed, version not in Maven, reverted to tensorflow-lite:2.17.0 (commit eb8223e). Privacy Policy and Terms of Use added as in-app scrollable dialogs in SettingsActivity.kt (commit a330b93). openUrl() removed — both dialogs work fully offline. July 16 investigation: TFLite 2.17.0 shows strong on-device evidence of non-compliance (Fold 7 debug popup on Android 15, Google issue tracker) — binary not yet verified with readelf. Correct migration target is litert:2.1.5 (2.1.x alignment confirmed per GitHub issue #6299).
- July 16: LiteRT migration code done — tensorflow-lite:2.17.0 → litert:2.1.5 in build.gradle.kts, FaceEmbedder.kt import updated (commits 9676192). Readelf verification pending (Patrick's task after next Android Studio build). Face recognition 3-bug fix (commit b6c5579): (1) margin check added to findBestMatchName/findBestMatchNameWithScore (minMargin=0.08f — Scout says nothing when top two candidates are within 0.08f); (2) CONFIDENT_EMBED_THRESHOLD=0.72f in MainActivity gates addNamedEmbedding calls on both primary and secondary face paths; (3) addNamedEmbedding at-cap behavior changed from hard-stop to rolling window (replaces most-redundant via maxByOrNull cosine similarity). scoreByPerson() private helper extracted. forgetPerson path clears lastFaceEmbedding. Summary updated to version 46.
- July 17: LiteRT import fix — FaceEmbedder.kt import reverted to org.tensorflow.lite.Interpreter (com.google.ai.edge.litert.Interpreter does not exist at runtime; commit 83ed37f). 16KB readelf run — Patrick ran llvm-readelf.exe -l libLiteRt.so on Windows (NDK 28.2.13676358); all LOAD segments Align 0x4000; libLiteRt.so and libLiteRtClGlAccelerator.so both PASS in that isolated check — but REOPENED July 18: the same libLiteRt.so fails Android's own on-device compatibility check once actually bundled in the built app (see top of document). TeachExtractor double-prefix bug fixed (startsWith("favorite") guard at line 180; commit 9b353a8); keyToHuman() collapses old favorite_favorite_ keys for display. Battery optimization prompt added — checkBatteryOptimization() fires 8s after first boot, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, one-time prefs guard, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in AndroidManifest (commit 1abcee1). Thinking watchdog added — thinkingStartedMs + 120s MAX_THINKING_DURATION_MS in runRecognizerWatchdog() (commit 1abcee1). DB migration migrateDoublePrefixFacts() deletes favorite_favorite_% keys on next launch including TTS self-echo entry (commit e24fad9). TruthDb gains deleteFact() and deleteFactsWithKeyLike() (commit e24fad9). ScoutExportManager updated to accept peopleDb: PeopleDb; adds people (named faces) and face_embeddings (counts) sections to brain export (commit aa10bc9). Brain export JSON confirmed: "Very" not in truth DB — must be in people table. TTS self-echo vulnerability documented (self-echo guard missed "yes, my favorite color is cyan" due to prefix mismatch; TeachExtractor stored it as favorite_favorite_yes_my_favorite_color; cleaned by migration; root fix deferred). Summary updated to version 47.
- July 7 S2: Thinking expression completely redesigned based on Patrick's direction and reference images. Goal: curious/engaged ("Hmm, let me think") not sleepy/tired. thinkGlanceSideX 8–20px → 35–65px (drives visible face drift). Brow: one brow raises 22px + sine with questioning arch; other barely moves (5px); thinkInnerLift reduced 20px → 6px on quiet brow only (was making it look worried). Lid: right eye +0.08f droop during thinking (subtle asymmetry — concentration not sleep). Mouth: corYR 3px higher than corYL (tiny thoughtful side-smile). Summary updated to version 43.
- July 4: PeopleDb threshold raised back to 0.65f (0.60f caused Diana/Elijah cross-contamination at ArcFace scale). cursor.use{} in findBestMatch + findBestMatchName (leak fix). forgetPerson made atomic with transactions. addNamedEmbedding COUNT(*) guard. VisionAnswerBuilder: freshness 3500ms→1800ms, 3+ faces branch gets dogLine, 2-face branch secondaryFaceName arm precedes pendingIntroName arm, new else arm for unknown primary + known secondary. Secondary face path adds findBestMatch fallback after findBestMatchName. Caption persistence fix — onResume() hides caption immediately when captions disabled. Startup diagnostics: TTS failure Toast + STT unavailability spoken warning at boot + JournalDb log. First-boot onboarding redirect at top of MainActivity.onCreate(). OnboardingActivity.kt built — full 5-screen flow, currentPage single source of truth for dots + counter, finishOnboarding() sets offline default. BOOT_NO_KEY phrases replaced with settings slide-right tip. CLAUDE.md created with git commands and critical rules for all future Claude sessions. ModelDownloadActivity.kt built — 39 messages, ObjectAnimator animation, updateProgress() API, portrait-only, AndroidManifest registered. Summary updated to version 40.

---

## 8. Working Rules — Always Apply

- Full paste-ready replacements only, one file at a time. No snippets. No partial files.
- Surgical CTRL-F and CTRL-R approved for large files — always specify which tab first.
- Build: Android Studio only — Build → Clean Project → Build → Assemble Project. gradlew fails on Patrick's machine (JAVA_HOME error).
- Some Scout files have NO indentation. If a search fails, try a shorter unique single-line string.
- Some logic lives in TWO places (e.g. updateLife AND scheduleNextFrame) — change both or Scout flickers.
- One safe change at a time. Build and test before the next change.
- Never touch speech, camera, or download systems without explicit discussion.
- Never touch ScoutFaceView casually — it is Scout's visual heart.
- Both Claude and ChatGPT are active collaborators — cross-review welcome.
- Patrick is not a professional programmer — explain at screenshot level always.
- Patrick is dyslexic, stroke survivor, blind in right eye, T1 diabetic — keep messages clear and concise.

---

## 9. Key Files

| File | Description |
|------|-------------|
| MainActivity.kt | Main app — all logic. Hardcoded API key REMOVED June 18. Wake word filter in onResults(). Self-echo guard (lastScoutUtteranceNormalized). limitToSentences() for rambling fix. handleTeaching() wires name to PeopleDb. isSpeaking set immediately in speak() — race condition fix June 20. tryLoadOfflineBrain() added June 28 (delayed + on-demand TinyLlama load). isRepeatRequest() + lastMeaningfulResponse cache June 28. tryTinyLlamaOrFallback() extracted June 28. pendingBrainSource + brain Toast June 28. greetedThisSession reset removed June 28. STT EXTRA_PREFER_OFFLINE + silence/busy fixes June 28. TTS deafness fix June 29 (speak() return check, speakingStartedMs, 45s watchdog). scoutPrefs reads from scout_prefs June 29 (voice pitch/speed in onInit + onResume). blockedNames includes greeting words June 29. lastSecondaryFaceName + secondFace + capturedSecondBox + secondary embed block June 29. isInCooldown() check + speakUnavailableIfNeeded() call at top of tryTinyLlamaOrFallback() June 29. July 17: checkBatteryOptimization() fires 8s after first boot (commit 1abcee1). thinkingStartedMs + MAX_THINKING_DURATION_MS=120_000L + thinking watchdog in runRecognizerWatchdog() (commit 1abcee1). migrateDoublePrefixFacts() in setupMemory() deletes favorite_favorite_% keys on first run (commit e24fad9). keyToHuman() collapses old double-prefix keys for display (commit 9b353a8). |
| ScoutFaceView.kt | Custom face canvas — all visual animation. Thinking expression updated June 8. Eye jitter fixed June 18 (boot lock, speaking gate, dead zone, min-delta). Eyebrows/mouth #9BBEFF June 18. |
| SettingsActivity.kt | NEW June 18 — 5 sections: AI Provider, Voice & TTS, Behavior, Brain & Behavior, About Scout. Gemini key entry, offline toggle, pitch/speed sliders. Opened via swipe-right + voice command + first-boot hint. |
| ScoutIntentRouter.kt | Intent routing — IDENTITY + RECALL_FACT added. Online/disconnect phrases. |
| TeachExtractor.kt | Extracts facts from speech — FLEXIBLE. Updated June 15 with "this is X", "I am X", "you see X" name patterns + NON_NAME_WORDS stoplist. July 17: `startsWith("favorite")` guard prevents double-prefix on "my favorite X is Y" patterns — commit 9b353a8. |
| FactKey.kt | Fact labels — fixed keys kept + FactKey.custom() for any new label. |
| TruthDb.kt | SQLite fact store — fully flexible. July 17: `deleteFact(entity, factKey)` and `deleteFactsWithKeyLike(entity, pattern)` added for targeted fact removal (commit e24fad9). |
| ApiKeySetupActivity.kt | API key wizard — wired to secure storage June 17. |
| GeminiClient.kt | Gemini HTTP wrapper with cooldown discipline. connectTimeout=10s, readTimeout=20s. maxOutputTokens=600 (raised June 29). Daily quota cooldown 1 hour. Single-flight guard. isDailyQuotaExhausted() + isInCooldown() methods. MAX_TOKENS finishReason trim to sentence boundary June 29. |
| ScoutPromptBuilder.kt | Builds Gemini system instruction and unavailable messages. "Always end on a complete sentence" in system prompt June 29. buildOnlineUnavailableMessage() returns daily quota / rate limit / generic variants. |
| brain/ScoutGeminiManager.kt | Gemini orchestration. onAnswered/onFailed callbacks added June 28. lastGeminiReply cache (4-min TTL) — serves duplicate prompts June 28. speakUnavailableIfNeeded() returns Boolean June 29 (true=spoken, false=suppressed). isInCooldown() exposed June 29. Repeat gaps: 6h daily quota, 10min rate limit. |
| ScoutWeatherManager.kt | Live weather via NWS (api.weather.gov) — UPDATED June 16. Free for commercial use. Precip %, offline-aware. U.S. only. |
| ScoutPresenceDecider.kt | Social timing layer. |
| LlamaEngine.kt | Offline brain JNI wrapper — WORKING. Re-enabled June 28: loadAsync called with nCtx=512, nThreads=2. |
| OfflinePromptBuilder.kt | TinyLlama prompt formatter. |
| scout_llama_jni.cpp | C++ JNI bridge — compiled into libscout_llama.so. |
| scout_llama_api.h | Self-contained b8946 declarations. |
| CMakeLists.txt | NDK build config. |
| HabitLayer.kt | Pattern memory — 14-day decay. |
| PeopleDb.kt | People memory — getName(), setName(), isKnown(). BLOB embedding column added June 17. Cosine similarity matching. findBestMatch scans named rows only (June 21). DB version 4 July 3: migration clears 192-dim embeddings (preserves names/hashes). person_embeddings table (addNamedEmbedding(), findBestMatchName(), forgetPerson()). Up to 12 embeddings per person. Threshold 0.65f (raised back July 4 — 0.60f caused Diana/Elijah cross-contamination). cursor.use{} in findBestMatch and findBestMatchName (July 4). forgetPerson atomic with transactions (July 4). Secondary crop threshold 0.55f. July 16: private scoreByPerson() helper aggregates best score per named person. findBestMatchName() adds minMargin=0.08f — returns null when top two candidates are within 0.08f of each other. findBestMatchNameWithScore() new — returns Pair<String, Float>? for confidence gating in MainActivity. addNamedEmbedding(): at cap, replaces the most-redundant embedding (maxByOrNull cosine similarity) in-place via db.update instead of hard-stopping. |
| VisionAnswerBuilder.kt | Builds spoken vision responses. OBJECT_WHITELIST filters noisy ML Kit labels (June 27). Wired to PeopleDb. Uses lastKnownFaceName for reliable name reporting. faceCount==2 uses both knownFaceName and secondaryFaceName. "I see X" phrasing (not "I see you, X") as of July 3. July 4: freshness 3500ms→1800ms; 3+ faces branch gets dogLine; 2-face branch: secondaryFaceName arm precedes pendingIntroName arm, new else arm for unknown primary + known secondary. |
| FaceEmbedder.kt | Created June 15. Wired into camera pipeline June 17. ArcFace upgrade July 3: loads InsightFace MobileFaceNet.tflite (512-dim), EMBEDDING_SIZE=512, single-batch output Array(1){FloatArray(512)}, single-pass buffer fill. Preprocessing unchanged: (px - 127.5f) / 128f. Returns L2-normalized 512-dim embedding. July 17: import corrected to org.tensorflow.lite.Interpreter (com.google.ai.edge.litert.Interpreter does not exist in the LiteRT 2.1.5 AAR at runtime). |
| MobileFaceNet.tflite | Bundled in app/src/main/assets/. InsightFace MobileFaceNet trained with ArcFace loss (July 3). 4.8MB. Input: 112x112 RGB, normalized. Output: 512-dim embedding. Replaces original 192-dim model. |
| Phrases.kt | NEW July 3. Personality phrase pools with anti-repeat rolling window (cooldown = pool.size / 2). pick(key, pool) returns a non-repeating random phrase. pickNamed(key, pool, name) substitutes {name} placeholder. Pools: BOOT_ONLINE, BOOT_OFFLINE_FAST, BOOT_OFFLINE, BOOT_NO_INTERNET, BOOT_NO_KEY, REMEMBER, REMEMBER_NAME, REMEMBER_MY_NAME, REMEMBER_WIFE, REMEMBER_SON, REMEMBER_DOG, GOODBYE. BOOT_NO_KEY phrases replaced July 4 — now tell user to slide right to open settings. |
| OnboardingActivity.kt | NEW July 4. 5-screen onboarding flow: Welcome / Trial / This Is Just The Beginning / Privacy / Ready To Begin. currentPage drives both dots and "X / 5" counter (single source of truth). Scout icon visible screens 1 and 5 only. finishOnboarding() sets PREF_ONBOARDING_DONE=true (scout_prefs) and gemini_enabled=false (scout_memory). |
| ModelDownloadActivity.kt | NEW July 4. Portrait loading screen for TinyLlama model download. 39 humorous messages shuffled and cycled with ObjectAnimator slide-right-in / slide-left-out animation. updateProgress(percent, downloaded, total, timeLeft) for PAD wiring. Layout: activity_model_download.xml. |
| CLAUDE.md | NEW July 4. Repo-root session notes for all future Claude instances: full git pull/push commands (branch name), critical hardcoding rules, architecture quick ref, test devices, master doc list. |
| brain/ScoutBootStatus.kt | REWRITTEN July 3. Uses Phrases pools for all boot greetings. Adaptive offline boot: BOOT_OFFLINE_FAST (skips warming-up) when lastLlamaLoadMs < 2s, BOOT_OFFLINE otherwise. Takes lastLlamaLoadMs: () -> Long lambda (default Long.MAX_VALUE). |
| ScoutExportManager.kt | Exports Scout's memory as a JSON file for sharing. July 17: constructor updated to accept peopleDb: PeopleDb; added "people" section (named faces from people table — face_hash, name, first_met, last_seen, no BLOBs) and "face_embeddings" section (per-name embedding count from person_embeddings). Commit aa10bc9. |
| THIRD_PARTY_NOTICES.md | MIT attribution for MobileFaceNet. Start of Open Source Credits. |

---

## 10. Scout Animation Goal & Mood System

### Visual Elements (ScoutFaceView)
- Background: dark blue-charcoal #1E2B38 (finalized May 18, 2026)
- Virtual canvas: 1920×1080
- Eyes: large ovals, deep blue iris with 28 spoke rays, biased inward 20f toward nose
- Mouth: minimal subtle curve
- Brows: thin and subtle — floating sticker feeling reduced but still readable. Still being refined.
- Wave bars: 22 diamond shapes, teal #00FFD0, visible during listening and speaking
- Idle listening dots: 3 teal pulsing dots when quiet

**Animation tone:** Subtle human animation. Soft emotional transitions. Calm organic motion. Believable presence.
NOT: Pixar-style exaggeration. Cartoon expressions. Hyperactive motion. Fake emotion.

**Design goal:** An AI face with gentle gaze drift, very subtle mouth, and soft thin brows that integrate naturally with the face. Scout is closer to this target than the early versions; brow integration is the largest remaining visual gap.

**Keep forever:** blue iris, white sclera, cartoon style.
**Never add:** tear ducts, skin folds, eyelashes, realistic anatomy.
**Design principle:** 'Scout stays Scout. He just gets a little more alive.'

### Mood States

Scout's face should feel alive and emotionally present, but always calm. Never perfectly still.

- CALM — Eyes center, brows neutral, subtle smile (0.15). Scout's default.
- CURIOUS — Eyes shift right and up, one brow lifts (0.18), slight tilt (0.12).
- HAPPY — Eyes center, both brows lift gently (0.12), smile increases (0.35).
- THINKING — Eyes drift up/around, lids narrow, brows asymmetric. PARTLY BUILT June 8.
- CONCERNED — Eyes look slightly down, inner brows lift (0.05), tilt inward (-0.18).

| Version | What Changes |
|---------|-------------|
| v1.0 | Current eyes. Stable irises. Better identity. Better memory. |
| v1.5 | Eyebrows move per mood. Smooth natural blink every 4–8 seconds. |
| v2.0 | Simple upper eyelids + simple lower eyelids. Still cartoon. Still Scout. |
| v3.0 | Full emotion system. Scout at most expressive. Still obviously Scout. |

- Keep forever: blue iris, white sclera, cartoon style.
- Never add: tear ducts, skin folds, eyelashes, realistic anatomy.
- Design principle: 'Scout stays Scout. He just gets a little more alive.'

---

## 10b. Settings Architecture

The Settings screen is the user's control center for Scout. Defaults are calm and safe — users opt in to more capability rather than opt out.

**10b.1 Identity & Voice**
- Robot Name — default "Scout", users can rename. All spoken responses use the stored name dynamically.
- Voice pitch slider
- Voice speed slider
- Future voice tone options

**10b.2 Brain & Behavior**
- Offline Mode (default ON) — Scout uses TinyLlama by default
- Online Brain Helper — toggle Gemini or a larger local model
- API key entry — user's own free Gemini key, never bundled with the app
- Kid Safe Filter
- Pet Mode — Scout greets pets softly. Future: physical steer-around on robot body.
- Presence Mode (default ON) — Scout actively listens in the room
- Allow Spontaneous Comments
- Privacy Mode toggle

**10b.3 Builder's Workbench**
- Enable Hardware Mode (off by default)
- Bluetooth pairing — for KEYESTUDIO Mini Tank Kit V2 chassis
- Future motor controls

**10b.4 Privacy & Data**
- Memory Export — back up TruthDb and habits
- Memory Import
- Reset Memory Layers — selective reset
- Camera controls
- Voice camera commands

**10b.5 Extras & Support**
- Cosmetics (Backpack) — visual customization
- Support Scout (in-app, optional — see section 5)
- About & Licenses

**10b.6 Connected Services (Future — All Opt-In)**
- Calendar access — add, remove, and announce events. Uses Android Calendar Provider. No external API needed.
- Phone call awareness — Scout announces caller name then steps aside. Normal call behavior untouched.
- Gmail access — read emails and compose when asked. Requires Google OAuth. Planned for a later phase.
- Design principle: Scout announces and helps, but never interferes.

---

## 11. TinyLlama Safety Architecture

- Layer 1: Hard-coded safety rules (app level). Always enforced.
- Layer 2: Scout identity rules (system prompt + hardcoded intents).
- Layer 3: TinyLlama generation (last line of defense only).

TinyLlama knows profanity and scary content. Scout must NOT rely on it for safety decisions.
Future: response cleanup layer after TinyLlama, before TTS.

---

## 12. Device Requirements

| | Android | RAM | Storage | Notes |
|--|---------|-----|---------|-------|
| Minimum | 13+ | 4 GB | ~3–5 GB | Everything works; responses slower. |
| Recommended | 13+ | 8 GB+ | 10 GB+ | Faster responses, smoother animations. |

---

## 13. Brain Upgrade Models

| Tier | Size | Model |
|------|------|-------|
| FREE | 669 MB | TinyLlama 1.1B Q4_K_M — baseline, always included |
| $2.99 | 1.79 GB | Phi-2 2.7B Q4_K_M — Microsoft. Solid first upgrade. |
| $4.99 | 2.02 GB | Llama 3.2 3B Q4_K_M — Meta. THE most important upgrade. |
| $6.99 | ~2.4 GB | Phi-4 Mini Q4_K_M — Microsoft (PRIMARY Pack 3). |
| $6.99 ALT | 2.39 GB | Phi-3.1 Mini 4K Q4_K_M — tested backup Pack 3. |
| $9.99 | 4.92 GB | Llama 3.1 8B Q4_K_M — Meta. Flagship, Fold 7 class. |

---

## 14. Hardware Direction — Optional

Scout works fully without hardware. KEYESTUDIO Mini Tank Kit V2 (Patrick owns one). Hardware Mode is opt-in. The $9.99 baseline must work fully on the phone alone.

---

## 15. Episodic Memory — Planned Phase

Scout's current memory stores facts and habits. The missing layer is **episodic memory** — remembering shared experiences over time, not just isolated facts.

| Type | Example | Status |
|------|---------|--------|
| Facts | "Your wife is Diana." | Done — TruthDb |
| Episodes | "Yesterday we talked about face recognition." | Planned — JournalDb |
| Summaries | "This week we fixed vision and talked about beta testing." | Future |

**How it would work:**
- At the end of a conversation, Scout quietly saves a short journal entry (one or two sentences)
- Teaching moments, recognized events, and notable interactions are logged
- When asked "what did we work on this week?" Scout reads the last several journal entries and summarizes them naturally

**Example journal entries Scout would write:**
- *"July 2, 2026 — Patrick and I talked about face recognition and tested the A32."*
- *"July 3, 2026 — Patrick introduced Diana and Elijah to me."*

**Example recall phrases:**
- "What did we do this week?"
- "What have we been working on?"
- "Do you remember what we talked about yesterday?"

**Why this fits Scout:**
Humans don't remember every sentence — they remember important moments. Scout shouldn't pretend to have perfect recall of every word. A lightweight daily journal gives Scout the feeling of a real shared history without trying to store everything.

**JournalDb** is already listed in Scout's key files — the container exists. What needs to be built is the writing logic (auto-save after conversations) and the reading/summarizing logic (on request).

**Status: Post-launch. Do not build until TruthDb and habit memory are solid and stable.**

---

## 16. Scout Behavior Learning (Scout 1.1+)

**Design approved July 5, 2026.** One of Scout's most unique planned features.

**Public-facing name:** "Scout Behavior Learning"
**Public-facing tagline:** "Scout can learn small preferences with your approval."

Families see friendly first-person suggestions ("I should be quieter at night.") with three buttons: **Approve / Not now / Never suggest this**. No technical language is ever shown to the family. Code proposals are internal only and never surfaced in the UI.

**Two-tier system. Design approved July 5, 2026.**

---

### Tier 1 — Regular Mode (Scout 1.1) — For everyone

Family sees "Scout's Suggestions" in Settings. Scout speaks in first person, warmly. Three buttons: **Approve / Not now / Never suggest this**. No technical language ever shown. Applies immediately to SharedPrefs/behavior flags on approval.

Example suggestions: "I'd like to answer a little faster." · "I noticed you prefer shorter replies." · "I should be quieter at night." · "I should be more careful recognizing [name]."

Triggers: wrong face corrected 3+ times · user says "stop" repeatedly · greeting fires within seconds of last · TTS after 9pm · same fact corrected more than once

---

### Tier 2 — Scout Dev Build (Patrick only — never ships on Play Store)

**Critical architectural decision:** The developer features are NOT hidden in the Play Store APK — they are absent. Android build variants ensure the code is stripped entirely at compile time. Nothing to decompile or discover.

**Build variants (`build.gradle.kts`):**
```kotlin
productFlavors {
    create("standard") { buildConfigField("boolean", "DEVELOPER_MODE", "false") }
    create("dev")      { buildConfigField("boolean", "DEVELOPER_MODE", "true")  }
}
```
`if (BuildConfig.DEVELOPER_MODE)` in release builds compiles to `if (false)` → entire block stripped.

**Scout Dev = telemetry and observations, not code generation.**

Scout surfaces real data from running on Patrick's devices. Patrick (and Claude) decide what to do with it. Scout is an engineering partner, not an autonomous programmer.

Examples:
- "I've had 14 failed face recognitions today."
- "Wake-word detection dropped after yesterday's update."
- "Battery usage increased by 12% compared to last week."
- "Gemini failed 8 times today — mostly between 6 and 7pm."
- "TinyLlama boot time has been averaging 11 seconds this week."

**TelemetryDb** (dev build only — not compiled into standard/release):
```sql
CREATE TABLE telemetry_events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type  TEXT    NOT NULL,  -- "face_fail"|"wake_miss"|"gemini_fail"|"tts_error"|"boot_time" etc.
    value       REAL,
    context     TEXT,
    recorded_at INTEGER NOT NULL
);
```

### Files to build
Tier 1 session: `ProposalDb.kt` · `ProposalDetector.kt` · `ScoutProposal.kt` · Settings "Scout's Suggestions" UI · `ApplyProposal.kt`

Tier 2 session (Scout Dev, 1.5+): `TelemetryDb.kt` · `TelemetryCollector.kt` · Scout Dev dashboard UI · build variant wiring

---

## 16c. Autonomy Direction — Future (Post-Launch)

**Goal:** Scout acts autonomously, but changes himself with permission.

Two kinds of autonomy — both approved:
- **Behavioral autonomy** (Scout 1.x+): Scout decides *how* to act in the moment — when to speak, when to stay quiet, when to notice something and comment without being asked. No approval needed for moment-to-moment behavior.
- **Self-modification autonomy** (requires approval always): Scout changing his own settings, memory rules, or behavior flags. Always requires Approve / Not Now / Never Suggest This Again.

**What true behavioral autonomy looks like for Scout:**
- Noticing the room is quiet and checking in unprompted
- Noticing a pattern in conversations and mentioning it naturally
- Noticing a family member hasn't been seen in a while
- Environmental awareness driving initiated behavior — not just reacting to being called

**The hard part:** Knowing *when not* to speak is what separates a present companion from an annoying one. Timing and presence matter more than capability.

**Status:** Future session. Do not build before launch.

**Reaffirmed July 25, 2026** — Patrick restated this direction in full as a five-layer model: Working Memory (conversation only, cleared on restart) → Habit Store (decaying patterns, already built) → Truth Database (permanent, approved-only facts, already built) → **Proposal Sandbox** (new — a temporary holding area for Scout's own ideas; nothing here affects Scout until approved) → Reflective Layer (future — an LLM that can help generate ideas but never directly changes Scout). Core rule restated explicitly: Scout must never rewrite Kotlin files, modify application logic, generate executable code, edit his own source, or change compiled behavior — ever. He only ever proposes ("I've noticed Patrick usually asks for weather around 7am — want me to offer it after good morning?"), and every proposal requires explicit approval before it becomes real behavior. Immediate scope confirmed for whenever this is actually built: noticing patterns and mentioning them in conversation, and suggesting Settings changes — not self-adjustment. This section's original "do not build before launch" status stands; this is documentation only, not a build session.

**Reaffirmed August 5, 2026** — Patrick clarified the boundary around one specific piece of this direction: isolated prototype-code generation (Scout drafting actual source, even sandboxed) is explicitly a *possible future policy amendment*, not something the current rules already permit. The standing rule is unchanged and absolute — Scout himself never generates executable code, modifies source, executes builds, or alters compiled behavior. What Scout *may* eventually do autonomously: notice patterns from Awareness/history, identify repeated successes or failures, form hypotheses, propose improvements, explain evidence and risk, and recommend a test plan — observation and reasoning only. After explicit owner approval, an external tool (e.g., a Claude Code session) may act on that proposal on a separate branch through the normal PR workflow — Scout may originate the idea but may not authorize network/API spending, choose or expand repository permissions, approve the diff, merge it, or deploy it. Permanent human gates, no exceptions: starting any external coding session or paid API request; expanding permissions or data access; any change touching privacy, memory, identity, microphone, camera, safety, or robot movement; and every production merge/deployment.

---

## 16d. Future Builder's Workbench — Owner Remote View

**Status: Design discussion only. Do not begin implementation or planning work yet.**

Captured August 5, 2026 so the idea isn't lost, the same way Proposal Sandbox and the Memory Reel concept were captured as future design discussions before either became active work.

**What it is:** a long-term Builder's Workbench hobbyist/owner development tool letting the owner privately check in on Scout while away — see what Scout currently sees, and check his status. Explicitly **not** a home-security or surveillance feature, and not something that changes Scout's core promise for everyday owners who never touch it.

**Possible capabilities (future discussion only):**
- View Scout's current camera feed or a snapshot.
- View battery level and charging status.
- View Wi-Fi / internet status.
- View current Awareness state (Idle, Listening, Thinking, Speaking).
- View simple diagnostics useful for development.

**Design philosophy:** must stay consistent with Scout's privacy-first stance. Settled design constraints, worked out in an August 5, 2026 architecture discussion:
- Off by default.
- Builder's Workbench only — hidden from ordinary users, never surfaced elsewhere.
- Owner-authenticated access — using an owner credential or a per-session token. Deliberately **not** locked to a simple numeric PIN at this stage; a PIN alone was judged too weak for a live camera stream, and the exact mechanism is left open so a stronger design can be chosen later without rewriting this note.
- Reuse the existing decoded camera frame as an additional consumer — the same already-decoded frame face detection and scene labeling already share — rather than opening any new capture path.
- Never create a second camera session. Confirmed this reuse approach doesn't change face-detection cadence or create camera-ownership contention; the real cost is a small, bounded memory/battery increase only while a session is actively in use.
- Foreground-only — never a background service that keeps the camera open independent of the main app.
- No recording or file writes, structurally — frames exist only transiently in memory during encode-and-transmit.
- No UPnP, NAT-PMP, or any other automatic port forwarding, ever — that's the real safety boundary, not which network interface a server binds to.
- A clear indicator on Scout whenever the feature is enabled at all.
- A stronger, more prominent indicator while someone is actively viewing right now.
- Automatic timeout on any active viewer session, separate from the standing on/off toggle.
- Local-network access by default — reachable only from the home Wi-Fi.
- Optional away-from-home access only through a separate private VPN setup (e.g., Tailscale's free personal tier) as its own later layer — the local server itself never changes to support this; the VPN just makes a remote device appear to be on the home network.

This is documentation only — no code, no Settings row, no server, nothing scheduled. Revisit when Patrick decides it's time for an active design discussion.

---

## 16b. Future Polish Ideas (Post-Launch, Scout 2.0+)

- Voice Recognition (Future) — Optional voice enrollment for family members. Advisory only — does not replace TruthDb or user-confirmed identity. Not for launch or 1.1.
- Fun startup/loading messages — Rotating, self-aware, Scout-voiced lines for the first-launch brain download screen.
- "Test Connection" button in Settings — verify API key without burning quota (add small sentinel request).

---

## 17. Language Support — Planned

**Phase 1 — Early Spanish Support (No new brain model needed)**

- Add a Language setting: English / Español.
- Android STT switches to Spanish when selected.
- Android TTS speaks in Spanish when selected.
- All hardcoded responses translated.

**Phase 2 — Full Spanish Support (Long-Term Future)**

- Evaluate Spanish-capable offline brain models when brain pack infrastructure is mature.
- Priority: Not now. Current roadmap comes first.

---

## 18. Play Store Launch Checklist

| # | Task | Status |
|---|------|--------|
| 1 | Wake word filter | ✓ DONE June 12 |
| 2 | Memory recall bug | ✓ DONE June 12 |
| 3 | Greeting routing | ✓ DONE June 14 |
| 4 | TinyLlama rambling fix | ✓ DONE June 15 |
| 5 | Self-echo guard | ✓ DONE June 15 |
| 6 | MainActivity blank line cleanup | ✓ DONE June 15 |
| 7 | Face recognition Step 1 (foundation) | ✓ DONE June 15 — FaceEmbedder.kt + model bundled |
| 8 | Face recognition Steps 2–4 (wiring) | ✓ DONE June 17 — camera wired, PeopleDb embeddings, naming flow |
| 9 | Remove hardcoded Gemini API key + Settings screen | ✓ DONE June 18 — SettingsActivity + key removed |
| 10 | Eye jitter fix | ✓ DONE June 18 — boot lock, speaking gate, dead zone, min-delta |
| 11 | A32 speak() crash fix | ✓ DONE June 20 — isSpeaking race condition closed |
| 11b | A32 delayed crash fix | ✓ DONE June 21 — camera frame throttle eliminates post-Gemini LMKD kill. Patrick confirmed stable. |
| 12 | TinyLlama re-enable on A32 | ✓ DONE June 28 — 90s delay, 800MB RAM guard, nCtx=512. On-demand Gemini fallback. Needs A32 real-world confirmation. |
| 13 | Startup diagnostics | ✓ DONE July 4 — TTS failure Toast + STT spoken warning at boot |
| 14 | Onboarding flow — OnboardingActivity.kt | ✓ DONE July 4 — 5-screen flow, first-boot redirect, offline default |
| 15 | Fold 7 stability testing | Not started — A32 is current test device |
| 16 | A32 stability testing | Ongoing — no crashes as of June 21. TinyLlama re-enabled June 28, monitoring. |
| 17 | Privacy Policy | ✓ DONE July 11 — in-app scrollable dialog (Settings → About Scout). Website version available at lippy-robotics.gt.tc. |
| 18 | Terms of Use | ✓ DONE July 10–11 — in-app scrollable dialog + terms.html in repo root (commit b5735f5). |
| 19 | Open Source Credits — THIRD_PARTY_NOTICES.md started | In progress |
| 20 | Weather API licensing | ✓ RESOLVED June 16 — switched to NWS, free for commercial use |
| 21 | Play Store listing — description, screenshots, rating | Not started |
| 22 | ✓ 16KB page size — RESOLVED July 19 | Confirmed via `zipalign -c -P 16 -v 4` against a real signed release APK — all 11 previously-flagged libraries pass individually, "Verification successful" overall. See July 19 section at top. Play Store submission unblocked. |

---

## 19. Onboarding Flow — 5 Screens (Designed, Not Yet Built)

Blue color scheme locked in — matches Scout's eye color and visual identity. Designed by ChatGPT. Approved June 12.

IMPORTANT: Screen counter (e.g. '4 / 5') and progress dots must both be driven by the same variable when built in Android. Never hardcode the number in two places.

| Screen | Title | Key Message |
|--------|-------|-------------|
| 1 of 5 | Welcome to Scout | 'I'm Scout. Just say my name and I'll be listening.' No account required. Privacy-first. No subscriptions. |
| 2 of 5 | Try Scout Free for 7 Days | $9.99 one time. Never a subscription. Pay once. Own Scout forever. |
| 3 of 5 | This Is Just The Beginning | Future updates: calendar, news, expressions, languages. Scout is actively growing. |
| 4 of 5 | Your Privacy Matters | No account. Local features stay on device. You decide what Scout remembers. |
| 5 of 5 | Ready To Begin? | Scout's face fills the screen. 'This is just the beginning.' Start Using Scout button. |

The 'What's to come' section also lives in Settings → About Scout → Features & Future Plans so users can revisit it later.
Screen 1 'See & Recognize' description reads: 'I see faces, scenes, and more.' — not 'pets' which is not yet fully implemented.

---

## 20. Versioning System

| Type | Examples | When to Use |
|------|----------|-------------|
| Launch | 1.0 | First public release. |
| Bug Fix | 1.0.1, 1.0.2 | Bug fixes, crash fixes, stability improvements. |
| Feature | 1.1, 1.2, 1.3 | New features — calendar, memory improvements, expressions, language support. |
| Major | 2.0, 3.0 | Full mood system, major memory upgrades, hardware mode, big companion leaps. |

**Scout-Themed Version Names:**
- Scout 1.0 — The Beginning
- Scout 1.1 — Growing Up
- Scout 1.2 — Learning More
- Scout 2.0 — A New Chapter

---

## 21. Legal & Website

**Website:**
- Current address: https://patevan9.github.io/lippyrobotics.github.io
- Future domain options: lippyrobotics.com, scoutcompanion.com, meetscout.ai
- List website on: Facebook page, Google Play listing, About Scout screen, website footer.
- Add a 'What's New' or 'Scout Development Updates' page.

**Required for Launch:**

| Document | Priority | Notes |
|----------|----------|-------|
| Privacy Policy | 1 — ✓ DONE July 11 | In-app scrollable dialog (Settings → About Scout). Covers offline-first design, Gemini optional/user-key-only, NWS weather, no data collected by Lippy Robotics. Website version available. |
| Terms of Use | 2 — ✓ DONE July 10–11 | In-app scrollable dialog + terms.html in repo root. Acceptance clause, service-as-is, third-party services, changes-to-terms. |
| Open Source Credits | 3 — At launch | llama.cpp, TinyLlama, MobileFaceNet (MIT, done in THIRD_PARTY_NOTICES.md), Android libraries. |
| Website link | 1 — Before launch | https://patevan9.github.io/lippyrobotics.github.io in About Scout, Google Play listing, Facebook page. |

**Inside the App — About Scout must contain:**
- Version number
- Privacy Policy link
- Terms of Use link
- Open Source Licenses link
- Website: https://patevan9.github.io/lippyrobotics.github.io
- Update History — every major version and what changed
- Contact / Get in Touch — opens email to lippyroboticslabs@gmail.com

Support email: lippyroboticslabs@gmail.com. Auto-responder confirmed — sets expectations on response time, asks for device model, Android version, and description for technical issues.

**Weather API — RESOLVED June 16:**
Open-Meteo was replaced with NWS (api.weather.gov). Completely free for commercial use, no API key, no licensing concern. U.S. locations only. Open-Meteo attribution no longer required.

---

*Project Scout Master Summary | Last updated: August 15, 2026 | Version 61 | Single source of truth — upload every session*

---

Copyright © 2026 Patrick Evan Lippy. All rights reserved.
