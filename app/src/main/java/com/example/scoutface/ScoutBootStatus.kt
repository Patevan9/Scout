package com.example.scoutface.brain

class ScoutBootStatus(
    private val isGeminiEnabled: () -> Boolean,
    private val hasApiKey: () -> Boolean,
    private val hasValidatedInternet: () -> Boolean
) {

    fun build(): String {
        val enabled = isGeminiEnabled()
        val hasKey = hasApiKey()
        val validated = hasValidatedInternet()

        val status = when {
            !enabled -> "I’m offline-first."
            enabled && !hasKey -> "Online mode is enabled, but not configured."
            enabled && hasKey && !validated -> "Online mode is enabled, but there’s no working internet."
            else -> "Online mode is on."
        }

        return "Hi. I’m here. $status"
    }
}