package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextUserMessageSuggestionPolicyTest {
    @Test
    fun contextRequiresLatestVisibleMessageToBeAssistant() {
        val context =
            NextUserMessageSuggestionPolicy.buildContext(
                listOf(
                    NextUserSuggestionMessage("user", "今天好累", 1L),
                    NextUserSuggestionMessage("ai", "<think>internal</think>辛苦了，想聊聊吗？", 2L),
                    NextUserSuggestionMessage("user", "", 3L),
                )
            )

        assertEquals(2, context.size)
        assertEquals("辛苦了，想聊聊吗？", context.last().content)
    }

    @Test
    fun contextIsEmptyWhileLatestConversationMessageIsUser() {
        val context =
            NextUserMessageSuggestionPolicy.buildContext(
                listOf(
                    NextUserSuggestionMessage("ai", "你今天怎么样？", 1L),
                    NextUserSuggestionMessage("user", "刚下班", 2L),
                )
            )

        assertTrue(context.isEmpty())
    }

    @Test
    fun sanitizesUserPrefixQuotesAndThinkingMarkup() {
        val suggestion =
            NextUserMessageSuggestionPolicy.sanitizeSuggestion(
                "<think>draft</think>\n\"用户可能会说：那你陪我聊一会儿吧\""
            )

        assertEquals("那你陪我聊一会儿吧", suggestion)
    }

    @Test
    fun rejectsAssistantPerspectiveOutput() {
        assertEquals(
            "",
            NextUserMessageSuggestionPolicy.sanitizeSuggestion("Assistant: Of course, I can help."),
        )
    }
}
