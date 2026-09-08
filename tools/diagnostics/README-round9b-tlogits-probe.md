# Round 9B — internal `t_logits` diagnostic probe (llama.cpp side)

Diagnostic only. Not for merge. Nothing in this directory ships in a
production build.

## What this answers

Round 7a proved the row returned by the public `llama_get_logits_ith()` is
bit-exact all `+0.0` for Qwen2.5-1.5B-Instruct Q4_K_M (151,936/151,936
`0x00000000`). Round 9A ruled out the optimized CPU backend path: with the
official non-DOTPROD `android_armv8.0_1` backend and no tensor repacking at
all, the row was still bit-exact zero (CASE B).

This probe splits the two remaining possibilities:

- **Case 1** — the final graph tensor `t_logits` is *itself* already zero, so
  the zero originates at or before `ggml_mul_mat(output.weight, cur)`.
- **Case 2** — `t_logits` holds real values and llama.cpp's own output
  extraction/copy path produces the zero host row.

It reads `t_logits` inside `llama_context::decode()` **before** the
raw-logits extraction copies anything into the host `logits` buffer.

## Exact location

- File: `src/llama-context.cpp`
- Function: `llama_context::decode()`
- Insertion point: immediately after `auto * t_logits = res->get_logits();`
  (line 1730 at the pinned revision), inside the per-ubatch
  `do { ... } while (mctx->next());` loop, and before the
  `// extract logits` block that calls `ggml_backend_tensor_get_async()`.

The identical statement at line 1315 is in `encode()` and is deliberately
**not** touched.

## Pinned revision — do not update

```
commit 0f1bb602dd52d3c0c07ac29c8898f2c58c3fa9b9
tag    b8946
```

Scout's shipped native libraries were verified against this revision by
extracting and hashing the `.text` section of each: `libllama.so`,
`libggml.so`, `libggml-base.so` and `libggml-cpu-android_armv8.2_2.so` are
all **byte-identical in machine code** to the official
`llama-b8946-bin-android-arm64.tar.gz` assets. The SHA256 of the shipped
files differs only because they are stripped.

## Safety design (corrections carried in from Round 8B review)

- `graph_compute()` dispatches via `ggml_backend_sched_graph_compute_async()`,
  so completion is **not** assumed. The probe calls
  `ggml_backend_sched_synchronize(sched.get())` first.
- `t_logits->data` is **not** read directly. The probe uses
  `ggml_backend_tensor_get(t_logits, tmp.data(), 0, ggml_nbytes(t_logits))`,
  which resolves views (`tensor->view_src`), dispatches through the owning
  buffer's accessor, and bounds-checks the read.
- `llama_context::synchronize()` is **not** used: it mutates
  `t_eval_us` / `t_p_eval_us` / `n_eval` / `n_p_eval` and resets
  `n_queued_tokens` / `t_compute_start_us`, which would corrupt the perf
  counters Scout reads back through `llama_perf_context()`.
  `ggml_backend_sched_synchronize()` only waits; at this point
  `sched->is_alloc` is still true, so it cannot touch `sched->next_copy`
  either.
- One-shot for the process lifetime, via
  `static std::atomic<bool>` + `compare_exchange_strong`, so logs are not
  flooded.
- Guarded on `t_logits->type == GGML_TYPE_F32`; a different type is reported
  rather than misread.
- Read-only: nothing is written back into `t_logits`, the ubatch, or any
  context state, and the extraction block below it is unchanged.

## Log output

Marker `internal_t_logits_diag`, reaching logcat through Scout's existing
`llama_log_set()` callback as `[llama.cpp] internal_t_logits_diag: ...`:

```
internal_t_logits_diag: n_elem=<N> ne0=<..> ne1=<..> n_outputs=<..> bytes=<..>
  pos_zero=<..> neg_zero=<..> other_finite=<..> nan=<..> pos_inf=<..> neg_inf=<..>
  min_finite=<..> max_finite=<..> sum=<..>
  raw0_7=XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX
```

Same bit-exact methodology as Round 7a's `zero_logits_diag(round7a)`, so the
two lines are directly comparable in one run: `internal_t_logits_diag` is the
graph tensor, `zero_logits_diag(round7a)` is the host row Scout samples from.

Expected for Scout/Qwen: `n_elem = 151936`, `ne0 = 151936`, `ne1 = 1`,
`n_outputs = 1`, `bytes = 607744`.

## Which libraries must be rebuilt

**`libllama.so` only.** The probe lives in `src/llama-context.cpp`, which
compiles into `libllama.so`; that library contains no compute kernels.

**Deliberately do NOT rebuild** `libggml-cpu-android_armv8.2_2.so` (nor
`libggml.so` / `libggml-base.so`). The CPU variant carries the matmul kernels
and the others carry the allocator and scheduler; rebuilding them with a
different toolchain could change codegen and shift or mask the behaviour
under observation. Keeping them byte-identical preserves the failing state.

ABI safety of mixing a locally built `libllama.so` with the shipped ggml
libraries is checkable mechanically rather than assumed — every `ggml_*`
import must be satisfied by the shipped exports:

```
nm -D --undefined-only <new libllama.so> | grep ggml_ | awk '{print $2}' | sort -u > /tmp/imports
nm -D --defined-only app/src/main/jniLibs/arm64-v8a/libggml.so \
                     app/src/main/jniLibs/arm64-v8a/libggml-base.so \
  | awk '$2=="T"{print $3}' | sort -u > /tmp/exports
comm -23 /tmp/imports /tmp/exports    # must be empty
```

## Build recipe (Android arm64, pinned revision)

Upstream's own release job for this revision
(`.github/workflows/release.yml`, `android-arm64`) — NDK **29.0.14206865**:

```bash
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
git checkout 0f1bb602dd52d3c0c07ac29c8898f2c58c3fa9b9
git apply /path/to/llama-b8946-internal-tlogits-probe.patch
git status            # must show exactly one modified file: src/llama-context.cpp

cmake -B build \
  -DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK}/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DCMAKE_INSTALL_RPATH='$ORIGIN' \
  -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
  -DGGML_BACKEND_DL=ON \
  -DGGML_NATIVE=OFF \
  -DGGML_CPU_ALL_VARIANTS=ON \
  -DLLAMA_FATAL_WARNINGS=ON \
  -DGGML_OPENMP=OFF \
  -DLLAMA_BUILD_BORINGSSL=ON

cmake --build build --config Release --target llama -j "$(nproc)"
```

Take **only** `build/bin/libllama.so` and drop it into
`app/src/main/jniLibs/arm64-v8a/libllama.so`, replacing the shipped copy on
this diagnostic branch. Leave every other `.so` untouched.

### Post-build gates

1. `readelf -lW libllama.so | grep LOAD` → every segment `Align 0x4000`
   (16 KiB; required on the Fold 7 / Android 15).
2. `readelf -d libllama.so` → NEEDED exactly `libggml.so`, `libggml-base.so`,
   `libm.so`, `libdl.so`, `libc.so`; **no `libc++_shared.so`**.
3. The import/export subset check above returns empty.
4. `nm -D` exported symbol set identical to the shipped `libllama.so`
   (the probe adds no exports).
5. `strings libllama.so | grep -c internal_t_logits_diag` → non-zero.

## Verification already performed

The patch was applied to the pinned tree and **compiled and linked cleanly**
in a host (x86-64 Linux) build of the `llama` target — configure and build
both exit 0, no warnings on `llama-context.cpp`, and the probe's format
strings are present in the resulting binary. That validates the patch as C++
against the real pinned headers.

It does **not** substitute for the Android build: the host artifact is
x86-64 and is not shippable. The Android cross-build could not be performed
in the Claude Code container because `dl.google.com` is denied by the
environment's network policy, so the NDK cannot be fetched there.
