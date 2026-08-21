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

## How Claude Code sessions on this repo actually run

Every substantial change goes through the same gated phases — each one waits for
Patrick's explicit go-ahead before the next begins. Don't skip ahead on your own
initiative even if the "obviously correct" next step seems clear.

1. **Investigation only** — read the real code, report findings in plain language.
   No code written.
2. **Design only** — propose the smallest safe design, reusing existing mechanisms
   where possible. No code, no branch.
3. **Design revision** — incorporate correction (from Patrick or from an external
   reviewer, usually ChatGPT reading the actual GitHub diff) before anything is built.
4. **Implementation** — only after explicit "implement this" approval. New branch off
   verified `main`, one PR per fix, PR left **open and unmerged** unless explicitly
   told to merge.
5. **Merge** — only on explicit instruction, and only after re-confirming CI is green
   immediately before merging.
6. **Post-merge verification** — never trust the merge API response alone. After every
   merge, confirm via local git, not just the tool's returned JSON:
   ```
   git fetch origin main && git rev-parse origin/main            # the real new head
   git diff <reviewedHeadSha> origin/main -- <changed files>      # must be empty
   git diff <reviewedHeadSha> origin/main                         # no path filter — must also be empty
   git diff --stat <oldMainSha> origin/main                       # file scope must match the PR's own stats
   git log --first-parent --oneline <oldMainSha>..origin/main     # exactly one merge commit, nothing extra
   ```

An external reviewer independently inspects every PR's actual diff before Patrick
decides whether to merge — a session's own report of what it did is never the only
signal. Stability over features; no silent scope expansion — flag any file outside
the approved list before touching it, not after.

## Local Kotlin test harness (no Android SDK available in some sessions)

`MainActivity.kt` can't be compiled outside Android Studio/Gradle — it pulls in
Android SDK/TFLite/ML Kit. It's verified by careful diff review plus CI's real
Gradle/Android build (`.github/workflows/android-build.yml` — `assembleDebug` then
`testDebugUnitTest`).

Everything under `app/src/main/java/com/example/scoutface/brain/` (and its tests)
is deliberately Android-import-free ("pure") specifically so it CAN be compiled and
tested with nothing but `K2JVMCompiler` + Gradle's own bundled jars, no Android SDK,
no emulator, no `./gradlew` needed:

```bash
# Once per session — cache the two classpaths (adjust the Gradle version glob):
find /opt/gradle-*/lib -maxdepth 1 -iname "*.jar" | tr '\n' ':' > /tmp/compiler_cp.txt
ls /opt/gradle-*/lib/kotlin-stdlib-*.jar /opt/gradle-*/lib/junit-4.13.2.jar \
   /opt/gradle-*/lib/hamcrest-core-1.3.jar | tr '\n' ':' > /tmp/code_cp.txt

# Compile a brain-package source file + its test:
java -cp "$(cat /tmp/compiler_cp.txt)" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$(cat /tmp/code_cp.txt)" -d /tmp/build/classes \
  app/src/main/java/com/example/scoutface/brain/SomeClass.kt \
  app/src/test/java/com/example/scoutface/brain/SomeClassTest.kt

# Run it (cd into app/ first if a test reads a bundled asset via a relative path —
# Gradle's own unit-test task working directory is the module dir, so tests should
# resolve paths the same way):
cd app && java -cp "/tmp/build/classes:$(cat /tmp/code_cp.txt)" \
  org.junit.runner.JUnitCore com.example.scoutface.brain.SomeClassTest
```

Rules that keep a `brain/` class actually testable this way:
- No `android.*` imports, ever.
- No `org.json` either — it's Android-provided, not on this local classpath, and NOT
  available in `app/src/test` under real Gradle here either (this project has no
  Robolectric dependency). If a pure class needs JSON, an Android-aware caller (e.g.
  `MainActivity`) decodes it and hands the pure class a plain Kotlin structure
  instead — see `ScoutLanguagePack.kt` for the pattern.
- A test that needs to read a real bundled asset uses plain `java.io.File` with a
  path relative to `app/`, never `AssetManager`.
- Before pushing, check whether any *other, pre-existing* test file references
  something you just changed — CI has failed at least once (PR #61) from a stale
  test still using an old API that a local, narrowly-scoped compile didn't catch.

## Keeping project memory current — the real alternative to `/compact`

`/compact` summarizes *this conversation* when it runs long — necessary, but lossy
by nature. The more durable fix: keep the facts that must survive in files read
fresh from disk at the start of every session, compacted or not, new conversation or
not. This repo already has that system — it just needs to be fed after a working
session, the same way code needs a commit:

- **This file** — stable rules/conventions/workflow, read automatically every session.
- **`Scout_Master_Summary.md`** — append-only changelog, upload at the start of a new
  conversation.
- **`MAIN BUILD PATH - ACTIVE.md`** — live current-priorities/blockers, edited in
  place (not append-only).
- **`Architecture.md`** — how the system fits together today.

The gap is never the mechanism — it's staleness. If a session shipped real work
(a merged PR, a completed investigation, a new design), the single highest-value
thing to do before ending it is append a dated entry to `Scout_Master_Summary.md`
and refresh `MAIN BUILD PATH - ACTIVE.md`'s current-priorities section — not write
more code. Ask for that explicitly at the end of a session if it doesn't happen on
its own.
