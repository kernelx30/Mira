package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAreaVisibilityTest {
    @Test
    fun failedEmptyAssistantPlaceholderNeverBecomesAVisibleBubble() {
        val emptyAssistant = ChatMessage(sender = "ai", content = "")

        assertFalse(
            shouldRenderChatTimelineMessage(
                message = emptyAssistant,
                hideAsLoadingPlaceholder = false,
            )
        )
    }

    @Test
    fun completedAssistantContentStillRenders() {
        val assistant = ChatMessage(sender = "ai", content = "reply")

        assertTrue(
            shouldRenderChatTimelineMessage(
                message = assistant,
                hideAsLoadingPlaceholder = false,
            )
        )
    }

    @Test
    fun internalOnlyAssistantMarkupDoesNotLeaveAnEmptyBubble() {
        val internalOnly =
            ChatMessage(
                sender = "ai",
                content = "<think>private reasoning</think><status>done</status>",
            )

        assertFalse(
            shouldRenderChatTimelineMessage(
                message = internalOnly,
                hideAsLoadingPlaceholder = false,
            )
        )
    }

    @Test
    fun markdownListMarkerWithoutBodyDoesNotLeaveAnEmptyBubble() {
        val markerOnly = ChatMessage(sender = "ai", content = "1.")

        assertFalse(
            shouldRenderChatTimelineMessage(
                message = markerOnly,
                hideAsLoadingPlaceholder = false,
            )
        )
    }
}
