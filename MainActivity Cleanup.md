Last updated: July 29, 2026
Based on commit: e6c2e829248035c5b01482e139bb1215549f381e
Status: Current

# MainActivity Cleanup

Technical debt and refactoring targets, verified directly against the current codebase (not carried over from older notes). Scope is `MainActivity.kt` primarily, plus closely-related files where a cleanup finding is really about duplication/dead code touching MainActivity's own responsibilities. This is a punch list, not a changelog — completed items are removed, not marked done. See `Architecture.md` for how these pieces fit together and `Scout_Master_Summary.md` for what's already shipped.

`MainActivity.kt` is currently **4,923 lines** — by far the largest file in the codebase (the next-largest, `ScoutFaceView.kt`, is 1,439). It has real internal section markers (`ONLINE/GEMINI`, `EYE MODE GATING`, `CAMERA`, `SPEECH`, `BRAIN-FIRST: INTENTS + TEACHING`, etc.) so it isn't disorganized, but several individual functions have grown far beyond what a single function should hold.

---

## 1. Large methods — the primary refactoring target

| Method | Approx. lines | What it does |
|---|---|---|
| `startCamera()` | **~745** (1839–2584) | Camera setup, the entire `ImageAnalysis` frame-analysis callback: throttling, face detection, the direct-address/listening-reminder gate, face-crop + embedding + `PeopleDb` matching, scene labeling, presence/absence tracking. All one function via deeply nested lambdas. |
| `setupSpeech()` | ~300 (2620–2920) | `SpeechRecognizer` construction and its full `RecognitionListener` callback set. |
| `handleTeaching()` | ~200 (4507–4707) | Routes a statement through `ScoutFactExtractor`/`TeachExtractor`, confirms what was learned, handles the "unrecognized teaching" fallback. |
| `tryTinyLlamaOrFallback()` | ~167 (3663–3830) | Builds the TinyLlama grounding prompt and dispatches generation. |
| `handleQuery()` | ~119 (4379–4498) | Top-level intent dispatch — the single largest `when`/if-chain routing point. |
| `maybeStartListening()` | ~116 (2992–3108) | The listening-session gatekeeper (permission/state/cooldown checks). |
| `shutdownSystems()` | ~142 (922–1064) | Teardown on `onDestroy()`. |

**`startCamera()` is the standout target.** At 745 lines it is by far the largest function in the codebase and mixes at least six distinct responsibilities in one nested-lambda callback: camera/analyzer setup, per-frame throttling, face detection + the vision-led direct-address gate, face-crop/rotate/embed + `PeopleDb` matching (with sensor-coordinate math inline), scene labeling, and presence/absence streak tracking. None of these sub-responsibilities need to share local scope with the others except the analyzed `ImageProxy`/bitmap itself. Splitting the analyzer body into named private methods (e.g. `processFaceDetection(bitmap, faces)`, `processEmbedding(...)`, `processSceneLabels(...)`, `updatePresenceTracking(...)`), each taking only what it needs, would make this reviewable and testable without changing behavior. This is a substantial refactor — recommend doing it as its own dedicated session with careful before/after behavioral testing, not mixed into an unrelated feature change.

---

## 2. Duplicate logic

- **`TeachExtractor.kt`'s wife/son/dog regex blocks are near-identical copy-paste** — each relation (wife, son, dog) repeats the same five-or-so regex shapes (`"my X's name is Y"`, `"my X name is Y"`, `"this is my X Y"`, `"that is my X, Y"`, `"that person is my X, Y"`) with only the relation word and `FactKey` constant changed. A data-driven version — a list of `(relationWord, FactKey)` pairs run through one shared regex-builder function — would cut this to a fraction of its current size and make adding a new relation (e.g. "daughter" as its own key rather than folding into `SON_NAME`) a one-line change instead of five new regexes. Not urgent, but a clear win whenever this file is touched next.
- **`VisionUtils.keepVisionLabel()` and `VisionLabelFilter.isUseful()` are the same function, twice**, in two different files/packages (`com.example.scoutface` and `com.example.scoutface.vision`) with identical bad-label sets. Only `VisionUtils.keepVisionLabel()` is actually called anywhere (`MainActivity.kt:1973`). `VisionLabelFilter.kt` is entirely unused — see Dead Code below.
- **Two parallel phrase-pool systems**: `VoiceBank.say()` (an older hardcoded `when` block per intent, simple "don't repeat the immediately-previous pick" logic) and `Phrases.pick()`/`pickNamed()` (a newer, generic pool + cooldown-window mechanism used for boot/remember/goodbye phrases). They're not bugs — both work — but having two different phrase-selection mechanisms with different repeat-avoidance strength is a real inconsistency. Newer phrase categories should probably be added to `Phrases`; migrating `VoiceBank`'s existing categories over is a reasonable future cleanup, not urgent.

---

## 3. Dead code

- **`ScoutDatabase.kt` + `PersonEntity.kt`** — a complete, unused Room-based database (`@Database`, `@Dao`, `@Entity`, table `people_memory` in `scout_brain.db`). Verified via repo-wide grep: nothing outside these two files references `ScoutDatabase`, `PersonDao`, or `PersonEntity`. Superseded entirely by `PeopleDb` (raw `SQLiteOpenHelper`). Safe to delete both files — and once deleted, the `androidx.room:room-runtime`/`room-ktx` Gradle dependencies become unused too (nothing else in the codebase imports `androidx.room`), so they can be removed from `app/build.gradle.kts` at the same time.
- **`VisionLabelFilter.kt`** — unused duplicate of `VisionUtils.keepVisionLabel()` (see Duplicate Logic above). Safe to delete.
- **`com.squareup.okhttp3:okhttp` Gradle dependency** — declared in `app/build.gradle.kts` but never imported anywhere in the codebase. Both `GeminiClient` and `ScoutWeatherManager` use raw `HttpURLConnection` for their HTTP calls. Safe to remove from the build file.
- **`browAsym` in `ScoutFaceView.drawBrow()`** — computed (`val browAsym = if (side < 0) 30f else 4f`) but never referenced afterward in the function. Confirmed still present. Harmless (dead local, not a behavior bug) but worth removing next time this function is touched.

---

## 4. Stale/inaccurate comments

Not bugs, but worth fixing since they actively mislead a reader about the current state of the system:

- `DiagReportActivity.kt`'s class doc says *"Not yet registered in the manifest (Step 5)."* It is registered (`AndroidManifest.xml` has a `DiagReportActivity` `<activity>` entry) and has been reachable from Settings' Diagnostics section for some time.
- `DiagLog.kt`'s class doc says *"Not wired into any existing file yet."* It's extensively wired throughout `MainActivity.kt` (boot, listen attempts, speech results, routing, brain-started events, Gemini decisions, TinyLlama lifecycle, network, errors) and into `ScoutGeminiManager`, `LlamaBenchmarkActivity`, and others.
- `ModelDownloadActivity.kt`'s startup-gate comment history versus `Scout_Master_Summary.md` — already reconciled as of this update (see the Master Summary's new July 19(cont.)–25 entry); flagging here only so a future pass double-checks no other file's comments still describe the pre-gate, single-purpose version of `ModelDownloadActivity`.

---

## 5. Other code-quality notes

- **`ScoutPresenceDecider` uses `Log.e()` for routine, non-error state changes** (`onConversationTurn()`'s battery-update log, `rechargeIfNeeded()`'s recharge log) — these are normal operational events, not errors. Low priority, but `Log.e` here will make error-level logcat filtering noisy and slightly misleading during real debugging.
- **`MainActivity.kt` has a blank line after most individual statements/declarations throughout the file** (visible in nearly every excerpt read during this audit) — not broken, but it roughly doubles the file's apparent length versus its actual code density, making the "4,923 lines" figure overstate how much logic is really there and making the file slower to scan/scroll than a more conventionally-formatted file of similar real complexity would be. A dedicated formatting pass (no logic change) would make the large-method problem above easier to see and fix.

---

## 6. Temporary/smoke-test values still in production code

These aren't cleanup targets in the traditional sense (they're deliberate, labeled temporary values from recent on-device testing), but they represent code that is *known* to need a follow-up change before it's considered finished. Tracked in full in `MAIN BUILD PATH - ACTIVE.md`; flagged here because they live inside `MainActivity.kt`/`ScoutPresenceDecider.kt` and a future contributor cleaning up either file should not "fix" them without checking that document first — they're intentional, not oversights:
- `MainActivity.MIN_GENUINE_ABSENCE_MS` — currently ~1 minute (smoke-test value), production value is ~10 minutes.
- `ScoutPresenceDecider.IDLE_SILENCE_PRESENCE_THRESHOLD_MS` — currently ~3 minutes (smoke-test value), production value is ~75 minutes.
- Listening-reminder yaw/face-height/center-offset thresholds — explicitly labeled "conservative test values, not final" pending real-world tuning evidence.
