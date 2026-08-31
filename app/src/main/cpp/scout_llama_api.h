#pragma once

/*
 * scout_llama_api.h  —  Safe minimal declarations for llama.cpp b8946.
 *
 * llama_model_params and llama_context_params use oversized opaque tails.
 * This guarantees we never undersize the struct (which crashes the stack).
 * llama_model_default_params() and llama_context_default_params() fill all
 * fields correctly. We only name the fields we actually set by hand.
 *
 * llama_batch layout is stable across versions and declared precisely.
 */

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Opaque handles ──────────────────────────────────────────── */
struct llama_model;
struct llama_context;
struct llama_vocab;

typedef int32_t llama_token;
typedef int32_t llama_pos;
typedef int32_t llama_seq_id;

/* ── Model params ────────────────────────────────────────────── */
/* n_gpu_layers is stable at offset 0 across all llama.cpp versions.
   The 508-byte opaque tail makes the struct safely larger than the
   real one — the extra bytes are ignored by the library.            */
struct llama_model_params {
    int32_t n_gpu_layers;   /* offset 0  — only field we set */
    char    _pad[508];      /* oversized safe tail            */
};

/* ── Context params ──────────────────────────────────────────── */
/* n_ctx/n_batch/n_ubatch/n_seq_max/n_threads/n_threads_batch are stable
   at offsets 0–20 across all llama.cpp versions (confirmed against the
   upstream llama.h struct ordering: n_ctx, n_batch, n_ubatch, n_seq_max,
   n_threads, n_threads_batch, then RoPE/YaRN/callback fields we never
   touch). The 504-byte opaque tail makes the struct safely larger.     */
struct llama_context_params {
    uint32_t n_ctx;            /* offset 0  — we set this */
    uint32_t n_batch;          /* offset 4  — we set this */
    uint32_t n_ubatch;         /* offset 8                */
    uint32_t n_seq_max;        /* offset 12               */
    int32_t  n_threads;        /* offset 16 — we set this */
    int32_t  n_threads_batch;  /* offset 20 — we set this */
    char     _pad[504];        /* oversized safe tail     */
};

/* ── Perf data (plain flat struct, returned by value — layout is part
   of the public API contract, not opaque like the handle structs above) */
struct llama_perf_context_data {
    double  t_start_ms;
    double  t_load_ms;
    double  t_p_eval_ms;   /* time spent processing the prompt (prefill) */
    double  t_eval_ms;     /* time spent generating tokens               */
    int32_t n_p_eval;      /* number of prompt tokens processed          */
    int32_t n_eval;        /* number of tokens generated                 */
    int32_t n_reused;
};

/* ── Batch ───────────────────────────────────────────────────── */
/* Layout: int32 + implicit 4-byte pad + six pointers on ARM64.   */
struct llama_batch {
    int32_t        n_tokens;
    /* 4 bytes implicit padding */
    llama_token*   token;
    float*         embd;
    llama_pos*     pos;
    int32_t*       n_seq_id;
    llama_seq_id** seq_id;
    int8_t*        logits;
};

/* ── Functions ───────────────────────────────────────────────── */
void llama_backend_init(void);
void llama_backend_free(void);

struct llama_model_params   llama_model_default_params(void);
struct llama_context_params llama_context_default_params(void);

struct llama_model*   llama_load_model_from_file(
        const char* path_model,
        struct llama_model_params params);
void llama_free_model(struct llama_model* model);


struct llama_batch llama_batch_init(
        int32_t n_tokens_alloc,
        int32_t embd,
        int32_t n_seq_max);
void llama_batch_free(struct llama_batch batch);



void llama_free(struct llama_context* ctx);

void llama_free_model(struct llama_model* model);
const struct llama_vocab* llama_model_get_vocab(const struct llama_model* model);
int32_t llama_tokenize(
        const struct llama_vocab* vocab,
        const char* text,
        int32_t text_len,
        llama_token* tokens,
        int32_t n_tokens_max,
        bool add_special,
        bool parse_special);
struct llama_context* llama_new_context_with_model(struct llama_model* model, struct llama_context_params params);
int llama_decode(struct llama_context* ctx, struct llama_batch batch);

float* llama_get_logits_ith(struct llama_context* ctx, int32_t i);

llama_token llama_token_eos(const struct llama_model* model);
int32_t llama_token_to_piece(const struct llama_vocab* vocab, llama_token token, char* buf, int32_t length, int32_t lstrip, bool special);
llama_token llama_token_eot(const struct llama_model* model);
typedef void (*llama_log_callback_t)(int level, const char* text, void* user_data);
void llama_log_set(llama_log_callback_t log_callback, void* user_data);
int  ggml_backend_load_all(void);
int  ggml_backend_load_all_from_path(const char* path);
int  ggml_backend_load(const char* path);

/* Benchmark-instrumentation additions. All confirmed present in the prebuilt
   libllama.so via `nm -D` before adding these declarations. */
int32_t  llama_n_threads(struct llama_context* ctx);
int32_t  llama_n_threads_batch(struct llama_context* ctx);
uint32_t llama_n_ctx(const struct llama_context* ctx);
struct llama_perf_context_data llama_perf_context(const struct llama_context* ctx);

/* Model-agnostic vocab/stop-token additions (native-model-agnostic-vocab-eos).
   Both confirmed present in the prebuilt libllama.so via `nm -D` before
   adding these declarations, and signatures cross-checked against the full
   vendored llama.h in this same directory (kept for reference; this file,
   not llama.h, is what scout_llama_jni.cpp actually includes and compiles
   against). llama_vocab_is_eog() is llama.cpp's own per-model "does this
   token end generation" check -- see its call site in scout_llama_jni.cpp
   for why it's used instead of comparing against llama_vocab_eos()/
   llama_vocab_eot() by value. */
int32_t llama_vocab_n_tokens(const struct llama_vocab* vocab);
bool    llama_vocab_is_eog(const struct llama_vocab* vocab, llama_token token);

/* Chat-template additions (structured-chat-template-seam / Qwen migration
   Step 2A). Both functions and the struct below are confirmed present in
   the prebuilt libllama.so via `nm -D` (both listed as strong `T` exported
   symbols) before adding these declarations, and cross-checked field-for-
   field against the full vendored llama.h in this same directory (kept for
   reference; this file, not llama.h, is what scout_llama_jni.cpp actually
   includes and compiles against -- see the note on the vocab/EOG additions
   above, same reasoning applies here).

   llama_model_chat_template() returns the loaded GGUF's own embedded chat
   template (the Jinja source baked into the model file at conversion time;
   name=nullptr selects the model's default template). llama_chat_apply_template()
   serializes a list of {role, content} messages using that template --
   llama.cpp's own built-in (non-Jinja) template matcher recognizes both
   TinyLlama's zephyr-style format and Qwen's ChatML format by content-
   sniffing the template string, so this stays model-agnostic without this
   file ever branching on which model is loaded. See applyModelChatTemplate()
   in scout_llama_jni.cpp for the call site and buffer-sizing handling. */
typedef struct llama_chat_message {
    const char* role;
    const char* content;
} llama_chat_message;

const char* llama_model_chat_template(const struct llama_model* model, const char* name);

int32_t llama_chat_apply_template(
        const char* tmpl,
        const struct llama_chat_message* chat,
        size_t n_msg,
        bool add_ass,
        char* buf,
        int32_t length);

#ifdef __cplusplus
}
#endif