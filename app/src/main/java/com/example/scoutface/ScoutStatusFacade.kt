package com.example.scoutface

import com.example.scoutface.brain.ScoutStatusText

class ScoutStatusFacade(
    private val isGeminiEnabled: () -> Boolean,
    private val hasApiKey: () -> Boolean,
    private val hasValidatedInternet: () -> Boolean,
    private val isOnWifi: () -> Boolean
) {
    fun buildBootStatusString(): String {
        return ScoutStatusText.buildBootStatusString(
            onlineEnabled = isGeminiEnabled(),
            hasApiKey = hasApiKey(),
            internetValidated = hasValidatedInternet()
        )
    }

    fun buildConnectivityAnswer(): String {
        return ScoutStatusText.buildConnectivityAnswer(
            onlineEnabled = isGeminiEnabled(),
            hasApiKey = hasApiKey(),
            internetValidated = hasValidatedInternet(),
            wifiConnected = isOnWifi()
        )
    }
}