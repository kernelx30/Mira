package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChatHistoryForDispatchTest {
    @Test
    fun excludesVisibleMessagesThatAreRecombinedIntoTheCurrentRequest() {
        val history =
            listOf(
                message(timestamp = 10L, content = "older"),
                message(timestamp = 20L, content = "first merged line"),
                message(timestamp = 30L, content = "second merged line"),
            )

        assertEquals(
            listOf(history.first()),
            chatHistoryForDispatch(history, excludedMessageTimestamps = setOf(20L, 30L)),
        )
    }

    @Test
    fun reusesTheOriginalHistoryWhenNothingIsExcluded() {
        val history = listOf(message(timestamp = 10L, content = "message"))

        assertSame(history, chatHistoryForDispatch(history, emptySet()))
    }

    private fun message(timestamp: Long, content: String) =
        ChatMessage(sender = "user", content = content, timestamp = timestamp)
}
