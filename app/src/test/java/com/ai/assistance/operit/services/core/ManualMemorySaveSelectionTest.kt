package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualMemorySaveSelectionTest {
    @Test
    fun selectsDistinctNonBlankUserMessagesInTimelineOrder() {
        val messages =
            listOf(
                ChatMessage(sender = "ai", content = "reply", timestamp = 40L),
                ChatMessage(sender = "user", content = "later", timestamp = 30L),
                ChatMessage(sender = "user", content = "   ", timestamp = 20L),
                ChatMessage(sender = "user", content = "first", timestamp = 10L),
                ChatMessage(sender = "user", content = "duplicate", timestamp = 10L),
            )

        assertEquals(
            listOf(10L, 30L),
            messages.validUserMessagesForMemory().map { it.timestamp },
        )
    }
}
