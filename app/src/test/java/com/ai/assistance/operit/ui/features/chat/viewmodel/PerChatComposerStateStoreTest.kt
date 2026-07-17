package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatComposerSnapshot
import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerChatComposerStateStoreTest {
    @Test
    fun keepsInitialSharedInputWhenTheFirstChatBecomesAvailable() {
        val store = PerChatComposerStateStore()
        val sharedAttachment = attachment("shared.txt")
        val sharedReply = ChatMessage(sender = "ai", content = "shared", timestamp = 50L)

        val restored =
            store.switchTo(
                nextChatId = "chat-a",
                currentAttachments = listOf(sharedAttachment),
                currentReply = sharedReply,
            )

        assertEquals(listOf(sharedAttachment), restored?.attachments)
        assertEquals(sharedReply, restored?.replyToMessage)
    }

    @Test
    fun preservesEachChatComposerAndConsumesOnlyTheDispatchedChat() {
        val store = PerChatComposerStateStore()
        val attachmentA = attachment("a.txt")
        val attachmentB = attachment("b.txt")
        val replyA = ChatMessage(sender = "ai", content = "reply-a", timestamp = 100L)
        val replyB = ChatMessage(sender = "ai", content = "reply-b", timestamp = 200L)

        store.switchTo("chat-a", emptyList(), null)
        val emptyB = store.switchTo("chat-b", listOf(attachmentA), replyA)
        assertEquals(emptyList<AttachmentInfo>(), emptyB?.attachments)
        assertNull(emptyB?.replyToMessage)

        val restoredA = store.switchTo("chat-a", listOf(attachmentB), replyB)
        assertEquals(listOf(attachmentA), restoredA?.attachments)
        assertEquals(replyA, restoredA?.replyToMessage)

        assertFalse(
            store.consume(
                chatId = "chat-b",
                snapshot =
                    ChatComposerSnapshot(
                        text = "sent in b",
                        attachments = listOf(attachmentB),
                        replyToMessage = replyB,
                    ),
            )
        )

        val restoredB = store.switchTo("chat-b", listOf(attachmentA), replyA)
        assertEquals(emptyList<AttachmentInfo>(), restoredB?.attachments)
        assertNull(restoredB?.replyToMessage)
        assertTrue(
            store.consume(
                chatId = "chat-b",
                snapshot = ChatComposerSnapshot(text = "active"),
            )
        )
    }

    private fun attachment(name: String) =
        AttachmentInfo(
            filePath = "/$name",
            fileName = name,
            mimeType = "text/plain",
            fileSize = 1L,
        )
}
