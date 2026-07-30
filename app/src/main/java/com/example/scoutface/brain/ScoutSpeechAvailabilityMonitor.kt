package com.example.scoutface.brain

/**
 * Detects a sustained pattern of network-dependent speech-recognition failures --
 * not an ordinary/transient recognizer error -- so Scout can be honest about a
 * real, ongoing "I can't hear you" problem instead of silently retrying forever
 * with no explanation. Purely observational: never changes
 * RecognizerIntent.EXTRA_PREFER_OFFLINE, never invokes a different recognition
 * path, never alters MainActivity's existing restart-on-error behavior -- see
 * MainActivity.onError(), the only call site, where scheduleListenRestart() runs
 * exactly as it already did before this class existed.
 *
 * Deliberately has no android.* import, matching every other class in this
 * package -- ERROR_NETWORK/ERROR_NETWORK_TIMEOUT below are plain Int constants
 * mirroring android.speech.SpeechRecognizer's documented values (1 and 2) rather
 * than importing that class, so this stays a pure, platform-independent,
 * unit-testable rule the same way ScoutMemoryGate/ScoutFactExtractor are.
 *
 * Only these two codes count -- the two Android-defined error codes that most
 * directly indicate the recognizer needed network connectivity it didn't have.
 * Every other error code (no match, client error, recognizer busy, etc.) is
 * explicitly ignored: those are already handled by MainActivity's existing
 * per-error logic and say nothing about offline-recognition availability.
 *
 * Not a process-wide singleton -- like ScoutPresenceDecider, one instance is
 * owned per MainActivity instance and its state (the rolling error window, the
 * last-announced timestamp) resets on a configuration-change recreation. A lost
 * window on rotation just means the pattern has to reaccumulate; this is
 * diagnostic-tracking state, not something that needs to survive rotation.
 */
class ScoutSpeechAvailabilityMonitor {

    companion object {
        // Mirrors android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT / ERROR_NETWORK.
        const val ERROR_NETWORK_TIMEOUT = 1
        const val ERROR_NETWORK = 2

        // Named and tunable -- initial values, expected to be retuned after real
        // A32 testing, same as other recently-added thresholds in this project.

        /** How many qualifying errors within the rolling window count as a real pattern. */
        const val QUALIFYING_ERROR_THRESHOLD = 3

        /** Rolling window qualifying errors must fall within to count together. */
        const val ERROR_WINDOW_MS = 2L * 60L * 1_000L // 2 minutes

        /** Minimum time between spoken availability warnings. */
        const val ANNOUNCE_COOLDOWN_MS = 10L * 60L * 1_000L // 10 minutes
    }

    // Timestamps of qualifying errors still inside the rolling window. Old entries
    // age out on every call (checked against the window, not on a timer/handler),
    // so no background scheduling is needed just to maintain this state.
    private val recentErrorTimestamps = ArrayDeque<Long>()

    private var lastAnnouncedMs = 0L

    /**
     * Call from onError() for every recognizer error, not just network ones --
     * [errorCode] values other than ERROR_NETWORK/ERROR_NETWORK_TIMEOUT are
     * recorded as "not a match" and never affect the rolling window.
     *
     * Returns true only when [errorCode] is a qualifying network error AND the
     * resulting pattern has just crossed the threshold AND the announce cooldown
     * has elapsed -- true means the caller should speak the availability warning
     * now and then call onWarned(). A false result requires no action: the error
     * may have been unrelated, a network error was recorded but hasn't (yet)
     * formed a pattern, or a pattern exists but was already announced recently.
     */
    fun onRecognizerError(errorCode: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (errorCode != ERROR_NETWORK && errorCode != ERROR_NETWORK_TIMEOUT) return false

        recentErrorTimestamps.addLast(nowMs)
        while (recentErrorTimestamps.isNotEmpty() &&
            nowMs - recentErrorTimestamps.first() > ERROR_WINDOW_MS
        ) {
            recentErrorTimestamps.removeFirst()
        }

        if (recentErrorTimestamps.size < QUALIFYING_ERROR_THRESHOLD) return false

        // lastAnnouncedMs == 0L is the "never announced yet" sentinel (same
        // convention as e.g. ScoutPresenceDecider's lastConversationTurnMs) --
        // without it, the very first pattern would need nowMs - 0 to already
        // exceed the cooldown, which only happens to be true in production
        // because real epoch milliseconds are astronomically larger than any
        // cooldown window, not because the check is actually correct.
        return lastAnnouncedMs == 0L || nowMs - lastAnnouncedMs >= ANNOUNCE_COOLDOWN_MS
    }

    /**
     * Call only after actually speaking the availability warning -- if the caller
     * skips speaking for some other reason (e.g. Scout is already mid-utterance),
     * do not call this, so the next qualifying error can try again rather than
     * being silently suppressed by a cooldown that never really fired.
     */
    fun onWarned(nowMs: Long = System.currentTimeMillis()) {
        lastAnnouncedMs = nowMs
    }

    /** Number of qualifying errors currently inside the rolling window -- diagnostic use only. */
    fun currentPatternSize(): Int = recentErrorTimestamps.size
}
