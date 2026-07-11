package com.example.scoutface.brain

import com.example.scoutface.Phrases

class ScoutBootStatus(
    private val isGeminiEnabled: () -> Boolean,
    private val hasApiKey: () -> Boolean,
    private val hasValidatedInternet: () -> Boolean,
    private val lastLlamaLoadMs: () -> Long = { Long.MAX_VALUE }
) {

    fun build(): String = when {
        !isGeminiEnabled() -> {
            // If we know TinyLlama loaded in under 2 s last time, skip the warming-up line.
            val fast = lastLlamaLoadMs().let { it in 1L..2000L }
            val pool = if (fast) Phrases.BOOT_OFFLINE_FAST else Phrases.BOOT_OFFLINE
            Phrases.pick("boot", pool)
        }
        !hasApiKey()           -> Phrases.pick("boot", Phrases.BOOT_NO_KEY)
        !hasValidatedInternet() -> Phrases.pick("boot", Phrases.BOOT_NO_INTERNET)
        else                   -> {
            val fast = lastLlamaLoadMs().let { it in 1L..2000L }
            val pool = if (fast) Phrases.BOOT_ONLINE_FAST else Phrases.BOOT_ONLINE
            Phrases.pick("boot", pool)
        }
    }
}
