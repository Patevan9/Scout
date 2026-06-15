package com.example.scoutface

import android.util.Log
import java.io.File

object LlamaEngine {

    private const val TAG = "LlamaEngine"

    @Volatile private var nativeHandle: Long = 0L

    @Volatile var isReady: Boolean = false
        private set

    @Volatile var isLoading: Boolean = false
        private set

    @Volatile private var isGenerating: Boolean = false

    private val nativeLock = Any()

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        nPredict: Int,
        temp: Float,
        repeatPenalty: Float
    ): String
    private external fun nativeFree(handle: Long)

    init {
        try {
            System.loadLibrary("scout_llama")
            Log.i(TAG, "scout_llama native library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load scout_llama — offline brain unavailable.", e)
        }
    }

    fun loadAsync(
        modelFile: File,
        nCtx: Int = 2048,
        nThreads: Int = 4,
        onReady: (success: Boolean) -> Unit = {}
    ) {
        if (isReady) {
            onReady(true)
            return
        }
        if (isLoading) {
            Log.w(TAG, "loadAsync called while already loading — ignored.")
            return
        }
        isLoading = true
        Thread {
            val success = load(modelFile, nCtx, nThreads)
            isLoading = false
            onReady(success)
        }.start()
    }

    fun load(modelFile: File, nCtx: Int = 2048, nThreads: Int = 4): Boolean {
        synchronized(nativeLock) {
            if (isReady) return true
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                return false
            }
            return try {
                Log.i(TAG, "Loading offline brain from: ${modelFile.name} (${modelFile.length() / 1_048_576}MB)")
                val handle = nativeLoad(modelFile.absolutePath, nCtx, nThreads)
                if (handle != 0L) {
                    nativeHandle = handle
                    isReady = true
                    Log.i(TAG, "Offline brain ready.")
                    true
                } else {
                    Log.e(TAG, "nativeLoad returned null handle — model rejected by llama.cpp.")
                    false
                }
            } catch (e: Throwable) {
                Log.e(TAG, "load() threw exception", e)
                false
            }
        }
    }

    fun generate(
        prompt: String,
        nPredict: Int = 150,
        temp: Float = 0.6f,
        repeatPenalty: Float = 1.12f
    ): String? {
        synchronized(nativeLock) {
            if (isGenerating) {
                Log.w(TAG, "generate() blocked because another generation is already running.")
                return null
            }
            if (!isReady || nativeHandle == 0L) {
                Log.w(TAG, "generate() called but engine not ready.")
                return null
            }

            isGenerating = true
            return try {
                val raw = nativeGenerate(nativeHandle, prompt, nPredict, temp, repeatPenalty)
                val cleaned = raw.trim()
                    .removePrefix("<|eot_id|>")
                    .removePrefix("<|end|>")
                    .trim()
                    .ifBlank { null }
                cleaned
            } catch (e: Throwable) {
                Log.e(TAG, "generate() threw exception", e)
                null
            } finally {
                isGenerating = false
            }
        }
    }

    fun free() {
        synchronized(nativeLock) {
            val h = nativeHandle
            if (h != 0L) {
                try {
                    nativeFree(h)
                } catch (e: Throwable) {
                    Log.e(TAG, "nativeFree() threw exception", e)
                }
                nativeHandle = 0L
                isReady = false
                Log.i(TAG, "Offline brain freed.")
            }
        }
    }

    fun statusString(): String = when {
        isReady -> "Offline brain: ready"
        isLoading -> "Offline brain: loading"
        else -> "Offline brain: not loaded"
    }
}