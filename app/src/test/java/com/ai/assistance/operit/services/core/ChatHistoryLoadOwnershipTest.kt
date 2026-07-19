package com.ai.assistance.operit.services.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryLoadOwnershipTest {
    @Test
    fun staleChatLoadCannotReplaceCurrentTimeline() {
        assertTrue(shouldApplyCurrentChatLoad(requestedChatId = "chat-b", currentChatId = "chat-b"))
        assertFalse(shouldApplyCurrentChatLoad(requestedChatId = "chat-a", currentChatId = "chat-b"))
        assertFalse(shouldApplyCurrentChatLoad(requestedChatId = "chat-a", currentChatId = null))
    }
}
