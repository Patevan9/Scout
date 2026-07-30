# Scout — Claude Session Notes

## Git branch
`main` is the source of truth — current stable code always lives there.

Workflow:
- **`main`** = current stable code.
- **Feature branches** = temporary work only. Branch off `main`, do the work, open a PR, merge back into `main`.
- **Delete branches after they're merged.** Don't let merged or stale branches accumulate — if in doubt whether a branch has unique commits left, check with `git log origin/main..origin/<branch> --oneline` before deleting (empty output = safe to delete).

**Always give Patrick the full pull command:**
```
git pull origin main
```

Starting new work:
```
git checkout main
git pull origin main
git checkout -b <short-descriptive-branch-name>
```

Pushing a feature branch:
```
git push -u origin <branch-name>
```

`claude/test-coverage-analysis-hsp9lt` was the long-lived active-development branch through the memory/entity system, presence layer, and security-hardening work — merged into `main` July 29, 2026 (PR #1) and deleted. Any reference to it elsewhere in older docs describes history, not the current branch to develop on.

## Building
Android Studio only — Build → Clean Project, then Build → Assemble Project.
`gradlew` does not work (JAVA_HOME error on Patrick's machine).

## Critical rules — never break these
- Scout's name must never be hardcoded in spoken responses. Always read it at runtime:
  `truthDb.getFactValue(ENTITY_SCOUT, FactKey.NAME) ?: "Scout"`
- Family member names (Patrick, Diana, Elijah, Nicolas) must never appear as string literals
  in any spoken response or TTS output.

## Architecture quick reference
- **Face recognition**: ArcFace MobileFaceNet, 512-dim, L2-normalized, threshold 0.65f
- **PeopleDb v4**: Two embedding stores — `people.embedding` (single BLOB, used by `findBestMatch`)
  and `person_embeddings` table (up to 12 per name, used by `findBestMatchName`)
- **EMBED_INTERVAL_MS = 2000**: embeddings throttled to once every 2 seconds
- **Phrases.kt**: Anti-repeat rolling window phrase pools (in-memory). `VoiceBank.say()` is a
  separate anti-repeat system via SharedPreferences. Both coexist.
- **ScoutBootStatus.kt**: Adaptive boot — `lastLlamaLoadMs in 1L..2000L` → BOOT_OFFLINE_FAST

## Test devices
- Samsung Galaxy A32 (primary active testing device)
- Samsung Galaxy Fold 7 (listed as primary, less frequently used)

## Master docs
- `Scout_Master_Summary.md` — full project history/changelog (upload to new sessions)
- `Architecture.md` — how Scout is built today; read this to understand the system without reading the code first
- `MAIN BUILD PATH - ACTIVE.md` — current priorities, blockers, in-progress work, parked ideas (live status, not history)
- `MainActivity Cleanup.md` — known technical debt and refactoring targets
- `Scout_Launch_Checklist.md` — what's done and what's next for Play Store launch
- `Scout_Quick_Start.md` — quick reference

The first four carry a header (`Last updated` / `Based on commit` / `Status`) — check it before trusting the content as current. `Scout_Master_Summary.md` is an append-only changelog (never delete history); the other three are live documents that get edited in place as things change or ship.

## Scout development philosophy — do not violate
- **TinyLlama is the primary brain.** Scout works fully offline. Gemini is an optional enhancement.
- **Scout never surprises the user.** He may notice patterns and suggest improvements, but every
  meaningful change requires explicit user approval (Approve / Not Now / Never Suggest This Again).
- **Public Scout (Play Store):** Behavior suggestions only — "I'd like to answer a little faster."
  No technical language. No code. No silent self-modification. SharedPreferences updates on approval only.
- **Scout Dev (Patrick's build only):** Telemetry and observations — face recognition failures,
  wake-word accuracy, battery trends, TinyLlama load times, Gemini failures. NOT in the Play Store
  APK — absent from the compiled release, not hidden. Build variant `dev` only.
  Scout Dev reports observations. Patrick and Claude decide the fixes.
- **Launch priority order:** Stability → Fold 7 / A32 testing → Play Store compliance
  (16KB libraries, Privacy Policy, Terms, Open Source notices) → Website → Launch.
  Behavior Learning and Scout Dev are post-launch. Do not add feature bloat before launch.
