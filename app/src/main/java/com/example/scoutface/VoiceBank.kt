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
            "PRESENCE_IDLE_SILENCE" -> listOf(
                "It’s nice having you around.",
                "I’m enjoying the quiet company.",
                "Just keeping you company.",
                "I hope your day is going okay.",
                "It’s good to have some company."
            )
            "PRESENCE_RETURN_GREETING" -> listOf(
                "Welcome back.",
                "Good to see you again.",
                "Welcome back. Good to have you around again."
            )
            "COMPANION_ENVIRONMENT" -> listOf(
                "It's nice hearing everyone together.",
                "Good to have more company.",
                "It's nice having you both around."
            )
            "COMPANION_CURIOSITY" -> listOf(
                "How's your day been?",
                "Anything interesting happen today?",
                "What's been keeping you busy?"
            )
            // A sentence fragment, not a complete line -- the caller (MainActivity,
            // via ScoutMemoryPhraser) appends entity-aware possessive wording and
            // the specific taught fact after this, e.g. "...your favorite color is
            // teal" or "...Diana's birthday is November 27." Kept as a fragment
            // here rather than a full templated sentence per intent, since
            // VoiceBank has no mechanism for inserting a value into a chosen phrase.
            "COMPANION_MEMORY_INTRO" -> listOf(
                "I still remember you told me",
                "I've been thinking about something you told me --",
                "Something you mentioned before came to mind --"
            )
            // Only used for the elevated-activity Observation path, which nothing
            // currently generates (see ScoutCompanionMomentsEngine wiring) -- kept
            // for forward compatibility rather than left to fall through to "Okay."
            "COMPANION_OBSERVATION_FALLBACK" -> listOf(
                "You've been pretty engaged today."
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