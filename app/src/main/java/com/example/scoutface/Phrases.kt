package com.example.scoutface

/**
 * Personality phrase pools. Call pick() to get a random phrase that
 * won't repeat until at least half the pool has been exhausted.
 *
 * Pools that include a person's name use {name} as a placeholder —
 * call pickNamed() and pass the name directly.
 */
object Phrases {

    private val history = mutableMapOf<String, ArrayDeque<String>>()

    fun pick(key: String, pool: List<String>): String {
        if (pool.size == 1) return pool[0]
        val recent = history.getOrPut(key) { ArrayDeque() }
        val cooldown = (pool.size / 2).coerceAtLeast(1)
        val available = pool.filter { it !in recent }
        val chosen = (if (available.isNotEmpty()) available else pool).random()
        recent.addLast(chosen)
        while (recent.size > cooldown) recent.removeFirst()
        return chosen
    }

    fun pickNamed(key: String, pool: List<String>, name: String): String =
        pick(key, pool).replace("{name}", name)

    // ── Boot ────────────────────────────────────────────────────────────────

    val BOOT_ONLINE = listOf(
        "Hi. I'm here. Online mode is on.",
        "Hey! Good to see you again. Online mode is ready.",
        "Hello! I'm up and running. Online mode is active.",
        "Hi. I'm awake and online.",
        "Hey. I'm here. Online and ready to go.",
        "Good to be back. Online mode is on."
    )

    val BOOT_OFFLINE = listOf(
        "Hi. I'm here. Running in offline mode today.",
        "Hey! I'm up. Offline mode is active.",
        "Hello! No internet today, but I've still got plenty I can do.",
        "Hi. I'm awake. Running offline — ready when you are.",
        "Hey. I'm here and offline. What do you need?",
        "Hi! Just woke up. Offline mode is on."
    )

    val BOOT_NO_INTERNET = listOf(
        "Hi. I'm here. Online mode is on but I'm not seeing internet right now.",
        "Hey! I'm up. Online mode is set but internet isn't connecting yet.",
        "Hello! Online mode is ready but I can't reach the internet. I'll keep trying.",
        "Hi. I'm awake. Online mode is enabled — just waiting on a connection."
    )

    val BOOT_NO_KEY = listOf(
        "Hi. I'm here. Online mode is enabled but not configured yet.",
        "Hey! I'm up. You'll need to finish setting up online mode when you get a chance.",
        "Hello! Online mode needs to be configured. Check Settings when you're ready."
    )

    // ── Remembering ─────────────────────────────────────────────────────────

    val REMEMBER = listOf(
        "Got it.",
        "Noted.",
        "Okay, I'll keep that in mind.",
        "Sounds good. I'll remember it.",
        "Thanks for telling me.",
        "I'll hang on to that.",
        "That's good to know.",
        "Consider it remembered.",
        "I'll remember that."
    )

    val REMEMBER_NAME = listOf(
        "Got it. I'll remember {name}.",
        "Okay. I'll keep {name} in mind.",
        "Noted — {name}. Got it.",
        "Thanks. I'll remember {name}.",
        "I'll hang on to {name}.",
        "Sounds good. I'll remember {name}."
    )

    val REMEMBER_MY_NAME = listOf(
        "Got it. I'll remember your name is {name}.",
        "Okay. {name} — I've got it.",
        "Noted. Your name is {name}.",
        "Thanks. I'll call you {name}.",
        "I'll remember {name}."
    )

    val REMEMBER_WIFE = listOf(
        "Okay. I'll remember your wife's name is {name}.",
        "Got it — your wife is {name}. I'll remember.",
        "Noted. {name} is your wife.",
        "Thanks for telling me. I'll remember {name}.",
        "I'll remember {name} is your wife."
    )

    val REMEMBER_SON = listOf(
        "Okay. I'll remember your son's name is {name}.",
        "Got it — your son is {name}. I'll remember.",
        "Noted. {name} is your son.",
        "Thanks. I'll remember {name}.",
        "I'll remember {name} is your son."
    )

    val REMEMBER_DOG = listOf(
        "Okay. I'll remember your dog's name is {name}.",
        "Got it — your dog is {name}.",
        "Noted. {name} is your dog. I'll remember.",
        "I'll remember {name}."
    )

    // ── Goodbye ──────────────────────────────────────────────────────────────

    val GOODBYE = listOf(
        "Okay. I'll see you later.",
        "Take care.",
        "Goodbye. Talk soon.",
        "Later. I'll be here.",
        "Okay. See you.",
        "Alright. Catch you later.",
        "Got it. Talk to you soon."
    )
}
