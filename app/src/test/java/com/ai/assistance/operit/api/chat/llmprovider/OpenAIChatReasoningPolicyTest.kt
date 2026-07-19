package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAIChatReasoningPolicyTest {
    @Test
    fun disabledThinkingOmitsReasoningEffort() {
        assertNull(
            normalizeOpenAiChatReasoningEffort(
                enableThinking = false,
                candidate = "none"
            )
        )
        assertNull(
            normalizeOpenAiChatReasoningEffort(
                enableThinking = false,
                candidate = "high"
            )
        )
    }

    @Test
    fun enabledThinkingKeepsNonBlankEffort() {
        assertEquals(
            "xhigh",
            normalizeOpenAiChatReasoningEffort(
                enableThinking = true,
                candidate = "  xhigh  "
            )
        )
        assertNull(
            normalizeOpenAiChatReasoningEffort(
                enableThinking = true,
                candidate = "  "
            )
        )
    }
}
