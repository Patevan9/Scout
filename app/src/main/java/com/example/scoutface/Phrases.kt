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

    // Online + Gemini ready, but TinyLlama still loading in the background.
    val BOOT_ONLINE = listOf(
        "Hi! I'm up. Just warming up my offline brain in the background.",
        "Hey! Good to see you. My offline brain is still waking up.",
        "Hello! I'm here. Give my offline brain just a moment to warm up.",
        "Hi there! Gemini is ready, and my offline brain is warming up.",
        "Hey, good to be back! My offline brain is still getting started.",
        "Good to see you! I'm just warming up my offline brain."
    )

    // Online + Gemini ready + TinyLlama loaded fast last time — no warming-up needed.
    val BOOT_ONLINE_FAST = listOf(
        "Hi! I'm up and ready.",
        "Hey! Good to see you again.",
        "Hello! Ready when you are.",
        "Hi there. I'm here.",
        "Hey, good to be back!",
        "Good to see you!"
    )

    // Used when the last TinyLlama load was fast (< 2 s) — skip the warming-up line.
    val BOOT_OFFLINE_FAST = listOf(
        "Hey there! Good to see you again.",
        "Hi! I'm up and ready.",
        "Hey. Good to be back.",
        "Hello! Ready when you are.",
        "Hi there! I'm awake and ready to go."
    )

    val BOOT_OFFLINE = listOf(
        "Hey there! Give my offline brain a few seconds to wake up.",
        "Good to see you! Just warming up my offline brain. Slide the screen to the right any time to open settings.",
        "Why, hello there! Give me a moment while my offline brain gets itself together.",
        "Hi! I'm waking up my offline brain. I'll be ready in just a few seconds. You can open settings any time by sliding right.",
        "Welcome back! My offline brain is still stretching. I'll be ready in a moment.",
        "Good to see you! I'm just warming up my offline brain. Open settings any time by sliding the screen to the right."
    )

    val BOOT_NO_INTERNET = listOf(
        "Hey! Just warming up my offline brain. No internet right now, but I'll keep trying.",
        "Hi there! Give my offline brain a moment. No internet yet — I'll let you know when I connect.",
        "Good to see you! My offline brain is warming up. Internet isn't connecting yet.",
        "Hi! Waking up my offline brain. I can't reach the internet right now, but I'll keep checking."
    )

    val BOOT_NO_KEY = listOf(
        "Hi. I'm here. Open settings any time by sliding the screen to the right.",
        "Hey! I'm up. Slide the screen to the right any time to open settings.",
        "Hello! You can open settings any time by sliding right."
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

    // ── Calendar Follow-up: doctor check-in ────────────────────────────────
    // Deliberately content-blind -- spoken regardless of what the user actually
    // said in reply to "Is everything okay?", never referencing specifics.
    // Scout doesn't parse, extract, or store anything from that reply (see
    // MainActivity's PendingCalendarFollowup.DoctorCheckIn handling); a plain
    // acknowledgment is also the more honest fit for Scout's own philosophy --
    // he shouldn't sound like he understood or is tracking a medical detail he
    // never actually processed.

    val DOCTOR_CHECKIN_ACK = listOf(
        "I'm glad you told me. I hope it goes well.",
        "Thanks for letting me know. I hope you feel better soon.",
        "I appreciate you telling me. Take care of yourself."
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

    // ── Courtesy (Phase 1) ──────────────────────────────────────────────────
    // Dedicated pools for ScoutCourtesyMatcher -- deliberately separate from
    // GREET/GOODBYE above (which stay reachable only through the existing
    // wake-name + ScoutIntentRouter path) rather than reused, so the two
    // systems don't become coupled. Wording is kept close to GREET/GOODBYE's
    // tone on purpose, so Scout sounds the same whether or not his name was said.

    val COURTESY_GREET = listOf(
        "Hi.",
        "Hello.",
        "Hey there."
    )

    val COURTESY_GOOD_MORNING = listOf(
        "Good morning.",
        "Morning. It's good to see you.",
        "Good morning. Ready when you are."
    )

    val COURTESY_THANKS = listOf(
        "You're welcome.",
        "Anytime.",
        "Happy to help.",
        "Of course."
    )

    val COURTESY_GOOD_NIGHT = listOf(
        "Good night.",
        "Sleep well.",
        "Good night. Take care."
    )

    val COURTESY_GOODBYE = listOf(
        "Okay. I'll see you later.",
        "Take care.",
        "Bye for now."
    )
}
