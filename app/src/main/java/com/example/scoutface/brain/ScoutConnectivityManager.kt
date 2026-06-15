package com.example.scoutface.brain

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.provider.Settings

class ScoutConnectivityManager(private val context: Context) {

    fun hasValidatedInternet(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            val hasInet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            hasInet && validated
        } catch (_: Exception) {
            false
        }
    }

    fun isOnWifi(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    fun getAvailableBytes(filesDirPath: String): Long {
        return try {
            val stat = StatFs(filesDirPath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    fun buildGoOnlineMessage(hasApiKey: Boolean, internetValidated: Boolean): String {
        return when {
            !internetValidated -> "Okay. I opened internet settings. Turn on Wi-Fi internet, then come back and try again."
            !hasApiKey -> "I’m connected, but online mode is not configured."
            else -> "Okay. I am connected."
        }
    }

    fun openInternetPanel() {
        try {
            val i = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {}

        try {
            val i = Intent(Settings.ACTION_WIFI_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (_: Exception) {}
    }
}