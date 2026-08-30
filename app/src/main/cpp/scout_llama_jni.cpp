// scout_llama_jni.cpp — step-debug build
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdint>
#include <cstdlib>
#include <cmath>
#include <cstdio>
#include <cerrno>
#include <dlfcn.h>
#include <algorithm>
#include <chrono>

#include "scout_llama_api.h"

#define TAG  "ScoutLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct ScoutModel {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
};

static std::string getLibDir() {
    Dl_info info;
    memset(&info, 0, sizeof(info));
    if (dladdr((void*)llama_backend_init, &info) && info.dli_fname) {
        std::string path(info.dli_fname);
        size_t slash = path.rfind('/');
        if (slash != std::string::npos) return path.substr(0, slash);
    }
    return "";
}

static void llamaLog(int level, const char* text, void*) {
    if (!text || !text[0]) return;
    std::string msg(text);
    while (!msg.empty() && (msg.back()=='\n' || msg.back()=='\r')) msg.pop_back();
    if (msg.empty()) return;
    if (level >= 2) LOGE("[llama.cpp] %s", msg.c_str());
    else            LOGI("[llama.cpp] %s", msg.c_str());
}

static std::string jstringToStd(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string result(cstr ? cstr : "");
    env->ReleaseStringUTFChars(jstr, cstr);
    return result;
}

// ── Shared generation core ──────────────────────────────────────────────
// Used by both the production nativeGenerate() path and the dev-only
// benchmark path below, so the two can never silently drift apart. Every
// numeric value (n_ctx, n_batch, n_threads) is supplied explicitly by the
// caller -- the production call site passes the exact literals it has
// always used (2048/512/2), so extracting this changes nothing about
// production behavior. nThreadsBatchOverride of 0 means "leave
// n_threads_batch at whatever llama_context_default_params() fills in",
// which is what production does; only the benchmark path passes a
// non-zero override.
struct GenResult {
    std::string text;
    int    n_prompt      = 0;
    int    n_generated   = 0;
    double prefill_ms    = 0.0;
    double ttft_ms       = 0.0;
    double gen_ms        = 0.0;
    double total_ms      = 0.0;
    int    n_threads       = 0;
    int    n_threads_batch = 0;
    int    n_ctx            = 0;
    // Every call below frees and recreates the context from scratch --
    // there is no KV-cache reuse across calls yet, so this is always false.
    // The field exists so a future reuse change has somewhere honest to
    // report it, rather than instrumentation having to be added at the
    // same time as the reuse logic itself.
    bool   ctx_reused = false;
    bool   ok = false;
};

static GenResult runGeneration(
        ScoutModel* sm, const std::string& prompt,
        int nPredict, float temp,
        int nCtx, int nBatch, int nThreads, int nThreadsBatchOverride)
{
    using clock = std::chrono::steady_clock;
    GenResult r;
    auto t_call_start = clock::now();

    const int kNBatch = nBatch;   // must match cparams.n_batch below -- llama_decode()
                                   // aborts (ggml_abort/SIGABRT) if a batch exceeds this,
                                   // so every llama_decode() call here is kept at or
                                   // under kNBatch tokens, never just n_ctx.

    llama_free(sm->ctx);
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = (uint32_t)nCtx;
    cparams.n_batch   = (uint32_t)kNBatch;
    cparams.n_threads = nThreads;
    if (nThreadsBatchOverride > 0) cparams.n_threads_batch = nThreadsBatchOverride;
    sm->ctx = llama_new_context_with_model(sm->model, cparams);
    if (!sm->ctx) {
        LOGE("runGeneration: failed to recreate context");
        return r;
    }

    // Read back the values llama.cpp actually granted, rather than assuming
    // our request was honored verbatim -- more accurate for a benchmark log.
    r.n_threads       = llama_n_threads(sm->ctx);
    r.n_threads_batch = llama_n_threads_batch(sm->ctx);
    r.n_ctx           = (int)llama_n_ctx(sm->ctx);

    // Model-independent vocabulary size (native-model-agnostic-vocab-eos):
    // read from the loaded model's own metadata via llama_vocab_n_tokens(),
    // not assumed. Previously hardcoded to 32000 (TinyLlama 1.1B's exact
    // vocab size) -- correct only for that one model; any other model's
    // sampling loop below would have silently scanned just the first 32000
    // of its logits, permanently blind to the rest of its real vocabulary.
    // vocab is obtained first so n_vocab can be derived from it, instead of
    // the reverse.
    std::vector<llama_token> prompt_tokens(nCtx);
    const struct llama_vocab* vocab = llama_model_get_vocab(sm->model);
    const int n_vocab = (int)llama_vocab_n_tokens(vocab);

    int n_prompt = llama_tokenize(
            vocab, prompt.c_str(), (int32_t)prompt.size(),
            prompt_tokens.data(), (int32_t)prompt_tokens.size(),
            true, true);
    if (n_prompt < 0) {
        LOGE("runGeneration: tokenize failed (%d)", n_prompt);
        return r;
    }
    prompt_tokens.resize(n_prompt);
    r.n_prompt = n_prompt;

    // Prefill in chunks of at most kNBatch tokens instead of one llama_batch_init(n_prompt)
    // covering the whole prompt -- a prompt over 512 tokens used to build one oversized
    // batch and crash llama_decode() with SIGABRT. Positions stay absolute across chunks
    // so the model sees the same token sequence it would have in a single batch; only the
    // true final token of the whole prompt requests logits, since that's the only one
    // needed to start the generation loop below.
    int n_done = 0;
    int lastPromptLogitsIdx = 0;
    while (n_done < n_prompt) {
        int chunk = std::min(kNBatch, n_prompt - n_done);

        llama_batch batch = llama_batch_init(chunk, 0, 1);
        for (int i = 0; i < chunk; i++) {
            int idx = n_done + i;
            batch.token[i]     = prompt_tokens[idx];
            batch.pos[i]       = idx;
            batch.n_seq_id[i]  = 1;
            batch.seq_id[i][0] = 0;
            bool isLastPromptToken = (idx == n_prompt - 1);
            batch.logits[i]    = isLastPromptToken ? 1 : 0;
            if (isLastPromptToken) lastPromptLogitsIdx = i;
        }
        batch.n_tokens = chunk;

        if (batch.n_tokens > kNBatch) {
            LOGE("runGeneration: refusing oversized prefill batch n_tokens=%d > kNBatch=%d",
                 batch.n_tokens, kNBatch);
            llama_batch_free(batch);
            return r;
        }

        int rc = llama_decode(sm->ctx, batch);
        llama_batch_free(batch);

        if (rc != 0) {
            LOGE("runGeneration: prefill decode failed at chunk starting %d (rc=%d)", n_done, rc);
            return r;
        }
        n_done += chunk;
    }

    auto t_prefill_end = clock::now();

    // Model-independent stop-token handling (native-model-agnostic-vocab-eos):
    // previously hardcoded llama_token eos = 2; eot = 2; (TinyLlama's own
    // </s> id, assumed to double as EOT too) and compared next_token against
    // both by value. That assumed every model shares TinyLlama's exact
    // token-id numbering AND its "no separate EOT" relationship between the
    // two -- neither is a safe assumption for a different model (see the
    // sampling loop below).
    //
    // llama_vocab_is_eog(vocab, token) is used instead of reading
    // llama_vocab_eos()/llama_vocab_eot() ourselves and comparing by value.
    // It is llama.cpp's own dedicated "should generation stop on this
    // token" check: it already knows, per-model, which token(s) end
    // generation (EOS, a separate EOT where one exists, or any other
    // model-specific end marker) and returns false safely if a model
    // defines no such token, rather than this code having to reason about
    // an invalid/LLAMA_TOKEN_NULL (-1) special-token id itself. Because the
    // check is "is this specific sampled token one of the model's real
    // end-of-generation tokens," an ordinary vocabulary token can never be
    // accidentally treated as a stop token -- there is no separate
    // eos/eot local value here whose staleness or absence could cause that.
    std::string output;
    output.reserve(512);
    char piece[256];
    int  cur_pos = n_prompt;
    bool haveFirstToken = false;

    for (int step = 0; step < nPredict; step++) {
        int logits_idx = (step == 0) ? lastPromptLogitsIdx : 0;
        float* logits = llama_get_logits_ith(sm->ctx, logits_idx);
        if (!logits) { LOGE("runGeneration: null logits step %d", step); break; }

        llama_token next_token = 0;
        if (temp <= 0.0f) {
            float best = logits[0];
            for (int v = 1; v < n_vocab; v++)
                if (logits[v] > best) { best = logits[v]; next_token = v; }
        } else {
            float max_l = logits[0];
            for (int v = 1; v < n_vocab; v++)
                if (logits[v] > max_l) max_l = logits[v];
            std::vector<float> probs(n_vocab);
            float sum = 0.0f;
            for (int v = 0; v < n_vocab; v++) {
                probs[v] = expf((logits[v] - max_l) / temp);
                sum += probs[v];
            }
            float rnd = ((float)rand() / (float)RAND_MAX) * sum;
            float acc = 0.0f;
            for (int v = 0; v < n_vocab; v++) {
                acc += probs[v];
                if (acc >= rnd) { next_token = v; break; }
            }
        }

        if (llama_vocab_is_eog(vocab, next_token)) break;

        int len = llama_token_to_piece(
                vocab, next_token, piece, sizeof(piece)-1, 0, false);
        if (len > 0) { piece[len] = '\0'; output += piece; }
        r.n_generated++;

        // Time-to-first-token: this fires right after the very first token is
        // sampled, which needs no extra llama_decode() call -- its logits came
        // from the last prefill chunk's lookahead (see lastPromptLogitsIdx
        // above). The llama_decode() call below is only feeding this token
        // back in to produce logits for step+1, so TTFT here is genuinely
        // "prefill + sampling," not "prefill + one full decode."
        if (!haveFirstToken) {
            haveFirstToken = true;
            r.ttft_ms = std::chrono::duration<double, std::milli>(clock::now() - t_call_start).count();
        }

        llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0]     = next_token;
        next.pos[0]       = cur_pos++;
        next.n_seq_id[0]  = 1;
        next.seq_id[0][0] = 0;
        next.logits[0]    = 1;
        next.n_tokens     = 1;
        if (next.n_tokens > kNBatch) {
            // Always 1 token here, so this can't actually fire -- kept for the
            // same reason as the prefill guard above.
            LOGE("runGeneration: refusing oversized step batch n_tokens=%d > kNBatch=%d",
                 next.n_tokens, kNBatch);
            llama_batch_free(next); break;
        }
        int rc = llama_decode(sm->ctx, next);
        if (rc != 0) {
            LOGE("runGeneration: decode failed step %d rc=%d", step, rc);
            llama_batch_free(next); break;
        }
        llama_batch_free(next);
    }

    auto t_gen_end = clock::now();

    // Prefer llama.cpp's own perf accounting (per-decode-call timing it already
    // does internally) over our own chrono wrapper where it reports anything --
    // more accurate, and avoids per-token instrumentation. Falls back to our
    // wall-clock measurement only if the library reports nothing (e.g. if
    // no_perf ends up true from whatever llama_context_default_params() set --
    // we don't override that field; see scout_llama_api.h for why).
    //
    // Bug fix (benchmark instrumentation review): the previous condition also
    // accepted pd.t_p_eval_ms/pd.t_eval_ms whenever the corresponding *token
    // count* (n_p_eval/n_eval) was nonzero, on the assumption that "llama.cpp
    // counted tokens" implied "llama.cpp's timing for them is valid." Those are
    // two independent counters -- if llama.cpp's perf-timing accumulation is
    // disabled/broken for this context (e.g. no_perf), the token counts can
    // still increment while the *_ms fields stay legitimately 0.0, and the old
    // OR condition would then pick that untrustworthy 0.0 instead of falling
    // through to the working chrono measurement right next to it -- exactly
    // the "prefill=0ms .../0ms 0.0 tok/s" real-device symptom this fixes.
    // Trust only the timing field's own value now: a millisecond reading is
    // either genuinely present (>0.0) or it isn't, independent of what the
    // token counters say.
    llama_perf_context_data pd = llama_perf_context(sm->ctx);
    r.prefill_ms = (pd.t_p_eval_ms > 0.0)
        ? pd.t_p_eval_ms
        : std::chrono::duration<double, std::milli>(t_prefill_end - t_call_start).count();
    r.gen_ms = (pd.t_eval_ms > 0.0)
        ? pd.t_eval_ms
        : std::chrono::duration<double, std::milli>(t_gen_end - t_prefill_end).count();

    r.total_ms = std::chrono::duration<double, std::milli>(clock::now() - t_call_start).count();
    r.text = output;
    r.ok = true;
    return r;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_scoutface_LlamaEngine_nativeLoad(
        JNIEnv* env, jobject,
        jstring jModelPath, jint nCtx, jint nThreads)
{
    llama_backend_init();
    llama_log_set(llamaLog, nullptr);

    int backendsLoaded = ggml_backend_load_all();
    LOGI("nativeLoad: ggml_backend_load_all loaded %d backend(s)", backendsLoaded);
    if (backendsLoaded == 0) {
        std::string libDir = getLibDir();
        LOGI("nativeLoad: lib dir = %s", libDir.c_str());
        backendsLoaded = ggml_backend_load_all_from_path(libDir.c_str());
        LOGI("nativeLoad: path load result: %d backend(s)", backendsLoaded);
        if (backendsLoaded == 0) {
            std::string cpuPath = libDir + "/libggml-cpu-android_armv8.2_2.so";
            int r = ggml_backend_load(cpuPath.c_str());
            LOGI("nativeLoad: direct CPU load result: %d", r);
        }
    }

    std::string modelPath = jstringToStd(env, jModelPath);
    LOGI("nativeLoad: path=%s nCtx=%d nThreads=%d",
         modelPath.c_str(), (int)nCtx, (int)nThreads);

    {
        FILE* f = fopen(modelPath.c_str(), "rb");
        if (!f) { LOGE("nativeLoad: fopen FAILED errno=%d", errno); return 0L; }
        uint8_t hdr[8] = {};
        size_t  nread  = fread(hdr, 1, 8, f);
        fseek(f, 0, SEEK_END);
        long sz = ftell(f);
        fclose(f);
        LOGI("nativeLoad: file open OK  size=%ld bytes  read=%zu  magic=%c%c%c%c",
             sz, nread, hdr[0], hdr[1], hdr[2], hdr[3]);
        if (hdr[0]!='G'||hdr[1]!='G'||hdr[2]!='U'||hdr[3]!='F') {
            LOGE("nativeLoad: GGUF magic FAILED"); return 0L;
        }
        LOGI("nativeLoad: GGUF magic OK");
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    LOGI("nativeLoad: calling llama_load_model_from_file...");
    llama_model* model = llama_load_model_from_file(modelPath.c_str(), mparams);
    if (!model) { LOGE("nativeLoad: llama_load_model_from_file returned NULL"); return 0L; }
    LOGI("nativeLoad: model loaded OK");

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = (uint32_t)nCtx;
    cparams.n_batch   = 512;
    cparams.n_threads = (int32_t)nThreads;

    LOGI("nativeLoad: calling llama_new_context_with_model...");
    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("nativeLoad: llama_new_context_with_model returned NULL");
        llama_free_model(model); return 0L;
    }
    LOGI("nativeLoad: context created OK");

    ScoutModel* sm = new ScoutModel();
    sm->model = model;
    sm->ctx   = ctx;
    LOGI("nativeLoad: complete — handle=%p", sm);
    return (jlong)(uintptr_t)sm;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_scoutface_LlamaEngine_nativeGenerate(
        JNIEnv* env, jobject,
        jlong handle, jstring jPrompt,
        jint nPredict, jfloat temp, jfloat)
{
    ScoutModel* sm = (ScoutModel*)(uintptr_t)handle;
    if (!sm || !sm->model || !sm->ctx) {
        LOGE("nativeGenerate: invalid handle"); return env->NewStringUTF("");
    }

    std::string prompt = jstringToStd(env, jPrompt);
    if (prompt.empty()) {
        LOGE("nativeGenerate: empty prompt"); return env->NewStringUTF("");
    }

    LOGI("nativeGenerate: START prompt_len=%zu nPredict=%d temp=%.2f",
         prompt.size(), (int)nPredict, (double)temp);

    // Exact same literals production has always used (n_ctx=2048, n_batch=512,
    // n_threads=2). nThreadsBatchOverride=0 means "don't touch n_threads_batch",
    // matching prior behavior exactly -- only the benchmark path below overrides it.
    GenResult r = runGeneration(sm, prompt, (int)nPredict, temp,
                                 /*nCtx=*/2048, /*nBatch=*/512,
                                 /*nThreads=*/2, /*nThreadsBatchOverride=*/0);

    LOGI("nativeGenerate: DONE ok=%d output_len=%zu n_prompt=%d prefill_ms=%.0f "
         "ttft_ms=%.0f n_gen=%d gen_ms=%.0f total_ms=%.0f threads=%d threads_batch=%d",
         r.ok ? 1 : 0, r.text.size(), r.n_prompt, r.prefill_ms,
         r.ttft_ms, r.n_generated, r.gen_ms, r.total_ms, r.n_threads, r.n_threads_batch);

    return env->NewStringUTF(r.text.c_str());
}

// Dev-only benchmark entry point -- gated behind a hidden developer unlock in
// SettingsActivity, never reachable by ordinary users. Runs the exact same
// runGeneration() core as production (so results are representative of real
// behavior) but with caller-supplied thread counts, and returns performance
// metrics only -- never the generated reply text, matching DiagnosticDb's
// existing privacy invariant that response content is never logged.
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_scoutface_LlamaEngine_nativeGenerateBenchmark(
        JNIEnv* env, jobject,
        jlong handle, jstring jPrompt,
        jint nPredict, jfloat temp,
        jint nThreads, jint nThreadsBatch)
{
    ScoutModel* sm = (ScoutModel*)(uintptr_t)handle;
    if (!sm || !sm->model || !sm->ctx) {
        LOGE("nativeGenerateBenchmark: invalid handle");
        return env->NewStringUTF("ok=0");
    }

    std::string prompt = jstringToStd(env, jPrompt);
    if (prompt.empty()) {
        LOGE("nativeGenerateBenchmark: empty prompt");
        return env->NewStringUTF("ok=0");
    }

    LOGI("nativeGenerateBenchmark: START nThreads=%d nThreadsBatch=%d prompt_len=%zu",
         (int)nThreads, (int)nThreadsBatch, prompt.size());

    // Same n_ctx/n_batch as production (2048/512) -- only thread counts vary --
    // so these numbers reflect the context size Scout actually runs with today.
    GenResult r = runGeneration(sm, prompt, (int)nPredict, temp,
                                 /*nCtx=*/2048, /*nBatch=*/512,
                                 (int)nThreads, (int)nThreadsBatch);

    if (!r.ok) {
        LOGE("nativeGenerateBenchmark: generation failed");
        return env->NewStringUTF("ok=0");
    }

    char buf[512];
    snprintf(buf, sizeof(buf),
        "ok=1;n_prompt=%d;prefill_ms=%.1f;ttft_ms=%.1f;n_gen=%d;gen_ms=%.1f;"
        "total_ms=%.1f;n_threads=%d;n_threads_batch=%d;n_ctx=%d;ctx_reused=%d",
        r.n_prompt, r.prefill_ms, r.ttft_ms, r.n_generated, r.gen_ms,
        r.total_ms, r.n_threads, r.n_threads_batch, r.n_ctx, r.ctx_reused ? 1 : 0);

    LOGI("nativeGenerateBenchmark: DONE %s", buf);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_scoutface_LlamaEngine_nativeFree(
        JNIEnv*, jobject, jlong handle)
{
ScoutModel* sm = (ScoutModel*)(uintptr_t)handle;
if (!sm) return;
if (sm->ctx)   { llama_free(sm->ctx);        sm->ctx   = nullptr; }
if (sm->model) { llama_free_model(sm->model); sm->model = nullptr; }
delete sm;
LOGI("nativeFree: released");
}