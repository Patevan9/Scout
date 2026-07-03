package com.example.scoutface.brain

import com.example.scoutface.Phrases

class ScoutBootStatus(
    private val isGeminiEnabled: () -> Boolean,
    private val hasApiKey: () -> Boolean,
    private val hasValidatedInternet: () -> Boolean
) {

    fun build(): String = when {
        !isGeminiEnabled()                          -> Phrases.pick("boot", Phrases.BOOT_OFFLINE)
        !hasApiKey()                                -> Phrases.pick("boot", Phrases.BOOT_NO_KEY)
        !hasValidatedInternet()                     -> Phrases.pick("boot", Phrases.BOOT_NO_INTERNET)
        else                                        -> Phrases.pick("boot", Phrases.BOOT_ONLINE)
    }
}
