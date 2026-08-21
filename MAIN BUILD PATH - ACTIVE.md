Last updated: August 20, 2026
Based on commit: 52bbc1c95a02db6699d4fb8a3936a3641809a5f2
Status: Partially current — the PR #1 item below was verified stale (PR #1 merged
July 30, 2026, commit `b5b5388`) and corrected August 20, 2026. Everything else in
this document was last verified July 29, 2026 and was NOT independently re-checked
in this pass — confirm before relying on it.

# Scout — Main Build Path (Active)

What's actually being worked on right now. Completed work is removed from this document, not marked done — see `Scout_Master_Summary.md` for history. See `Architecture.md` for how the system fits together and `MainActivity Cleanup.md` for technical-debt detail behind any item referenced here.

---

## Current Priorities

1. **Restore presence-layer thresholds to production values.** Both presence moments currently ship with deliberately-lowered thresholds left over from A32 smoke-testing (see In Progress below) — they need to be confirmed working at those values on-device, then restored to their intended production numbers.
2. **Fold 7 dedicated stability testing.** All recent testing (A32 crash root-causing, presence-layer smoke tests, startup-gate testing) has been on the Galaxy A32. The Fold 7 is Scout's primary target device but hasn't had its own dedicated validation session recently.

---

## Current Blockers

- **Play Asset Delivery (PAD) wiring** — `ModelDownloadActivity` currently delivers the ~669MB TinyLlama model via Android's `DownloadManager` from a GitHub Release asset URL, which works end-to-end (this replaced an earlier unfilled-placeholder state). Wiring Play Asset Delivery as an alternative/replacement delivery mechanism for a real Play Store release is still unstarted — not urgent since the current mechanism is functional, but worth deciding on before a real store submission.
- **Play Store listing** — description, screenshots, content rating, and the privacy-policy link for the store listing itself are not yet prepared (separate from the in-app privacy policy dialog, which is done).
- **Open Source Credits screen** — `THIRD_PARTY_NOTICES.md` exists in the repo (MobileFaceNet's MIT license documented) but there's no in-app screen surfacing it yet; needed before a store launch.

---

## In Progress

- **Presence Layer smoke-test values, awaiting restoration**:
  - `ScoutPresenceDecider.IDLE_SILENCE_PRESENCE_THRESHOLD_MS` — currently ~3 minutes; production value is ~75 minutes (labeled inline with the restore value).
  - `MainActivity.MIN_GENUINE_ABSENCE_MS` — currently ~1 minute; production value is ~10 minutes (labeled inline with the restore value).
  - Diagnostic logging added for both moments (tag `ScoutPresenceDebug`) is also labeled temporary and should be removed or gated once behavior is confirmed.
- **Listening-reminder thresholds** (yaw tolerance, minimum face-height fraction, center-offset bound, sustain duration) are explicitly conservative test values, not final — tuning needs real-world evidence from an actual family conversation before they're considered settled.
- **`MainActivity.kt` decomposition** — the `brain` package split (intent routing, fact extraction, entity resolution, memory gating, prompt building already pulled out) is the intended direction; most of `MainActivity`'s remaining logic (camera, speech, presence orchestration) hasn't been extracted yet. `startCamera()` (~745 lines, six responsibilities in one function) is the single largest concrete target — see `MainActivity Cleanup.md` §1. Not started; recommended as its own dedicated session given the size and risk of behavioral regression.

---

## Parked Ideas

Deliberately not being worked on right now, either by explicit hold or because they depend on something else landing first:

- **Barge-in** (interrupting Scout mid-speech) — deliberately disabled. Caused a runaway interruption loop in earlier testing. Status: parked, not attempted again without a redesign.
- **Mike Forst (Amazon Astro) expert feedback** — contacted, responded positively, feedback still pending via email/call. When it arrives, it should inform `ScoutFaceView`'s animation timing, `ScoutPresenceDecider`'s social timing, and Scout's identity/response philosophy. Do not make changes in this area without his input in the meantime.
- **Settings robot-rename feature is disconnected from actual identity** — confirmed (not yet fixed, holding per explicit instruction): `SettingsActivity`'s rename control writes to a `robot_name` preference that nothing else reads; every actual spoken self-identification reads a separate TruthDb fact. Renaming Scout via Settings currently does nothing outside the Settings screen itself. Deliberately not fixed yet — flagged for a future decision on which one should be the source of truth.
- **OpenAI/Claude client wiring** — keys can be saved via `ApiKeySetupActivity` (encrypted correctly) but no HTTP client exists for either provider; only Gemini is wired end-to-end. Hidden from the provider picker (`Provider.isAvailable = false`) until this is built.
- **Proposal Sandbox** — a "Want me to remember that?" confirmation step before Scout commits a taught fact. Not built.
- **Permanent vs. temporary memory sorting.** Not built.
- **Caring follow-up loop.** Not built.
- **Full mood system wiring** — `ScoutFaceView` has mood-adjacent animation states already; a complete mood system driven by presence/social-battery state is still conceptual.
- **Scout news feed** — future feature, not started; neither brain currently has any live-news awareness.
- **`VoiceBank`/`Phrases` consolidation** — two parallel phrase-pool mechanisms exist (see `MainActivity Cleanup.md` §2); no pressure to unify them right now, just a known inconsistency for whenever new phrase categories are added.
- **`HabitLayer`/`ScoutPresenceDecider` as process-wide singletons** — both currently reset their in-memory state on every Activity recreation (screen rotation), unlike `LlamaEngine`/`ScoutLlamaController`. Worth doing eventually using the same pattern, not urgent since the practical impact (a lost social-battery/habit-score reset on rotation) is minor.

---

## Next Recommended Tasks

In rough priority order:

1. Confirm presence-layer behavior at current smoke-test thresholds on-device, then restore both to production values and remove/gate the temporary debug logging.
2. Dedicated Fold 7 stability session.
3. Low-risk, high-value cleanup pass: delete confirmed dead code (`ScoutDatabase.kt`, `PersonEntity.kt`, `VisionLabelFilter.kt`) and the now-unused Gradle dependencies (`androidx.room:*`, `com.squareup.okhttp3:okhttp`) — verified unused, no behavior risk. See `MainActivity Cleanup.md` §3.
4. Decide the fate of the disconnected Settings robot-rename control (fix the wiring, or remove the control) — currently misleading to a user who tries it.
5. Plan the `startCamera()` decomposition as its own dedicated session once the above is settled.
6. Prepare Play Store listing assets (description, screenshots, content rating) and the in-app Open Source Credits screen ahead of any real store submission.
