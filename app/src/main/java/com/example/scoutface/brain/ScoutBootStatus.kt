package com.example.scoutface.brain

import com.example.scoutface.Phrases

class ScoutBootStatus(
    private val isGeminiEnabled: () -> Boolean,
    private val hasApiKey: () -> Boolean,
    private val hasValidatedInternet: () -> Boolean
) {

    // build() only ever runs after MainActivity's startup gate (ModelDownloadActivity) has
    // already confirmed LlamaEngine.isReady -- Scout is never seen or heard until the offline
    // brain has fully finished loading. "Still warming up" phrasing is therefore never
    // accurate at this point, so the ready/fast pool is always used instead of picking
    // between a fast/slow pair.
    fun build(): String = when {
        !isGeminiEnabled()      -> Phrases.pick("boot", Phrases.BOOT_OFFLINE_FAST)
        !hasApiKey()            -> Phrases.pick("boot", Phrases.BOOT_NO_KEY)
        !hasValidatedInternet() -> Phrases.pick("boot", Phrases.BOOT_NO_INTERNET)
        else                    -> Phrases.pick("boot", Phrases.BOOT_ONLINE_FAST)
    }
}
