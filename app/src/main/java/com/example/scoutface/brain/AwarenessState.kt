package com.example.scoutface.brain

/**
 * Live, in-memory Awareness state — Scout_Awareness_Layer_Spec.md §2. Never
 * persisted; exists only in memory and rebuilds from nothing on next launch.
 * Cheap to hold, produces zero writes on its own, never itself a trigger for
 * anything — only AwarenessResolver's edge detection writes to history.
 *
 * Phase 1 populates only the two required environmental signals (charging,
 * connectivity). Every other field the spec describes (presence, orientation,
 * direct-address tier, physical state, brightness) is out of scope for this
 * phase and intentionally not represented here yet — adding a field for it
 * now would be scope creep ahead of the phase that actually needs it.
 *
 * null means "not yet observed" (e.g. before the first sensor reading),
 * distinct from a real false/true value.
 */
class AwarenessState {

    @Volatile
    var isCharging: Boolean? = null
        private set

    @Volatile
    var isOnline: Boolean? = null
        private set

    fun updateCharging(charging: Boolean) {
        isCharging = charging
    }

    fun updateOnline(online: Boolean) {
        isOnline = online
    }
}
