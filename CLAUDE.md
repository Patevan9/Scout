# Scout — Claude Session Notes

## Git branch
All active development happens on: `claude/test-coverage-analysis-hsp9lt`

**Always give Patrick the full pull command — he finds it easy to forget the branch name:**
```
git pull origin claude/test-coverage-analysis-hsp9lt
```

When pushing, always use:
```
git push -u origin claude/test-coverage-analysis-hsp9lt
```

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
- `Scout_Master_Summary.md` — full project history and architecture (upload to new sessions)
- `Scout_Launch_Checklist.md` — what's done and what's next for Play Store launch
- `Scout_Quick_Start.md` — quick reference

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
