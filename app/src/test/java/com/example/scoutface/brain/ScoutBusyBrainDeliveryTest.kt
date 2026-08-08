package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutBusyBrainDeliveryTest {

    // --- shouldQueue: never flush over something already being said ---

    @Test fun `queues while Scout is speaking`() {
        assertTrue(ScoutBusyBrainDelivery.shouldQueue(isSpeaking = true, isThinking = false))
    }

    @Test fun `queues while Scout is mid-dispatch of another accepted request`() {
        assertTrue(ScoutBusyBrainDelivery.shouldQueue(isSpeaking = false, isThinking = true))
    }

    @Test fun `queues when both speaking and thinking are somehow true`() {
        assertTrue(ScoutBusyBrainDelivery.shouldQueue(isSpeaking = true, isThinking = true))
    }

    @Test fun `delivers immediately when Scout is genuinely idle`() {
        assertFalse(ScoutBusyBrainDelivery.shouldQueue(isSpeaking = false, isThinking = false))
    }

    // --- phraseDelivery: the bridge only appears when the answer waited ---

    @Test fun `a queued answer is delivered with the earlier-question bridge`() {
        val phrased = ScoutBusyBrainDelivery.phraseDelivery("It's 72 degrees.", wasQueued = true)
        assertEquals("And about your earlier question — It's 72 degrees.", phrased)
    }

    @Test fun `an immediately-delivered answer has no bridge`() {
        val phrased = ScoutBusyBrainDelivery.phraseDelivery("It's 72 degrees.", wasQueued = false)
        assertEquals("It's 72 degrees.", phrased)
    }
}
