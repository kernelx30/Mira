package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MiraThinkingGearTest {
    @Test
    fun `disabled thinking is displayed as gear zero`() {
        assertEquals(0, resolveMiraThinkingGear(false, 5))
    }

    @Test
    fun `enabled thinking exposes the actual configured gear`() {
        assertEquals(1, resolveMiraThinkingGear(true, 1))
        assertEquals(3, resolveMiraThinkingGear(true, 3))
        assertEquals(5, resolveMiraThinkingGear(true, 5))
    }

    @Test
    fun `thinking gear is clamped to the supported range`() {
        assertEquals(1, resolveMiraThinkingGear(true, -1))
        assertEquals(4, resolveMiraThinkingGear(true, 5, maxThinkingQualityLevel = 4))
    }
}
