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

    llama_free(sm->ctx);
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
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

    LOGI("nativeGenerate: step F — calling llama_batch_init(%d)", n_prompt);
    llama_batch batch = llama_batch_init(n_prompt, 0, 1);
    LOGI("nativeGenerate: step G — filling batch token=%p pos=%p logits=%p",
         (void*)batch.token, (void*)batch.pos, (void*)batch.logits);

    for (int i = 0; i < n_prompt; i++) {
        batch.token[i]     = prompt_tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == n_prompt - 1) ? 1 : 0;
    }
    batch.n_tokens = n_prompt;

    LOGI("nativeGenerate: step H — calling llama_decode (prefill)");
    if (llama_decode(sm->ctx, batch) != 0) {
        LOGE("nativeGenerate: prefill decode failed");
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }
    llama_batch_free(batch);
    LOGI("nativeGenerate: step I — prefill done, starting generation loop");

    llama_token eos = 2;   // TinyLlama EOS = </s> — confirmed in model metadata
    llama_token eot = 2;   // TinyLlama has no separate EOT, same as EOS

    std::string output;
    output.reserve(512);
    char piece[256];
    int  cur_pos = n_prompt;

    for (int step = 0; step < (int)nPredict; step++) {
        int logits_idx = (step == 0) ? (n_prompt - 1) : 0;
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
        if (llama_decode(sm->ctx, next) != 0) {
            LOGE("nativeGenerate: decode failed step %d", step);
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