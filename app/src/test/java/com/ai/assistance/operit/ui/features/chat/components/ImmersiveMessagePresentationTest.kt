package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveMessagePresentationTest {
    @Test
    fun explicitChineseSentenceEndCreatesSeparateBubbles() {
        assertEquals(
            listOf(
                "你好呀。",
                "刚才那段话居然重复了一遍，像我紧张到自我介绍了两次（笑）。",
            ),
            ImmersiveMessagePresentation.splitText(
                "你好呀。刚才那段话居然重复了一遍，像我紧张到自我介绍了两次（笑）。"
            ),
        )
    }

    @Test
    fun ellipsisWithoutWhitespaceStaysWithFollowingSentence() {
        assertEquals(
            listOf("啊……这种玩笑最好不要开哦。"),
            ImmersiveMessagePresentation.splitText("啊……这种玩笑最好不要开哦。"),
        )
    }

    @Test
    fun englishPeriodOnlySplitsAtWhitespaceBoundary() {
        assertEquals(
            listOf("Version 1.2 is ready.", "Try it now."),
            ImmersiveMessagePresentation.splitText("Version 1.2 is ready. Try it now."),
        )
    }

    @Test
    fun inlineCodeDoesNotCreateFalseSentenceBoundary() {
        assertEquals(
            listOf("Run `foo?.bar()` first.", "Then continue."),
            ImmersiveMessagePresentation.splitText("Run `foo?.bar()` first. Then continue."),
        )
    }
}
