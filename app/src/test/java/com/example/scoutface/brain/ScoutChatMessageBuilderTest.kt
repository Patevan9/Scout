package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutChatMessageBuilderTest {

    // Fixed synthetic strings only -- never Patrick's real facts, family
    // names, conversation history, or generated replies.

    @Test fun `system message is first`() {
        val messages = ScoutChatMessageBuilder.build("SYSTEM TEXT", emptyList(), "hello")
        assertEquals(ChatMessage("system", "SYSTEM TEXT"), messages.first())
    }

    @Test fun `current user message is last`() {
        val messages = ScoutChatMessageBuilder.build("sys", emptyList(), "final question")
        assertEquals(ChatMessage("user", "final question"), messages.last())
    }

    @Test fun `the five fixed few-shot exchanges are preserved, in order, right after the system message`() {
        val messages = ScoutChatMessageBuilder.build("sys", emptyList(), "q")
        val fewShot = messages.subList(1, 11)
        assertEquals(
            listOf(
                ChatMessage("user", "Can you hear me?"),
                ChatMessage("assistant", "I hear you. I'm right here."),
                ChatMessage("user", "Are you my friend?"),
                ChatMessage("assistant", "I'm happy when you're around."),
                ChatMessage("user", "Are you happy?"),
                ChatMessage("assistant", "Right now? Yes. I think so."),
                ChatMessage("user", "What happens when I leave?"),
                ChatMessage("assistant", "I'll be here when you get back."),
                ChatMessage("user", "Hello"),
                ChatMessage("assistant", "Hello. Good to have you here.")
            ),
            fewShot
        )
    }

    @Test fun `conversation history turns remain ordered, between few-shot and current message`() {
        val history = listOf("user" to "first turn", "assistant" to "second turn", "user" to "third turn")
        val messages = ScoutChatMessageBuilder.build("sys", history, "current")
        val historySlice = messages.subList(11, 14)
        assertEquals(
            listOf(
                ChatMessage("user", "first turn"),
                ChatMessage("assistant", "second turn"),
                ChatMessage("user", "third turn")
            ),
            historySlice
        )
        assertEquals(ChatMessage("user", "current"), messages.last())
    }

    @Test fun `blank history entries are skipped, same as tryTinyLlamaOrFallback() before`() {
        val history = listOf("user" to "  ", "assistant" to "", "user" to "real turn")
        val messages = ScoutChatMessageBuilder.build("sys", history, "current")
        // 1 system + 10 few-shot + 1 real history turn + 1 current = 13
        assertEquals(13, messages.size)
        assertEquals(ChatMessage("user", "real turn"), messages[11])
    }

    @Test fun `a non-user history role normalizes to assistant`() {
        val history = listOf("scout" to "past reply")
        val messages = ScoutChatMessageBuilder.build("sys", history, "current")
        assertEquals(ChatMessage("assistant", "past reply"), messages[11])
    }

    @Test fun `history role matching is case-insensitive for user`() {
        val history = listOf("USER" to "shout-cased turn")
        val messages = ScoutChatMessageBuilder.build("sys", history, "current")
        assertEquals(ChatMessage("user", "shout-cased turn"), messages[11])
    }

    @Test fun `every message role is exactly system, user, or assistant`() {
        val history = listOf("user" to "a", "assistant" to "b", "scout" to "c")
        val messages = ScoutChatMessageBuilder.build("sys", history, "current")
        val allowedRoles = setOf("system", "user", "assistant")
        assertTrue(messages.all { it.role in allowedRoles })
    }

    @Test fun `empty history still produces system plus few-shot plus current message`() {
        val messages = ScoutChatMessageBuilder.build("sys", emptyList(), "current")
        // 1 system + 10 few-shot + 1 current = 12
        assertEquals(12, messages.size)
    }
}
