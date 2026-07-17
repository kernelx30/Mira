package com.ai.assistance.operit.data

import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.OperitArchivedChat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatArchiveStateTest {
    @Test
    fun `room entity round trip preserves archive state`() {
        val source = ChatHistory(title = "archived", messages = emptyList(), archived = true)

        val entity = ChatEntity.fromChatHistory(source)
        val restored = entity.toChatHistory(emptyList())

        assertTrue(entity.archived)
        assertTrue(restored.archived)
    }

    @Test
    fun `chat export preserves archive state and old default stays active`() {
        val archived = ChatHistory(title = "archived", messages = emptyList(), archived = true)
        val exported = OperitArchivedChat.fromChatHistory(archived, messages = emptyList())

        assertTrue(exported.archived)
        assertTrue(exported.toChatHistory().archived)
        assertFalse(ChatHistory(title = "active", messages = emptyList()).archived)
    }
}
