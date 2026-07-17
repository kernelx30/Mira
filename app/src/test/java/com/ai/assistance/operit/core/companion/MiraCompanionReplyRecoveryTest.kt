package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiraCompanionReplyRecoveryTest {
    @Test
    fun `completed segmented reply is recovered after a hidden trigger`() {
        val marker = MiraCompanionContract.promptMarker("event-1")
        val recovered =
            MiraCompanionReplyRecovery.find(
                messages =
                    listOf(
                        ChatMessage(
                            sender = "user",
                            content = marker,
                            timestamp = 100,
                            displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER,
                        ),
                        ChatMessage(sender = "ai", content = "先别拖了。", timestamp = 101, completedAt = 120),
                        ChatMessage(sender = "ai", content = "报告现在交。", timestamp = 102, completedAt = 120),
                    ),
                memoryUuid = "event-1",
            )

        assertEquals("先别拖了。\n报告现在交。", recovered?.content)
        assertEquals(100L, recovered?.triggerTimestamp)
    }

    @Test
    fun `streaming snapshot is not mistaken for a delivered reply`() {
        val marker = MiraCompanionContract.promptMarker("event-2")
        val recovered =
            MiraCompanionReplyRecovery.find(
                messages =
                    listOf(
                        ChatMessage(
                            sender = "user",
                            content = marker,
                            timestamp = 200,
                            displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER,
                        ),
                        ChatMessage(sender = "ai", content = "half a", timestamp = 201, completedAt = 0),
                    ),
                memoryUuid = "event-2",
            )

        assertNull(recovered)
    }
}
