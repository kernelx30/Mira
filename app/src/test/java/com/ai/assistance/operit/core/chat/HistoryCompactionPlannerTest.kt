package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCompactionPlannerTest {
    @Test
    fun keepsRecentTailAndCutsAfterAssistantTurn() {
        val messages = (1L..14L).map { timestamp ->
            ChatMessage(
                sender = if (timestamp % 2L == 0L) "ai" else "user",
                content = "message-$timestamp",
                timestamp = timestamp
            )
        }

        val rawPlan = HistoryCompactionPlanner.plan(messages)
        assertNotNull(rawPlan)
        val plan = requireNotNull(rawPlan)

        assertEquals(6, plan.compactedMessageCount)
        assertEquals(8, plan.protectedMessageCount)
        assertEquals(6L, plan.beforeTimestamp)
        assertEquals(7L, plan.afterTimestamp)
        assertEquals(6, plan.summaryInputMessages.size)
    }

    @Test
    fun carriesPreviousSummaryIntoNewSummaryInput() {
        val messages = buildList {
            add(ChatMessage(sender = "summary", content = "old", timestamp = 10L))
            (11L..22L).forEach { timestamp ->
                add(
                    ChatMessage(
                        sender = if (timestamp % 2L == 0L) "ai" else "user",
                        content = "message-$timestamp",
                        timestamp = timestamp
                    )
                )
            }
        }

        val rawPlan = HistoryCompactionPlanner.plan(messages)
        assertNotNull(rawPlan)
        val plan = requireNotNull(rawPlan)

        assertEquals("summary", plan.summaryInputMessages.first().sender)
        assertEquals(4, plan.compactedMessageCount)
        assertEquals(8, plan.protectedMessageCount)
        assertEquals(5, plan.summaryInputMessages.size)
    }

    @Test
    fun skipsWhenThereIsNoCompletedPrefix() {
        val messages = listOf(
            ChatMessage(sender = "user", content = "one", timestamp = 1L),
            ChatMessage(sender = "user", content = "two", timestamp = 2L),
            ChatMessage(sender = "user", content = "three", timestamp = 3L),
            ChatMessage(sender = "user", content = "four", timestamp = 4L)
        )

        assertNull(HistoryCompactionPlanner.plan(messages))
    }
}
