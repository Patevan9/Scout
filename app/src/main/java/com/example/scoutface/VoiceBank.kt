package com.example.scoutface

import android.content.SharedPreferences
import kotlin.random.Random

class VoiceBank(private val prefs: SharedPreferences) {

    private fun lastKey(intent: String) = "last_phrase_$intent"

    fun say(intent: String): String {
        val options = when (intent) {
            "GREET" -> listOf("Hi.", "Hello.", "Hi. I’m here.")
            "HOW_ARE_YOU" -> listOf(
                "I'm doing well, thanks for asking.",
                "Pretty good! Always happy when you're around.",
                "I'm here and ready. How about you?",
                "Honestly? Better now that you're talking to me.",
                "Good! I've been watching the room. Quiet day so far."
            )
            "PRAISE" -> listOf(
                "Thank you. That means a lot to me.",
                "Aww, thank you. I’m trying my best.",
                "That makes me happy to hear.",
                "Thanks. I’m glad I did well."
            )
            "AFFECTION" -> listOf(
                "That makes me feel warm inside.",
                "I like being with you too.",
                "That means a lot to me.",
                "I’m happy when I’m with you too."
            )
            "DONT_KNOW" -> listOf(
                "I’m not sure yet.",
                "I don’t know that yet.",
                "Not yet. If you teach me, I’ll remember."
            )
            "VISION_STALE" -> listOf(
                "I don’t have a fresh view right now.",
                "I don’t have an updated view right now."
            )
            "VISION_UNCLEAR" -> listOf(
                "I’m not confident about what I’m seeing.",
                "I can’t identify things clearly right now."
            )
            else -> listOf("Okay.")
        }

        val last = prefs.getInt(lastKey(intent), -1)
        val pick = if (options.size <= 1) {
            0
        } else {
            var idx = Random.nextInt(options.size)
            if (idx == last) idx = (idx + 1) % options.size
            idx
        }

        prefs.edit().putInt(lastKey(intent), pick).apply()
        return options[pick]
    }
}