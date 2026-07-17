package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollToBottomButtonTest {
    @Test
    fun `button stays hidden while following the latest message`() {
        assertFalse(
            shouldShowScrollToBottomButton(
                autoScrollToBottom = true,
                gestureRequestedButton = false,
            ),
        )
    }

    @Test
    fun `button appears after an external jump disables auto scroll`() {
        assertTrue(
            shouldShowScrollToBottomButton(
                autoScrollToBottom = false,
                gestureRequestedButton = false,
            ),
        )
    }

    @Test
    fun `button appears when newer history exists below the current window`() {
        assertTrue(
            shouldShowScrollToBottomButton(
                autoScrollToBottom = true,
                gestureRequestedButton = false,
                hasNewerDisplayHistory = true,
            ),
        )
    }

    @Test
    fun `gesture request still reveals the button`() {
        assertTrue(
            shouldShowScrollToBottomButton(
                autoScrollToBottom = true,
                gestureRequestedButton = true,
            ),
        )
    }
}
