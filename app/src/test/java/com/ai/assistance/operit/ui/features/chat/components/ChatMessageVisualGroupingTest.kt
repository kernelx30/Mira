package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageVisualGroupingTest {
    @Test
    fun `adjacent assistant segments from one request form one visual group`() {
        val messages =
            listOf(
                assistantSegment("第一句", sentAt = 100L),
                assistantSegment("第二句", sentAt = 100L),
                assistantSegment("第三句", sentAt = 100L),
            )

        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = false),
            ChatMessageVisualGrouping.position(messages, 0),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = false, isEnd = false),
            ChatMessageVisualGrouping.position(messages, 1),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = false, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 2),
        )
    }

    @Test
    fun `time separator starts a new visual group`() {
        val messages =
            listOf(
                assistantSegment("第一句", sentAt = 100L),
                assistantSegment("第二句", sentAt = 100L),
            )

        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 0, separatorIndices = setOf(1)),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 1, separatorIndices = setOf(1)),
        )
    }

    @Test
    fun `different request or role is not grouped`() {
        val messages =
            listOf(
                assistantSegment("Zero", sentAt = 100L, roleName = "Zero"),
                assistantSegment("Mira", sentAt = 101L, roleName = "Zero"),
                assistantSegment("Other", sentAt = 101L, roleName = "Other"),
            )

        messages.indices.forEach { index ->
            assertEquals(
                ChatMessageVisualGroupPosition(isStart = true, isEnd = true),
                ChatMessageVisualGrouping.position(messages, index),
            )
        }
    }

    @Test
    fun `legacy assistant segments without turn metrics group by a short timestamp gap`() {
        val messages =
            listOf(
                assistantSegment("first", sentAt = 0L, timestamp = 1_000L),
                assistantSegment("second", sentAt = 0L, timestamp = 2_000L),
                assistantSegment("later", sentAt = 0L, timestamp = 40_000L),
            )

        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = false),
            ChatMessageVisualGrouping.position(messages, 0),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = false, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 1),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 2),
        )
    }

    @Test
    fun `consecutive user messages form a compact visual group`() {
        val messages =
            listOf(
                ChatMessage(sender = "user", content = "first", roleName = "User", timestamp = 1_000L),
                ChatMessage(sender = "user", content = "second", roleName = "User", timestamp = 2_000L),
                assistantSegment("reply", sentAt = 3_000L, timestamp = 3_000L),
            )

        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = false),
            ChatMessageVisualGrouping.position(messages, 0),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = false, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 1),
        )
        assertEquals(
            ChatMessageVisualGroupPosition(isStart = true, isEnd = true),
            ChatMessageVisualGrouping.position(messages, 2),
        )
    }

    @Test
    fun `starter actions stop scanning once a real conversation exists`() {
        assertTrue(shouldShowStarterActions(listOf(assistantSegment("opening", sentAt = 0L))))
        assertFalse(
            shouldShowStarterActions(
                listOf(
                    ChatMessage(sender = "user", content = "hello"),
                    assistantSegment("reply", sentAt = 1L),
                )
            )
        )
    }

    private fun assistantSegment(
        content: String,
        sentAt: Long,
        roleName: String = "Zero",
        timestamp: Long = System.currentTimeMillis(),
    ) =
        ChatMessage(
            sender = "ai",
            content = content,
            sentAt = sentAt,
            roleName = roleName,
            timestamp = timestamp,
        )
}
