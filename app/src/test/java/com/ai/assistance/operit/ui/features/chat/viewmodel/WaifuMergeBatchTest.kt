package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WaifuMergeBatchTest {
    @Test
    fun combinesEveryVisibleMessageThroughTheLastSettledSend() {
        val messages =
            listOf(
                pending(sequence = 1L, text = "第一句"),
                pending(sequence = 2L, text = " 第二句 "),
                pending(sequence = 3L, text = "第三句"),
            )

        assertEquals(
            "第一句\n第二句",
            buildWaifuMergeDispatchText(messages, throughSequenceId = 2L),
        )
    }

    @Test
    fun sequenceIdentityDoesNotDropMessagesWithEqualWallClockTimestamps() {
        val messages =
            listOf(
                pending(sequence = 4L, text = "a", timestamp = 100L),
                pending(sequence = 5L, text = "b", timestamp = 100L),
            )

        assertEquals("a\nb", buildWaifuMergeDispatchText(messages, throughSequenceId = 5L))
    }

    @Test
    fun recoversOnlyTrailingMessagesThatWereDisplayedBeforeDispatch() {
        val messages =
            listOf(
                ChatMessage(sender = "ai", content = "之前的回复", timestamp = 1L),
                ChatMessage(
                    sender = "user",
                    content = "第一句",
                    timestamp = 2L,
                    displayMode = ChatMessageDisplayMode.PENDING_DISPATCH,
                ),
                ChatMessage(
                    sender = "user",
                    content = "第二句",
                    timestamp = 3L,
                    displayMode = ChatMessageDisplayMode.PENDING_DISPATCH,
                ),
            )

        assertEquals(
            listOf(2L, 3L),
            recoverablePendingWaifuMessages(messages).map { it.timestamp },
        )
        assertEquals(
            emptyList<ChatMessage>(),
            recoverablePendingWaifuMessages(
                messages + ChatMessage(sender = "ai", content = "已经回复", timestamp = 4L)
            ),
        )
    }

    private fun pending(sequence: Long, text: String, timestamp: Long = sequence) =
        PendingWaifuMergeMessage(
            sequenceId = sequence,
            chatId = "chat-a",
            text = text,
            visibleMessageTimestamp = timestamp,
        )
}
