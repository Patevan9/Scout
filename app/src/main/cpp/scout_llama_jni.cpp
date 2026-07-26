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

    const int kNBatch = 512;   // must match cparams.n_batch below -- llama_decode()
                               // aborts (ggml_abort/SIGABRT) if a batch exceeds this,
                               // so every llama_decode() call in this function is kept
                               // at or under kNBatch tokens, never just n_ctx.

    llama_free(sm->ctx);
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = kNBatch;
    cparams.n_threads = 2;
    sm->ctx = llama_new_context_with_model(sm->model, cparams);
    if (!sm->ctx) {
        LOGE("nativeGenerate: failed to recreate context");
        return env->NewStringUTF("");
    }
    LOGI("nativeGenerate: step A — using hardcoded vocab/ctx sizes");
    int n_vocab = 32000;   // TinyLlama 1.1B — confirmed in model metadata
    int n_ctx   = 2048;    // matches our requested context size
    LOGI("nativeGenerate: step C — n_vocab=%d n_ctx=%d", n_vocab, n_ctx);
    std::vector<llama_token> prompt_tokens(n_ctx);
    LOGI("nativeGenerate: step D — model_ptr=0x%016llx calling llama_tokenize", (unsigned long long)(uintptr_t)sm->model);
    const struct llama_vocab* vocab = llama_model_get_vocab(sm->model);

    int n_prompt = llama_tokenize(
vocab,
            prompt.c_str(), (int32_t)prompt.size(),
            prompt_tokens.data(), (int32_t)prompt_tokens.size(),
            true, true);
    LOGI("nativeGenerate: step E — n_prompt=%d", n_prompt);

    if (n_prompt < 0) {
        LOGE("nativeGenerate: tokenize failed (%d)", n_prompt);
        return env->NewStringUTF("");
    }
    prompt_tokens.resize(n_prompt);

    // Prefill in chunks of at most kNBatch tokens instead of one llama_batch_init(n_prompt)
    // covering the whole prompt -- a prompt over 512 tokens (easily reached now that the
    // personal-memory/general fact grounding can inject a dozen facts plus conversation
    // history) used to build one oversized batch and crash llama_decode() with SIGABRT.
    // Positions stay absolute across chunks (idx, not the chunk-local loop index i) so the
    // model sees the same token sequence it would have in a single batch; only the true
    // final token of the whole prompt requests logits, since that's the only one needed to
    // start the generation loop below.
    LOGI("nativeGenerate: step F — prefilling %d prompt token(s) in chunks of %d", n_prompt, kNBatch);

    // llama_get_logits_ith(ctx, i) indexes into the batch from the *most recent*
    // llama_decode() call, not a global position across every decode ever made --
    // so once prefill is chunked, the final prompt token's logits live at its
    // local index within the *last* chunk's batch, not at n_prompt-1. Captured
    // below as lastPromptLogitsIdx and used instead of n_prompt-1 for step 0.
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
            LOGE("nativeGenerate: refusing oversized prefill batch n_tokens=%d > kNBatch=%d",
                 batch.n_tokens, kNBatch);
            llama_batch_free(batch);
            return env->NewStringUTF("");
        }

        LOGI("nativeGenerate: step G — prefill chunk [%d, %d) n_tokens=%d calling llama_decode",
             n_done, n_done + chunk, batch.n_tokens);
        int rc = llama_decode(sm->ctx, batch);
        LOGI("nativeGenerate: step H — prefill chunk decode rc=%d", rc);
        llama_batch_free(batch);

        if (rc != 0) {
            LOGE("nativeGenerate: prefill decode failed at chunk starting %d (rc=%d)", n_done, rc);
            return env->NewStringUTF("");
        }

        n_done += chunk;
    }
    LOGI("nativeGenerate: step I — prefill done, starting generation loop");

    llama_token eos = 2;   // TinyLlama EOS = </s> — confirmed in model metadata
    llama_token eot = 2;   // TinyLlama has no separate EOT, same as EOS

    std::string output;
    output.reserve(512);
    char piece[256];
    int  cur_pos = n_prompt;

    for (int step = 0; step < (int)nPredict; step++) {
        int logits_idx = (step == 0) ? lastPromptLogitsIdx : 0;
        float* logits = llama_get_logits_ith(sm->ctx, logits_idx);
        if (!logits) { LOGE("nativeGenerate: null logits step %d", step); break; }

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
            float r = ((float)rand() / (float)RAND_MAX) * sum;
            float acc = 0.0f;
            for (int v = 0; v < n_vocab; v++) {
                acc += probs[v];
                if (acc >= r) { next_token = v; break; }
            }
        }

        if (next_token == eos || next_token == eot) {
            LOGI("nativeGenerate: stop token at step %d", step); break;
        }

        int len = llama_token_to_piece(
                vocab, next_token, piece, sizeof(piece)-1, 0, false);
        if (len > 0) { piece[len] = '\0'; output += piece; }

        llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0]     = next_token;
        next.pos[0]       = cur_pos++;
        next.n_seq_id[0]  = 1;
        next.seq_id[0][0] = 0;
        next.logits[0]    = 1;
        next.n_tokens     = 1;
        if (next.n_tokens > kNBatch) {
            // Always 1 token here, so this can't actually fire -- kept for the same
            // reason as the prefill guard: no llama_decode() call in this function
            // should ever be able to exceed kNBatch without being caught explicitly.
            LOGE("nativeGenerate: refusing oversized step batch n_tokens=%d > kNBatch=%d",
                 next.n_tokens, kNBatch);
            llama_batch_free(next); break;
        }
        int rc = llama_decode(sm->ctx, next);
        if (rc != 0) {
            LOGE("nativeGenerate: decode failed step %d rc=%d", step, rc);
            llama_batch_free(next); break;
        }
        llama_batch_free(next);
    }

    LOGI("nativeGenerate: DONE output_len=%zu", output.size());
    return env->NewStringUTF(output.c_str());
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