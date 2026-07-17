package com.ai.assistance.operit.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContentVisibilityTest {
    @Test
    fun markdownStructureWithoutBodyIsNotRenderable() {
        assertFalse(ConversationContentVisibility.hasRenderableAssistantContent("1."))
        assertFalse(ConversationContentVisibility.hasRenderableAssistantContent("-"))
        assertFalse(ConversationContentVisibility.hasRenderableAssistantContent("```"))
    }

    @Test
    fun internalBlocksWithoutConversationTextAreNotRenderable() {
        assertFalse(
            ConversationContentVisibility.hasRenderableAssistantContent(
                "<think>private</think><status>done</status>",
            )
        )
        assertFalse(
            ConversationContentVisibility.hasRenderableAssistantContent(
                "<tool name=\"noop\"></tool>",
            )
        )
    }

    @Test
    fun punctuationAndRealMarkdownContentRemainRenderable() {
        assertTrue(ConversationContentVisibility.hasRenderableAssistantContent("？"))
        assertTrue(ConversationContentVisibility.hasRenderableAssistantContent("1. 真正的内容"))
        assertTrue(ConversationContentVisibility.hasRenderableAssistantContent("![speechless](emoji://speechless)"))
    }
}
