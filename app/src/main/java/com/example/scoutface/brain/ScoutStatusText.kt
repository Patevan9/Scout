package com.example.scoutface.brain

object ScoutStatusText {

    fun buildConnectivityAnswer(
        onlineEnabled: Boolean,
        hasApiKey: Boolean,
        internetValidated: Boolean,
        wifiConnected: Boolean
    ): String {
        val onlineMode = when {
            !onlineEnabled -> "Online mode: off."
            onlineEnabled && !hasApiKey -> "Online mode: on, but not configured."
            onlineEnabled && hasApiKey && !internetValidated -> "Online mode: on, but internet is not working."
            else -> "Online mode: ready."
        }

        val net = "Wi-Fi: " + if (wifiConnected) "connected." else "not connected."
        val internet = "Internet: " + if (internetValidated) "working." else "not validated."

        return "$net $internet $onlineMode"
    }

    fun buildBootStatusString(
        onlineEnabled: Boolean,
        hasApiKey: Boolean,
        internetValidated: Boolean
    ): String {
        val status = when {
            !onlineEnabled -> "I’m offline-first."
            onlineEnabled && !hasApiKey -> "Online mode is enabled, but not configured."
            onlineEnabled && hasApiKey && !internetValidated -> "Online mode is enabled, but there’s no working internet."
            else -> "Online mode is available."
        }

        return "Hi. I’m here. $status"
    }
}