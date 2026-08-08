package com.example.scoutface.brain

/**
 * Busy-Brain -- PR 2. Pure delivery-arbitration decisions for a resolved
 * Gemini/TinyLlama answer, kept separate from MainActivity's own
 * isSpeaking/isThinking fields so the two rules that matter most --
 * "never flush over something already being said" and "only use the
 * bridging phrase when the answer actually had to wait" -- are locked in by
 * a unit test rather than only exercised by hand on a real device.
 */
object ScoutBusyBrainDelivery {

    /**
     * True if the answer must be held (see MainActivity's pendingAiAnswer)
     * rather than spoken immediately -- Scout is either mid-utterance
     * (isSpeaking) or mid-dispatch of another accepted request (isThinking).
     * Speaking now in either case would flush over it (speak() always uses
     * QUEUE_FLUSH), which is exactly what must never happen.
     */
    fun shouldQueue(isSpeaking: Boolean, isThinking: Boolean): Boolean = isSpeaking || isThinking

    /**
     * The exact text to speak once the answer is actually delivered.
     * wasQueued -- true only when the answer had to wait behind another
     * interaction (see shouldQueue() above) -- adds the "And about your
     * earlier question--" bridge; an answer delivered while Scout was
     * genuinely idle is spoken plainly, with no bridge.
     */
    fun phraseDelivery(answer: String, wasQueued: Boolean): String =
        if (wasQueued) "And about your earlier question — $answer" else answer
}
