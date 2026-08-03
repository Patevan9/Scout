# Scout Awareness Layer — Design Specification

> Status: **Target architecture, not current implementation.** This document describes where Scout's awareness is designed to go, not what exists in the codebase today. Nothing here is built. `SettingsActivity`, `MainActivity`, `ScoutPresenceDecider`, `ScoutCompanionMomentsEngine`, `HabitLayer`, and every other file referenced here remain exactly as they are — a status column noting something "exists today" describes the current codebase, not a claim that Awareness itself is running. No implementation branch should open against this document without an explicit decision to do so. This is the canonical design reference for that future work.

## Guiding Principles

- Sensors observe.
- Awareness understands.
- Behavior decides.
- Awareness is truthful, not predictive.
- Silence remains the default.
- Prefer the smallest safe migration over architectural rewrites.

Sensors answer "what just happened" and have no opinion about it. Awareness answers "what does Scout currently know about the world" — resolved, named, still with no opinion about whether anything is worth acting on. Behavior answers "should I do anything about it" — and is the only layer allowed to decide that, using restraint gates and cooldowns exactly as Presence and Companion Moments already do today.

No layer is allowed to do another layer's job. A sensor must never decide something is worth mentioning. Awareness must never assert something it lacks evidence for — it reports what was observed, never what's likely or probable beyond what the evidence actually supports. Behavior must never reach past Awareness to read a raw sensor directly. And at every layer, silence stays the default outcome unless something actively earns a response — exactly as it already works in Presence and Companion Moments today.

---

## Non-goals for Phase 1

Consolidated in one place, even though each is also mentioned in context elsewhere, specifically so Phase 1's scope can't drift wider by accident. **None of the following change, move, or get touched during Phase 1:**

- Presence's existing detection logic — not migrated, not modified
- Companion Moments' existing detection logic — not migrated, not modified
- Speech routing / recognition pipeline — no changes
- Microphone behavior — no changes, no RMS-baseline work
- `HabitLayer` — not extended, not fed any new event categories
- Automatic promotion into durable memory (`TruthDb`/`PeopleDb`) — never automatic, not built at all
- Proposal Sandbox / self-reflection — not built, not connected to anything here
- Pickup / motion detection — remains its own separately-designed system, not folded in during Phase 1
- The `UNFAMILIAR` entity classification — not implemented, no calibration work started
- Direct-address confidence tiering — fully specified in the Appendix, not built
- A Courtesy layer — not part of this spec, not built

If a change during implementation seems to require touching anything on this list, that's a signal to stop and revisit the spec, not to make the exception quietly.

---

## 1. Raw Sensor Inputs

Sensors are dumb. They emit what happened, at whatever cadence their underlying API delivers, with zero interpretation.

| Sensor | Emits | Status |
|---|---|---|
| Camera / ML Kit face detection | Per-frame face boxes, yaw, position, size | Exists today — currently read directly by `MainActivity` and `ScoutPresenceDecider` independently |
| Camera / ML Kit scene labeling | Per-frame generic labels + confidence ("dog," "chair," "person") | Exists today — throttled to a 1.5s interval, stored as `lastSceneLabels` |
| `PeopleDb` face-embedding match | Winning identity + winning score only | Exists today (`findBestMatchNameWithScore`) — verified the actual return type is `Pair<String, Float>?`. Margin over the runner-up is checked *internally* before returning and is never exposed to the caller: a thin-margin near-miss and a clean below-threshold miss both simply return `null`, indistinguishable from each other today. Any future signal that needs the margin itself, not just pass/fail, requires new plumbing. |
| Speech recognizer | Recognized text, per-callback RMS level | Exists today — RMS currently drives only a visual meter, not an ambient-baseline comparison |
| Wake-word/name matcher | Exact vs. fuzzy-distance classification | Exists today (`FuzzyNameMatcher`) but currently collapsed to a boolean (`hearsHisName`) — needs to expose *which* tier matched, not just yes/no |
| Open-conversation-window state | Elapsed time since Scout's last utterance / presence reply window | Exists today (`CONVO_WINDOW_MS`, `presenceReplyWindowUntilMs`) |
| `BatteryManager` broadcast | Charging state, battery level | Does not exist today — new, cheap, no permission required |
| `ConnectivityManager` | General online/offline | Exists today (`ScoutConnectivityManager`) for Gemini fallback. Phase 1 reuses this exactly as it is — general online/offline only. A Wi-Fi-specific distinction would require extending the manager and is explicitly not promised for Phase 1 (see §3, §9). |
| Ambient brightness | Light level | Does not exist today. Recommend deriving an approximate bucket from camera frames already being captured, rather than a dedicated light-sensor dependency — needs on-device validation before trusting it, not assumed to work from theory. **Conditional for Phase 1** — see §3. |
| Motion (accelerometer/gravity) | Pickup/set-down state machine | Not yet built — already speced separately (RESTING/SUSPECTED_LIFT/CONFIRMED_LIFT). That design's own state machine *is* the sensor-to-Awareness resolver for physical state; it plugs into this layer once built, rather than remaining its own bespoke system |

---

## 2. Live Awareness State

The in-memory, continuously-overwritten snapshot of "what's true right now." Cheap to hold, produces zero writes on its own, never itself a trigger for anything.

- **Presence**: the set of currently-perceived entities, each tagged `KNOWN` / `UNRESOLVED` / `UNKNOWN` (see §6 on why `UNFAMILIAR` is deliberately excluded from this set for now), each with a gap-tolerant "present since" timestamp (reusing the same 2-minute grace window already proven in Presence's own streak tracker).
- **Orientation**: for each perceived person, whether they currently appear oriented toward Scout (generalizes the existing yaw/face-height/center-offset gate, today scoped only to the listening-reminder feature).
- **Direct-address evidence tier**: for the most recent utterance, which tier applied — exact name / strong fuzzy match / active conversation window / neither. A per-utterance evaluation, not continuously-held state. **Explicitly out of scope for Phase 1** — see §9.
- **Physical state**: resting / held / moving. Absent until pickup detection is built.
- **Environmental state**: connectivity (online/offline, later Wi-Fi-specific), charging state, brightness bucket. Absent until Phase 1 lands.
- **Time-of-day mode**: already exists; becomes a read from Awareness instead of independently computed by each consumer.

---

## 3. Edge-Triggered Awareness Events

The only category ever written anywhere. A continuous per-frame state never produces a write — only a *transition* does. One row per edge, never per frame.

**Phase 1 required events** — the lowest-risk proof, both reusing sensors that already exist with zero extension:
- charging started / charging stopped
- connectivity lost / restored (general online/offline, via `ScoutConnectivityManager` exactly as it exists today — not Wi-Fi-specific; see §1)

**Phase 1 optional event**, included only if it clears its own bar:
- brightness crossed into a different bucket — included **only if** it can be sampled without changing camera cadence, ownership, or any existing vision behavior (face detection, scene labeling, the listening-reminder gate). If deriving brightness from camera frames turns out to require any change to how or when frames are captured, brightness is deferred past Phase 1 rather than accepted at that cost. Charging and connectivity alone are sufficient to prove the pattern.

**Explicitly deferred past Phase 1** (each needs either new classification work, calibrated confidence work, or touches the speech pipeline):
- presence began/ended, known-entity-encounter began/ended — needs the entity vocabulary and orientation resolution proven first
- pickup confirmed / set down confirmed — depends on the separately-designed motion state machine actually being built
- greeting / thanks / goodbye detected — touches speech routing
- direct-address confidence — last of all, no microphone changes yet
- unfamiliar-pet/person, rain/sunset, quiet-house-duration — each flagged earlier as a bigger lift than the rest

Each event is a small, named fact — category, what happened, timestamp, and (only when genuinely resolved) which entity it concerns. Never raw sensor payloads, never speech text.

---

## 4. Rolling History

- A **new, dedicated local store** — physically separate from both `JournalDb` (wrong: durable narrative purpose, no retention policy — verified by reading the actual schema, not assumed) and `DiagnosticDb` (wrong: exportable via Share Diagnostic Report, and Awareness history will contain real names).
- Schema mirrors `DiagnosticDb`'s shape (timestamp, category, detail, optional resolved-entity reference) — resolved events only, never raw sensor values.
- Write pattern: edge-triggered only, exactly matching §3.
- Retention: same dual mechanism `DiagnosticDb` already proves works — a time-based cutoff plus a hard row-count ceiling, purged on first write per session.
  - Time cutoff: 7 days, reused directly from `DiagnosticDb`'s existing, already-shipped constant — not a fresh guess.
  - Count ceiling: **not chosen in advance.** Phase 1 includes a logging-only trial on the A32, with nothing yet reading from the store, specifically to measure real daily event volume before a final number is picked. Same discipline already applied to the pickup-detection design's own validation phase.
- No automatic promotion path. Nothing in Phase 1 writes from this store into `HabitLayer` or durable memory. Promotion, if it ever happens, is a deliberate and separate later decision.

---

## 5. Behavior Consumers

Anything that reads Awareness (live state and/or history) and decides whether to act — never touches a sensor directly, never bypasses Awareness's resolution.

**Phase 1 has zero consumers.** Nothing reads from Awareness yet, by design — the first phase proves sensors can publish and history can be written safely before anything downstream depends on it.

Anticipated future consumers, each keeping its own existing restraint gates, cooldowns, and budgets exactly as they work today — Awareness only changes *where they get their facts*, never their decision logic:
- Presence (idle-silence, return greeting)
- Companion Moments
- A future direct-address confidence gate for the speech pipeline — last, after every other phase is stable

---

## 6. Durable Memory

Unchanged. `TruthDb` (facts) and `PeopleDb` (identities/embeddings) remain the only durable stores. Awareness — live or historical — never writes to either automatically. Promotion from a noticed pattern into something Scout permanently knows is always a deliberate act (an explicit teaching moment, or, much later, an approved Proposal Sandbox suggestion) — never a background side effect of Awareness accumulating data.

**On `UNFAMILIAR`**: the entity vocabulary is `KNOWN` / `UNRESOLVED` / `UNKNOWN` for Phase 1. `UNFAMILIAR` requires calibrated evidence that an entity is confidently *dissimilar* from every known profile — not merely a failed match. Today's matching code (`findBestMatchName`) only reports whether a match cleared the confidence bar, not whether a non-match is a genuine negative signal. Building real `UNFAMILIAR` detection needs its own calibration work against real embedding-distance data before it's trustworthy — deferred, not because it's a bad idea, but because it isn't ready.

---

## 7. Privacy Boundaries

- Rolling history is never reachable from the Diagnostic Report / Share flow — enforced structurally, by being a physically separate store that `DiagReportActivity`/`DiagnosticDb` never reference, not by a runtime filter that could be bypassed by a future edit.
- Rolling history **may** contain resolved entity names ("Patrick," "Nicolas") — this is a deliberate contrast with `DiagnosticDb`'s stricter no-names rule. That rule exists *because* `DiagnosticDb` is exportable; Awareness history is not, and never should be. A future contributor should not assume one store's privacy rules apply to the other.
- No raw sensor data — frames, embeddings, audio — is ever persisted at any Awareness tier. Only resolved, named facts and events, matching the same controlled-vocabulary discipline `DiagLog` already holds today.
- This is on-device-only processing, never uploaded or exported, consistent with what Settings' Privacy & Data screen already tells the owner. That said, **the actual Privacy Policy text must be reviewed before release, not assumed sufficient as written.** The policy predates this design — it was never written with a rolling, week-long local history of household events and resolved names in mind, even one that stays entirely on-device. "On-device-only" describes the mechanism; whether the existing policy's wording already covers a new local store like this, or needs an explicit line added, is a real question that needs a deliberate answer, not an assumption made in this spec.

---

## 8. Retention

- **Live Awareness state**: not persisted at all. Exists only in memory, gone on process death, rebuilt from nothing on next launch — matching the "observe before arming" calibration pattern already designed for pickup detection.
- **Rolling history**: 7-day time cutoff (reused precedent) + an empirically-sized count ceiling (measured, not guessed — see §4).
- **Durable memory**: unchanged, governed entirely by existing `TruthDb`/`PeopleDb` rules.

---

## 9. Phased Migration

**Phase 1 — this spec's actual scope:**
- Build sensor → Awareness resolution for the cheapest, lowest-risk signals only: charging state and connectivity (general online/offline) are required. Brightness is included only if it clears the no-camera-impact bar in §3 — charging and connectivity alone are sufficient to prove the pattern if it doesn't.
- Stand up the new rolling-history store with `DiagnosticDb`-modeled retention, logging-only, zero consumers reading from it.
- Run an on-device, logging-only A32 trial to confirm the resolution behaves correctly and to measure real event volume for sizing the count cap.
- **Explicitly not in Phase 1**: see "Non-goals for Phase 1" above — full list, not repeated here.

Phase 1 is judged complete only once the A32 trial has produced real event-volume data and the rolling-history store's retention cap has been sized from it, per §4.

---

## 10. Future Phases (Placeholder)

Not designed yet — named here only so each can be added as its own subsection later without restructuring this document. Nothing below is committed to, sequenced precisely, or scoped in detail; that happens when each phase's turn actually comes.

- **Presence migration** — the first real migration candidate. Presence's own idle-silence/streak detection is arguably already doing Awareness-shaped work today, just locally; migrating it to read from Awareness rather than maintaining independent camera state is the natural first step once Phase 1 is stable.
- **Pickup / motion detection** — already has its own separate design (RESTING/SUSPECTED_LIFT/CONFIRMED_LIFT), not repeated here. Joins as an Awareness event source once built.
- **Direct-address awareness** — fully specified below in the Appendix. Last in sequence, after every earlier phase is stable, no exceptions.
- **`UNFAMILIAR` entity classification** — only once calibrated non-match evidence exists (see §6).
- **Richer awareness signals** — the categories flagged as bigger lifts throughout this document: presence/encounter events, greetings, unfamiliar-pet/person detection, rain/sunset, quiet-house-duration.
- **HabitLayer integration** — evaluated only with real Phase 1 history data in hand, and only for event categories that actually fit its existing decaying person/topic/time model rather than forcing new concepts into it (see §6, §9).
- **Courtesy layer** — mentioned in earlier design discussion, not part of this spec.
- **Self-reflection / Proposal Sandbox foundation** — Scout reasoning from his own accumulated history rather than raw sensors, the long-term direction this whole layer exists to support. Depends on the rolling-history tier (§4) having real data to reason from first.

---

## Appendix: Direct-Address Confidence (Future Phase — Not Built Now)

Captured here in full so this design isn't lost by the time its phase is reached. This is the **last** thing in the Awareness rollout — built only after the Phase 1 foundation (charging, connectivity, optionally brightness, rolling history) is proven stable on the A32. No microphone or speech-routing changes of any kind until then.

**Tier-first, not additive.** Confidence is not one global score summing every signal. The evidence tier is determined first; vision and speech-prominence signals corroborate within a tier, they never establish one on their own except at the lowest tier, where they're all there is (see the Tier 2/3 clarification below).

| Tier | Evidence | Confidence |
|---|---|---|
| 1 | Exact configured name detected | Near-certain |
| 2 | Strong fuzzy/known STT variation | High, not absolute |
| 3 | Active conversation-window follow-up | Medium-high |
| 4 | No name, no active window | Graded — vision + speech-prominence signals are the entire basis for confidence, not just corroboration |

An exact name match (Tier 1) must never be weakened by absent vision — someone speaking from behind Scout or outside the frame must not be penalized for it. Tier 1 alone is already sufficient; nothing else is consulted.

**Clarification on Tier 2 and Tier 3**: an earlier pass of this appendix stated vision/audio apply "only" within Tier 4 — that's stricter than what was actually agreed, and contradicts Tier 2's own "not absolute" qualifier. The corrected rule: vision and speech-prominence signals *may corroborate* Tier 2 (strong fuzzy match) and Tier 3 (active conversation window), nudging confidence within that tier's own band — never below its own floor, and never penalized by the *absence* of corroborating evidence, the same non-penalty principle Tier 1 gets. What's actually unique to Tier 4 is that it has no name-based signal to fall back on at all — vision and audio aren't just corroborating there, they're the entire basis for whatever confidence exists.

**Two distinct signals (usable as Tier 2/3 corroboration or as Tier 4's primary evidence), never merged into one "closeness" signal:**
- **Face-height-fraction** — a rough proximity proxy only.
- **RMS relative to an ambient baseline** — whether speech stands out from the room's normal noise floor, not distance. Android's automatic gain control makes raw loudness unreliable as a distance measure.

Neither proves distance or speaker identity; neither substitutes for the other.

**Multi-person vision fact, precisely bounded.** A single camera can support "multiple people present, neither oriented toward Scout." It cannot support any claim about whether they're speaking to each other. Awareness records only the bounded fact — nothing about their relationship.

**Clarification prompt ("were you speaking to me?") — every condition required, not any single one:**
- A real, unresolved utterance was actually captured. Never triggered by ambient noise, RMS, or vision alone with nothing said.
- Directedness confidence for that utterance landed in the ambiguous band.
- Suppressed by the multi-person/no-orientation fact above when it applies — a likely ongoing conversation between others is a more probable explanation than an unresolved address to Scout.
- Its own dedicated cooldown, checked independently of how often the ambiguous condition recurs — same discipline `ScoutSpeechAvailabilityMonitor`'s existing 10-minute cooldown already proves works for a rare spoken warning.

---

## Closing

None of this is about making Scout talk more. It's about making sure that when Scout does decide to say something, it traces back to something he genuinely observed — never a sensor guessing on Awareness's behalf, never Awareness guessing on Behavior's behalf, never Behavior inventing a signal that was never really there.

---

Copyright © 2026 Patrick Evan Lippy. All rights reserved.
