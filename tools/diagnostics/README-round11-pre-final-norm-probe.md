# Round 11 — `pre_final_norm` diagnostic probe (llama.cpp side)

Diagnostic only. Not for merge. Nothing in this directory ships in a
production build.

## What this answers

Round 9B proved the internal `t_logits` tensor is entirely `+0.0`. Round 10
proved `result_norm` (the tensor feeding the final `output.weight` matmul)
is entirely `±0.0`. That rules out `output.weight`/lm_head as the leading
suspect and narrows the fault to at-or-before the final RMSNorm.

This probe reads `inpL` — the transformer stack's final hidden state — at
the exact point where it is about to be fed into the final RMSNorm
(`build_norm(cur, model.output_norm, ...)` in `src/models/qwen2.cpp`).

- **`inpL` non-zero** — the transformer stack produced a real hidden state
  and final RMSNorm/`output_norm` destroyed it. Narrow investigation to
  that region only.
- **`inpL` all `±0`** — the transformer stack has already collapsed to zero
  upstream of the final norm. Per agreed stop rule, this ends the
  layer-by-layer tensor chase and moves the discussion to engine/runtime
  strategy rather than probing layers one at a time.

## Exact location

**`src/models/qwen2.cpp`** — one naming call:

```cpp
    cur = inpL;

    cb(cur, "pre_final_norm", -1);   // <-- added

    cur = build_norm(cur,
            model.output_norm, NULL,
            LLM_NORM_RMS, -1);
```

Confirmed by direct read of the pinned revision: `cur` and `inpL` are the
same tensor pointer at that line, and this is the only point where it is
both fully computed (the 28-layer loop has finished) and not yet touched by
the norm. `graph_get_cb()`'s `il == -1` branch (`src/llama-context.cpp`)
calls plain `ggml_set_name()`, so the tensor is named exactly
`"pre_final_norm"` with no layer suffix — confirmed unused anywhere else in
the tree before this patch.

**`src/llama-context.cpp`**, function `llama_context::decode()` — new block
inserted immediately after the Round 9B (`internal_t_logits_diag`) block
closes, and before the existing
`auto * t_embd = cparams.embeddings ? res->get_embd() : nullptr;` local.
When Round 10 (`internal_result_norm_diag`) is also applied, this same
context match anchors Round 11 after Round 10's block instead — both
diagnostic blocks share the identical triple-closing-brace shape
immediately before that `t_embd` line, so this patch applies correctly
whichever of Round 9B/Round 10 precede it.

Retrieval is by name, not by the `res->get_logits()`/`res->get_embd()`
accessors Round 9B/10 use:

```cpp
ggml_tensor * t_pre_norm = ggml_graph_get_tensor(res->get_gf(), "pre_final_norm");
```

`ggml_graph_get_tensor()` (`ggml/src/ggml.c`) linear-scans graph leafs then
nodes by `strcmp` and returns the first match.

## Decision sequence (revised after independent review)

Round 10 treated a null owning backend as "not scanned." The first draft of
this probe queried the backend but did not reject a null result before
reading. Corrected order, confirmed in the committed patch:

1. tensor not found in graph → log, do not scan.
2. tensor found but has no owning backend
   (`ggml_backend_sched_get_tensor_backend()` returns null) → log
   `ne0`/`ne1`/`type`, do not scan.
3. tensor has an owning backend but is not `GGML_TYPE_F32` → log the actual
   type, do not scan.
4. otherwise → synchronize and perform the full scan.

## Safety design (same discipline as Round 9B/10)

- `graph_compute()` dispatches via `ggml_backend_sched_graph_compute_async()`,
  so completion is **not** assumed. `ggml_backend_sched_synchronize(sched.get())`
  is called before any read, once the owning-backend and type checks pass.
- The tensor's `->data` is **not** read directly. The probe uses
  `ggml_backend_tensor_get(t_pre_norm, tmp.data(), 0, ggml_nbytes(t_pre_norm))`,
  which resolves views, dispatches through the owning buffer's accessor, and
  bounds-checks the read.
- `llama_context::synchronize()` is **not** used — it mutates
  `t_eval_us` / `t_p_eval_us` / `n_eval` / `n_p_eval` and resets
  `n_queued_tokens` / `t_compute_start_us`, corrupting the perf counters
  Scout reads via `llama_perf_context()`. `ggml_backend_sched_synchronize()`
  only waits.
- One-shot for the process lifetime, via `static std::atomic<bool>` +
  `compare_exchange_strong`.
- Gated on `n_outputs > 0`, matching Round 9B's gate — the same ubatch this
  guards `internal_t_logits_diag`'s scan.
- Owning backend checked (this round's correction) and type checked as
  `GGML_TYPE_F32` before any scan.
- Read-only throughout: nothing is written back into the graph, the ubatch,
  or any context state. Round 9B/10's blocks and the extraction below are
  unchanged.

## Risk of naming `inpL`

Checked against real source, not assumed:

- `ggml_set_name()` (`ggml/src/ggml.c`) is a bare byte-copy into the
  tensor's fixed `name[64]` buffer — no global state, no side effect.
- `graph_get_cb()`'s only name-conditional behavior is a backend-affinity
  reassignment gated on the literal string `"norm"`
  (`strcmp(name, "norm") == 0`); `"pre_final_norm"` does not match, so this
  branch never fires differently because of this patch.
- `llm_graph_result::can_reuse()` (`src/llama-graph.cpp`) — the graph-reuse
  fast path flagged as unread in earlier rounds — was read directly. It
  checks `params.allow_reuse()` and each input's own typed `can_reuse()`
  override (ubatch equality, KV/attention-mask compatibility). None
  reference `tensor->name`. Renaming cannot change a reuse decision.
- This does overwrite the name the last loop iteration's own
  `cb(cur, "l_out", il)` call gave the same tensor (otherwise
  `"l_out-<n_layer-1>"`). Nothing in llama.cpp's own decode path looks up a
  tensor by that name; Scout attaches no eval callback that would.

## Log output

Marker `internal_pre_final_norm_diag`, reaching logcat through Scout's
existing `llama_log_set()` callback. Four possible lines, one per decision
branch above. Success case:

```
internal_pre_final_norm_diag: n_elem=<N> ne0=<..> ne1=<..> type=f32 backend=<..> bytes=<..>
  pos_zero=<..> neg_zero=<..> other_finite=<..> nan=<..> pos_inf=<..> neg_inf=<..>
  min_finite=<..> max_finite=<..> sum=<..>
  raw0_7=XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX,XXXXXXXX
```

Expected for Scout/Qwen if `inpL` is healthy: `n_elem = 1536`, `ne0 = 1536`,
`ne1 = 1` — same shape as Round 10's `result_norm` (it's the same tensor,
before vs. after the norm), `backend = CPU`.

## Which libraries must be rebuilt

**`libllama.so` only** — same as Round 9B/10. The probe lives in
`src/llama-context.cpp` and `src/models/qwen2.cpp`, both of which compile
into `libllama.so`; neither contains compute kernels.

**Deliberately do NOT rebuild** `libggml-cpu-android_armv8.2_2.so`,
`libggml.so`, or `libggml-base.so` — same reasoning as every prior round:
keeping them byte-identical to the shipped copies preserves the failing
state under observation.

## Build recipe (Android arm64, pinned revision)

Identical to the Round 9B recipe — apply Round 9B, then Round 10 (once
reapplied from Patrick's saved copy), then this patch, in that order, to
the pinned tree:

```bash
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
git checkout 0f1bb602dd52d3c0c07ac29c8898f2c58c3fa9b9
git apply /path/to/llama-b8946-internal-tlogits-probe.patch
git apply /path/to/<round10-patch>
git apply /path/to/llama-b8946-pre-final-norm-probe.patch
git status            # must show exactly: src/llama-context.cpp, src/models/qwen2.cpp modified

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

### Post-build gates (unchanged from Round 9B)

1. `readelf -lW libllama.so | grep LOAD` → every segment `Align 0x4000`.
2. `readelf -d libllama.so` → NEEDED exactly `libggml.so`, `libggml-base.so`,
   `libm.so`, `libdl.so`, `libc.so`; **no `libc++_shared.so`**.
3. Import/export subset check (as in the Round 9B README) returns empty.
4. `nm -D` exported symbol set identical to the shipped `libllama.so`.
5. `strings libllama.so | grep -c internal_pre_final_norm_diag` → non-zero.

## Verification already performed (this session)

Verified against real source, not assumed:

- Cloned the pinned llama.cpp commit fresh
  (`0f1bb602dd52d3c0c07ac29c8898f2c58c3fa9b9`) and read
  `src/models/qwen2.cpp` and `src/llama-context.cpp` directly to confirm
  exact insertion points and surrounding context before writing this patch.
- Read `graph_get_cb()`, `ggml_graph_get_tensor()`, `ggml_set_name()`, and
  `llm_graph_result::can_reuse()` directly to support the risk analysis
  above, rather than assuming behavior.
- Applied the real, committed Round 9B patch
  (`llama-b8946-internal-tlogits-probe.patch`) from this repo, then this
  Round 11 patch, on top of the pristine pinned tree, in a scratch clone.
- Host (x86-64 Linux) build of the `llama` target: **configure and build
  both exit 0, zero warnings** on either touched file.
- `strings` on the built `libllama.a` confirms all four
  `internal_pre_final_norm_diag` log-format markers present, and the
  Round 9B `internal_t_logits_diag` markers remain present and unchanged.
- Reverted the scratch clone to pristine after verification — nothing was
  left applied there and nothing was pushed to any llama.cpp remote.

**Not performed, and cannot be performed in this environment:** the Android
cross-build. This container has no Android SDK/NDK, and `dl.google.com` is
denied by the environment's network policy (policy, not a transient
failure), so the NDK cannot be fetched here. The Android build, install,
and on-device test are Patrick's machine, per the established workflow.
