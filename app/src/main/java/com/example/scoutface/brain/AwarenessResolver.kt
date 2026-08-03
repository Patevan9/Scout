package com.example.scoutface.brain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.scoutface.AwarenessCategory
import com.example.scoutface.AwarenessHistoryDb

/**
 * Phase 1 sensor → Awareness resolver — Scout_Awareness_Layer_Spec.md §1, §3, §9.
 *
 * Owns exactly the two required Phase 1 signals: charging state (new —
 * BatteryManager broadcasts, no permission required) and general connectivity
 * (reuses ScoutConnectivityManager.hasValidatedInternet() exactly as it exists
 * today, per §1 — not Wi-Fi-specific). Publishes each real transition into
 * AwarenessState (live) and AwarenessHistoryDb (rolling history); nothing
 * reads from either yet, per §5's "Phase 1 has zero consumers."
 *
 * Brightness (the spec's optional Phase 1 signal, §3) is deliberately not
 * implemented here. Deriving it would mean reading into the existing camera
 * frame analyzer, and the spec is explicit that charging + connectivity alone
 * are sufficient to prove the pattern — so brightness is deferred rather than
 * risking any change to vision cadence, ownership, or behavior.
 *
 * Does not touch Presence, Companion Moments, HabitLayer, JournalDb, the
 * speech pipeline, or the camera pipeline. This class is additive only — a
 * new, independent observer wired in alongside existing systems, not a
 * modification of any of them.
 */
class AwarenessResolver(
    private val context: Context,
    private val state: AwarenessState,
    private val history: AwarenessHistoryDb,
    private val connectivityManager: ScoutConnectivityManager
) {
    private var started = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(receivedContext: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> onChargingChanged(true)
                Intent.ACTION_POWER_DISCONNECTED -> onChargingChanged(false)
            }
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Registers both sensor listeners. Safe to call once per activity lifecycle. */
    fun start() {
        if (started) return
        started = true

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
        }

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onConnectivityChanged()
                override fun onLost(network: Network) = onConnectivityChanged()
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) = onConnectivityChanged()
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
        }

        // Seed live state with the current reading without treating it as a
        // transition — only edges detected after this point are written to
        // history, matching §3's "only a transition ever produces a write."
        state.updateOnline(connectivityManager.hasValidatedInternet())
    }

    /** Unregisters both sensor listeners. Call from the same lifecycle owner's teardown. */
    fun stop() {
        if (!started) return
        started = false
        try {
            context.unregisterReceiver(powerReceiver)
        } catch (_: Exception) {
        }
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    private fun onChargingChanged(charging: Boolean) {
        val previous = state.isCharging
        state.updateCharging(charging)
        if (previous == charging) return
        history.add(
            if (charging) AwarenessCategory.CHARGING_STARTED else AwarenessCategory.CHARGING_STOPPED,
            "charging=$charging"
        )
        Log.i(TAG, "charging=$charging")
    }

    private fun onConnectivityChanged() {
        val online = connectivityManager.hasValidatedInternet()
        val previous = state.isOnline
        state.updateOnline(online)
        if (previous == online) return
        history.add(
            if (online) AwarenessCategory.CONNECTIVITY_RESTORED else AwarenessCategory.CONNECTIVITY_LOST,
            "online=$online"
        )
        Log.i(TAG, "online=$online")
    }

    companion object {
        private const val TAG = "ScoutAwareness"
    }
}
