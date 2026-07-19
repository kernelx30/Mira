package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistorySearchQueryTest {
    @Test
    fun `escapes sqlite like wildcard characters as literals`() {
        assertEquals(
            "100\\%\\_done\\\\next",
            escapeLikeSearchLiteral("100%_done\\next"),
        )
    }

    @Test
    fun `leaves ordinary chinese and latin text unchanged`() {
        assertEquals("Mira 聊天", escapeLikeSearchLiteral("Mira 聊天"))
    }
}
